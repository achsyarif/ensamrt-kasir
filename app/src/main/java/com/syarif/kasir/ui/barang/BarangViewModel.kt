package com.syarif.kasir.ui.barang

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.data.sync.SyncSummary
import com.syarif.kasir.util.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TieredPriceFormState(
    val id: Int = 0,
    val minQty: String = "",
    val hargaGrosir: String = ""
)

data class BarangDialogState(
    val isEditing: Boolean = false,
    val barangId: Int = 0,
    val kodeBarang: String = "",
    val namaBarang: String = "",
    val kategori: String = "",
    val hargaBeli: String = "",
    val hargaJual: String = "",
    val stok: String = "",
    val satuan: String = "pcs",
    val barcode: String = "",
    val tieredPrices: List<TieredPriceFormState> = emptyList(),
    val errorMessage: String? = null
)

data class BarangUiState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList(),
    val barangList: List<BarangWithHargaGrosir> = emptyList(),
    val filteredBarangList: List<BarangWithHargaGrosir> = emptyList(),
    val selectedSortOption: SortOption = SortOption.AZ,
    val isCategoryDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val showSyncDialog: Boolean = false,
    val syncSummary: SyncSummary? = null,
    val syncErrorMessage: String? = null,
    val isDialogOpen: Boolean = false,
    val dialogState: BarangDialogState = BarangDialogState(),
    val snackbarMessage: String? = null,
    val showCatalogScannerDialog: Boolean = false,
    val showUnrecognizedBarcodeDialog: Boolean = false,
    val unrecognizedBarcode: String = "",
    val showProductSelectionDialog: Boolean = false
)

