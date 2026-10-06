package com.syarif.kasir.ui.pelanggan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.util.effectiveSisa
import com.syarif.kasir.util.parseDateToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PelangganFilter {
    SEMUA,
    PIUTANG
}

data class PelangganDialogState(
    val isEditing: Boolean = false,
    val id: Int = 0,
    val nama: String = "",
    val telepon: String = "",
    val alamat: String = "",
    val errorMessage: String? = null
)

data class PelangganUiState(
    val searchQuery: String = "",
    val selectedFilter: PelangganFilter = PelangganFilter.SEMUA,
    val pelangganList: List<PelangganEntity> = emptyList(),
    val filteredPelangganList: List<PelangganEntity> = emptyList(),
    val piutangMap: Map<Int, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val isDialogOpen: Boolean = false,
    val dialogState: PelangganDialogState = PelangganDialogState(),
    val selectedPelangganForDetail: PelangganEntity? = null,
    val isDetailDialogOpen: Boolean = false,
    val customerTransactions: List<TransaksiWithDetails> = emptyList(),
    val snackbarMessage: String? = null
)

open class PelangganViewModel(
    private val repository: KasirRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PelangganUiState(isLoading = true))
    val uiState: StateFlow<PelangganUiState> = _uiState.asStateFlow()

    private val _allTransaksiList = MutableStateFlow<List<TransaksiWithDetails>>(emptyList())

    init {
        loadPelangganList()
        loadTransaksiList()
    }

    private fun loadPelangganList() {
        viewModelScope.launch {
            repository.getAllPelanggan().collectLatest { list ->
                _uiState.update { currentState ->
                    val piutangMap = list.associate { it.id to calculateTotalPiutang(it, _allTransaksiList.value) }
                    val filtered = filterPelangganList(list, currentState.searchQuery, currentState.selectedFilter, _allTransaksiList.value)
                    currentState.copy(
                        pelangganList = list,
                        filteredPelangganList = filtered,
                        piutangMap = piutangMap,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadTransaksiList() {
        viewModelScope.launch {
            repository.getAllTransaksiWithDetails().collectLatest { list ->
                _allTransaksiList.value = list
                _uiState.update { state ->
                    val piutangMap = state.pelangganList.associate { it.id to calculateTotalPiutang(it, list) }
                    val filtered = filterPelangganList(state.pelangganList, state.searchQuery, state.selectedFilter, list)
                    val customer = state.selectedPelangganForDetail
                    val customerTx = if (customer != null) getTransactionsForCustomer(customer, list) else state.customerTransactions
                    state.copy(
                        filteredPelangganList = filtered,
                        piutangMap = piutangMap,
                        customerTransactions = customerTx
                    )
                }
            }
        }
    }

    fun calculateTotalPiutang(customer: PelangganEntity, allTx: List<TransaksiWithDetails>): Double {
        return allTx.filter { tx ->
            (tx.transaksi.pelangganId == customer.id) ||
                    (!customer.nama.isBlank() && tx.transaksi.namaPelanggan?.equals(customer.nama, ignoreCase = true) == true)
        }.sumOf { it.effectiveSisa }
    }

    private fun getTransactionsForCustomer(
        customer: PelangganEntity,
        allTx: List<TransaksiWithDetails>
    ): List<TransaksiWithDetails> {
        val oneYearAgo = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L
        return allTx.filter { tx ->
            val matchCustomer = (tx.transaksi.pelangganId == customer.id) ||
                    (!customer.nama.isBlank() && tx.transaksi.namaPelanggan?.equals(customer.nama, ignoreCase = true) == true)
            if (!matchCustomer) return@filter false

            val txTime = if (tx.transaksi.tanggal > 0L) tx.transaksi.tanggal else parseDateToLong(tx.transaksi.waktuTransaksi, 0L)
            txTime >= oneYearAgo || txTime == 0L
        }.sortedByDescending { it.transaksi.tanggal }
    }

    fun openDetailDialog(pelanggan: PelangganEntity) {
        val transactions = getTransactionsForCustomer(pelanggan, _allTransaksiList.value)
        _uiState.update {
            it.copy(
                selectedPelangganForDetail = pelanggan,
                isDetailDialogOpen = true,
                customerTransactions = transactions
            )
        }
    }

    fun closeDetailDialog() {
        _uiState.update {
            it.copy(
                isDetailDialogOpen = false,
                selectedPelangganForDetail = null,
                customerTransactions = emptyList()
            )
        }
    }

    fun markTransaksiAsLunas(transaksiId: Int) {
        markTransaksiAsPelunasan(transaksiId.toLong(), "TUNAI", 0.0, 0.0)
    }

    fun markTransaksiAsPelunasan(transaksiId: Long, metode: String, uangDp: Double, sisa: Double) {
        viewModelScope.launch {
            try {
                repository.pelunasanTransaksi(transaksiId, metode, uangDp, sisa)
                _uiState.update { state ->
                    val customer = state.selectedPelangganForDetail
                    val updatedTxList = if (customer != null) getTransactionsForCustomer(customer, _allTransaksiList.value) else state.customerTransactions
                    state.copy(
                        customerTransactions = updatedTxList,
                        snackbarMessage = "Pelunasan transaksi berhasil disimpan ($metode)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Gagal memproses pelunasan: ${e.message}")
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { currentState ->
            val filtered = filterPelangganList(currentState.pelangganList, query, currentState.selectedFilter, _allTransaksiList.value)
            currentState.copy(
                searchQuery = query,
                filteredPelangganList = filtered
            )
        }
    }

    fun onFilterSelected(filter: PelangganFilter) {
        _uiState.update { currentState ->
            val filtered = filterPelangganList(currentState.pelangganList, currentState.searchQuery, filter, _allTransaksiList.value)
            currentState.copy(
                selectedFilter = filter,
                filteredPelangganList = filtered
            )
        }
    }

    private fun filterPelangganList(
        list: List<PelangganEntity>,
        query: String,
        filter: PelangganFilter,
        allTx: List<TransaksiWithDetails>
    ): List<PelangganEntity> {
        val q = query.trim()
        return list.filter { item ->
            val matchesSearch = q.isBlank() ||
                    item.nama.contains(q, ignoreCase = true) ||
                    (item.telepon?.contains(q, ignoreCase = true) == true) ||
                    (item.alamat?.contains(q, ignoreCase = true) == true)

            val totalPiutang = calculateTotalPiutang(item, allTx)
            val matchesFilter = when (filter) {
                PelangganFilter.SEMUA -> true
                PelangganFilter.PIUTANG -> totalPiutang > 0.0
            }

            matchesSearch && matchesFilter
        }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isDialogOpen = true,
                dialogState = PelangganDialogState()
            )
        }
    }

    fun openEditDialog(pelanggan: PelangganEntity) {
        _uiState.update {
            it.copy(
                isDialogOpen = true,
                dialogState = PelangganDialogState(
                    isEditing = true,
                    id = pelanggan.id,
                    nama = pelanggan.nama,
                    telepon = pelanggan.telepon ?: "",
                    alamat = pelanggan.alamat ?: ""
                )
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isDialogOpen = false) }
    }

    fun updateDialogNama(nama: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(nama = nama, errorMessage = null)) }
    }

    fun updateDialogTelepon(telepon: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(telepon = telepon)) }
    }

    fun updateDialogAlamat(alamat: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(alamat = alamat)) }
    }

    fun savePelanggan() {
        val dState = _uiState.value.dialogState
        if (dState.nama.isBlank()) {
            _uiState.update { it.copy(dialogState = it.dialogState.copy(errorMessage = "Nama pelanggan tidak boleh kosong")) }
            return
        }

        val pelangganEntity = PelangganEntity(
            id = if (dState.isEditing) dState.id else 0,
            nama = dState.nama.trim(),
            telepon = dState.telepon.ifBlank { null },
            alamat = dState.alamat.ifBlank { null },
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                if (dState.isEditing) {
                    repository.updatePelanggan(pelangganEntity)
                    _uiState.update {
                        it.copy(
                            isDialogOpen = false,
                            snackbarMessage = "Data pelanggan berhasil diperbarui"
                        )
                    }
                } else {
                    repository.insertPelanggan(pelangganEntity)
                    _uiState.update {
                        it.copy(
                            isDialogOpen = false,
                            snackbarMessage = "Pelanggan baru berhasil ditambahkan"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(dialogState = it.dialogState.copy(errorMessage = "Gagal menyimpan: ${e.message}"))
                }
            }
        }
    }

    fun deletePelanggan(pelanggan: PelangganEntity) {
        viewModelScope.launch {
            try {
                repository.deletePelanggan(pelanggan)
                _uiState.update { it.copy(snackbarMessage = "Pelanggan '${pelanggan.nama}' berhasil dihapus") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Gagal menghapus: ${e.message}") }
            }
        }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
