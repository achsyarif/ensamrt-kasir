package com.syarif.kasir.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private const val TAG = "GeminiStrukScanner"
    private const val PREF_NAME = "kasir_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    internal var base64Encoder: (ByteArray) -> String = { bytes ->
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            println("DEBUG: $TAG: $message")
        }
    }

    private fun logE(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        } catch (_: RuntimeException) {
            println("ERROR: $TAG: $message ${throwable?.message ?: ""}")
        }
    }

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
        val rawMimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        val mimeType = if (rawMimeType.equals("image/png", ignoreCase = true)) "image/png" else "image/jpeg"

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        try {
            return@withContext scanReceiptInternal(bytes, mimeType, apiKey, client)
        } catch (e: Exception) {
            logD("Gemini API call failed (${e.message}), automatically falling back to offline ML Kit OCR")
            // Automatic Offline Fallback to ML Kit StrukOcrParser on any API error (including 404 model not found) or exception
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val visionText = recognizer.process(image).await()
                val offlineItems = StrukOcrParser.parse(visionText.text)
                logD("Offline ML Kit OCR fallback successful - found ${offlineItems.size} items")
                return@withContext offlineItems
            } catch (fallbackException: Exception) {
                logE("Offline ML Kit OCR fallback also failed", fallbackException)
                return@withContext emptyList()
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(result))
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(exception))
            }
        }
    }

    internal suspend fun scanReceiptInternal(
        bytes: ByteArray,
        mimeType: String,
        apiKey: String,
        client: OkHttpClient
    ): List<ParsedReceiptItem> = withContext(Dispatchers.IO) {
        val base64Image = base64Encoder(bytes)

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

        val models = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-3.8-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-1.5-pro"
        )
        val apiVersions = listOf("v1", "v1beta")

        var lastException: Exception? = null

        for (model in models) {
            for (apiVersion in apiVersions) {
                val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$model:generateContent?key=$apiKey"
                val maskedUrl = url.replace(Regex("key=[^&]+"), "key=***")

                logD("Attempting Gemini API call - Model: $model, Version: $apiVersion, URL: $maskedUrl")
                logD("Request Body JSON: $requestBodyJson")

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val code = response.code
                        val responseBody = response.body
                        val responseString = responseBody?.string() ?: ""

                        logD("Gemini API response received - Model: $model, Version: $apiVersion, HTTP Response Code: $code")

                        if (response.isSuccessful) {
                            if (responseString.isEmpty()) {
                                throw RuntimeException("Empty response body from Gemini API")
                            }
                            logD("Gemini API call successful for model '$model' ($apiVersion)")
                            return@withContext parseGeminiResponse(responseString)
                        } else {
                            logE("Gemini API error response - Model: $model, Version: $apiVersion, HTTP Response Code: $code, Error Body: $responseString")
                            val errorMessage = when (code) {
                                404 -> "Model '$model' ($apiVersion) not found (HTTP 404). Please verify API key or model availability."
                                429 -> "API quota limit reached / Resource exhausted (HTTP 429 - Quota Limit Exceeded)."
                                401, 403 -> "Unauthorized / Invalid API key (HTTP $code). Please check your Gemini API key."
                                else -> "Gemini API error (HTTP $code): $responseString"
                            }
                            val exception = RuntimeException(errorMessage)
                            lastException = exception
                        }
                    }
                } catch (e: Exception) {
                    logE("Gemini API exception - Model: $model, Version: $apiVersion, URL: $maskedUrl, Error: ${e.message}", e)
                    lastException = e
                }
            }
        }
        logE("All Gemini models and API versions failed. Last exception: ${lastException?.message}", lastException)
        throw lastException ?: RuntimeException("All Gemini models and API versions failed.")
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
