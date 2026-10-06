package com.syarif.kasir.ui.riwayat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.repository.KasirRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RiwayatUiState(
    val transaksiList: List<TransaksiWithDetails> = emptyList(),
    val filteredTransaksiList: List<TransaksiWithDetails> = emptyList(),
    val searchQuery: String = "",
    val selectedTransaksi: TransaksiWithDetails? = null,
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null
)

class RiwayatViewModel(
    private val repository: KasirRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RiwayatUiState())
    val uiState: StateFlow<RiwayatUiState> = _uiState.asStateFlow()

    init {
        loadTransaksi()
    }

    private fun loadTransaksi() {
        viewModelScope.launch {
            repository.getAllTransaksiWithDetails().collectLatest { list ->
                _uiState.update { state ->
                    val filtered = filterTransaksi(list, state.searchQuery)
                    state.copy(
                        transaksiList = list,
                        filteredTransaksiList = filtered,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = filterTransaksi(state.transaksiList, query)
            state.copy(searchQuery = query, filteredTransaksiList = filtered)
        }
    }

    private fun filterTransaksi(list: List<TransaksiWithDetails>, query: String): List<TransaksiWithDetails> {
        if (query.isBlank()) return list
        return list.filter { item ->
            item.transaksi.noFaktur.contains(query, ignoreCase = true) ||
                    (item.transaksi.namaPelanggan?.contains(query, ignoreCase = true) == true) ||
                    item.transaksi.metodePembayaran.contains(query, ignoreCase = true)
        }
    }

    fun selectTransaksi(transaksi: TransaksiWithDetails?) {
        if (transaksi == null) {
            _uiState.update { it.copy(selectedTransaksi = null) }
        } else {
            _uiState.update { it.copy(selectedTransaksi = transaksi) }
            viewModelScope.launch {
                val result = repository.getTransaksiDetail(transaksi.transaksi.id.toLong())
                if (result is com.syarif.kasir.data.sync.SyncResult.Success) {
                    val dto = result.data
                    val existingEntity = transaksi.transaksi
                    val newWaktu = if (!dto.waktuTransaksi.isNullOrBlank()) dto.waktuTransaksi 
                        else if (!dto.rawTanggal.isNullOrBlank()) dto.rawTanggal 
                        else existingEntity.waktuTransaksi
                    val parsedTanggal = com.syarif.kasir.util.parseDateToLong(dto.tanggal, fallback = 0L)
                    val finalTanggal = if (parsedTanggal > 0L) parsedTanggal else existingEntity.tanggal
                    val updatedEntity = com.syarif.kasir.data.local.entity.TransaksiEntity(
                        id = dto.id ?: existingEntity.id,
                        noFaktur = dto.noFaktur.ifBlank { existingEntity.noFaktur },
                        pelangganId = dto.pelangganId ?: existingEntity.pelangganId,
                        namaPelanggan = dto.namaPelanggan ?: existingEntity.namaPelanggan,
                        tanggal = finalTanggal,
                        waktuTransaksi = newWaktu,
                        totalHarga = if (dto.totalHarga > 0.0) dto.totalHarga else existingEntity.totalHarga,
                        diskon = if (dto.diskon > 0.0) dto.diskon else existingEntity.diskon,
                        bayar = if (dto.bayar > 0.0) dto.bayar else existingEntity.bayar,
                        uangBayar = if (dto.uangBayar > 0.0) dto.uangBayar else (if (existingEntity.uangBayar > 0.0) existingEntity.uangBayar else existingEntity.bayar),
                        dp = if (dto.dp > 0.0) dto.dp else existingEntity.dp,
                        sisa = if (dto.sisa > 0.0) dto.sisa else existingEntity.sisa,
                        kembali = if (dto.kembali > 0.0) dto.kembali else existingEntity.kembali,
                        metodePembayaran = dto.metodePembayaran.ifBlank { existingEntity.metodePembayaran },
                        isSynced = true,
                        updatedAt = existingEntity.updatedAt
                    )
                    val updatedDetails = if (dto.details.isNotEmpty()) {
                        dto.details.map { d ->
                            com.syarif.kasir.data.local.entity.DetailTransaksiEntity(
                                id = d.id ?: 0,
                                transaksiId = dto.id ?: transaksi.transaksi.id,
                                barangId = d.barangId,
                                namaBarang = d.namaBarang,
                                jumlah = d.jumlah,
                                hargaSatuan = d.hargaSatuan,
                                subtotal = d.subtotal
                            )
                        }
                    } else {
                        transaksi.details
                    }
                    val updatedWithDetails = TransaksiWithDetails(
                        transaksi = updatedEntity,
                        details = updatedDetails
                    )
                    _uiState.update { state ->
                        if (state.selectedTransaksi?.transaksi?.id == transaksi.transaksi.id) {
                            state.copy(selectedTransaksi = updatedWithDetails)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun deleteTransaksi(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteTransaksi(id)
                _uiState.update { state ->
                    state.copy(
                        selectedTransaksi = null,
                        snackbarMessage = "Transaksi berhasil dihapus"
                    )
                }
                refreshTransaksi()
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "Gagal menghapus transaksi: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshTransaksi() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.refreshTransaksiHistory()
            } catch (_: Exception) {}
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() = refreshTransaksi()

    fun refreshHistory() = refreshTransaksi()

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
