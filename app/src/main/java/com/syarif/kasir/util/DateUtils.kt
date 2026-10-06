package com.syarif.kasir.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val localeId = Locale.Builder().setLanguage("id").setRegion("ID").build()

fun Long.formatDateTime(): String {
    if (this <= 0L) return ""
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
    return sdf.format(Date(this))
}

fun Long.formatDate(): String {
    if (this <= 0L) return ""
    val sdf = SimpleDateFormat("dd MMM yyyy", localeId)
    return sdf.format(Date(this))
}

fun formatTransactionDate(waktuTransaksi: String?, fallbackMillis: Long = 0L): String {
    if (!waktuTransaksi.isNullOrBlank()) {
        val trimmed = waktuTransaksi.trim()

        trimmed.toLongOrNull()?.let {
            if (it > 0L) return it.formatDateTime()
        }

        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", localeId),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", localeId),
            SimpleDateFormat("yyyy-MM-dd HH:mm", localeId),
            SimpleDateFormat("yyyy-MM-dd", localeId),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", localeId),
            SimpleDateFormat("dd/MM/yyyy HH:mm", localeId),
            SimpleDateFormat("dd/MM/yyyy", localeId),
            SimpleDateFormat("dd MMM yyyy, HH:mm", localeId),
            SimpleDateFormat("dd MMM yyyy", localeId)
        )

        for (fmt in formats) {
            try {
                val d = fmt.parse(trimmed)
                if (d != null) {
                    val outputFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
                    return outputFmt.format(d)
                }
            } catch (_: Exception) {}
        }

        return trimmed
    }

    if (fallbackMillis > 0L) {
        return fallbackMillis.formatDateTime()
    }

    return ""
}

fun parseDateToLong(dateStr: String?, fallback: Long = 0L): Long {
    if (dateStr.isNullOrBlank()) return fallback
    dateStr.toLongOrNull()?.let { return it }
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", localeId),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", localeId),
        SimpleDateFormat("yyyy-MM-dd HH:mm", localeId),
        SimpleDateFormat("yyyy-MM-dd", localeId),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", localeId),
        SimpleDateFormat("dd/MM/yyyy HH:mm", localeId),
        SimpleDateFormat("dd/MM/yyyy", localeId)
    )
    for (fmt in formats) {
        try {
            val d = fmt.parse(dateStr)
            if (d != null) return d.time
        } catch (_: Exception) {}
    }
    return fallback
}
