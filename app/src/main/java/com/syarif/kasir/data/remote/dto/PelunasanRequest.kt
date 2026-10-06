package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PelunasanRequest(
    @Json(name = "id") val id: String,
    @Json(name = "metode") val metode: String,
    @Json(name = "uang_dp") val uangDp: String
)
