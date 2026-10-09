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
        "cash", "change", "sub total", "store", "shop", "merchant",
        "catatan", "belanja", "toko", "warung", "lunas", "sisa", "kembalian"
    )

    private val invalidNameWords = listOf("rp", "idr", "harga", "subtotal", "total", "tunai", "kembali")

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

        // 1. Try regex pattern: Qty + Item Name + Price (e.g. "9 Lifebuoy cair 16.000", "1 caplang 30ml 10.500", "7 micelar gcg 300 31.000", "2x Indomie Goreng 3.500")
        val qtyNamePriceRegex = Regex("^(\\d+)(?:\\s*[xX]|\\s*pcs|\\s*bks)?\\s+(.+?)\\s+([Rr][pP]?\\s*[\\d\\.,]+(?:\\s*(?:k|rb|ribu|jt|juta)+)?)$", RegexOption.IGNORE_CASE)
        val matchResult = qtyNamePriceRegex.find(lineWithoutBullet)
        if (matchResult != null) {
            val qtyStr = matchResult.groupValues[1]
            val nameStr = matchResult.groupValues[2].trim()
            val priceStr = matchResult.groupValues[3]

            val qty = qtyStr.toIntOrNull() ?: 1
            val price = parsePrice(priceStr)
            if (price != null && isValidItemName(nameStr)) {
                return ParsedReceiptItem(
                    namaBarang = nameStr,
                    quantity = qty,
                    harga = price
                )
            }
        }

        // 2. Try regex pattern: Item Name + Qty + Price (e.g. "Indomie Goreng 2 3.500")
        val nameQtyPriceRegex = Regex("^(.+?)\\s+(\\d+)(?:\\s*[xX]|\\s*pcs|\\s*bks)?\\s+([Rr][pP]?\\s*[\\d\\.,]+(?:\\s*(?:k|rb|ribu|jt|juta)+)?)$", RegexOption.IGNORE_CASE)
        val nameQtyMatch = nameQtyPriceRegex.find(lineWithoutBullet)
        if (nameQtyMatch != null) {
            val nameStr = nameQtyMatch.groupValues[1].trim()
            val qtyStr = nameQtyMatch.groupValues[2]
            val priceStr = nameQtyMatch.groupValues[3]

            val qty = qtyStr.toIntOrNull() ?: 1
            val price = parsePrice(priceStr)
            if (price != null && isValidItemName(nameStr)) {
                return ParsedReceiptItem(
                    namaBarang = nameStr,
                    quantity = qty,
                    harga = price
                )
            }
        }

        // 3. Try regex pattern: Item Name + Price (e.g. "Minyak Goreng 14.000")
        val namePriceRegex = Regex("^(.+?)\\s+([Rr][pP]?\\s*[\\d\\.,]+(?:\\s*(?:k|rb|ribu|jt|juta)+)?)$", RegexOption.IGNORE_CASE)
        val nameMatchResult = namePriceRegex.find(lineWithoutBullet)
        if (nameMatchResult != null) {
            val nameStr = nameMatchResult.groupValues[1].trim()
            val priceStr = nameMatchResult.groupValues[2]

            val price = parsePrice(priceStr)
            if (price != null && isValidItemName(nameStr)) {
                return ParsedReceiptItem(
                    namaBarang = nameStr,
                    quantity = 1,
                    harga = price
                )
            }
        }

        // 4. Fallback to token-based parsing
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
        if (!isValidItemName(itemName)) return null

        return ParsedReceiptItem(
            namaBarang = itemName,
            quantity = qty,
            harga = price
        )
    }

    private fun isValidItemName(name: String): Boolean {
        if (name.isBlank() || !name.contains(Regex("[a-zA-Z]"))) return false
        val lower = name.lowercase()
        if (invalidNameWords.any { lower == it || lower.startsWith("$it ") }) return false
        return true
    }

    private fun parsePrice(token: String): Double? {
        var clean = token.trim()
        // Remove currency prefix/suffix
        clean = clean.replace(Regex("^(?:[rR][pP]|[iI][dD][rR])\\s*"), "")
        clean = clean.replace(Regex("\\s*(?:,-|-)$"), "")

        var multiplier = 1.0
        val lower = clean.lowercase()
        if (lower.contains("jt") || lower.contains("juta")) {
            multiplier = 1_000_000.0
            clean = clean.replace(Regex("(?i)(?:jt|juta)"), "")
        } else if (lower.contains("rb") || lower.contains("ribu") || lower.contains("k")) {
            multiplier = 1_000.0
            clean = clean.replace(Regex("(?i)(?:rb|ribu|k)"), "")
        }

        // Fix OCR confusions in numbers: O -> 0, l/I -> 1, S -> 5, etc.
        clean = clean.replace('O', '0')
            .replace('o', '0')
            .replace('l', '1')
            .replace('I', '1')
            .replace('S', '5')
            .replace('s', '5')
            .replace('B', '8')

        val numClean = clean.replace(Regex("[^0-9.,]"), "")
        if (numClean.isBlank()) return null

        val normalized = if (numClean.contains(".") && numClean.contains(",")) {
            numClean.replace(".", "").replace(",", ".")
        } else if (numClean.contains(".")) {
            if (numClean.count { it == '.' } > 1 || numClean.substringAfterLast('.').length == 3) {
                numClean.replace(".", "")
            } else {
                numClean
            }
        } else if (numClean.contains(",")) {
            if (numClean.count { it == ',' } > 1 || numClean.substringAfterLast(',').length == 3) {
                numClean.replace(",", "")
            } else {
                numClean.replace(",", ".")
            }
        } else {
            numClean
        }

        val baseValue = normalized.toDoubleOrNull() ?: return null
        return baseValue * multiplier
    }
}
