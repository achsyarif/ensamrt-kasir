package com.syarif.kasir.util

import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import java.text.NumberFormat
import java.util.Locale

fun Double.toRupiah(): String {
    val localeID = Locale.Builder().setLanguage("id").setRegion("ID").build()
    val formatter = NumberFormat.getCurrencyInstance(localeID)
    formatter.maximumFractionDigits = 0
    return formatter.format(this)
}

fun Long.toRupiah(): String = this.toDouble().toRupiah()
fun Int.toRupiah(): String = this.toDouble().toRupiah()

fun calculateSisa(totalHarga: Double, dp: Double, metodePembayaran: String, rawSisa: Double): Double {
    val trimmedMethod = metodePembayaran.trim()
    val isBon = trimmedMethod.equals("bon", ignoreCase = true)
    return when {
        dp > 0.0 -> (totalHarga - dp).coerceAtLeast(0.0)
        isBon -> {
            if (rawSisa <= 0.0) (totalHarga - dp).coerceAtLeast(0.0) else rawSisa
        }
        else -> rawSisa
    }
}

val TransaksiEntity.effectiveSisa: Double
    get() = calculateSisa(totalHarga, dp, metodePembayaran, sisa)

val TransaksiWithDetails.effectiveSisa: Double
    get() = transaksi.effectiveSisa

