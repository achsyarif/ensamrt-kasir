package com.syarif.kasir.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.util.SortOption
import com.syarif.kasir.util.calculateSisa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartItem(
    val barangWithGrosir: BarangWithHargaGrosir,
    val quantity: Int,
    val customUnitPrice: Double? = null
) {
    val barang: BarangEntity get() = barangWithGrosir.barang
    val hargaGrosirList: List<HargaGrosirEntity> get() = barangWithGrosir.hargaGrosirList

    val validTiers: List<HargaGrosirEntity>
        get() = hargaGrosirList.filter { it.hargaGrosir > 0 }

    val applicableTier: HargaGrosirEntity?
        get() = validTiers
            .sortedByDescending { it.minQty }
            .firstOrNull { quantity >= it.minQty }

    val isGrosirActive: Boolean
        get() = applicableTier != null

    val effectiveUnitPrice: Double
        get() {
            if (customUnitPrice != null && customUnitPrice >= 0) {
                return customUnitPrice
            }
            val tierPrice = applicableTier?.hargaGrosir
            if (tierPrice != null && tierPrice > 0) {
                return tierPrice
            }
            if (barang.hargaJual > 0) {
                return barang.hargaJual
            }
            val fallbackTierPrice = validTiers.minByOrNull { it.minQty }?.hargaGrosir
            if (fallbackTierPrice != null && fallbackTierPrice > 0) {
                return fallbackTierPrice
            }
            return 0.0
        }

    val lineSubtotal: Double
        get() = quantity * effectiveUnitPrice

    val lineNormalSubtotal: Double
        get() = quantity * barang.hargaJual

    val lineDiscount: Double
        get() = lineNormalSubtotal - lineSubtotal
}

data class HeldTransaction(
    val id: String = "HELD-${System.currentTimeMillis()}",
    val customerName: String,
    val cartItems: List<CartItem>,
    val timestamp: Long = System.currentTimeMillis()
)

data class CheckoutState(
    val paymentMethod: String = "Tunai",
    val nominalPaidText: String = "",
    val discountText: String = "",
    val errorMessage: String? = null
) {
    val nominalPaid: Double get() = nominalPaidText.toDoubleOrNull() ?: 0.0
    val discount: Double get() = discountText.toDoubleOrNull() ?: 0.0
}

data class PosUiState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList(),
    val barangList: List<BarangWithHargaGrosir> = emptyList(),
    val filteredBarangList: List<BarangWithHargaGrosir> = emptyList(),
    val pelangganList: List<PelangganEntity> = emptyList(),
    val selectedPelanggan: PelangganEntity? = null,
    val cartItems: List<CartItem> = emptyList(),
    val unsyncedCount: Int = 0,
    val heldTransactions: List<HeldTransaction> = emptyList(),
    val selectedSortOption: SortOption = SortOption.AZ,
    val isCategoryDialogOpen: Boolean = false,
    val isBarcodeScannerDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isCheckoutDialogOpen: Boolean = false,
    val isSelectCustomerDialogOpen: Boolean = false,
    val isHeldTransactionsDialogOpen: Boolean = false,
    val isHistoryDialogOpen: Boolean = false,
    val isQuickProductDialogOpen: Boolean = false,
    val isAiAnalyzing: Boolean = false,
    val checkoutState: CheckoutState = CheckoutState(),
    val completedTransaction: TransaksiWithDetails? = null,
    val snackbarMessage: String? = null
) {
    val totalItems: Int get() = cartItems.sumOf { it.quantity }
    val normalSubtotal: Double get() = cartItems.sumOf { it.lineNormalSubtotal }
    val totalDiscount: Double get() = cartItems.sumOf { it.lineDiscount }
    val finalAmount: Double get() = cartItems.sumOf { it.lineSubtotal }
    val hasGrosirActive: Boolean get() = cartItems.any { it.isGrosirActive }
}

