package com.syarif.kasir.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @param:Json(name = "content") val content: Content?
)

@JsonClass(generateAdapter = true)
data class Content(
    @param:Json(name = "parts") val parts: List<Part>?
)

@JsonClass(generateAdapter = true)
data class Part(
    @param:Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GeminiReceiptItemDto(
    @param:Json(name = "nama_barang") val namaBarang: String?,
    @param:Json(name = "qty") val qty: Int?,
    @param:Json(name = "harga_satuan") val hargaSatuan: Double?
)

object GeminiStrukScanner {
    private const val PREF_NAME = "kasir_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null)
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    suspend fun scanReceipt(context: Context, imageUri: Uri, apiKey: String): List<ParsedReceiptItem> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw IllegalArgumentException("Gagal membaca InputStream dari Uri gambar")
        val bytes = inputStream.use { it.readBytes() }
        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"

        val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val prompt = "Extract all purchased items from this receipt image into a JSON array of objects with keys: \"nama_barang\" (string), \"qty\" (integer), \"harga_satuan\" (number). Return ONLY valid JSON array without markdown code blocks (no ```json ... ```) and without explanatory text. Example format: [{\"nama_barang\": \"Kopi Susu\", \"qty\": 2, \"harga_satuan\": 15000}]"

        val promptJson = moshi.adapter(String::class.java).toJson(prompt)
        val requestBodyJson = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": $promptJson
                    },
                    {
                      "inline_data": {
                        "mime_type": "$mimeType",
                        "data": "$base64Image"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

        val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
        var lastException: Exception? = null

        for (model in models) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body
                    if (response.isSuccessful) {
                        val responseString = responseBody?.string() ?: throw RuntimeException("Empty response body from Gemini API")
                        return@withContext parseGeminiResponse(responseString)
                    } else {
                        val errorBody = responseBody?.string() ?: "Unknown error"
                        lastException = RuntimeException("Gemini API error ($model) (${response.code}): $errorBody")
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("Failed to scan receipt with Gemini API")
    }

    fun parseGeminiResponse(responseJsonString: String): List<ParsedReceiptItem> {
        val responseAdapter = moshi.adapter(GeminiResponse::class.java)
        val geminiResponse = responseAdapter.fromJson(responseJsonString)
            ?: throw RuntimeException("Empty response from Gemini API")

        val candidates = geminiResponse.candidates
            ?: throw RuntimeException("No candidates found in Gemini response")
        if (candidates.isEmpty()) throw RuntimeException("Empty candidates in Gemini response")

        val rawText = candidates[0].content?.parts?.firstOrNull()?.text?.trim()
            ?: throw RuntimeException("No text part found in Gemini response")

        val cleanedText = rawText
            .replace(Regex("^```json\\s*"), "")
            .replace(Regex("^```\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()

        val firstBracket = cleanedText.indexOf('[')
        val lastBracket = cleanedText.lastIndexOf(']')
        val jsonToParse = if (firstBracket >= 0 && lastBracket > firstBracket) {
            cleanedText.substring(firstBracket, lastBracket + 1)
        } else {
            cleanedText
        }

        val type = Types.newParameterizedType(List::class.java, GeminiReceiptItemDto::class.java)
        val listAdapter = moshi.adapter<List<GeminiReceiptItemDto>>(type)
        val itemDtos = listAdapter.fromJson(jsonToParse)
            ?: throw RuntimeException("Failed to parse receipt items JSON array")

        val items = mutableListOf<ParsedReceiptItem>()
        for (dto in itemDtos) {
            val namaBarang = dto.namaBarang?.trim() ?: continue
            val qty = dto.qty ?: 1
            val harga = dto.hargaSatuan ?: 0.0
            if (namaBarang.isNotBlank() && harga > 0.0) {
                items.add(ParsedReceiptItem(namaBarang = namaBarang, quantity = qty, harga = harga))
            }
        }
        return items
    }
}
