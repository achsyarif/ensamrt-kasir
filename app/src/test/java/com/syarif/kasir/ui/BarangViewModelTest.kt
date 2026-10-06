package com.syarif.kasir.ui

import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.data.sync.SyncSummary
import com.syarif.kasir.ui.barang.BarangViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeKasirRepository : KasirRepository(null, null) {
    val barangFlow = MutableStateFlow<List<BarangWithHargaGrosir>>(emptyList())
    val insertedBarang = mutableListOf<BarangEntity>()
    val updatedBarang = mutableListOf<BarangEntity>()
    val deletedBarang = mutableListOf<BarangEntity>()

    override fun getBarangWithHargaGrosir(): Flow<List<BarangWithHargaGrosir>> = barangFlow

    override suspend fun getBarangByBarcode(barcode: String): BarangEntity? {
        return barangFlow.value.find { it.barang.barcode.equals(barcode, ignoreCase = true) }?.barang
    }

    override suspend fun insertBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity>
    ): Long {
        insertedBarang.add(barang)
        val newList = barangFlow.value + BarangWithHargaGrosir(barang, hargaGrosirList)
        barangFlow.value = newList
        return 1L
    }

    override suspend fun updateBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity>
    ) {
        updatedBarang.add(barang)
    }

    override suspend fun deleteBarang(barang: BarangEntity) {
        deletedBarang.add(barang)
    }

    override suspend fun syncAll(context: android.content.Context?): SyncResult<SyncSummary> {
        return SyncResult.Success(
            SyncSummary(barangPulled = 5, pelangganPulled = 2, transaksiPushed = 1, isSuccess = true),
            "Sync successful"
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BarangViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeKasirRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeKasirRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndSearchFiltering() = runTest {
        val item1 = BarangWithHargaGrosir(
            barang = BarangEntity(id = 1, kodeBarang = "B01", namaBarang = "Kopi Hitam", hargaJual = 5000.0),
            hargaGrosirList = emptyList()
        )
        val item2 = BarangWithHargaGrosir(
            barang = BarangEntity(id = 2, kodeBarang = "B02", namaBarang = "Teh Manis", hargaJual = 3000.0),
            hargaGrosirList = emptyList()
        )
        repository.barangFlow.value = listOf(item1, item2)

        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.barangList.size)
        assertEquals(2, viewModel.uiState.value.filteredBarangList.size)

        // Search query filter
        viewModel.onSearchQueryChanged("Kopi")
        assertEquals(1, viewModel.uiState.value.filteredBarangList.size)
        assertEquals("Kopi Hitam", viewModel.uiState.value.filteredBarangList[0].barang.namaBarang)

        viewModel.onSearchQueryChanged("B02")
        assertEquals(1, viewModel.uiState.value.filteredBarangList.size)
        assertEquals("Teh Manis", viewModel.uiState.value.filteredBarangList[0].barang.namaBarang)

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredBarangList.size)
    }

    @Test
    fun testOpenAddDialogAndTierOperations() = runTest {
        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddDialog()
        assertTrue(viewModel.uiState.value.isDialogOpen)
        assertFalse(viewModel.uiState.value.dialogState.isEditing)

        viewModel.addTieredPriceRow()
        assertEquals(1, viewModel.uiState.value.dialogState.tieredPrices.size)

        viewModel.updateTieredPriceRow(0, "10", "8000")
        assertEquals("10", viewModel.uiState.value.dialogState.tieredPrices[0].minQty)
        assertEquals("8000", viewModel.uiState.value.dialogState.tieredPrices[0].hargaGrosir)

        viewModel.removeTieredPriceRow(0)
        assertEquals(0, viewModel.uiState.value.dialogState.tieredPrices.size)

        viewModel.closeDialog()
        assertFalse(viewModel.uiState.value.isDialogOpen)
    }

    @Test
    fun testOpenEditDialogPopulatesFields() = runTest {
        val item = BarangWithHargaGrosir(
            barang = BarangEntity(
                id = 5,
                kodeBarang = "K05",
                namaBarang = "Susu UHT",
                kategori = "Minuman",
                hargaBeli = 4000.0,
                hargaJual = 6000.0,
                stok = 20,
                satuan = "kotak"
            ),
            hargaGrosirList = listOf(
                HargaGrosirEntity(id = 1, barangId = 5, minQty = 12, hargaGrosir = 5500.0)
            )
        )

        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openEditDialog(item)
        val dState = viewModel.uiState.value.dialogState

        assertTrue(viewModel.uiState.value.isDialogOpen)
        assertTrue(dState.isEditing)
        assertEquals(5, dState.barangId)
        assertEquals("K05", dState.kodeBarang)
        assertEquals("Susu UHT", dState.namaBarang)
        assertEquals(1, dState.tieredPrices.size)
        assertEquals("12", dState.tieredPrices[0].minQty)
        assertEquals("5500", dState.tieredPrices[0].hargaGrosir)
    }

    @Test
    fun testSaveBarangValidationAndInsertion() = runTest {
        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddDialog()
        viewModel.saveBarang() // Blank fields, error expected
        assertNotNull(viewModel.uiState.value.dialogState.errorMessage)

        viewModel.updateDialogNamaBarang("Gula Pasir 1kg")
        viewModel.updateDialogHargaJual("15000")
        viewModel.updateDialogStok("30")

        viewModel.saveBarang()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDialogOpen)
        assertEquals(1, repository.insertedBarang.size)
        assertEquals("Gula Pasir 1kg", repository.insertedBarang[0].namaBarang)
        assertTrue(repository.insertedBarang[0].kodeBarang.isNotBlank())
        assertEquals("Barang berhasil ditambahkan", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun testUpdateDialogBarcodeAndSave() = runTest {
        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddDialog()
        viewModel.updateDialogNamaBarang("Minyak Goreng")
        viewModel.updateDialogBarcode("8991234567890")
        viewModel.updateDialogHargaJual("20000")
        viewModel.updateDialogStok("10")

        viewModel.saveBarang()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDialogOpen)
        assertEquals(1, repository.insertedBarang.size)
        assertEquals("Minyak Goreng", repository.insertedBarang[0].namaBarang)
        assertEquals("8991234567890", repository.insertedBarang[0].barcode)
    }

    @Test
    fun testTriggerSyncSuccess() = runTest {
        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.triggerSync()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.snackbarMessage!!.contains("Sync Berhasil"))
    }

    @Test
    fun testBarcodeScannedExistingProduct() = runTest {
        val item = BarangWithHargaGrosir(
            barang = BarangEntity(id = 1, kodeBarang = "B01", namaBarang = "Kopi Hitam", barcode = "12345678", hargaJual = 5000.0),
            hargaGrosirList = emptyList()
        )
        repository.barangFlow.value = listOf(item)

        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBarcodeScanned("12345678")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDialogOpen)
        assertTrue(viewModel.uiState.value.dialogState.isEditing)
        assertEquals("Kopi Hitam", viewModel.uiState.value.dialogState.namaBarang)
        assertEquals("Barang 'Kopi Hitam' ditemukan", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun testBarcodeScannedUnrecognizedProduct() = runTest {
        val viewModel = BarangViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBarcodeScanned("99999999")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showUnrecognizedBarcodeDialog)
        assertEquals("99999999", viewModel.uiState.value.unrecognizedBarcode)

        viewModel.openAddDialogWithBarcode("99999999")
        assertFalse(viewModel.uiState.value.showUnrecognizedBarcodeDialog)
        assertTrue(viewModel.uiState.value.isDialogOpen)
        assertEquals("99999999", viewModel.uiState.value.dialogState.barcode)

        viewModel.openProductSelectionForBarcode("99999999")
        assertTrue(viewModel.uiState.value.showProductSelectionDialog)
        assertEquals("99999999", viewModel.uiState.value.unrecognizedBarcode)

        val item = BarangWithHargaGrosir(
            barang = BarangEntity(id = 1, kodeBarang = "B01", namaBarang = "Kopi Hitam", hargaJual = 5000.0),
            hargaGrosirList = emptyList()
        )
        viewModel.assignBarcodeToProduct(item, "99999999")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showProductSelectionDialog)
        assertEquals(1, repository.updatedBarang.size)
        assertEquals("99999999", repository.updatedBarang[0].barcode)
    }
}
