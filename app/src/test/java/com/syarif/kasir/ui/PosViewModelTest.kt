package com.syarif.kasir.ui

import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.data.sync.SyncSummary
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.ui.pos.CartItem
import com.syarif.kasir.ui.pos.PosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakePosKasirRepository : KasirRepository(null, null) {
    val barangFlow = MutableStateFlow<List<BarangWithHargaGrosir>>(emptyList())
    val insertedBarangList = mutableListOf<BarangEntity>()
    var lastTambahBarangCepatNama: String? = null
    var lastTambahBarangCepatHarga: Double? = null

    val pelangganFlow = MutableStateFlow<List<PelangganEntity>>(emptyList())
    val insertedPelangganList = mutableListOf<PelangganEntity>()
    var lastTambahPelangganNama: String? = null

    override fun getBarangWithHargaGrosir(): Flow<List<BarangWithHargaGrosir>> = barangFlow
    override fun getAllPelanggan(): Flow<List<PelangganEntity>> = pelangganFlow
    override fun getAllTransaksiWithDetails(): Flow<List<TransaksiWithDetails>> = flowOf(emptyList())

    override suspend fun insertBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity>
    ): Long {
        val newId = insertedBarangList.size + 1
        val inserted = barang.copy(id = newId)
        insertedBarangList.add(inserted)
        val newList = barangFlow.value + BarangWithHargaGrosir(inserted, hargaGrosirList)
        barangFlow.value = newList
        return newId.toLong()
    }

    override suspend fun tambahBarangCepat(namaBarang: String, hargaJual: Double) {
        lastTambahBarangCepatNama = namaBarang
        lastTambahBarangCepatHarga = hargaJual
    }

    override suspend fun insertPelanggan(pelanggan: PelangganEntity): Long {
        val newId = insertedPelangganList.size + 1
        val inserted = pelanggan.copy(id = newId)
        insertedPelangganList.add(inserted)
        val newList = pelangganFlow.value + inserted
        pelangganFlow.value = newList
        return newId.toLong()
    }

    override suspend fun tambahPelanggan(nama: String, telepon: String?, alamat: String?) {
        lastTambahPelangganNama = nama
    }

    override suspend fun updateBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity>
    ) {
        // no-op in fake repository
    }

    val insertedTransaksiList = mutableListOf<TransaksiWithDetails>()
    var nextTransaksiId = 1L

    override suspend fun insertTransaksi(
        transaksi: TransaksiEntity,
        details: List<DetailTransaksiEntity>
    ): Long {
        val id = nextTransaksiId++
        val saved = TransaksiWithDetails(
            transaksi = transaksi.copy(id = id.toInt()),
            details = details.mapIndexed { index, d -> d.copy(id = index + 1, transaksiId = id.toInt()) }
        )
        insertedTransaksiList.add(saved)
        return id
    }

    override suspend fun getTransaksiById(id: Int): TransaksiWithDetails? {
        return insertedTransaksiList.find { it.transaksi.id == id }
    }

    override suspend fun pushUnsyncedTransactions(): SyncResult<SyncSummary> {
        return SyncResult.Success(SyncSummary(0, 0, 0))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePosKasirRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakePosKasirRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val baseBarang = BarangEntity(
        id = 1,
        kodeBarang = "BRG001",
        namaBarang = "Barang Test",
        kategori = "Umum",
        hargaBeli = 5000.0,
        hargaJual = 10000.0,
        stok = 100,
        satuan = "pcs"
    )

    @Test
    fun testWholesaleTierPriceFilterAndSelection() {
        val tier0 = HargaGrosirEntity(id = 1, barangId = 1, minQty = 2, hargaGrosir = 0.0) // Invalid tier
        val tier1 = HargaGrosirEntity(id = 2, barangId = 1, minQty = 5, hargaGrosir = 9000.0)
        val tier2 = HargaGrosirEntity(id = 3, barangId = 1, minQty = 10, hargaGrosir = 8000.0)

        val itemWithTiers = BarangWithHargaGrosir(
            barang = baseBarang,
            hargaGrosirList = listOf(tier0, tier1, tier2)
        )

        // Qty 1: Below all valid minQty (minQty 5 & 10) -> Uses base hargaJual (10000.0)
        val cartItem1 = CartItem(itemWithTiers, quantity = 1)
        assertEquals(10000.0, cartItem1.effectiveUnitPrice, 0.001)
        assertFalse(cartItem1.isGrosirActive)

        // Qty 3: Matches invalid tier (minQty 2, price 0), but tier is filtered out -> Uses base hargaJual (10000.0)
        val cartItem3 = CartItem(itemWithTiers, quantity = 3)
        assertEquals(10000.0, cartItem3.effectiveUnitPrice, 0.001)
        assertFalse(cartItem3.isGrosirActive)

        // Qty 7: Reaches tier1 (minQty 5) -> Uses tier1 price (9000.0)
        val cartItem7 = CartItem(itemWithTiers, quantity = 7)
        assertEquals(9000.0, cartItem7.effectiveUnitPrice, 0.001)
        assertTrue(cartItem7.isGrosirActive)

        // Qty 12: Reaches tier2 (minQty 10) -> Uses highest applicable tier price (8000.0)
        val cartItem12 = CartItem(itemWithTiers, quantity = 12)
        assertEquals(8000.0, cartItem12.effectiveUnitPrice, 0.001)
        assertTrue(cartItem12.isGrosirActive)
    }

    @Test
    fun testUnitPriceNeverZeroWhenHargaJualOrTierPriceExists() {
        // Case A: Invalid tier with price 0, base hargaJual > 0
        val barangZeroTier = BarangWithHargaGrosir(
            barang = baseBarang.copy(hargaJual = 15000.0),
            hargaGrosirList = listOf(HargaGrosirEntity(id = 1, barangId = 1, minQty = 1, hargaGrosir = 0.0))
        )
        val cartItemA = CartItem(barangZeroTier, quantity = 5)
        assertEquals(15000.0, cartItemA.effectiveUnitPrice, 0.001)

        // Case B: Base hargaJual is 0.0, but valid tier price exists
        val barangZeroBase = BarangWithHargaGrosir(
            barang = baseBarang.copy(hargaJual = 0.0),
            hargaGrosirList = listOf(HargaGrosirEntity(id = 2, barangId = 1, minQty = 5, hargaGrosir = 12000.0))
        )
        // Qty < minQty: falls back to valid tier price so unit price is NOT 0
        val cartItemB1 = CartItem(barangZeroBase, quantity = 2)
        assertEquals(12000.0, cartItemB1.effectiveUnitPrice, 0.001)

        // Qty >= minQty: applies tier price
        val cartItemB2 = CartItem(barangZeroBase, quantity = 5)
        assertEquals(12000.0, cartItemB2.effectiveUnitPrice, 0.001)
    }

    @Test
    fun testCheckoutDiscountAndFinalTotalCalculation() {
        val checkoutState = com.syarif.kasir.ui.pos.CheckoutState(
            paymentMethod = "BON",
            nominalPaidText = "1000000", // 1jt DP
            discountText = "500000"       // 500k discount
        )
        assertEquals(1000000.0, checkoutState.nominalPaid, 0.001)
        assertEquals(500000.0, checkoutState.discount, 0.001)

        val subtotal = 5000000.0 // 5jt subtotal
        val finalTotal = (subtotal - checkoutState.discount).coerceAtLeast(0.0)
        assertEquals(4500000.0, finalTotal, 0.001)

        val sisa = com.syarif.kasir.util.calculateSisa(finalTotal, checkoutState.nominalPaid, "BON", 0.0)
        assertEquals(3500000.0, sisa, 0.001)
    }

    @Test
    fun testQuickProductDialogToggle() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isQuickProductDialogOpen)

        viewModel.openQuickProductDialog()
        assertTrue(viewModel.uiState.value.isQuickProductDialogOpen)

        viewModel.closeQuickProductDialog()
        assertFalse(viewModel.uiState.value.isQuickProductDialogOpen)
    }

    @Test
    fun testAddQuickProductSavesToDbCallsApiAndAddsToCart() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("Kopi Susu")
        viewModel.openQuickProductDialog()
        assertTrue(viewModel.uiState.value.isQuickProductDialogOpen)

        viewModel.addQuickProduct("Kopi Susu", 15000.0)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Saved to local DB
        assertEquals(1, repository.insertedBarangList.size)
        assertEquals("Kopi Susu", repository.insertedBarangList[0].namaBarang)
        assertEquals(15000.0, repository.insertedBarangList[0].hargaJual, 0.001)

        // 2. Called API
        assertEquals("Kopi Susu", repository.lastTambahBarangCepatNama)
        assertEquals(15000.0, repository.lastTambahBarangCepatHarga!!, 0.001)

        // 3. Automatically added to active cart
        val cartItems = viewModel.uiState.value.cartItems
        assertEquals(1, cartItems.size)
        assertEquals("Kopi Susu", cartItems[0].barang.namaBarang)
        assertEquals(15000.0, cartItems[0].effectiveUnitPrice, 0.001)
        assertEquals(1, cartItems[0].quantity)

        // 4. Search query cleared & dialog closed
        assertFalse(viewModel.uiState.value.isQuickProductDialogOpen)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun testSelectCustomerDialogToggle() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectCustomerDialogOpen)

        viewModel.openCustomerSelectionDialog()
        assertTrue(viewModel.uiState.value.isSelectCustomerDialogOpen)

        viewModel.closeCustomerSelectionDialog()
        assertFalse(viewModel.uiState.value.isSelectCustomerDialogOpen)
    }

    @Test
    fun testAddQuickCustomerAndSelectSavesToDbCallsApiAndSelectsCustomer() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openCustomerSelectionDialog()
        assertTrue(viewModel.uiState.value.isSelectCustomerDialogOpen)

        viewModel.addQuickCustomerAndSelect("Pak Agus")
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Saved to local DB
        assertEquals(1, repository.insertedPelangganList.size)
        assertEquals("Pak Agus", repository.insertedPelangganList[0].nama)

        // 2. Called API
        assertEquals("Pak Agus", repository.lastTambahPelangganNama)

        // 3. Automatically selected as active buyer
        val selectedCustomer = viewModel.uiState.value.selectedPelanggan
        assertNotNull(selectedCustomer)
        assertEquals("Pak Agus", selectedCustomer?.nama)

        // 4. Customer selection dialog closed
        assertFalse(viewModel.uiState.value.isSelectCustomerDialogOpen)
    }

    @Test
    fun testBarcodeScanningDebounce1Second() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        var virtualTime = 10000L
        viewModel.currentTimeProvider = { virtualTime }

        val barangWithGrosir = BarangWithHargaGrosir(
            barang = baseBarang.copy(barcode = "12345678"),
            hargaGrosirList = emptyList()
        )
        repository.insertedBarangList.add(baseBarang.copy(barcode = "12345678"))
        repository.barangFlow.value = listOf(barangWithGrosir)
        testDispatcher.scheduler.advanceUntilIdle()

        // First scan at t = 10000
        viewModel.onBarcodeScanned("12345678")
        assertEquals(1, viewModel.uiState.value.cartItems.size)
        assertEquals(1, viewModel.uiState.value.cartItems[0].quantity)

        // Immediate second scan at t = 10500 (within 1 second) - ignored
        virtualTime = 10500L
        viewModel.onBarcodeScanned("12345678")
        assertEquals(1, viewModel.uiState.value.cartItems.size)
        assertEquals(1, viewModel.uiState.value.cartItems[0].quantity)

        // Scan at t = 11100 (after 1 second) - increments quantity
        virtualTime = 11100L
        viewModel.onBarcodeScanned("12345678")
        assertEquals(1, viewModel.uiState.value.cartItems.size)
        assertEquals(2, viewModel.uiState.value.cartItems[0].quantity)
    }

    @Test
    fun testRestoreTransactionForEditRestoresCartBuyerDiscountAndTab() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val item1 = BarangWithHargaGrosir(barang = baseBarang, hargaGrosirList = emptyList())
        repository.barangFlow.value = listOf(item1)
        testDispatcher.scheduler.advanceUntilIdle()

        val transaksiEntity = com.syarif.kasir.data.local.entity.TransaksiEntity(
            id = 101,
            noFaktur = "INV-101",
            pelangganId = null,
            namaPelanggan = "Budi Santoso",
            totalHarga = 25000.0,
            diskon = 5000.0,
            bayar = 20000.0,
            uangBayar = 20000.0,
            kembali = 0.0,
            metodePembayaran = "TUNAI"
        )
        val detail1 = com.syarif.kasir.data.local.entity.DetailTransaksiEntity(
            id = 1,
            transaksiId = 101,
            barangId = 1,
            namaBarang = "Barang Test",
            jumlah = 3,
            hargaSatuan = 10000.0,
            subtotal = 30000.0
        )
        val txWithDetails = TransaksiWithDetails(transaksi = transaksiEntity, details = listOf(detail1))

        viewModel.restoreTransactionForEdit(txWithDetails)

        val state = viewModel.uiState.value
        assertEquals(1, state.cartItems.size)
        assertEquals("Barang Test", state.cartItems[0].barang.namaBarang)
        assertEquals(3, state.cartItems[0].quantity)
        assertEquals("Budi Santoso", state.selectedPelanggan?.nama)
        assertEquals("5000", state.checkoutState.discountText)
    }

    @Test
    fun testBonCheckoutWithoutCustomerFailsWithErrorMessage() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val item1 = BarangWithHargaGrosir(barang = baseBarang, hargaGrosirList = emptyList())
        repository.barangFlow.value = listOf(item1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addToCart(item1, 1)
        viewModel.openCheckoutDialog()
        viewModel.updatePaymentMethod("BON")

        assertNull(viewModel.uiState.value.selectedPelanggan)

        viewModel.processCheckout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Harap pilih nama pelanggan terlebih dahulu untuk transaksi BON", viewModel.uiState.value.checkoutState.errorMessage)
        assertTrue(repository.insertedTransaksiList.isEmpty())
    }

    @Test
    fun testBonCheckoutWithValidCustomerSucceedsAndResetsCustomer() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val item1 = BarangWithHargaGrosir(barang = baseBarang, hargaGrosirList = emptyList())
        repository.barangFlow.value = listOf(item1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addToCart(item1, 1)
        val customer = PelangganEntity(id = 1, nama = "Pak Budi", telepon = "08123456789")
        viewModel.selectPelanggan(customer)
        assertEquals("Pak Budi", viewModel.uiState.value.selectedPelanggan?.nama)

        viewModel.openCheckoutDialog()
        viewModel.updatePaymentMethod("BON")
        viewModel.updateNominalPaid("5000")

        viewModel.processCheckout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.checkoutState.errorMessage)
        assertEquals(1, repository.insertedTransaksiList.size)
        assertNull(viewModel.uiState.value.selectedPelanggan)
    }

    @Test
    fun testCheckoutResetsCustomerToPelangganUmum() = runTest {
        val viewModel = PosViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val item1 = BarangWithHargaGrosir(barang = baseBarang, hargaGrosirList = emptyList())
        repository.barangFlow.value = listOf(item1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addToCart(item1, 1)
        val customer = PelangganEntity(id = 2, nama = "Ibu Siti")
        viewModel.selectPelanggan(customer)

        viewModel.openCheckoutDialog()
        viewModel.updatePaymentMethod("TUNAI")
        viewModel.updateNominalPaid("10000")

        viewModel.processCheckout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.insertedTransaksiList.size)
        assertNull(viewModel.uiState.value.selectedPelanggan)
    }
}
