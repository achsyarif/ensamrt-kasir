package com.syarif.kasir.data.repository

import com.syarif.kasir.data.local.AppDatabase
import com.syarif.kasir.data.local.entity.*
import com.syarif.kasir.data.remote.ApiService
import com.syarif.kasir.data.remote.NetworkClient
import com.syarif.kasir.data.remote.dto.DetailTransaksiDto
import com.syarif.kasir.data.remote.dto.HapusBarangRequest
import com.syarif.kasir.data.remote.dto.HapusTransaksiRequest
import com.syarif.kasir.data.remote.dto.TambahBarangCepatRequest
import com.syarif.kasir.data.remote.dto.PelunasanRequest
import com.syarif.kasir.data.remote.dto.TambahPelangganRequest
import com.syarif.kasir.data.remote.dto.TransaksiDto
import com.syarif.kasir.data.sync.SyncEngine
import com.syarif.kasir.data.sync.SyncResult
import com.syarif.kasir.data.sync.SyncSummary
import kotlinx.coroutines.flow.Flow

open class KasirRepository(
    private val database: AppDatabase?,
    private var apiService: ApiService?
) {

    private val barangDao = database?.barangDao()
    private val hargaGrosirDao = database?.hargaGrosirDao()
    private val pelangganDao = database?.pelangganDao()
    private val transaksiDao = database?.transaksiDao()

    val syncEngine: SyncEngine? = if (apiService != null && barangDao != null && hargaGrosirDao != null && pelangganDao != null && transaksiDao != null) {
        SyncEngine(
            apiService = apiService!!,
            barangDao = barangDao,
            hargaGrosirDao = hargaGrosirDao,
            pelangganDao = pelangganDao,
            transaksiDao = transaksiDao
        )
    } else null

    open fun updateBaseUrl(baseUrl: String, userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KasirApp/1.0") {
        apiService = NetworkClient.createApiService(baseUrl, userAgent)
        syncEngine?.updateApiService(apiService!!)
    }

    // --- Barang Operations ---
    open fun getAllBarang(): Flow<List<BarangEntity>> = barangDao!!.getAllBarang()

    open fun getBarangWithHargaGrosir(): Flow<List<BarangWithHargaGrosir>> =
        barangDao!!.getBarangWithHargaGrosir()

    open suspend fun getBarangById(id: Int): BarangEntity? = barangDao!!.getBarangById(id)

    open suspend fun getBarangByKode(kode: String): BarangEntity? = barangDao!!.getBarangByKode(kode)

    open suspend fun getBarangByBarcode(barcode: String): BarangEntity? =
        barangDao!!.getBarangByBarcode(barcode)

    open suspend fun insertBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity> = emptyList()
    ): Long {
        val barangId = barangDao!!.insertBarang(barang).toInt()
        if (hargaGrosirList.isNotEmpty()) {
            hargaGrosirDao!!.deleteHargaGrosirByBarangId(barangId)
            val mappedList = hargaGrosirList.map { it.copy(barangId = barangId) }
            hargaGrosirDao.insertHargaGrosirList(mappedList)
        }
        return barangId.toLong()
    }

    open suspend fun updateBarang(
        barang: BarangEntity,
        hargaGrosirList: List<HargaGrosirEntity> = emptyList()
    ) {
        barangDao!!.updateBarang(barang)
        hargaGrosirDao!!.deleteHargaGrosirByBarangId(barang.id)
        if (hargaGrosirList.isNotEmpty()) {
            val mappedList = hargaGrosirList.map { it.copy(barangId = barang.id) }
            hargaGrosirDao.insertHargaGrosirList(mappedList)
        }
    }

    open suspend fun deleteBarang(barang: BarangEntity) {
        hargaGrosirDao!!.deleteHargaGrosirByBarangId(barang.id)
        barangDao!!.deleteBarang(barang)
        try {
            apiService?.hapusBarang(action = "hapus", request = HapusBarangRequest(id = barang.id.toString()))
        } catch (_: Exception) {}
    }

    open suspend fun tambahBarangCepat(namaBarang: String, hargaJual: Double) {
        try {
            apiService?.tambahBarangCepat(
                action = "tambah_cepat",
                request = TambahBarangCepatRequest(namaBarang = namaBarang, hargaJual = hargaJual)
            )
        } catch (_: Exception) {}
    }

    // --- Pelanggan Operations ---
    open fun getAllPelanggan(): Flow<List<PelangganEntity>> = pelangganDao!!.getAllPelanggan()

    open suspend fun getPelangganById(id: Int): PelangganEntity? = pelangganDao!!.getPelangganById(id)

    open suspend fun insertPelanggan(pelanggan: PelangganEntity): Long =
        pelangganDao!!.insertPelanggan(pelanggan)

    open suspend fun tambahPelanggan(nama: String, telepon: String? = null, alamat: String? = null) {
        try {
            apiService?.tambahPelanggan(
                action = "tambah",
                request = TambahPelangganRequest(nama = nama, telepon = telepon, alamat = alamat)
            )
        } catch (_: Exception) {}
    }

    open suspend fun updatePelanggan(pelanggan: PelangganEntity) =
        pelangganDao!!.updatePelanggan(pelanggan)

    open suspend fun deletePelanggan(pelanggan: PelangganEntity) =
        pelangganDao!!.deletePelanggan(pelanggan)

    // --- Transaksi Operations ---
    open fun getAllTransaksi(): Flow<List<TransaksiEntity>> = transaksiDao!!.getAllTransaksi()

    open fun getAllTransaksiWithDetails(): Flow<List<TransaksiWithDetails>> =
        transaksiDao!!.getAllTransaksiWithDetails()

    open suspend fun getTransaksiById(id: Int): TransaksiWithDetails? =
        transaksiDao!!.getTransaksiById(id)

    open suspend fun insertTransaksi(
        transaksi: TransaksiEntity,
        details: List<DetailTransaksiEntity>
    ): Long = transaksiDao!!.insertFullTransaksi(transaksi, details)

    open suspend fun deleteTransaksi(id: Long) {
        transaksiDao?.purgeTransaksi(id.toInt())
        try {
            apiService?.hapusTransaksi(action = "hapus", request = HapusTransaksiRequest(id = id))
        } catch (_: Exception) {}
    }

    open suspend fun pelunasanTransaksi(transaksiId: Long, metode: String, uangDp: Double, sisa: Double) {
        val txWithDetails = transaksiDao?.getTransaksiById(transaksiId.toInt()) ?: return
        val tx = txWithDetails.transaksi
        val total = tx.totalHarga

        val isDp = metode.equals("DP", ignoreCase = true)
        val finalBayar = if (isDp) uangDp else total
        val finalSisa = if (isDp) sisa else 0.0
        val finalMetode = if (isDp) "DP" else metode.uppercase()

        val updatedTx = tx.copy(
            metodePembayaran = finalMetode,
            bayar = finalBayar,
            uangBayar = finalBayar,
            dp = if (isDp) uangDp else 0.0,
            sisa = finalSisa,
            kembali = if (finalBayar > total) finalBayar - total else 0.0,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        val detailEntities = txWithDetails.details
        transaksiDao.insertOrReplaceSyncedTransaksi(updatedTx, detailEntities)

        try {
            val uangDpStr = if (isDp) {
                if (uangDp % 1.0 == 0.0) uangDp.toLong().toString() else uangDp.toString()
            } else {
                "0"
            }
            apiService?.pelunasanLunas(
                action = "pelunasan",
                request = PelunasanRequest(
                    id = transaksiId.toString(),
                    metode = metode.lowercase(),
                    uangDp = uangDpStr
                )
            )
            val syncedTx = updatedTx.copy(isSynced = true)
            transaksiDao.insertOrReplaceSyncedTransaksi(syncedTx, detailEntities)
        } catch (_: Exception) {
            // Unsynced, will sync later
        }
    }

    open suspend fun markTransaksiAsPaid(transaksiId: Int) {
        pelunasanTransaksi(transaksiId.toLong(), "TUNAI", 0.0, 0.0)
    }

    open suspend fun purgeTransaksi(id: Long) {
        deleteTransaksi(id)
    }

    open suspend fun refreshTransaksiHistory(): SyncResult<SyncSummary> {
        return syncAll()
    }

    open suspend fun refreshHistory(): SyncResult<SyncSummary> {
        return syncAll()
    }

    // --- Sync Operations ---
    open suspend fun pullMasterData(keyword: String? = null): SyncResult<SyncSummary> = syncEngine?.pullMasterData(keyword) ?: SyncResult.Error(Exception("SyncEngine not initialized"))

    open suspend fun getTransaksiHistori(search: String? = null): SyncResult<List<TransaksiDto>> = syncEngine?.getTransaksiHistori(search) ?: SyncResult.Error(Exception("SyncEngine not initialized"))

    open suspend fun getTransaksiDetail(id: Long): SyncResult<TransaksiDto> {
        val remoteResult = syncEngine?.getTransaksiDetail(id)
        if (remoteResult is SyncResult.Success) {
            val dto = remoteResult.data
            try {
                val localExisting = transaksiDao?.getTransaksiById(id.toInt())?.transaksi
                val newWaktu = if (!dto.waktuTransaksi.isNullOrBlank()) dto.waktuTransaksi 
                    else if (!dto.rawTanggal.isNullOrBlank()) dto.rawTanggal 
                    else localExisting?.waktuTransaksi
                val parsedTanggal = com.syarif.kasir.util.parseDateToLong(dto.tanggal, fallback = 0L)
                val finalTanggal = if (parsedTanggal > 0L) parsedTanggal 
                    else if (localExisting != null && localExisting.tanggal > 0L) localExisting.tanggal 
                    else localExisting?.tanggal ?: 0L
                val entity = TransaksiEntity(
                    id = dto.id ?: id.toInt(),
                    noFaktur = dto.noFaktur,
                    pelangganId = dto.pelangganId,
                    namaPelanggan = dto.namaPelanggan,
                    tanggal = finalTanggal,
                    waktuTransaksi = newWaktu,
                    totalHarga = dto.totalHarga,
                    diskon = dto.diskon,
                    bayar = dto.bayar,
                    uangBayar = dto.uangBayar,
                    dp = dto.dp,
                    sisa = dto.sisa,
                    kembali = dto.kembali,
                    metodePembayaran = dto.metodePembayaran,
                    isSynced = true,
                    updatedAt = localExisting?.updatedAt ?: System.currentTimeMillis()
                )
                val detailEntities = dto.details.map { d ->
                    DetailTransaksiEntity(
                        id = d.id ?: 0,
                        transaksiId = dto.id ?: id.toInt(),
                        barangId = d.barangId,
                        namaBarang = d.namaBarang,
                        jumlah = d.jumlah,
                        hargaSatuan = d.hargaSatuan,
                        subtotal = d.subtotal
                    )
                }
                transaksiDao?.insertOrReplaceSyncedTransaksi(entity, detailEntities)
            } catch (_: Exception) {}
            return remoteResult
        }

        // Fallback to local Room data
        val local = transaksiDao?.getTransaksiById(id.toInt())
        if (local != null) {
            val dto = TransaksiDto(
                id = local.transaksi.id,
                noFaktur = local.transaksi.noFaktur,
                pelangganId = local.transaksi.pelangganId,
                rawNamaPelanggan = local.transaksi.namaPelanggan,
                rawTanggal = local.transaksi.tanggal.toString(),
                waktuTransaksi = local.transaksi.waktuTransaksi,
                rawTotalHarga = local.transaksi.totalHarga,
                rawDiskon = local.transaksi.diskon,
                discountAlt = local.transaksi.diskon,
                rawBayar = local.transaksi.bayar,
                uangBayarAlt = local.transaksi.uangBayar,
                paid = local.transaksi.uangBayar,
                rawDp = local.transaksi.dp,
                uangDp = local.transaksi.dp,
                rawSisa = local.transaksi.sisa,
                uangSisa = local.transaksi.sisa,
                rawKembali = local.transaksi.kembali,
                change = local.transaksi.kembali,
                rawMetodePembayaran = local.transaksi.metodePembayaran,
                rawDetails = local.details.map { d ->
                    DetailTransaksiDto(
                        id = d.id,
                        transaksiId = d.transaksiId,
                        barangId = d.barangId,
                        rawNamaBarang = d.namaBarang,
                        rawJumlah = d.jumlah,
                        rawHargaSatuan = d.hargaSatuan,
                        rawSubtotal = d.subtotal
                    )
                }
            )
            return SyncResult.Success(dto)
        }

        return remoteResult ?: SyncResult.Error(Exception("Repository not initialized"))
    }

    open suspend fun pushUnsyncedTransactions(): SyncResult<SyncSummary> =
        syncEngine?.pushUnsyncedTransactions() ?: SyncResult.Error(Exception("SyncEngine not initialized"))

    open suspend fun syncAll(context: android.content.Context? = null): SyncResult<SyncSummary> =
        syncEngine?.syncAll(context) ?: SyncResult.Error(Exception("SyncEngine not initialized"))
}
