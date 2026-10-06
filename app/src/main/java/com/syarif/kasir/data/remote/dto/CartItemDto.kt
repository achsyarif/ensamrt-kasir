package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CartItemDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "nama_barang") val namaBarang: String? = "",
    @Json(name = "barcode") val barcode: String? = null,
    @Json(name = "harga_beli") val hargaBeli: Double? = 0.0,
    @Json(name = "harga_jual") val hargaJual: Double? = 0.0,
    @Json(name = "catatan") val catatan: String? = null,
    @Json(name = "kategori") val kategori: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "is_archived") val isArchived: Boolean? = null,
    @Json(name = "grosir") val grosir: List<HargaGrosirDto>? = null,
    @Json(name = "qty") val qty: Int? = 0,
    @Json(name = "harga_asli") val hargaAsli: Double? = 0.0
)
