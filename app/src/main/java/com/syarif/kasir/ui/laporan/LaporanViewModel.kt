package com.syarif.kasir.ui.laporan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.util.parseDateToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class LaporanPeriod(val label: String) {
    HARI_INI("Hari Ini"),
    KEMARIN("Kemarin"),
    BULAN_INI("Bulan Ini"),
    BULAN_LALU("Bulan Lalu"),
    PILIH_TANGGAL("Pilih Tanggal"),
    SEMUA("Semua")
}

data class BestSellerItem(
    val barangId: Int,
    val namaBarang: String,
    val totalQty: Int,
    val totalRevenue: Double
)

data class LaporanUiState(
    val selectedPeriod: LaporanPeriod = LaporanPeriod.HARI_INI,
    val selectedDateMillis: Long = System.currentTimeMillis(),
    val showDatePickerDialog: Boolean = false,
    val totalOmzet: Double = 0.0,
    val totalLabaBersih: Double = 0.0,
    val totalTransaksi: Int = 0,
    val bestSellerList: List<BestSellerItem> = emptyList(),
    val kasAwalInput: String = "0",
    val kasAwal: Double = 0.0,
    val pemasukanTunai: Double = 0.0,
    val pemasukanTransfer: Double = 0.0,
    val pemasukanDp: Double = 0.0,
    val kasAkhir: Double = 0.0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null
)

