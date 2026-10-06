package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HargaGrosirDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "barang_id") val barangId: Int? = null,
    @Json(name = "min_qty") val minQty: Int = 1,
    @Json(name = "harga") val harga: Double = 0.0
)
