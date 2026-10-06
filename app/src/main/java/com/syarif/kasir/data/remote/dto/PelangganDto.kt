package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PelangganDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "nama") val nama: String = "",
    @Json(name = "telepon") val telepon: String? = null,
    @Json(name = "alamat") val alamat: String? = null,
    @Json(name = "catatan") val catatan: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)
