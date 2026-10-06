package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SimpanTransaksiRequest(
    @Json(name = "payment") val payment: PaymentDto? = null,
    @Json(name = "cart") val cart: List<CartItemDto>? = null
)
