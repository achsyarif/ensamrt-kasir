package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TambahPelangganRequest(
    @Json(name = "nama") val nama: String,
    @Json(name = "telepon") val telepon: String? = null,
    @Json(name = "alamat") val alamat: String? = null,
    @Json(name = "catatan") val catatan: String? = null
)
