package com.syarif.kasir.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiStrukScannerTest {

    @Test
    fun testParseGeminiResponse() {
        val sampleResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n[\n  {\"nama_barang\": \"Kopi Hitam\", \"qty\": 2, \"harga_satuan\": 10000},\n  {\"nama_barang\": \"Roti Bakar\", \"qty\": 1, \"harga_satuan\": 15000}\n]\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val items = GeminiStrukScanner.parseGeminiResponse(sampleResponse)

        assertEquals(2, items.size)
        assertEquals("Kopi Hitam", items[0].namaBarang)
        assertEquals(2, items[0].quantity)
        assertEquals(10000.0, items[0].harga, 0.01)

        assertEquals("Roti Bakar", items[1].namaBarang)
        assertEquals(1, items[1].quantity)
        assertEquals(15000.0, items[1].harga, 0.01)
    }
}
