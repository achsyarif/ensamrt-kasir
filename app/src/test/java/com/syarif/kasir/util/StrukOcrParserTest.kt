package com.syarif.kasir.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StrukOcrParserTest {

    @Test
    fun testParseReceiptText() {
        val rawText = """
            TOKO BERKAH JAYA
            JL. RAYA NO 10
            
            2x Indomie Goreng 3.500
            1 Beras 5kg 65.000
            Minyak Goreng 14.000
            
            TOTAL 82.500
            TUNAI 100.000
            KEMBALI 17.500
            TERIMA KASIH
        """.trimIndent()

        val items = StrukOcrParser.parse(rawText)

        assertEquals(3, items.size)
        assertEquals("Indomie Goreng", items[0].namaBarang)
        assertEquals(2, items[0].quantity)
        assertEquals(3500.0, items[0].harga, 0.01)

        assertEquals("Beras 5kg", items[1].namaBarang)
        assertEquals(1, items[1].quantity)
        assertEquals(65000.0, items[1].harga, 0.01)

        assertEquals("Minyak Goreng", items[2].namaBarang)
        assertEquals(1, items[2].quantity)
        assertEquals(14000.0, items[2].harga, 0.01)
    }
}
