package com.syarif.kasir

import com.syarif.kasir.data.local.dao.BarangDao
import com.syarif.kasir.data.local.dao.HargaGrosirDao
import com.syarif.kasir.data.local.dao.PelangganDao
import com.syarif.kasir.data.local.dao.TransaksiDao
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.data.remote.ApiService
import com.syarif.kasir.data.remote.dto.*
import com.syarif.kasir.data.sync.SyncEngine
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.data.sync.SyncSummary
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {

    private val dummyApiService = object : ApiService {
        override suspend fun getBarang(action: String, keyword: String?) = throw NotImplementedError()
        override suspend fun saveBarang(action: String, barang: BarangDto) = throw NotImplementedError()
        override suspend fun tambahBarang(action: String, request: TambahBarangRequest) = throw NotImplementedError()
        override suspend fun tambahBarangCepat(action: String, request: TambahBarangCepatRequest) = throw NotImplementedError()
        override suspend fun hapusBarang(action: String, request: HapusBarangRequest) = throw NotImplementedError()
        override suspend fun editBarang(action: String, request: UpdateBarangRequest) = throw NotImplementedError()
        override suspend fun updateBarang(action: String, request: UpdateBarangRequest) = throw NotImplementedError()
        override suspend fun getPelanggan(action: String) = throw NotImplementedError()
        override suspend fun savePelanggan(action: String, pelanggan: PelangganDto) = throw NotImplementedError()
        override suspend fun tambahPelanggan(action: String, request: TambahPelangganRequest) = throw NotImplementedError()
        override suspend fun getTransaksi(action: String, search: String?) = throw NotImplementedError()
        override suspend fun getTransaksiDetail(action: String, id: Long) = throw NotImplementedError()
        override suspend fun getTransaksiHistori(action: String, search: String?) = throw NotImplementedError()
        override suspend fun getHistoriTransaksi(action: String, search: String?) = throw NotImplementedError()
        override suspend fun pushTransaksi(action: String, transaksiList: List<TransaksiDto>) = throw NotImplementedError()
        override suspend fun saveSingleTransaksi(action: String, transaksi: TransaksiDto) = throw NotImplementedError()
        override suspend fun simpanTransaksi(action: String, request: SimpanTransaksiRequest) = throw NotImplementedError()
        override suspend fun hapusTransaksi(action: String, request: HapusTransaksiRequest) = throw NotImplementedError()
        override suspend fun pelunasanLunas(action: String, request: PelunasanRequest) = throw NotImplementedError()
    }

    private val dummyBarangDao = object : BarangDao {
        override fun getAllBarang() = flowOf<List<BarangEntity>>(emptyList())
        override fun getBarangWithHargaGrosir() = flowOf<List<BarangWithHargaGrosir>>(emptyList())
        override suspend fun getBarangById(id: Int) = null
        override suspend fun getBarangByKode(kode: String) = null
        override suspend fun getBarangByBarcode(barcode: String) = null
        override suspend fun getUnsyncedBarang() = emptyList<BarangEntity>()
        override suspend fun getAllBarangList() = emptyList<BarangEntity>()
        override suspend fun insertBarang(barang: BarangEntity) = 1L
        override suspend fun insertBarangList(barangList: List<BarangEntity>) {}
        override suspend fun updateBarang(barang: BarangEntity) {}
        override suspend fun deleteBarang(barang: BarangEntity) {}
        override suspend fun deleteAllBarang() {}
    }

    private val dummyHargaGrosirDao = object : HargaGrosirDao {
        override suspend fun getHargaGrosirByBarangId(barangId: Int) = emptyList<HargaGrosirEntity>()
        override suspend fun insertHargaGrosirList(list: List<HargaGrosirEntity>) {}
        override suspend fun deleteHargaGrosirByBarangId(barangId: Int) {}
        override suspend fun deleteAllHargaGrosir() {}
    }

    private val dummyPelangganDao = object : PelangganDao {
        override fun getAllPelanggan() = flowOf<List<PelangganEntity>>(emptyList())
        override suspend fun getPelangganById(id: Int) = null
        override suspend fun getUnsyncedPelanggan() = emptyList<PelangganEntity>()
        override suspend fun getAllPelangganList() = emptyList<PelangganEntity>()
        override suspend fun insertPelanggan(pelanggan: PelangganEntity) = 1L
        override suspend fun insertPelangganList(pelangganList: List<PelangganEntity>) {}
        override suspend fun updatePelanggan(pelanggan: PelangganEntity) {}
        override suspend fun deletePelanggan(pelanggan: PelangganEntity) {}
        override suspend fun deleteAllPelanggan() {}
    }

    private val dummyTransaksiDao = object : TransaksiDao {
        override fun getAllTransaksi() = flowOf<List<TransaksiEntity>>(emptyList())
        override fun getAllTransaksiWithDetails() = flowOf<List<TransaksiWithDetails>>(emptyList())
        override suspend fun getTransaksiById(id: Int) = null
        override suspend fun insertTransaksi(transaksi: TransaksiEntity) = 1L
        override suspend fun insertDetailTransaksiList(details: List<com.syarif.kasir.data.local.entity.DetailTransaksiEntity>) {}
        override suspend fun insertFullTransaksi(transaksi: TransaksiEntity, details: List<com.syarif.kasir.data.local.entity.DetailTransaksiEntity>) = 1L
        override suspend fun deleteDetailsForTransaksi(transaksiId: Int) {}
        override suspend fun deleteTransaksi(id: Int) {}
        override suspend fun insertOrReplaceSyncedTransaksi(transaksi: TransaksiEntity, details: List<com.syarif.kasir.data.local.entity.DetailTransaksiEntity>) {}
        override suspend fun getUnsyncedTransaksiWithDetails() = emptyList<TransaksiWithDetails>()
        override suspend fun getAllTransaksiList() = emptyList<TransaksiEntity>()
        override suspend fun updateSyncStatus(transaksiId: Int, isSynced: Boolean) {}
        override suspend fun markSynced(ids: List<Int>) {}
    }

    private val syncEngine = SyncEngine(dummyApiService, dummyBarangDao, dummyHargaGrosirDao, dummyPelangganDao, dummyTransaksiDao)

    @Test
    fun testSyncSummaryCreation() {
        val summary = SyncSummary(
            barangPulled = 10,
            pelangganPulled = 5,
            transaksiPushed = 3,
            isSuccess = true
        )

        assertEquals(10, summary.barangPulled)
        assertEquals(5, summary.pelangganPulled)
        assertEquals(3, summary.transaksiPushed)
        assertTrue(summary.isSuccess)
        assertNull(summary.errorMessage)
    }

    @Test
    fun testSyncResultSuccessAndError() {
        val summary = SyncSummary(barangPulled = 2, isSuccess = true)
        val successResult = SyncResult.Success(summary, "Success message")

        assertEquals("Success message", successResult.message)
        assertEquals(2, successResult.data.barangPulled)

        val errorResult = SyncResult.Error(Exception("Network error"), "Network error")
        assertEquals("Network error", errorResult.message)
    }

    @Test
    fun testBarangDtoMapping() {
        val dto = BarangDto(
            id = 1,
            kodeBarang = "BRG001",
            namaBarang = "Kopi Susu",
            hargaBeli = 5000.0,
            hargaJual = 10000.0,
            stok = 50,
            qty = null
        )

        assertEquals(1, dto.id)
        assertEquals("BRG001", dto.kodeBarang)
        assertEquals("Kopi Susu", dto.namaBarang)
        assertEquals(5000.0, dto.hargaBeli!!, 0.001)
        assertEquals(10000.0, dto.hargaJual!!, 0.001)
        assertEquals(50, dto.stok!!)
        assertEquals(50, dto.effectiveStok)

        val dtoWithQty = BarangDto(
            id = 2,
            namaBarang = "Teh",
            stok = null,
            qty = 25
        )
        assertEquals(25, dtoWithQty.effectiveStok)
    }

    @Test
    fun testBarangDtoQtyMapping() {
        val map = mapOf(
            "id" to 10,
            "nama_barang" to "Barang Qty Test",
            "qty" to 15
        )
        val dto = syncEngine.mapToBarangDto(map)
        assertEquals(15, dto.effectiveStok)

        val mapStok = mapOf(
            "id" to 11,
            "nama_barang" to "Barang Stok Test",
            "stok" to 20
        )
        val dtoStok = syncEngine.mapToBarangDto(mapStok)
        assertEquals(20, dtoStok.effectiveStok)
    }

    @Test
    fun testTambahBarangRequestsStockQtyFields() {
        val tambahReq = TambahBarangRequest(
            namaBarang = "Kopi Milk",
            hargaJual = 15000.0,
            stok = 10,
            qty = 10
        )
        assertEquals("Kopi Milk", tambahReq.namaBarang)
        assertEquals(10, tambahReq.stok)
        assertEquals(10, tambahReq.qty)

        val tambahCepatDefault = TambahBarangCepatRequest(
            namaBarang = "Es Batu",
            hargaJual = 2000.0
        )
        assertEquals("Es Batu", tambahCepatDefault.namaBarang)
        assertEquals(0, tambahCepatDefault.stok)
        assertNull(tambahCepatDefault.qty)
    }

    @Test
    fun testPelangganAndTransaksiDtoMapping() {
        val pelangganDto = PelangganDto(
            id = 100,
            nama = "Budi",
            telepon = "08123456789",
            alamat = "Jakarta"
        )
        assertEquals("Budi", pelangganDto.nama)

        val transaksiDto = TransaksiDto(
            id = 1,
            noFaktur = "INV-001",
            rawTotalHarga = 25000.0,
            rawBayar = 30000.0,
            rawKembali = 5000.0
        )
        assertEquals("INV-001", transaksiDto.noFaktur)
        assertEquals(25000.0, transaksiDto.totalHarga, 0.001)
    }

    @Test
    fun testHistoriSummaryMapping() {
        val map = mapOf(
            "id" to 42,
            "waktu_transaksi" to "2023-10-01 12:00:00",
            "total_akhir" to 150000.0,
            "metode_pembayaran" to "QRIS",
            "nama_pembeli" to "Siti"
        )
        val dto = syncEngine.mapToTransaksiDto(map)
        assertEquals(42, dto.id)
        assertEquals("2023-10-01 12:00:00", dto.tanggal)
        assertEquals(150000.0, dto.totalHarga, 0.001)
        assertEquals("QRIS", dto.metodePembayaran)
        assertEquals("Siti", dto.namaPelanggan)
    }

    @Test
    fun testDetailAndHistoriParsingWithNestedPaymentAndItems() {
        val map = mapOf(
            "id" to "6",
            "no_faktur" to "INV-6",
            "waktu_transaksi" to "2026-10-02 10:30:00",
            "payment" to mapOf(
                "nama_pembeli" to "John Doe",
                "total_akhir" to 175000.0,
                "paid" to 200000.0,
                "change" to 25000.0,
                "method" to "TUNAI"
            ),
            "items" to listOf(
                mapOf(
                    "barang_id" to 101,
                    "nama_barang_saat_transaksi" to "Kopi Arabika",
                    "qty" to 2,
                    "harga_saat_transaksi" to 50000.0
                ),
                mapOf(
                    "barang_id" to 102,
                    "nama_barang_saat_transaksi" to "Roti Bakar",
                    "qty" to 1,
                    "harga_saat_transaksi" to 75000.0
                )
            )
        )

        val dto = syncEngine.mapToTransaksiDto(map)
        assertEquals(6, dto.id)
        assertEquals("INV-6", dto.noFaktur)
        assertEquals("John Doe", dto.namaPelanggan)
        assertEquals("2026-10-02 10:30:00", dto.tanggal)
        assertEquals(175000.0, dto.totalHarga, 0.001)
        assertEquals(200000.0, dto.bayar, 0.001)
        assertEquals(25000.0, dto.kembali, 0.001)
        assertEquals("TUNAI", dto.metodePembayaran)
        assertEquals(2, dto.details.size)

        val item1 = dto.details[0]
        assertEquals("Kopi Arabika", item1.namaBarang)
        assertEquals(2, item1.jumlah)
        assertEquals(50000.0, item1.hargaSatuan, 0.001)
        assertEquals(100000.0, item1.subtotal, 0.001)

        val item2 = dto.details[1]
        assertEquals("Roti Bakar", item2.namaBarang)
        assertEquals(1, item2.jumlah)
        assertEquals(75000.0, item2.hargaSatuan, 0.001)
        assertEquals(75000.0, item2.subtotal, 0.001)
    }

    @Test
    fun testPaymentMethodPreservation() {
        val mapPas = mapOf("id" to 1, "metode_pembayaran" to "pas")
        assertEquals("pas", syncEngine.mapToTransaksiDto(mapPas).metodePembayaran)

        val mapBon = mapOf("id" to 2, "payment" to mapOf("metode_pembayaran" to "bon"))
        assertEquals("bon", syncEngine.mapToTransaksiDto(mapBon).metodePembayaran)

        val mapWA = mapOf("id" to 3, "payment" to mapOf("method" to "WhatsApp"))
        assertEquals("WhatsApp", syncEngine.mapToTransaksiDto(mapWA).metodePembayaran)

        val mapDebit = mapOf("id" to 4, "payment_method" to "Debit")
        assertEquals("Debit", syncEngine.mapToTransaksiDto(mapDebit).metodePembayaran)
    }

    @Test
    fun testPaymentDtoFlexibility() {
        // Test action=detail keys in payment object
        val detailPaymentMap = mapOf(
            "total_belanja" to 100000.0,
            "diskon" to 10000.0,
            "metode_pembayaran" to "QRIS",
            "uang_bayar" to 90000.0,
            "kembalian" to 0.0,
            "nama_pembeli" to "Ani",
            "uang_dp" to 0.0,
            "sisa" to 0.0
        )
        val mapDetail = mapOf(
            "id" to 99,
            "payment" to detailPaymentMap
        )
        val dtoDetail = syncEngine.mapToTransaksiDto(mapDetail)
        assertEquals(100000.0, dtoDetail.totalHarga, 0.001)
        assertEquals(10000.0, dtoDetail.diskon, 0.001)
        assertEquals("QRIS", dtoDetail.metodePembayaran)
        assertEquals(90000.0, dtoDetail.bayar, 0.001)
        assertEquals(0.0, dtoDetail.kembali, 0.001)
        assertEquals("Ani", dtoDetail.namaPelanggan)

        // Test action=simpan keys in payment object
        val simpanPaymentMap = mapOf(
            "total" to 200000.0,
            "discount" to 20000.0,
            "finalTotal" to 180000.0,
            "method" to "TUNAI",
            "paid" to 200000.0,
            "change" to 20000.0,
            "buyerName" to "Budi",
            "dp" to 50000.0,
            "sisa" to 130000.0
        )
        val mapSimpan = mapOf(
            "id" to 100,
            "payment" to simpanPaymentMap
        )
        val dtoSimpan = syncEngine.mapToTransaksiDto(mapSimpan)
        assertEquals(180000.0, dtoSimpan.totalHarga, 0.001)
        assertEquals(20000.0, dtoSimpan.diskon, 0.001)
        assertEquals("TUNAI", dtoSimpan.metodePembayaran)
        assertEquals(200000.0, dtoSimpan.bayar, 0.001)
        assertEquals(20000.0, dtoSimpan.kembali, 0.001)
        assertEquals("Budi", dtoSimpan.namaPelanggan)
        assertEquals(50000.0, dtoSimpan.dp, 0.001)
        assertEquals(130000.0, dtoSimpan.sisa, 0.001)
    }

    @Test
    fun testDpUangBayarAndSisaMapping() {
        val map = mapOf(
            "id" to 10,
            "no_faktur" to "INV-10",
            "total_akhir" to 100000.0,
            "uang_dp" to 30000.0,
            "uang_bayar" to 50000.0,
            "sisa" to 20000.0
        )
        val dto = syncEngine.mapToTransaksiDto(map)
        assertEquals(30000.0, dto.dp, 0.001)
        assertEquals(50000.0, dto.uangBayar, 0.001)
        assertEquals(70000.0, dto.sisa, 0.001)
    }

    @Test
    fun testParseGrosirJsonStringWithPrice() {
        val jsonStr = "[{\"min_qty\":8,\"price\":12500}]"
        val parsedList = syncEngine.parseGrosir(jsonStr)

        assertEquals(1, parsedList.size)
        val grosir = parsedList[0]
        assertEquals(8, grosir.minQty)
        assertEquals(12500.0, grosir.harga, 0.001)
    }

    @Test
    fun testBarangDtoWithGrosirJsonString() {
        val map = mapOf(
            "id" to 10,
            "nama_barang" to "Susu Grosir",
            "harga_jual" to 15000.0,
            "grosir" to "[{\"min_qty\":8,\"price\":12500}]"
        )
        val barangDto = syncEngine.mapToBarangDto(map)
        val effectiveGrosir = barangDto.effectiveHargaGrosirList

        assertNotNull(effectiveGrosir)
        assertEquals(1, effectiveGrosir!!.size)
        assertEquals(8, effectiveGrosir[0].minQty)
        assertEquals(12500.0, effectiveGrosir[0].harga, 0.001)
    }

    @Test
    fun testRobustDiscountParsingAllKeys() {
        // Top-level diskon
        val mapTopDiskon = mapOf("id" to 1, "diskon" to 15000.0)
        assertEquals(15000.0, syncEngine.mapToTransaksiDto(mapTopDiskon).diskon, 0.001)

        // Top-level discount
        val mapTopDiscount = mapOf("id" to 2, "discount" to 25000.0)
        assertEquals(25000.0, syncEngine.mapToTransaksiDto(mapTopDiscount).diskon, 0.001)

        // Payment diskon
        val mapPaymentDiskon = mapOf("id" to 3, "payment" to mapOf("diskon" to 35000.0))
        assertEquals(35000.0, syncEngine.mapToTransaksiDto(mapPaymentDiskon).diskon, 0.001)

        // Payment discount
        val mapPaymentDiscount = mapOf("id" to 4, "payment" to mapOf("discount" to 45000.0))
        assertEquals(45000.0, syncEngine.mapToTransaksiDto(mapPaymentDiscount).diskon, 0.001)

        // Mixed zero and non-zero (top-level zero, payment non-zero)
        val mapMixed1 = mapOf("id" to 5, "diskon" to 0.0, "payment" to mapOf("discount" to 55000.0))
        assertEquals(55000.0, syncEngine.mapToTransaksiDto(mapMixed1).diskon, 0.001)

        // Mixed zero and non-zero (top-level non-zero, payment zero)
        val mapMixed2 = mapOf("id" to 6, "diskon" to 65000.0, "payment" to mapOf("discount" to 0.0))
        assertEquals(65000.0, syncEngine.mapToTransaksiDto(mapMixed2).diskon, 0.001)
    }

    @Test
    fun testAuthoritativeMasterAndTransactionPullDeletion() = runBlocking {
        val deletedBarangIds = mutableListOf<Int>()
        val deletedPelangganIds = mutableListOf<Int>()
        val deletedTransaksiIds = mutableListOf<Int>()

        val testBarangDao = object : BarangDao by dummyBarangDao {
            override suspend fun getAllBarangList() = listOf(
                BarangEntity(id = 1, kodeBarang = "B1", namaBarang = "Barang 1"),
                BarangEntity(id = 2, kodeBarang = "B2", namaBarang = "Barang 2")
            )
            override suspend fun deleteBarang(barang: BarangEntity) {
                deletedBarangIds.add(barang.id)
            }
        }

        val testPelangganDao = object : PelangganDao by dummyPelangganDao {
            override suspend fun getAllPelangganList() = listOf(
                PelangganEntity(id = 10, nama = "Pelanggan 10"),
                PelangganEntity(id = 20, nama = "Pelanggan 20")
            )
            override suspend fun deletePelanggan(pelanggan: PelangganEntity) {
                deletedPelangganIds.add(pelanggan.id)
            }
        }

        val testTransaksiDao = object : TransaksiDao by dummyTransaksiDao {
            override suspend fun getAllTransaksiList() = listOf(
                TransaksiEntity(id = 100, noFaktur = "INV-100", totalHarga = 10000.0, bayar = 10000.0, kembali = 0.0),
                TransaksiEntity(id = 200, noFaktur = "INV-200", totalHarga = 20000.0, bayar = 20000.0, kembali = 0.0)
            )
            override suspend fun deleteTransaksiWithDetails(id: Int) {
                deletedTransaksiIds.add(id)
            }
        }

        val testApiService = object : ApiService by dummyApiService {
            override suspend fun getBarang(action: String, keyword: String?): retrofit2.Response<okhttp3.ResponseBody> =
                retrofit2.Response.success("[{\"id\": 1, \"kode_barang\": \"B1\", \"nama_barang\": \"Barang 1\"}]".toResponseBody("application/json".toMediaType()))

            override suspend fun getPelanggan(action: String): retrofit2.Response<okhttp3.ResponseBody> =
                retrofit2.Response.success("[{\"id\": 10, \"nama\": \"Pelanggan 10\"}]".toResponseBody("application/json".toMediaType()))

            override suspend fun getHistoriTransaksi(action: String, search: String?): retrofit2.Response<okhttp3.ResponseBody> =
                retrofit2.Response.success("[{\"id\": 100, \"no_faktur\": \"INV-100\", \"total_akhir\": 10000.0, \"uang_bayar\": 10000.0, \"kembalian\": 0.0}]".toResponseBody("application/json".toMediaType()))

            override suspend fun getTransaksiDetail(action: String, id: Long): retrofit2.Response<okhttp3.ResponseBody> =
                retrofit2.Response.success("{\"id\": 100, \"no_faktur\": \"INV-100\", \"total_akhir\": 10000.0, \"uang_bayar\": 10000.0, \"kembalian\": 0.0}".toResponseBody("application/json".toMediaType()))
        }

        val engine = SyncEngine(testApiService, testBarangDao, dummyHargaGrosirDao, testPelangganDao, testTransaksiDao)

        val barangRes = engine.pullBarang(null)
        assertTrue(barangRes is SyncResult.Success)
        assertEquals(listOf(2), deletedBarangIds)

        val pelangganRes = engine.pullPelanggan()
        assertTrue(pelangganRes is SyncResult.Success)
        assertEquals(listOf(20), deletedPelangganIds)

        val historiRes = engine.getTransaksiHistori(null)
        assertTrue(historiRes is SyncResult.Success)
        assertEquals(listOf(200), deletedTransaksiIds)
    }
}