class BarangViewModel(
    private val repository: KasirRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BarangUiState(isLoading = true))
    val uiState: StateFlow<BarangUiState> = _uiState.asStateFlow()

    init {
        loadBarangList()
    }

    private fun loadBarangList() {
        viewModelScope.launch {
            repository.getBarangWithHargaGrosir().collectLatest { list ->
                _uiState.update { currentState ->
                    val cats = list.map { it.barang.kategori }.filter { it.isNotBlank() }.distinct().sorted()
                    val filtered = filterAndSortBarangList(list, currentState.searchQuery, currentState.selectedCategory, currentState.selectedSortOption)
                    currentState.copy(
                        barangList = list,
                        filteredBarangList = filtered,
                        categories = cats,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { currentState ->
            val filtered = filterAndSortBarangList(currentState.barangList, query, currentState.selectedCategory, currentState.selectedSortOption)
            currentState.copy(
                searchQuery = query,
                filteredBarangList = filtered
            )
        }
    }

    fun onCategorySelected(category: String?) {
        _uiState.update { currentState ->
            val newCategory = if (currentState.selectedCategory == category) null else category
            val filtered = filterAndSortBarangList(currentState.barangList, currentState.searchQuery, newCategory, currentState.selectedSortOption)
            currentState.copy(
                selectedCategory = newCategory,
                isCategoryDialogOpen = false,
                filteredBarangList = filtered
            )
        }
    }

    fun openCategoryDialog() {
        _uiState.update { it.copy(isCategoryDialogOpen = true) }
    }

    fun closeCategoryDialog() {
        _uiState.update { it.copy(isCategoryDialogOpen = false) }
    }

    fun onSortSelected(sortOption: SortOption) {
        _uiState.update { currentState ->
            val filtered = filterAndSortBarangList(
                currentState.barangList,
                currentState.searchQuery,
                currentState.selectedCategory,
                sortOption
            )
            currentState.copy(
                selectedSortOption = sortOption,
                filteredBarangList = filtered
            )
        }
    }

    private fun filterAndSortBarangList(
        list: List<BarangWithHargaGrosir>,
        query: String,
        category: String?,
        sortOption: SortOption
    ): List<BarangWithHargaGrosir> {
        val q = query.trim()
        val filtered = list.filter { item ->
            val matchesQuery = q.isBlank() ||
                    item.barang.namaBarang.contains(q, ignoreCase = true) ||
                    item.barang.kodeBarang.contains(q, ignoreCase = true) ||
                    item.barang.kategori.contains(q, ignoreCase = true) ||
                    (item.barang.barcode?.contains(q, ignoreCase = true) == true)
            val matchesCategory = category == null || item.barang.kategori.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }

        return when (sortOption) {
            SortOption.AZ -> filtered.sortedBy { it.barang.namaBarang }
            SortOption.ZA -> filtered.sortedByDescending { it.barang.namaBarang }
            SortOption.HARGA_RENDAH -> filtered.sortedBy { it.barang.hargaJual }
            SortOption.HARGA_TINGGI -> filtered.sortedByDescending { it.barang.hargaJual }
            SortOption.STOK_RENDAH -> filtered.sortedBy { it.barang.stok }
            SortOption.STOK_TINGGI -> filtered.sortedByDescending { it.barang.stok }
        }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isDialogOpen = true,
                dialogState = BarangDialogState()
            )
        }
    }

    fun openEditDialog(item: BarangWithHargaGrosir) {
        val barang = item.barang
        val tiers = item.hargaGrosirList.map { hg ->
            TieredPriceFormState(
                id = hg.id,
                minQty = hg.minQty.toString(),
                hargaGrosir = if (hg.hargaGrosir % 1.0 == 0.0) hg.hargaGrosir.toLong().toString() else hg.hargaGrosir.toString()
            )
        }

        _uiState.update {
            it.copy(
                isDialogOpen = true,
                dialogState = BarangDialogState(
                    isEditing = true,
                    barangId = barang.id,
                    kodeBarang = barang.kodeBarang,
                    namaBarang = barang.namaBarang,
                    kategori = barang.kategori,
                    hargaBeli = if (barang.hargaBeli % 1.0 == 0.0) barang.hargaBeli.toLong().toString() else barang.hargaBeli.toString(),
                    hargaJual = if (barang.hargaJual % 1.0 == 0.0) barang.hargaJual.toLong().toString() else barang.hargaJual.toString(),
                    stok = barang.stok.toString(),
                    satuan = barang.satuan,
                    barcode = barang.barcode ?: "",
                    tieredPrices = tiers
                )
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isDialogOpen = false) }
    }

    fun updateDialogNamaBarang(nama: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(namaBarang = nama, errorMessage = null)) }
    }

    fun updateDialogBarcode(barcode: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(barcode = barcode)) }
    }

    fun updateDialogKategori(kategori: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(kategori = kategori)) }
    }

    fun updateDialogHargaBeli(hargaBeli: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(hargaBeli = hargaBeli, errorMessage = null)) }
    }

    fun updateDialogHargaJual(hargaJual: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(hargaJual = hargaJual, errorMessage = null)) }
    }

    fun updateDialogStok(stok: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(stok = stok, errorMessage = null)) }
    }

    fun updateDialogSatuan(satuan: String) {
        _uiState.update { it.copy(dialogState = it.dialogState.copy(satuan = satuan)) }
    }

    fun addTieredPriceRow() {
        _uiState.update { state ->
            val updatedTiers = state.dialogState.tieredPrices + TieredPriceFormState()
            state.copy(dialogState = state.dialogState.copy(tieredPrices = updatedTiers))
        }
    }

    fun removeTieredPriceRow(index: Int) {
        _uiState.update { state ->
            val currentTiers = state.dialogState.tieredPrices.toMutableList()
            if (index in currentTiers.indices) {
                currentTiers.removeAt(index)
            }
            state.copy(dialogState = state.dialogState.copy(tieredPrices = currentTiers))
        }
    }

    fun updateTieredPriceRow(index: Int, minQty: String, hargaGrosir: String) {
        _uiState.update { state ->
            val currentTiers = state.dialogState.tieredPrices.toMutableList()
            if (index in currentTiers.indices) {
                currentTiers[index] = currentTiers[index].copy(minQty = minQty, hargaGrosir = hargaGrosir)
            }
            state.copy(dialogState = state.dialogState.copy(tieredPrices = currentTiers))
        }
    }

    fun saveBarang() {
        val dState = _uiState.value.dialogState
        if (dState.namaBarang.isBlank()) {
            _uiState.update { it.copy(dialogState = it.dialogState.copy(errorMessage = "Nama barang tidak boleh kosong")) }
            return
        }
        val hargaJualVal = dState.hargaJual.toDoubleOrNull()
        if (hargaJualVal == null || hargaJualVal < 0) {
            _uiState.update { it.copy(dialogState = it.dialogState.copy(errorMessage = "Harga jual tidak valid")) }
            return
        }
        val hargaBeliVal = dState.hargaBeli.toDoubleOrNull() ?: 0.0
        val stokVal = dState.stok.toIntOrNull() ?: 0

        val mappedTiers = mutableListOf<HargaGrosirEntity>()
        for (tier in dState.tieredPrices) {
            val q = tier.minQty.toIntOrNull()
            val p = tier.hargaGrosir.toDoubleOrNull()
            if (q == null || q <= 0 || p == null || p < 0) {
                _uiState.update { it.copy(dialogState = it.dialogState.copy(errorMessage = "Harap isi min. qty dan harga grosir dengan angka valid (> 0)")) }
                return
            }
            mappedTiers.add(
                HargaGrosirEntity(
                    id = tier.id,
                    barangId = dState.barangId,
                    minQty = q,
                    hargaGrosir = p,
                    isSynced = false
                )
            )
        }

        val finalKodeBarang = if (dState.isEditing && dState.kodeBarang.isNotBlank()) {
            dState.kodeBarang.trim()
        } else {
            "KASIR-" + System.currentTimeMillis() + "-" + (100..999).random()
        }

        val barangEntity = BarangEntity(
            id = if (dState.isEditing) dState.barangId else 0,
            kodeBarang = finalKodeBarang,
            namaBarang = dState.namaBarang.trim(),
            kategori = dState.kategori.trim(),
            hargaBeli = hargaBeliVal,
            hargaJual = hargaJualVal,
            stok = stokVal,
            satuan = if (dState.satuan.isBlank()) "pcs" else dState.satuan.trim(),
            barcode = dState.barcode.ifBlank { null },
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                if (dState.isEditing) {
                    repository.updateBarang(barangEntity, mappedTiers)
                    _uiState.update {
                        it.copy(
                            isDialogOpen = false,
                            snackbarMessage = "Barang berhasil diperbarui"
                        )
                    }
                } else {
                    repository.insertBarang(barangEntity, mappedTiers)
                    _uiState.update {
                        it.copy(
                            isDialogOpen = false,
                            snackbarMessage = "Barang berhasil ditambahkan"
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

    fun deleteBarang(item: BarangEntity) {
        viewModelScope.launch {
            try {
                repository.deleteBarang(item)
                _uiState.update { it.copy(snackbarMessage = "Barang '${item.namaBarang}' berhasil dihapus") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Gagal menghapus: ${e.message}") }
            }
        }
    }

    fun triggerSync() {
        _uiState.update { it.copy(isSyncing = true, showSyncDialog = true, syncSummary = null, syncErrorMessage = null) }
        viewModelScope.launch {
            try {
                val result = repository.syncAll()
                when (result) {
                    is SyncResult.Success<*> -> {
                        val summary = result.data as? SyncSummary
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                syncSummary = summary,
                                snackbarMessage = "Sync Berhasil!"
                            )
                        }
                    }
                    is SyncResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                syncErrorMessage = result.message,
                                snackbarMessage = "Sync Gagal: ${result.message}"
                            )
                        }
                    }
                    is SyncResult.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncErrorMessage = e.message ?: "Unknown error",
                        snackbarMessage = "Error sync: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissSyncDialog() {
        _uiState.update { it.copy(showSyncDialog = false, syncSummary = null, syncErrorMessage = null) }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun openCatalogScannerDialog() {
        _uiState.update { it.copy(showCatalogScannerDialog = true) }
    }

    fun closeCatalogScannerDialog() {
        _uiState.update { it.copy(showCatalogScannerDialog = false) }
    }

    fun closeUnrecognizedBarcodeDialog() {
        _uiState.update { it.copy(showUnrecognizedBarcodeDialog = false, unrecognizedBarcode = "") }
    }

    fun openAddDialogWithBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                showUnrecognizedBarcodeDialog = false,
                isDialogOpen = true,
                dialogState = BarangDialogState(barcode = barcode)
            )
        }
    }

    fun openProductSelectionForBarcode(barcode: String) {
        _uiState.update {
            it.copy(
                showUnrecognizedBarcodeDialog = false,
                unrecognizedBarcode = barcode,
                showProductSelectionDialog = true
            )
        }
    }

    fun closeProductSelectionDialog() {
        _uiState.update { it.copy(showProductSelectionDialog = false, unrecognizedBarcode = "") }
    }

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val existingItem = _uiState.value.barangList.find {
                it.barang.barcode.equals(trimmed, ignoreCase = true) ||
                it.barang.kodeBarang.equals(trimmed, ignoreCase = true)
            } ?: run {
                val entity = repository.getBarangByBarcode(trimmed)
                if (entity != null) {
                    _uiState.value.barangList.find { it.barang.id == entity.id }
                } else {
                    null
                }
            }

            if (existingItem != null) {
                openEditDialog(existingItem)
                _uiState.update {
                    it.copy(
                        showCatalogScannerDialog = false,
                        snackbarMessage = "Barang '${existingItem.barang.namaBarang}' ditemukan"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        showCatalogScannerDialog = false,
                        unrecognizedBarcode = trimmed,
                        showUnrecognizedBarcodeDialog = true
                    )
                }
            }
        }
    }

    fun assignBarcodeToProduct(item: BarangWithHargaGrosir, barcode: String) {
        viewModelScope.launch {
            try {
                val updatedBarang = item.barang.copy(
                    barcode = barcode,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
                repository.updateBarang(updatedBarang, item.hargaGrosirList)
                _uiState.update {
                    it.copy(
                        showProductSelectionDialog = false,
                        unrecognizedBarcode = "",
                        snackbarMessage = "Barcode '$barcode' berhasil ditambahkan ke '${item.barang.namaBarang}'"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Gagal assign barcode: ${e.message}"
                    )
                }
            }
        }
    }
}
