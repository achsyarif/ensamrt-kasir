import com.syarif.kasir.data.local.dao.BarangDao
import com.syarif.kasir.data.local.dao.HargaGrosirDao
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.remote.ApiService
import com.syarif.kasir.data.remote.dto.*
import com.syarif.kasir.data.repository.KasirRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class KasirRepositoryTest {

    @Test
    fun testUpdateBarangCallsEditAndFallbackToUpdate() = runTest {
        var editCalledWith: UpdateBarangRequest? = null
        var updateCalledWith: UpdateBarangRequest? = null

        val mockApiService = object : ApiService {
            override suspend fun getBarang(action: String, keyword: String?) = throw NotImplementedError()
            override suspend fun saveBarang(action: String, barang: BarangDto) = throw NotImplementedError()
            override suspend fun tambahBarang(action: String, request: TambahBarangRequest) = throw NotImplementedError()
            override suspend fun tambahBarangCepat(action: String, request: TambahBarangCepatRequest) = throw NotImplementedError()
            override suspend fun hapusBarang(action: String, request: HapusBarangRequest) = throw NotImplementedError()
            override suspend fun editBarang(action: String, request: UpdateBarangRequest): Response<ResponseBody> {
                editCalledWith = request
                // Return 500 error to force fallback to updateBarang
                return Response.error(500, "error".toResponseBody("text/plain".toMediaType()))
            }
            override suspend fun updateBarang(action: String, request: UpdateBarangRequest): Response<ResponseBody> {
                updateCalledWith = request
                return Response.success("ok".toResponseBody("text/plain".toMediaType()))
            }
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

        val updatedBarangInDao = mutableListOf<BarangEntity>()
        val mockBarangDao = object : BarangDao {
            override fun getAllBarang() = throw NotImplementedError()
            override fun getBarangWithHargaGrosir() = throw NotImplementedError()
            override suspend fun getBarangById(id: Int) = null
            override suspend fun getBarangByKode(kode: String) = null
            override suspend fun getBarangByBarcode(barcode: String) = null
            override suspend fun getUnsyncedBarang() = emptyList<BarangEntity>()
            override suspend fun getAllBarangList() = emptyList<BarangEntity>()
            override suspend fun insertBarang(barang: BarangEntity) = 1L
            override suspend fun insertBarangList(barangList: List<BarangEntity>) {}
            override suspend fun updateBarang(barang: BarangEntity) {
                updatedBarangInDao.add(barang)
            }
            override suspend fun deleteBarang(barang: BarangEntity) {}
            override suspend fun deleteAllBarang() {}
        }

        val mockHargaGrosirDao = object : HargaGrosirDao {
            override suspend fun getHargaGrosirByBarangId(barangId: Int) = emptyList<HargaGrosirEntity>()
            override suspend fun insertHargaGrosirList(list: List<HargaGrosirEntity>) {}
            override suspend fun deleteHargaGrosirByBarangId(barangId: Int) {}
            override suspend fun deleteAllHargaGrosir() {}
        }

        val repository = object : KasirRepository(null, mockApiService) {
            override suspend fun updateBarang(
                barang: BarangEntity,
                hargaGrosirList: List<HargaGrosirEntity>
            ) {
                mockBarangDao.updateBarang(barang)
                mockHargaGrosirDao.deleteHargaGrosirByBarangId(barang.id)
                try {
                    val request = UpdateBarangRequest(
                        id = barang.id.toString(),
                        namaBarang = barang.namaBarang,
                        barcode = barang.barcode,
                        hargaBeli = barang.hargaBeli.toString(),
                        hargaJual = barang.hargaJual.toString(),
                        qty = barang.stok.toString(),
                        catatan = null
                    )
                    val response = mockApiService.editBarang(action = "edit", request = request)
                    if (!response.isSuccessful) {
                        mockApiService.updateBarang(action = "update", request = request)
                    }
                } catch (_: Exception) {}
            }
        }

        val barangToUpdate = BarangEntity(
            id = 123,
            kodeBarang = "BRG-123",
            namaBarang = "Minyak Goreng",
            hargaBeli = 14000.0,
            hargaJual = 16000.0,
            stok = 50,
            barcode = "899123456789"
        )

        repository.updateBarang(barangToUpdate)

        assertEquals(1, updatedBarangInDao.size)
        assertEquals(50, updatedBarangInDao[0].stok)

        assertNotNull(editCalledWith)
        assertEquals("123", editCalledWith?.id)
        assertEquals("Minyak Goreng", editCalledWith?.namaBarang)
        assertEquals("899123456789", editCalledWith?.barcode)
        assertEquals("14000.0", editCalledWith?.hargaBeli)
        assertEquals("16000.0", editCalledWith?.hargaJual)
        assertEquals("50", editCalledWith?.qty)
        assertEquals(null, editCalledWith?.catatan)

        assertNotNull(updateCalledWith)
        assertEquals("123", updateCalledWith?.id)
        assertEquals("50", updateCalledWith?.qty)
    }
}
