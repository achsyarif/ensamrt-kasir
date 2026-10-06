package com.syarif.kasir.data.remote

import com.syarif.kasir.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("barang.php")
    suspend fun getBarang(
        @Query("action") action: String = "baca",
        @Query("keyword") keyword: String? = null
    ): Response<ResponseBody>

    @POST("barang.php")
    suspend fun saveBarang(
        @Query("action") action: String = "simpan",
        @Body barang: BarangDto
    ): Response<ResponseBody>

    @POST("barang.php")
    suspend fun tambahBarang(
        @Query("action") action: String = "tambah",
        @Body request: TambahBarangRequest
    ): Response<ResponseBody>

    @POST("barang.php")
    suspend fun tambahBarangCepat(
        @Query("action") action: String = "tambah_cepat",
        @Body request: TambahBarangCepatRequest
    ): Response<ResponseBody>

    @POST("barang.php")
    suspend fun hapusBarang(
        @Query("action") action: String = "hapus",
        @Body request: HapusBarangRequest
    ): Response<ResponseBody>

    @GET("pelanggan.php")
    suspend fun getPelanggan(
        @Query("action") action: String = "baca"
    ): Response<ResponseBody>

    @POST("pelanggan.php")
    suspend fun savePelanggan(
        @Query("action") action: String = "simpan",
        @Body pelanggan: PelangganDto
    ): Response<ResponseBody>

    @POST("pelanggan.php")
    suspend fun tambahPelanggan(
        @Query("action") action: String = "tambah",
        @Body request: TambahPelangganRequest
    ): Response<ResponseBody>

    @GET("transaksi.php")
    suspend fun getTransaksiDetail(
        @Query("action") action: String = "detail",
        @Query("id") id: Long
    ): Response<ResponseBody>

    @GET("transaksi.php")
    suspend fun getTransaksi(
        @Query("action") action: String = "baca",
        @Query("search") search: String? = null
    ): Response<ResponseBody>

    @GET("transaksi.php")
    suspend fun getTransaksiHistori(
        @Query("action") action: String = "histori",
        @Query("search") search: String? = null
    ): Response<ResponseBody>

    @GET("transaksi.php")
    suspend fun getHistoriTransaksi(
        @Query("action") action: String = "histori",
        @Query("search") search: String? = null
    ): Response<ResponseBody>

    @POST("transaksi.php")
    suspend fun pushTransaksi(
        @Query("action") action: String = "simpan",
        @Body transaksiList: List<TransaksiDto>
    ): Response<ResponseBody>

    @POST("transaksi.php")
    suspend fun saveSingleTransaksi(
        @Query("action") action: String = "simpan",
        @Body transaksi: TransaksiDto
    ): Response<ResponseBody>

    @POST("transaksi.php")
    suspend fun simpanTransaksi(
        @Query("action") action: String = "simpan",
        @Body request: SimpanTransaksiRequest
    ): Response<ResponseBody>

    @POST("transaksi.php")
    suspend fun hapusTransaksi(
        @Query("action") action: String = "hapus",
        @Body request: HapusTransaksiRequest
    ): Response<ResponseBody>

    @POST("transaksi.php")
    suspend fun pelunasanLunas(
        @Query("action") action: String = "pelunasan",
        @Body request: PelunasanRequest
    ): Response<ResponseBody>
}
