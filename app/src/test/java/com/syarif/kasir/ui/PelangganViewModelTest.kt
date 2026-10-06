package com.syarif.kasir.ui

import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.ui.pelanggan.PelangganViewModel
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

class FakePelangganRepository : KasirRepository(null, null) {
    val pelangganFlow = MutableStateFlow<List<PelangganEntity>>(emptyList())
    val transaksiFlow = MutableStateFlow<List<com.syarif.kasir.data.local.entity.TransaksiWithDetails>>(emptyList())
    val insertedPelanggan = mutableListOf<PelangganEntity>()
    val updatedPelanggan = mutableListOf<PelangganEntity>()
    val deletedPelanggan = mutableListOf<PelangganEntity>()
    val pelunasanCalls = mutableListOf<Quadruple<Long, String, Double, Double>>()

    override fun getAllPelanggan(): Flow<List<PelangganEntity>> = pelangganFlow

    override fun getAllTransaksiWithDetails(): Flow<List<com.syarif.kasir.data.local.entity.TransaksiWithDetails>> = transaksiFlow

    override suspend fun insertPelanggan(pelanggan: PelangganEntity): Long {
        insertedPelanggan.add(pelanggan)
        pelangganFlow.value = pelangganFlow.value + pelanggan
        return 1L
    }

    override suspend fun updatePelanggan(pelanggan: PelangganEntity) {
        updatedPelanggan.add(pelanggan)
    }

    override suspend fun deletePelanggan(pelanggan: PelangganEntity) {
        deletedPelanggan.add(pelanggan)
    }

    override suspend fun pelunasanTransaksi(transaksiId: Long, metode: String, uangDp: Double, sisa: Double) {
        pelunasanCalls.add(Quadruple(transaksiId, metode, uangDp, sisa))
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalCoroutinesApi::class)
class PelangganViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePelangganRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakePelangganRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndFiltering() = runTest {
        val p1 = PelangganEntity(id = 1, nama = "Budi Santoso", telepon = "0812345", alamat = "Jakarta")
        val p2 = PelangganEntity(id = 2, nama = "Siti Aminah", telepon = "0898765", alamat = "Bandung")
        repository.pelangganFlow.value = listOf(p1, p2)

        val viewModel = PelangganViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pelangganList.size)
        assertEquals(2, viewModel.uiState.value.filteredPelangganList.size)

        viewModel.onSearchQueryChanged("Budi")
        assertEquals(1, viewModel.uiState.value.filteredPelangganList.size)
        assertEquals("Budi Santoso", viewModel.uiState.value.filteredPelangganList[0].nama)

        viewModel.onSearchQueryChanged("0898765")
        assertEquals(1, viewModel.uiState.value.filteredPelangganList.size)
        assertEquals("Siti Aminah", viewModel.uiState.value.filteredPelangganList[0].nama)

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredPelangganList.size)
    }

    @Test
    fun testSavePelangganValidationAndSave() = runTest {
        val viewModel = PelangganViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openAddDialog()
        viewModel.savePelanggan() // Blank name, error
        assertNotNull(viewModel.uiState.value.dialogState.errorMessage)

        viewModel.updateDialogNama("Toko Kelontong Makmur")
        viewModel.updateDialogTelepon("0811223344")
        viewModel.updateDialogAlamat("Jl. Ahmad Yani No. 10")

        viewModel.savePelanggan()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDialogOpen)
        assertEquals(1, repository.insertedPelanggan.size)
        assertEquals("Toko Kelontong Makmur", repository.insertedPelanggan[0].nama)
        assertEquals("Pelanggan baru berhasil ditambahkan", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun testDeletePelanggan() = runTest {
        val viewModel = PelangganViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val p = PelangganEntity(id = 1, nama = "Pelanggan A")
        viewModel.deletePelanggan(p)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.deletedPelanggan.size)
        assertEquals("Pelanggan 'Pelanggan A' berhasil dihapus", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun testMarkTransaksiAsPelunasan() = runTest {
        val viewModel = PelangganViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markTransaksiAsPelunasan(18L, "DP", 1000000.0, 500000.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.pelunasanCalls.size)
        val call = repository.pelunasanCalls[0]
        assertEquals(18L, call.first)
        assertEquals("DP", call.second)
        assertEquals(1000000.0, call.third, 0.001)
        assertEquals(500000.0, call.fourth, 0.001)
        assertEquals("Pelunasan transaksi berhasil disimpan (DP)", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun testPiutangFilterAndTotalPiutang() = runTest {
        val p1 = PelangganEntity(id = 1, nama = "Customer A")
        val p2 = PelangganEntity(id = 2, nama = "Customer B")
        repository.pelangganFlow.value = listOf(p1, p2)

        val tx = com.syarif.kasir.data.local.entity.TransaksiEntity(
            id = 1,
            noFaktur = "INV/001",
            totalHarga = 100000.0,
            bayar = 50000.0,
            uangBayar = 50000.0,
            kembali = 0.0,
            pelangganId = 1,
            namaPelanggan = "Customer A",
            metodePembayaran = "Bon",
            sisa = 50000.0
        )
        val txWithDetails = com.syarif.kasir.data.local.entity.TransaksiWithDetails(
            transaksi = tx,
            details = emptyList()
        )
        repository.transaksiFlow.value = listOf(txWithDetails)

        val viewModel = PelangganViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.filteredPelangganList.size)

        viewModel.onFilterSelected(com.syarif.kasir.ui.pelanggan.PelangganFilter.PIUTANG)
        assertEquals(1, viewModel.uiState.value.filteredPelangganList.size)
        assertEquals("Customer A", viewModel.uiState.value.filteredPelangganList[0].nama)
        assertEquals(50000.0, viewModel.uiState.value.piutangMap[1] ?: 0.0, 0.001)
        assertEquals(0.0, viewModel.uiState.value.piutangMap[2] ?: 0.0, 0.001)
    }
}
