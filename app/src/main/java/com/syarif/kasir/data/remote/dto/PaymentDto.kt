package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentDto(
    @Json(name = "total") val total: Double? = null,
    @Json(name = "total_belanja") val totalBelanja: Double? = null,
    @Json(name = "discount") val discount: Double? = null,
    @Json(name = "diskon") val diskon: Double? = null,
    @Json(name = "finalTotal") val finalTotal: Double? = null,
    @Json(name = "total_akhir") val totalAkhir: Double? = null,
    @Json(name = "method") val method: String? = null,
    @Json(name = "metode_pembayaran") val metodePembayaran: String? = null,
    @Json(name = "paid") val paid: Double? = null,
    @Json(name = "uang_bayar") val uangBayar: Double? = null,
    @Json(name = "change") val change: Double? = null,
    @Json(name = "kembalian") val kembalian: Double? = null,
    @Json(name = "buyerName") val buyerName: String? = null,
    @Json(name = "nama_pembeli") val namaPembeli: String? = null,
    @Json(name = "dp") val dp: Double? = null,
    @Json(name = "uang_dp") val uangDp: Double? = null,
    @Json(name = "sisa") val sisa: Double? = null,
    @Json(name = "uang_sisa") val uangSisa: Double? = null
) {
    val effectiveTotal: Double?
        get() = total ?: totalBelanja

    val effectiveDiscount: Double?
        get() = listOfNotNull(discount, diskon).firstOrNull { it != 0.0 } ?: discount ?: diskon

    val effectiveFinalTotal: Double?
        get() = finalTotal ?: totalAkhir ?: effectiveTotal

    val effectiveMethod: String?
        get() = method ?: metodePembayaran

    val effectivePaid: Double?
        get() = paid ?: uangBayar

    val effectiveChange: Double?
        get() = change ?: kembalian

    val effectiveBuyerName: String?
        get() = buyerName ?: namaPembeli

    val effectiveDp: Double?
        get() = dp ?: uangDp

    val effectiveSisa: Double?
        get() = sisa ?: uangSisa
}
