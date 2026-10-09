package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateBarangRequest(
    @Json(name = "id") val id: String,
    @Json(name = "kode_barang") val kodeBarang: String? = null,
    @Json(name = "nama_barang") val namaBarang: String? = null,
    @Json(name = "kategori") val kategori: String? = null,
    @Json(name = "barcode") val barcode: String? = null,
    @Json(name = "harga_beli") val hargaBeli: String? = null,
    @Json(name = "harga_jual") val hargaJual: String? = null,
    @Json(name = "stok") val stok: String? = null,
    @Json(name = "qty") val qty: String? = null,
    @Json(name = "satuan") val satuan: String? = null,
    @Json(name = "catatan") val catatan: String? = null
)