class PosViewModel(
    private val repository: KasirRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosUiState(isLoading = true))
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            launch {
                repository.getBarangWithHargaGrosir().collectLatest { list ->
                    _uiState.update { state ->
                        val cats = list.map { it.barang.kategori }.filter { it.isNotBlank() }.distinct().sorted()
                        val filtered = filterBarang(list, state.searchQuery, state.selectedCategory, state.selectedSortOption)
                        
                        val updatedCart = state.cartItems.map { cartItem ->
                            val updatedItem = list.find { it.barang.id == cartItem.barang.id } ?: cartItem.barangWithGrosir
                            cartItem.copy(barangWithGrosir = updatedItem)
                        }

                        state.copy(
                            barangList = list,
                            filteredBarangList = filtered,
                            categories = cats,
                            cartItems = updatedCart,
                            isLoading = false
                        )
                    }
                }
            }

            launch {
                repository.getAllPelanggan().collectLatest { list ->
                    _uiState.update { state ->
                        state.copy(pelangganList = list)
                    }
                }
            }

            launch {
                repository.getAllTransaksiWithDetails().collectLatest { list ->
                    val unsynced = list.count { !it.transaksi.isSynced }
                    _uiState.update { state ->
                        state.copy(unsyncedCount = unsynced)
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = filterBarang(state.barangList, query, state.selectedCategory, state.selectedSortOption)
            state.copy(searchQuery = query, filteredBarangList = filtered)
        }
    }

    fun onCategorySelected(category: String?) {
        _uiState.update { state ->
            val newCategory = if (state.selectedCategory == category) null else category
            val filtered = filterBarang(state.barangList, state.searchQuery, newCategory, state.selectedSortOption)
            state.copy(selectedCategory = newCategory, isCategoryDialogOpen = false, filteredBarangList = filtered)
        }
    }

    fun openCategoryDialog() {
        _uiState.update { it.copy(isCategoryDialogOpen = true) }
    }

    fun closeCategoryDialog() {
        _uiState.update { it.copy(isCategoryDialogOpen = false) }
    }

    fun openBarcodeScannerDialog() {
        _uiState.update { it.copy(isBarcodeScannerDialogOpen = true) }
    }

    fun closeBarcodeScannerDialog() {
        _uiState.update { it.copy(isBarcodeScannerDialogOpen = false) }
    }

    fun onSortSelected(sortOption: SortOption) {
        _uiState.update { state ->
            val filtered = filterBarang(state.barangList, state.searchQuery, state.selectedCategory, sortOption)
            state.copy(selectedSortOption = sortOption, filteredBarangList = filtered)
        }
    }

    private fun filterBarang(
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

    private var lastScannedBarcode: String = ""
    private var lastScanTimestamp: Long = 0L
    var currentTimeProvider: () -> Long = { System.currentTimeMillis() }

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return
        val currentTime = currentTimeProvider()
        if (trimmed.equals(lastScannedBarcode, ignoreCase = true) && (currentTime - lastScanTimestamp < 1000L)) {
            return
        }
        lastScannedBarcode = trimmed
        lastScanTimestamp = currentTime

        val match = _uiState.value.barangList.find {
            it.barang.barcode.equals(trimmed, ignoreCase = true) ||
                    it.barang.kodeBarang.equals(trimmed, ignoreCase = true)
        }
        if (match != null) {
            addToCart(match, 1)
            _uiState.update { it.copy(snackbarMessage = "Barang ${match.barang.namaBarang} ditambahkan via scan") }
        } else {
            onSearchQueryChanged(trimmed)
            _uiState.update { it.copy(snackbarMessage = "Barcode '$trimmed' tidak ditemukan di produk") }
        }
    }

    fun selectPelanggan(pelanggan: PelangganEntity?) {
        _uiState.update {
            it.copy(
                selectedPelanggan = pelanggan,
                isSelectCustomerDialogOpen = false
            )
        }
    }

    fun addQuickCustomerAndSelect(nama: String) {
        val trimmed = nama.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val newPelanggan = PelangganEntity(
                    id = 0,
                    nama = trimmed,
                    telepon = null,
                    alamat = null,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
                val generatedId = repository.insertPelanggan(newPelanggan).toInt()
                val finalId = if (generatedId > 0) generatedId else System.currentTimeMillis().toInt()
                val insertedPelanggan = newPelanggan.copy(id = finalId)

                try {
                    repository.tambahPelanggan(trimmed)
                } catch (_: Exception) {}

                _uiState.update { state ->
                    state.copy(
                        selectedPelanggan = insertedPelanggan,
                        isSelectCustomerDialogOpen = false,
                        snackbarMessage = "Pelanggan '${insertedPelanggan.nama}' berhasil ditambahkan"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "Gagal menambah pelanggan: ${e.message}"
                    )
                }
            }
        }
    }

    fun openCustomerSelectionDialog() {
        _uiState.update { it.copy(isSelectCustomerDialogOpen = true) }
    }

    fun closeCustomerSelectionDialog() {
        _uiState.update { it.copy(isSelectCustomerDialogOpen = false) }
    }

    fun openHeldTransactionsDialog() {
        _uiState.update { it.copy(isHeldTransactionsDialogOpen = true) }
    }

    fun closeHeldTransactionsDialog() {
        _uiState.update { it.copy(isHeldTransactionsDialogOpen = false) }
    }

    fun openHistoryDialog() {
        _uiState.update { it.copy(isHistoryDialogOpen = true) }
    }

    fun closeHistoryDialog() {
        _uiState.update { it.copy(isHistoryDialogOpen = false) }
    }

    fun openQuickProductDialog() {
        _uiState.update { it.copy(isQuickProductDialogOpen = true) }
    }

    fun closeQuickProductDialog() {
        _uiState.update { it.copy(isQuickProductDialogOpen = false) }
    }

    fun addQuickProduct(nama: String, hargaJual: Double) {
        viewModelScope.launch {
            try {
                val autoKode = "BRG-" + System.currentTimeMillis().toString().takeLast(6)
                val newBarang = BarangEntity(
                    id = 0,
                    kodeBarang = autoKode,
                    namaBarang = nama.trim(),
                    kategori = "Umum",
                    hargaBeli = 0.0,
                    hargaJual = hargaJual,
                    stok = 100,
                    satuan = "pcs",
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
                val generatedId = repository.insertBarang(newBarang).toInt()
                val finalId = if (generatedId > 0) generatedId else System.currentTimeMillis().toInt()
                val insertedBarang = newBarang.copy(id = finalId)

                try {
                    repository.tambahBarangCepat(nama.trim(), hargaJual)
                } catch (_: Exception) {}

                val itemWithGrosir = BarangWithHargaGrosir(
                    barang = insertedBarang,
                    hargaGrosirList = emptyList()
                )

                addToCart(itemWithGrosir, 1)

                _uiState.update { state ->
                    val filtered = filterBarang(state.barangList, "", state.selectedCategory, state.selectedSortOption)
                    state.copy(
                        searchQuery = "",
                        filteredBarangList = filtered,
                        isQuickProductDialogOpen = false,
                        snackbarMessage = "Produk '${insertedBarang.namaBarang}' berhasil ditambahkan"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "Gagal menambah produk: ${e.message}"
                    )
                }
            }
        }
    }

    fun setAiAnalyzing(analyzing: Boolean) {
        _uiState.update { it.copy(isAiAnalyzing = analyzing) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun processParsedReceiptItems(parsedItems: List<com.syarif.kasir.util.ParsedReceiptItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAnalyzing = false, isLoading = true) }
            try {
                var addedCount = 0
                var createdCount = 0
                val currentBarangList = _uiState.value.barangList

                for (item in parsedItems) {
                    if (item.namaBarang.isBlank() || item.harga <= 0.0) continue

                    val existing = currentBarangList.find {
                        it.barang.namaBarang.equals(item.namaBarang, ignoreCase = true)
                    }

                    if (existing != null) {
                        addToCart(existing, item.quantity.coerceAtLeast(1))
                        addedCount++
                    } else {
                        val autoKode = "STRUK-" + System.currentTimeMillis().toString().takeLast(6) + "-" + (0..99).random()
                        val newBarang = BarangEntity(
                            id = 0,
                            kodeBarang = autoKode,
                            namaBarang = item.namaBarang.trim(),
                            kategori = "Struk AI",
                            hargaBeli = 0.0,
                            hargaJual = item.harga,
                            stok = 100,
                            satuan = "pcs",
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        val generatedId = repository.insertBarang(newBarang).toInt()
                        val finalId = if (generatedId > 0) generatedId else System.currentTimeMillis().toInt()
                        val insertedBarang = newBarang.copy(id = finalId)

                        try {
                            repository.tambahBarangCepat(item.namaBarang.trim(), item.harga)
                        } catch (_: Exception) {}

                        val itemWithGrosir = BarangWithHargaGrosir(
                            barang = insertedBarang,
                            hargaGrosirList = emptyList()
                        )

                        addToCart(itemWithGrosir, item.quantity.coerceAtLeast(1))
                        createdCount++
                    }
                }

                _uiState.update { state ->
                    val filtered = filterBarang(state.barangList, state.searchQuery, state.selectedCategory, state.selectedSortOption)
                    state.copy(
                        filteredBarangList = filtered,
                        isLoading = false,
                        snackbarMessage = "AI Struk: $addedCount item ditambahkan, $createdCount produk baru dibuat & ditambahkan"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        snackbarMessage = "Gagal memproses struk AI: ${e.message}"
                    )
                }
            }
        }
    }

    fun holdCurrentTransaction() {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Keranjang masih kosong, tidak ada yang ditunda") }
            return
        }
        val held = HeldTransaction(
            customerName = state.selectedPelanggan?.nama ?: "Pelanggan Umum",
            cartItems = state.cartItems
        )
        _uiState.update {
            it.copy(
                heldTransactions = it.heldTransactions + held,
                cartItems = emptyList(),
                snackbarMessage = "Transaksi ditunda (${held.cartItems.sumOf { c -> c.quantity }} item)"
            )
        }
    }

    fun restoreHeldTransaction(held: HeldTransaction) {
        _uiState.update {
            it.copy(
                cartItems = held.cartItems,
                heldTransactions = it.heldTransactions.filterNot { h -> h.id == held.id },
                isHeldTransactionsDialogOpen = false,
                snackbarMessage = "Transaksi tertunda dipulihkan"
            )
        }
    }

    fun deleteHeldTransaction(heldId: String) {
        _uiState.update {
            it.copy(
                heldTransactions = it.heldTransactions.filterNot { h -> h.id == heldId },
                snackbarMessage = "Transaksi tertunda dihapus"
            )
        }
    }

    fun addToCart(item: BarangWithHargaGrosir, qty: Int = 1) {
        _uiState.update { state ->
            val existingIndex = state.cartItems.indexOfFirst { it.barang.id == item.barang.id }
            val updatedCart = if (existingIndex >= 0) {
                val existing = state.cartItems[existingIndex]
                val newQty = existing.quantity + qty
                state.cartItems.toMutableList().apply {
                    this[existingIndex] = existing.copy(quantity = newQty)
                }
            } else {
                state.cartItems + CartItem(item, qty)
            }
            state.copy(cartItems = updatedCart)
        }
    }

    fun updateCartQuantity(item: BarangWithHargaGrosir, newQty: Int) {
        _uiState.update { state ->
            if (newQty <= 0) {
                val updatedCart = state.cartItems.filterNot { it.barang.id == item.barang.id }
                state.copy(cartItems = updatedCart)
            } else {
                val updatedCart = state.cartItems.map { cartItem ->
                    if (cartItem.barang.id == item.barang.id) {
                        cartItem.copy(quantity = newQty)
                    } else cartItem
                }
                state.copy(cartItems = updatedCart)
            }
        }
    }

    fun removeFromCart(item: BarangWithHargaGrosir) {
        _uiState.update { state ->
            val updatedCart = state.cartItems.filterNot { it.barang.id == item.barang.id }
            state.copy(cartItems = updatedCart)
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cartItems = emptyList(), snackbarMessage = "Keranjang dikosongkan") }
    }

    fun updateCartItemPrice(item: BarangWithHargaGrosir, newPrice: Double?) {
        _uiState.update { state ->
            val updatedCart = state.cartItems.map { cartItem ->
                if (cartItem.barang.id == item.barang.id) {
                    cartItem.copy(customUnitPrice = if (newPrice != null && newPrice >= 0) newPrice else null)
                } else cartItem
            }
            state.copy(cartItems = updatedCart)
        }
    }

    private fun isTunaiMethod(method: String): Boolean {
        return method.equals("Tunai", ignoreCase = true) || method.equals("TUNAI", ignoreCase = true)
    }

    private fun isTransferMethod(method: String): Boolean {
        return method.equals("Transfer", ignoreCase = true) || method.equals("TRANSFER", ignoreCase = true)
    }

    private fun isPasMethod(method: String): Boolean {
        return method.equals("Pas", ignoreCase = true) || method.equals("PAS", ignoreCase = true)
    }

    private fun isBonMethod(method: String): Boolean {
        return method.equals("Bon", ignoreCase = true) || method.equals("BON", ignoreCase = true) || method.equals("bon", ignoreCase = true)
    }

    private fun isCashLikeMethod(method: String): Boolean {
        return isTunaiMethod(method) || isPasMethod(method)
    }

    fun openCheckoutDialog() {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Keranjang belanja kosong") }
            return
        }
        _uiState.update {
            it.copy(
                isCheckoutDialogOpen = true,
                checkoutState = CheckoutState(
                    paymentMethod = "TUNAI",
                    nominalPaidText = if (it.finalAmount % 1.0 == 0.0) it.finalAmount.toLong().toString() else it.finalAmount.toString(),
                    discountText = "",
                    errorMessage = null
                )
            )
        }
    }

    fun closeCheckoutDialog() {
        _uiState.update { it.copy(isCheckoutDialogOpen = false) }
    }

    fun updatePaymentMethod(method: String) {
        _uiState.update { state ->
            val subtotal = state.finalAmount
            val discount = state.checkoutState.discount
            val currentFinal = (subtotal - discount).coerceAtLeast(0.0)
            val isTunai = isTunaiMethod(method)
            val isTransfer = isTransferMethod(method)
            val isBon = isBonMethod(method)
            val isPas = isPasMethod(method)

            val defaultPaidText = when {
                isTunai -> state.checkoutState.nominalPaidText.ifBlank {
                    if (currentFinal % 1.0 == 0.0) currentFinal.toLong().toString() else currentFinal.toString()
                }
                isTransfer -> if (currentFinal % 1.0 == 0.0) currentFinal.toLong().toString() else currentFinal.toString()
                isBon -> "0"
                isPas -> if (currentFinal % 1.0 == 0.0) currentFinal.toLong().toString() else currentFinal.toString()
                else -> if (currentFinal % 1.0 == 0.0) currentFinal.toLong().toString() else currentFinal.toString()
            }
            state.copy(
                checkoutState = state.checkoutState.copy(
                    paymentMethod = method,
                    nominalPaidText = defaultPaidText,
                    errorMessage = null
                )
            )
        }
    }

    fun updateNominalPaid(nominalText: String) {
        _uiState.update { state ->
            state.copy(
                checkoutState = state.checkoutState.copy(
                    nominalPaidText = nominalText,
                    errorMessage = null
                )
            )
        }
    }

    fun updateDiscount(discountText: String) {
        _uiState.update { state ->
            state.copy(
                checkoutState = state.checkoutState.copy(
                    discountText = discountText,
                    errorMessage = null
                )
            )
        }
    }

    fun processCheckout() {
        val state = _uiState.value
        val cart = state.cartItems
        if (cart.isEmpty()) return

        val subtotal = state.finalAmount
        val discount = state.checkoutState.discount
        val finalAmount = (subtotal - discount).coerceAtLeast(0.0)
        val paymentMethod = state.checkoutState.paymentMethod
        val isTunai = isTunaiMethod(paymentMethod)
        val isTransfer = isTransferMethod(paymentMethod)
        val isBon = isBonMethod(paymentMethod)
        val isPas = isPasMethod(paymentMethod)
        val isCashLike = isTunai || isPas

        val isPelangganUmum = state.selectedPelanggan?.let {
            it.nama.isBlank() || it.nama.equals("Pelanggan Umum", ignoreCase = true)
        } ?: true

        if (isBon && isPelangganUmum) {
            _uiState.update {
                it.copy(
                    checkoutState = it.checkoutState.copy(
                        errorMessage = "Harap pilih nama pelanggan terlebih dahulu untuk transaksi BON"
                    ),
                    snackbarMessage = "Harap pilih nama pelanggan terlebih dahulu untuk transaksi BON"
                )
            }
            return
        }

        val dpAmount = if (isBon) state.checkoutState.nominalPaid.coerceAtLeast(0.0) else 0.0
        val sisaAmount = calculateSisa(finalAmount, dpAmount, paymentMethod, 0.0)
        val bayarAmount = when {
            isTunai -> state.checkoutState.nominalPaid
            isTransfer -> finalAmount
            isBon -> dpAmount
            isPas -> finalAmount
            else -> finalAmount
        }

        if (discount > subtotal) {
            _uiState.update {
                it.copy(
                    checkoutState = it.checkoutState.copy(
                        errorMessage = "Diskon tidak boleh melebihi total belanja"
                    )
                )
            }
            return
        }

        if (isTunai && state.checkoutState.nominalPaid < finalAmount) {
            _uiState.update {
                it.copy(
                    checkoutState = it.checkoutState.copy(
                        errorMessage = "Nominal pembayaran kurang dari total belanja"
                    )
                )
            }
            return
        }

        if (isBon && dpAmount > finalAmount) {
            _uiState.update {
                it.copy(
                    checkoutState = it.checkoutState.copy(
                        errorMessage = "Uang DP tidak boleh melebihi total belanja"
                    )
                )
            }
            return
        }

        val now = currentTimeProvider()
        val change = if (isCashLike) (bayarAmount - finalAmount).coerceAtLeast(0.0) else 0.0
        val invoiceNo = "INV-$now"
        val customerName = state.selectedPelanggan?.nama ?: "Pelanggan Umum"
        val customerId = state.selectedPelanggan?.id

        val transaksiEntity = TransaksiEntity(
            id = 0,
            noFaktur = invoiceNo,
            pelangganId = customerId,
            namaPelanggan = customerName,
            tanggal = now,
            waktuTransaksi = com.syarif.kasir.util.formatTransactionDate(null, fallbackMillis = now),
            totalHarga = finalAmount,
            diskon = discount,
            bayar = bayarAmount,
            uangBayar = bayarAmount,
            dp = dpAmount,
            sisa = sisaAmount,
            kembali = change,
            metodePembayaran = paymentMethod,
            isSynced = false,
            updatedAt = now
        )

        val details = cart.map { cartItem ->
            DetailTransaksiEntity(
                id = 0,
                transaksiId = 0,
                barangId = cartItem.barang.id,
                namaBarang = cartItem.barang.namaBarang,
                jumlah = cartItem.quantity,
                hargaSatuan = cartItem.effectiveUnitPrice,
                subtotal = cartItem.lineSubtotal
            )
        }

        viewModelScope.launch {
            try {
                val insertedId = repository.insertTransaksi(transaksiEntity, details)

                cart.forEach { cartItem ->
                    val barang = cartItem.barang
                    val updatedStock = (barang.stok - cartItem.quantity).coerceAtLeast(0)
                    repository.updateBarang(
                        barang.copy(stok = updatedStock, isSynced = false),
                        cartItem.hargaGrosirList
                    )
                }

                val savedTransaksiWithDetails = repository.getTransaksiById(insertedId.toInt())
                    ?: TransaksiWithDetails(
                        transaksi = transaksiEntity.copy(id = insertedId.toInt()),
                        details = details.mapIndexed { index, d ->
                            d.copy(id = index + 1, transaksiId = insertedId.toInt())
                        }
                    )

                _uiState.update {
                    it.copy(
                        cartItems = emptyList(),
                        isCheckoutDialogOpen = false,
                        completedTransaction = savedTransaksiWithDetails,
                        selectedPelanggan = null,
                        snackbarMessage = "Transaksi $invoiceNo berhasil disimpan!"
                    )
                }

                repository.pushUnsyncedTransactions()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        checkoutState = it.checkoutState.copy(
                            errorMessage = "Gagal memproses transaksi: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun closeReceiptDialog() {
        _uiState.update { it.copy(completedTransaction = null) }
    }

    fun triggerSync() {
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            try {
                val result = repository.syncAll()
                when (result) {
                    is SyncResult.Success -> {
                        val summary = result.data
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                snackbarMessage = "Sync Berhasil! (${summary.transaksiPushed} transaksi terkirim)"
                            )
                        }
                    }
                    is SyncResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
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
                        snackbarMessage = "Error sync: ${e.message}"
                    )
                }
            }
        }
    }

    fun snackbarMessageStation() {}

    fun restoreTransactionForEdit(transaction: TransaksiWithDetails) {
        val tx = transaction.transaksi
        val details = transaction.details

        val currentBarangList = _uiState.value.barangList
        val restoredCartItems = details.map { detail ->
            val existingItem = currentBarangList.find { it.barang.id == detail.barangId }
            val barangWithGrosir = existingItem ?: BarangWithHargaGrosir(
                barang = BarangEntity(
                    id = detail.barangId,
                    kodeBarang = "BRG-${detail.barangId}",
                    namaBarang = detail.namaBarang,
                    kategori = "Umum",
                    hargaBeli = 0.0,
                    hargaJual = detail.hargaSatuan,
                    stok = 100,
                    satuan = "pcs"
                ),
                hargaGrosirList = emptyList()
            )

            val defaultUnitPrice = barangWithGrosir.barang.hargaJual
            val customPrice = if (detail.hargaSatuan != defaultUnitPrice) detail.hargaSatuan else null

            CartItem(
                barangWithGrosir = barangWithGrosir,
                quantity = detail.jumlah,
                customUnitPrice = customPrice
            )
        }

        val pelangganList = _uiState.value.pelangganList
        val customerName = tx.namaPelanggan?.trim()
        val restoredPelanggan = if (tx.pelangganId != null && tx.pelangganId > 0) {
            pelangganList.find { it.id == tx.pelangganId }
                ?: if (!customerName.isNullOrBlank() && !customerName.equals("Pelanggan Umum", ignoreCase = true) && !customerName.equals("Umum", ignoreCase = true)) {
                    PelangganEntity(id = tx.pelangganId, nama = customerName)
                } else null
        } else if (!customerName.isNullOrBlank() && !customerName.equals("Pelanggan Umum", ignoreCase = true) && !customerName.equals("Umum", ignoreCase = true)) {
            pelangganList.find { it.nama.equals(customerName, ignoreCase = true) }
                ?: PelangganEntity(id = 0, nama = customerName)
        } else null

        val discountVal = tx.diskon
        val discountText = if (discountVal > 0.0) {
            if (discountVal % 1.0 == 0.0) discountVal.toLong().toString() else discountVal.toString()
        } else ""

        val restoredCheckoutState = CheckoutState(
            paymentMethod = tx.metodePembayaran.ifBlank { "TUNAI" },
            nominalPaidText = "",
            discountText = discountText,
            errorMessage = null
        )

        _uiState.update { state ->
            state.copy(
                cartItems = restoredCartItems,
                selectedPelanggan = restoredPelanggan,
                checkoutState = restoredCheckoutState,
                snackbarMessage = "Transaksi dimuat kembali ke Kasir"
            )
        }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