class LaporanViewModel(
    private val repository: KasirRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaporanUiState())
    val uiState: StateFlow<LaporanUiState> = _uiState.asStateFlow()

    private val _transaksiList = MutableStateFlow<List<TransaksiWithDetails>>(emptyList())
    private val _barangList = MutableStateFlow<List<BarangEntity>>(emptyList())

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAllTransaksiWithDetails().collect { list ->
                _transaksiList.value = list
                recalculateMetrics()
            }
        }
        viewModelScope.launch {
            repository.getAllBarang().collect { list ->
                _barangList.value = list
                recalculateMetrics()
            }
        }
    }

    fun setPeriod(period: LaporanPeriod) {
        if (period == LaporanPeriod.PILIH_TANGGAL) {
            _uiState.update { it.copy(selectedPeriod = period, showDatePickerDialog = true) }
        } else {
            _uiState.update { it.copy(selectedPeriod = period) }
            recalculateMetrics()
        }
    }

    fun setSelectedDate(dateMillis: Long) {
        _uiState.update {
            it.copy(
                selectedPeriod = LaporanPeriod.PILIH_TANGGAL,
                selectedDateMillis = dateMillis,
                showDatePickerDialog = false
            )
        }
        recalculateMetrics()
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePickerDialog = false) }
    }

    fun onKasAwalChanged(input: String) {
        val sanitized = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val kasAwalVal = sanitized.toDoubleOrNull() ?: 0.0
        _uiState.update { state ->
            val expectedCash = kasAwalVal + state.pemasukanTunai + state.pemasukanDp
            state.copy(
                kasAwalInput = input,
                kasAwal = kasAwalVal,
                kasAkhir = expectedCash
            )
        }
    }

    fun recalculateMetrics() {
        val state = _uiState.value
        val period = state.selectedPeriod
        val allTx = _transaksiList.value
        val allBarang = _barangList.value
        val barangMap = allBarang.associateBy { it.id }

        val range = getStartAndEndMillis(period, state.selectedDateMillis)

        // Filter transactions for selected period
        val filteredTx = if (period == LaporanPeriod.SEMUA) {
            allTx
        } else {
            allTx.filter { tx ->
                val txTime = if (tx.transaksi.tanggal > 0L) {
                    tx.transaksi.tanggal
                } else {
                    parseDateToLong(tx.transaksi.waktuTransaksi, 0L)
                }
                txTime in range
            }
        }

        // 1. Total Omzet (Gross Revenue)
        val totalOmzet = filteredTx.sumOf { it.transaksi.totalHarga }

        // 2. Total Transaksi
        val totalTransaksi = filteredTx.size

        // 3. Laba Bersih (Net Profit = Total Revenue - Cost of Goods Sold)
        var totalHpp = 0.0
        filteredTx.forEach { tx ->
            tx.details.forEach { detail ->
                val hargaBeli = barangMap[detail.barangId]?.hargaBeli ?: 0.0
                totalHpp += detail.jumlah * hargaBeli
            }
        }
        val totalLabaBersih = totalOmzet - totalHpp

        // 4. Best Seller products list (ranked by total qty sold and total revenue)
        data class BestSellerAcc(
            val barangId: Int,
            val namaBarang: String,
            var totalQty: Int,
            var totalRevenue: Double
        )

        val bestSellerGroupMap = mutableMapOf<String, BestSellerAcc>()

        filteredTx.forEach { tx ->
            tx.details.forEach { detail ->
                val key = if (detail.barangId > 0) {
                    "ID_${detail.barangId}"
                } else {
                    "NAME_${detail.namaBarang.trim().lowercase()}"
                }

                val existing = bestSellerGroupMap[key]
                if (existing == null) {
                    bestSellerGroupMap[key] = BestSellerAcc(
                        barangId = detail.barangId,
                        namaBarang = detail.namaBarang.ifBlank { "Produk Tanpa Nama" },
                        totalQty = detail.jumlah,
                        totalRevenue = detail.subtotal
                    )
                } else {
                    existing.totalQty += detail.jumlah
                    existing.totalRevenue += detail.subtotal
                }
            }
        }

        val bestSellerList = bestSellerGroupMap.values
            .map { acc ->
                BestSellerItem(
                    barangId = acc.barangId,
                    namaBarang = acc.namaBarang,
                    totalQty = acc.totalQty,
                    totalRevenue = acc.totalRevenue
                )
            }
            .sortedWith(
                compareByDescending<BestSellerItem> { it.totalQty }
                    .thenByDescending { it.totalRevenue }
            )

        // 5. Shift & Cash Drawer Recap (Pemasukan Tunai [Tunai + Pas], DP) & Total Pemasukan Transfer
        var pemasukanTunai = 0.0
        var pemasukanTransfer = 0.0
        var pemasukanDp = 0.0

        filteredTx.forEach { tx ->
            val method = tx.transaksi.metodePembayaran.trim()
            val isTransfer = method.contains("transfer", ignoreCase = true)
            val isDp = method.contains("bon", ignoreCase = true) || method.contains("dp", ignoreCase = true) || (tx.transaksi.dp > 0.0 && !method.contains("TUNAI", ignoreCase = true) && !method.contains("PAS", ignoreCase = true) && !isTransfer)
            val isTunaiOrPas = method.isBlank() || method.contains("TUNAI", ignoreCase = true) || method.contains("PAS", ignoreCase = true) || method.contains("CASH", ignoreCase = true)

            when {
                isTransfer -> {
                    pemasukanTransfer += tx.transaksi.totalHarga
                }
                isDp -> {
                    pemasukanDp += tx.transaksi.dp
                }
                isTunaiOrPas -> {
                    pemasukanTunai += tx.transaksi.totalHarga
                }
                else -> {
                    pemasukanTunai += tx.transaksi.totalHarga
                }
            }
        }

        val kasAwal = _uiState.value.kasAwal
        val kasAkhir = kasAwal + pemasukanTunai + pemasukanDp

        _uiState.update { state ->
            state.copy(
                totalOmzet = totalOmzet,
                totalLabaBersih = totalLabaBersih,
                totalTransaksi = totalTransaksi,
                bestSellerList = bestSellerList,
                pemasukanTunai = pemasukanTunai,
                pemasukanTransfer = pemasukanTransfer,
                pemasukanDp = pemasukanDp,
                kasAkhir = kasAkhir
            )
        }
    }

    private fun getStartAndEndMillis(period: LaporanPeriod, customDateMillis: Long): LongRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return when (period) {
            LaporanPeriod.HARI_INI -> {
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val end = cal.timeInMillis - 1
                start..end
            }
            LaporanPeriod.KEMARIN -> {
                val end = cal.timeInMillis - 1
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val start = cal.timeInMillis
                start..end
            }
            LaporanPeriod.BULAN_INI -> {
                val start = cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                val end = System.currentTimeMillis()
                start..end
            }
            LaporanPeriod.BULAN_LALU -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val end = cal.timeInMillis - 1
                cal.add(Calendar.MONTH, -1)
                val start = cal.timeInMillis
                start..end
            }
            LaporanPeriod.PILIH_TANGGAL -> {
                cal.timeInMillis = customDateMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val end = cal.timeInMillis - 1
                start..end
            }
            LaporanPeriod.SEMUA -> {
                0L..Long.MAX_VALUE
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                repository.refreshTransaksiHistory()
            } catch (_: Exception) {}
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
