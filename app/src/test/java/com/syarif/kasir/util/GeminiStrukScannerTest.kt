package com.syarif.kasir.util

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiStrukScannerTest {

    @Before
    fun setUp() {
        GeminiStrukScanner.base64Encoder = { bytes ->
            Base64.getEncoder().encodeToString(bytes)
        }
    }

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

    @Test
    fun testScanReceiptModelIterationFallback() = runTest {
        val successResponseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"nama_barang\": \"Kopi Pro\", \"qty\": 1, \"harga_satuan\": 20000}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val urlString = chain.request().url.toString()
                val isFail = urlString.contains("gemini-3.8-flash") ||
                    urlString.contains("gemini-3.7-flash")
                val responseBody = if (isFail) {
                    "{\"error\":{\"code\":404,\"message\":\"Not Found\"}}".toResponseBody("application/json".toMediaType())
                } else {
                    successResponseJson.toResponseBody("application/json".toMediaType())
                }
                val code = if (isFail) 404 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 404) "Not Found" else "OK")
                    .body(responseBody)
                    .build()
            }
            .build()

        val items = GeminiStrukScanner.scanReceiptInternal(
            "test image bytes".toByteArray(),
            "image/jpeg",
            "test_api_key",
            client
        )

        assertEquals(1, items.size)
        assertEquals("Kopi Pro", items[0].namaBarang)
        assertEquals(1, items[0].quantity)
        assertEquals(20000.0, items[0].harga, 0.01)
    }

    @Test(expected = RuntimeException::class)
    fun testScanReceiptAllModelsFail404() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val responseBody = "{\"error\":{\"code\":404,\"message\":\"Not Found\"}}".toResponseBody("application/json".toMediaType())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body(responseBody)
                    .build()
            }
            .build()

        GeminiStrukScanner.scanReceiptInternal(
            "test image bytes".toByteArray(),
            "image/jpeg",
            "test_api_key",
            client
        )
    }
}
