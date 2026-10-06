package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.syarif.kasir.util.calculateSisa

@JsonClass(generateAdapter = true)
data class TransaksiDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "no_faktur") val noFaktur: String = "",
    @Json(name = "pelanggan_id") val pelangganId: Int? = null,
    @Json(name = "nama_pelanggan") val rawNamaPelanggan: String? = null,
    @Json(name = "nama_pembeli") val namaPembeli: String? = null,
    @Json(name = "buyerName") val buyerName: String? = null,
    @Json(name = "tanggal") val rawTanggal: String? = null,
    @Json(name = "waktu_transaksi") val waktuTransaksi: String? = null,
    @Json(name = "total_harga") val rawTotalHarga: Double? = null,
    @Json(name = "total_akhir") val totalAkhir: Double? = null,
    @Json(name = "total_belanja") val totalBelanja: Double? = null,
    @Json(name = "total") val totalAlt: Double? = null,
    @Json(name = "finalTotal") val finalTotal: Double? = null,
    @Json(name = "diskon") val rawDiskon: Double? = null,
    @Json(name = "discount") val discountAlt: Double? = null,
    @Json(name = "bayar") val rawBayar: Double? = null,
    @Json(name = "uang_bayar") val uangBayarAlt: Double? = null,
    @Json(name = "paid") val paid: Double? = null,
    @Json(name = "dp") val rawDp: Double? = null,
    @Json(name = "uang_dp") val uangDp: Double? = null,
    @Json(name = "sisa") val rawSisa: Double? = null,
    @Json(name = "uang_sisa") val uangSisa: Double? = null,
    @Json(name = "kembali") val rawKembali: Double? = null,
    @Json(name = "change") val change: Double? = null,
    @Json(name = "metode_pembayaran") val rawMetodePembayaran: String? = null,
    @Json(name = "method") val method: String? = null,
    @Json(name = "payment") val payment: PaymentDto? = null,
    @Json(name = "detail") val rawDetails: List<DetailTransaksiDto>? = null,
    @Json(name = "details") val detailsAlt: List<DetailTransaksiDto>? = null,
    @Json(name = "items") val items: List<DetailTransaksiDto>? = null
) {
    val namaPelanggan: String?
        get() = if (!rawNamaPelanggan.isNullOrBlank()) rawNamaPelanggan
            else if (!namaPembeli.isNullOrBlank()) namaPembeli
            else if (!buyerName.isNullOrBlank()) buyerName
            else payment?.effectiveBuyerName

    val tanggal: String?
        get() = waktuTransaksi ?: rawTanggal

    val totalHarga: Double
        get() = totalAkhir
            ?: totalBelanja
            ?: rawTotalHarga
            ?: finalTotal
            ?: totalAlt
            ?: payment?.effectiveFinalTotal
            ?: payment?.effectiveTotal
            ?: 0.0

    val diskon: Double
        get() = listOfNotNull(
            rawDiskon,
            discountAlt,
            payment?.effectiveDiscount,
            payment?.discount,
            payment?.diskon
        ).firstOrNull { it != 0.0 }
            ?: rawDiskon
            ?: discountAlt
            ?: payment?.effectiveDiscount
            ?: payment?.discount
            ?: payment?.diskon
            ?: 0.0

    val bayar: Double
        get() = uangBayarAlt ?: rawBayar ?: paid ?: payment?.effectivePaid ?: 0.0

    val uangBayar: Double
        get() = bayar

    val dp: Double
        get() = uangDp ?: rawDp ?: payment?.effectiveDp ?: 0.0

    val sisa: Double
        get() {
            val baseSisa = rawSisa ?: uangSisa ?: payment?.effectiveSisa ?: 0.0
            return calculateSisa(totalHarga, dp, metodePembayaran, baseSisa)
        }

    val kembali: Double
        get() = rawKembali ?: change ?: payment?.effectiveChange ?: payment?.change ?: 0.0

    val metodePembayaran: String
        get() {
            val m = if (!rawMetodePembayaran.isNullOrBlank()) rawMetodePembayaran
                else if (!method.isNullOrBlank()) method
                else payment?.effectiveMethod
            return m ?: "TUNAI"
        }

    val details: List<DetailTransaksiDto>
        get() = items ?: rawDetails ?: detailsAlt ?: emptyList()
}
