package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DetailTransaksiDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "transaksi_id") val transaksiId: Int? = null,
    @Json(name = "barang_id") val barangId: Int = 0,
    @Json(name = "nama_barang") val rawNamaBarang: String = "",
    @Json(name = "nama_barang_saat_transaksi") val namaBarangSaatTransaksi: String? = null,
    @Json(name = "jumlah") val rawJumlah: Int? = null,
    @Json(name = "qty") val qty: Int? = null,
    @Json(name = "harga_satuan") val rawHargaSatuan: Double? = null,
    @Json(name = "harga_saat_transaksi") val hargaSaatTransaksi: Double? = null,
    @Json(name = "subtotal") val rawSubtotal: Double? = null
) {
    val namaBarang: String
        get() = if (!namaBarangSaatTransaksi.isNullOrBlank()) namaBarangSaatTransaksi else rawNamaBarang

    val jumlah: Int
        get() = qty ?: rawJumlah ?: 0

    val hargaSatuan: Double
        get() = hargaSaatTransaksi ?: rawHargaSatuan ?: 0.0

    val subtotal: Double
        get() = rawSubtotal ?: (jumlah * hargaSatuan)
}

