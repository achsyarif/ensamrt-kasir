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

    @Test
    fun testParseHandwrittenReceiptText() {
        val rawText = """
            CATATAN BELANJA
            
            9 Lifebuoy cair 16.000
            1 caplang 30ml 10.500
            7 micelar gcg 300 31.000
            
            TOTAL 300.000
        """.trimIndent()

        val items = StrukOcrParser.parse(rawText)

        assertEquals(3, items.size)
        assertEquals("Lifebuoy cair", items[0].namaBarang)
        assertEquals(9, items[0].quantity)
        assertEquals(16000.0, items[0].harga, 0.01)

        assertEquals("caplang 30ml", items[1].namaBarang)
        assertEquals(1, items[1].quantity)
        assertEquals(10500.0, items[1].harga, 0.01)

        assertEquals("micelar gcg 300", items[2].namaBarang)
        assertEquals(7, items[2].quantity)
        assertEquals(31000.0, items[2].harga, 0.01)
    }

    @Test
    fun testParseHandwrittenReceiptWithMultipliersAndCurrency() {
        val rawText = """
            5 Sabun Mandi 15k
            2 Shampo 10.5rb
            Rp 50.000
        """.trimIndent()

        val items = StrukOcrParser.parse(rawText)

        assertEquals(2, items.size)
        assertEquals("Sabun Mandi", items[0].namaBarang)
        assertEquals(5, items[0].quantity)
        assertEquals(15000.0, items[0].harga, 0.01)

        assertEquals("Shampo", items[1].namaBarang)
        assertEquals(2, items[1].quantity)
        assertEquals(10500.0, items[1].harga, 0.01)
    }
}
