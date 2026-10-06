package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HapusBarangRequest(
    @Json(name = "id") val id: String
)
