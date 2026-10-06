package com.syarif.kasir.util

data class ParsedReceiptItem(
    val namaBarang: String,
    val quantity: Int,
    val harga: Double
)

object StrukOcrParser {
    private val ignoreKeywords = listOf(
        "total", "tunai", "kembali", "kasir", "jl.", "jalan", "telp", "hp",
        "terima kasih", "nota", "faktur", "tanggal", "jam", "subtotal",
        "diskon", "pajak", "ppn", "admin", "bca", "qris", "debit", "kredit",
        "cash", "change", "sub total", "store", "shop", "merchant"
    )

    fun parse(rawText: String): List<ParsedReceiptItem> {
        val lines = rawText.split("\n")
        val items = mutableListOf<ParsedReceiptItem>()

        for (line in lines) {
            val cleanedLine = line.trim()
            if (cleanedLine.length < 3) continue
            val lower = cleanedLine.lowercase()
            if (ignoreKeywords.any { lower.contains(it) }) continue

            val parsedItem = parseLine(cleanedLine)
            if (parsedItem != null && parsedItem.namaBarang.isNotBlank() && parsedItem.harga > 0) {
                items.add(parsedItem)
            }
        }
        return items
    }

    private fun parseLine(line: String): ParsedReceiptItem? {
        val lineWithoutBullet = line.replaceFirst(Regex("^\\d+[\"\\.\\)]\\s*"), "").trim()
        if (lineWithoutBullet.isBlank()) return null

        val words = lineWithoutBullet.split(Regex("\\s+")).toMutableList()
        if (words.size < 2) return null

        val lastWord = words.last()
        val price = parsePrice(lastWord) ?: return null
        words.removeLast() // remove price

        var qty = 1
        val firstWord = words.firstOrNull()?.lowercase()?.replace("x", "")?.replace("pcs", "")?.replace("bks", "")?.trim()
        val firstQty = firstWord?.toIntOrNull()
        if (firstQty != null && firstQty > 0 && firstQty < 10000) {
            qty = firstQty
            words.removeFirst()
        } else {
            val lastRemWord = words.lastOrNull()?.lowercase()?.replace("x", "")?.replace("pcs", "")?.replace("bks", "")?.trim()
            val lastQty = lastRemWord?.toIntOrNull()
            if (lastQty != null && lastQty > 0 && lastQty < 10000) {
                qty = lastQty
                words.removeLast()
            }
        }

        val itemName = words.joinToString(" ").trim().removeSurrounding("-", "").trim()
        if (itemName.isBlank()) return null

        return ParsedReceiptItem(
            namaBarang = itemName,
            quantity = qty,
            harga = price
        )
    }

    private fun parsePrice(token: String): Double? {
        val clean = token.replace(Regex("[^0-9.,]"), "")
        if (clean.isBlank()) return null

        val normalized = if (clean.contains(".") && clean.contains(",")) {
            clean.replace(".", "").replace(",", ".")
        } else if (clean.contains(".")) {
            if (clean.count { it == '.' } > 1 || clean.substringAfterLast('.').length == 3) {
                clean.replace(".", "")
            } else {
                clean
            }
        } else if (clean.contains(",")) {
            if (clean.count { it == ',' } > 1 || clean.substringAfterLast(',').length == 3) {
                clean.replace(",", "")
            } else {
                clean.replace(",", ".")
            }
        } else {
            clean
        }

        return normalized.toDoubleOrNull()
    }
}
