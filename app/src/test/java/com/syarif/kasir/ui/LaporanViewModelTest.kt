package com.syarif.kasir.ui

import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.ui.laporan.LaporanPeriod
import com.syarif.kasir.ui.laporan.LaporanViewModel
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
import java.util.Calendar

class FakeLaporanKasirRepository : KasirRepository(null, null) {
    val transaksiFlow = MutableStateFlow<List<TransaksiWithDetails>>(emptyList())
    val barangFlow = MutableStateFlow<List<BarangEntity>>(emptyList())

    override fun getAllTransaksiWithDetails(): Flow<List<TransaksiWithDetails>> = transaksiFlow
    override fun getAllBarang(): Flow<List<BarangEntity>> = barangFlow
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaporanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLaporanKasirRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeLaporanKasirRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMetricsCalculationForPeriodAndShiftRecap() = runTest {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Barang 1: Cost 5000, Sell 10000
        val b1 = BarangEntity(id = 1, kodeBarang = "B1", namaBarang = "Kopi", hargaBeli = 5000.0, hargaJual = 10000.0)
        // Barang 2: Cost 10000, Sell 20000
        val b2 = BarangEntity(id = 2, kodeBarang = "B2", namaBarang = "Roti", hargaBeli = 10000.0, hargaJual = 20000.0)

        repository.barangFlow.value = listOf(b1, b2)

        // Today transaction (Cash): 2x Kopi (20000) -> Omzet 20000, Cost 10000 -> Laba 10000
        val txTodayCash = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 1,
                noFaktur = "INV-1",
                tanggal = now,
                totalHarga = 20000.0,
                bayar = 20000.0,
                kembali = 0.0,
                metodePembayaran = "TUNAI"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 1, transaksiId = 1, barangId = 1, namaBarang = "Kopi", jumlah = 2, hargaSatuan = 10000.0, subtotal = 20000.0)
            )
        )

        // Today transaction (Transfer): 1x Roti (20000) -> Omzet 20000, Cost 10000 -> Laba 10000
        val txTodayTransfer = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 2,
                noFaktur = "INV-2",
                tanggal = now,
                totalHarga = 20000.0,
                bayar = 20000.0,
                kembali = 0.0,
                metodePembayaran = "TRANSFER"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 2, transaksiId = 2, barangId = 2, namaBarang = "Roti", jumlah = 1, hargaSatuan = 20000.0, subtotal = 20000.0)
            )
        )

        // Today transaction (Pas): 1x Kopi (10000) -> Omzet 10000, Cost 5000 -> Laba 5000
        val txTodayPas = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 3,
                noFaktur = "INV-3",
                tanggal = now,
                totalHarga = 10000.0,
                bayar = 10000.0,
                kembali = 0.0,
                metodePembayaran = "pas"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 3, transaksiId = 3, barangId = 1, namaBarang = "Kopi", jumlah = 1, hargaSatuan = 10000.0, subtotal = 10000.0)
            )
        )

        // Today transaction (DP / Bon): 1x Roti (20000), DP = 15000 -> Omzet 20000, Cost 10000 -> Laba 10000
        val txTodayDp = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 4,
                noFaktur = "INV-4",
                tanggal = now,
                totalHarga = 20000.0,
                bayar = 15000.0,
                dp = 15000.0,
                sisa = 5000.0,
                kembali = 0.0,
                metodePembayaran = "bon"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 4, transaksiId = 4, barangId = 2, namaBarang = "Roti", jumlah = 1, hargaSatuan = 20000.0, subtotal = 20000.0)
            )
        )

        // Previous month transaction: 5x Kopi (50000)
        cal.timeInMillis = now
        cal.add(Calendar.MONTH, -2)
        val oldTimestamp = cal.timeInMillis

        val txOldMonth = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 5,
                noFaktur = "INV-5",
                tanggal = oldTimestamp,
                totalHarga = 50000.0,
                bayar = 50000.0,
                kembali = 0.0,
                metodePembayaran = "TUNAI"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 5, transaksiId = 5, barangId = 1, namaBarang = "Kopi", jumlah = 5, hargaSatuan = 10000.0, subtotal = 50000.0)
            )
        )

        repository.transaksiFlow.value = listOf(txTodayCash, txTodayTransfer, txTodayPas, txTodayDp, txOldMonth)

        val viewModel = LaporanViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Test "HARI_INI"
        val stateToday = viewModel.uiState.value
        assertEquals(70000.0, stateToday.totalOmzet, 0.001) // 20k + 20k + 10k + 20k
        assertEquals(35000.0, stateToday.totalLabaBersih, 0.001) // (20k-10k) + (20k-10k) + (10k-5k) + (20k-10k) = 35k
        assertEquals(4, stateToday.totalTransaksi)
        assertEquals(30000.0, stateToday.pemasukanTunai, 0.001)    // txTodayCash (20k) + txTodayPas (10k) combined
        assertEquals(20000.0, stateToday.pemasukanTransfer, 0.001) // txTodayTransfer (20k)
        assertEquals(15000.0, stateToday.pemasukanDp, 0.001)       // txTodayDp (15k)

        // Test Best Seller for Today
        assertEquals(2, stateToday.bestSellerList.size)
        // Kopi total qty 3, Roti total qty 2 -> Kopi is rank #1
        assertEquals("Kopi", stateToday.bestSellerList[0].namaBarang)
        assertEquals(3, stateToday.bestSellerList[0].totalQty)
        assertEquals(30000.0, stateToday.bestSellerList[0].totalRevenue, 0.001)

        // 2. Test Kas Awal & Cash Drawer Recap Formula
        // Kas Akhir = Kas Awal + Pemasukan Tunai + Pemasukan DP
        viewModel.onKasAwalChanged("50000")
        val stateKas = viewModel.uiState.value
        assertEquals(50000.0, stateKas.kasAwal, 0.001)
        assertEquals(95000.0, stateKas.kasAkhir, 0.001) // 50000 + 30000 (Tunai) + 15000 (DP) = 95000

        // 3. Test "SEMUA"
        viewModel.setPeriod(LaporanPeriod.SEMUA)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateSemua = viewModel.uiState.value
        assertEquals(120000.0, stateSemua.totalOmzet, 0.001) // 70k today + 50k old
        assertEquals(5, stateSemua.totalTransaksi)
        // Best seller across all time: Kopi (3 + 5 = 8 pcs), Roti (2 pcs)
        assertEquals("Kopi", stateSemua.bestSellerList[0].namaBarang)
        assertEquals(8, stateSemua.bestSellerList[0].totalQty)
    }

    @Test
    fun testKemarinAndBulanLaluAndCustomDateFilters() = runTest {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val todayTime = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterdayTime = cal.timeInMillis

        cal.timeInMillis = todayTime
        cal.set(Calendar.DAY_OF_MONTH, 10)
        cal.add(Calendar.MONTH, -1)
        val lastMonthTime = cal.timeInMillis

        val b1 = BarangEntity(id = 1, kodeBarang = "B1", namaBarang = "Kopi", hargaBeli = 5000.0, hargaJual = 10000.0)
        repository.barangFlow.value = listOf(b1)

        val txYesterday = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 10,
                noFaktur = "INV-YEST",
                tanggal = yesterdayTime,
                totalHarga = 15000.0,
                bayar = 15000.0,
                kembali = 0.0,
                metodePembayaran = "TUNAI"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 10, transaksiId = 10, barangId = 1, namaBarang = "Kopi", jumlah = 1, hargaSatuan = 15000.0, subtotal = 15000.0)
            )
        )

        val txLastMonth = TransaksiWithDetails(
            transaksi = TransaksiEntity(
                id = 20,
                noFaktur = "INV-LAST",
                tanggal = lastMonthTime,
                totalHarga = 25000.0,
                bayar = 25000.0,
                kembali = 0.0,
                metodePembayaran = "TUNAI"
            ),
            details = listOf(
                DetailTransaksiEntity(id = 20, transaksiId = 20, barangId = 1, namaBarang = "Kopi", jumlah = 2, hargaSatuan = 12500.0, subtotal = 25000.0)
            )
        )

        repository.transaksiFlow.value = listOf(txYesterday, txLastMonth)

        val viewModel = LaporanViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Test "KEMARIN"
        viewModel.setPeriod(LaporanPeriod.KEMARIN)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15000.0, viewModel.uiState.value.totalOmzet, 0.001)
        assertEquals(1, viewModel.uiState.value.totalTransaksi)

        // 2. Test "BULAN_LALU"
        viewModel.setPeriod(LaporanPeriod.BULAN_LALU)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(25000.0, viewModel.uiState.value.totalOmzet, 0.001)
        assertEquals(1, viewModel.uiState.value.totalTransaksi)

        // 3. Test "PILIH_TANGGAL" / Custom date
        viewModel.setSelectedDate(yesterdayTime)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LaporanPeriod.PILIH_TANGGAL, viewModel.uiState.value.selectedPeriod)
        assertEquals(15000.0, viewModel.uiState.value.totalOmzet, 0.001)
        assertEquals(1, viewModel.uiState.value.totalTransaksi)
    }
}
