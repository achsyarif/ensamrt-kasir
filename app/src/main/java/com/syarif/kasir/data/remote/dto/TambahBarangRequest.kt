package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TambahBarangRequest(
    @Json(name = "nama_barang") val namaBarang: String,
    @Json(name = "barcode") val barcode: String? = null,
    @Json(name = "harga_beli") val hargaBeli: Double? = 0.0,
    @Json(name = "harga_jual") val hargaJual: Double? = 0.0,
    @Json(name = "stok") val stok: Int? = 0,
    @Json(name = "qty") val qty: Int? = null,
    @Json(name = "catatan") val catatan: String? = null
)
