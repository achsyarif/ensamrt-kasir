package com.syarif.kasir.data.sync

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.syarif.kasir.data.local.dao.BarangDao
import com.syarif.kasir.data.local.dao.HargaGrosirDao
import com.syarif.kasir.data.local.dao.PelangganDao
import com.syarif.kasir.data.local.dao.TransaksiDao
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import java.text.SimpleDateFormat
import java.util.Locale
import com.syarif.kasir.data.remote.ApiService
import com.syarif.kasir.data.remote.adapter.FlexiblePrimitiveAdapterFactory
import com.syarif.kasir.data.remote.dto.BarangDto
import com.syarif.kasir.data.remote.dto.CartItemDto
import com.syarif.kasir.data.remote.dto.DetailTransaksiDto
import com.syarif.kasir.data.remote.dto.HargaGrosirDto
import com.syarif.kasir.data.remote.dto.HargaGrosirListJsonAdapter
import com.syarif.kasir.data.remote.dto.PaymentDto
import com.syarif.kasir.data.remote.dto.PelangganDto
import com.syarif.kasir.data.remote.dto.SimpanTransaksiRequest
import com.syarif.kasir.data.remote.dto.SyncResponseDto
import com.syarif.kasir.data.remote.dto.TransaksiDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncEngine(
    private var apiService: ApiService,
    private val barangDao: BarangDao,
    private val hargaGrosirDao: HargaGrosirDao,
    private val pelangganDao: PelangganDao,
    private val transaksiDao: TransaksiDao
) {

    companion object {
        private const val TAG = "SyncEngine"
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(FlexiblePrimitiveAdapterFactory)
        .add(Types.newParameterizedType(List::class.java, HargaGrosirDto::class.java), HargaGrosirListJsonAdapter())
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

    fun updateApiService(newApiService: ApiService) {
        this.apiService = newApiService
    }

    /**
     * Cleans and sanitizes raw JSON string responses from server.
     * - Removes UTF-8 BOM (\uFEFF) and zero-width spaces (\u200B, \u200C, \u200D).
     * - Trims any PHP warning/notice preamble or HTML before the first '[' or '{'.
     * - Trims any PHP notice/warning text after the last ']' or '}'.
     */
    fun cleanJson(rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return ""

        var cleaned = rawJson
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")

        val firstBracket = cleaned.indexOf('[')
        val firstBrace = cleaned.indexOf('{')
        val startIdx = when {
            firstBracket != -1 && firstBrace != -1 -> minOf(firstBracket, firstBrace)
            firstBracket != -1 -> firstBracket
            firstBrace != -1 -> firstBrace
            else -> -1
        }

        val lastBracket = cleaned.lastIndexOf(']')
        val lastBrace = cleaned.lastIndexOf('}')
        val endIdx = maxOf(lastBracket, lastBrace)

        if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
            cleaned = cleaned.substring(startIdx, endIdx + 1)
        }

        return cleaned.trim()
    }

    fun checkHtmlOrError(rawBody: String?, code: Int): String? {
        if (rawBody.isNullOrBlank()) {
            return "Server returned empty response (HTTP $code)"
        }
        val trimmed = rawBody.trim()
        if (code >= 400) {
            if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true)) {
                return "Server returned HTML error page (HTTP $code - InfinityFree block/error 403/500)"
            }
            return "Server error HTTP $code: ${if (trimmed.length > 200) trimmed.substring(0, 200) + "..." else trimmed}"
        }
        if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true)) {
            return "Server returned HTML page instead of JSON (HTTP $code - InfinityFree security/error page)"
        }
        return null
    }

    fun parseGrosir(grosirObj: Any?): List<HargaGrosirDto> {
        if (grosirObj == null) return emptyList()
        return when (grosirObj) {
            is List<*> -> {
                val list = mutableListOf<HargaGrosirDto>()
                for (item in grosirObj) {
                    when (item) {
                        is HargaGrosirDto -> list.add(item)
                        is Map<*, *> -> list.add(mapToHargaGrosirDto(item))
                        is String -> list.addAll(parseGrosir(item))
                        else -> {
                            if (item != null) {
                                try {
                                    val json = moshi.adapter(Any::class.java).lenient().toJson(item)
                                    list.addAll(parseGrosir(json))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                list
            }
            is String -> {
                val cleaned = cleanJson(grosirObj)
                if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true) || cleaned == "[]") {
                    emptyList()
                } else {
                    try {
                        val mapListType = Types.newParameterizedType(List::class.java, Map::class.java)
                        val mapListAdapter = moshi.adapter<List<Map<*, *>>>(mapListType).lenient()
                        val mapList = mapListAdapter.fromJson(cleaned)
                        if (!mapList.isNullOrEmpty()) {
                            mapList.map { mapToHargaGrosirDto(it) }
                        } else {
                            val mapAdapter = moshi.adapter(Map::class.java).lenient()
                            val map = mapAdapter.fromJson(cleaned)
                            if (map != null) listOf(mapToHargaGrosirDto(map)) else emptyList()
                        }
                    } catch (_: Exception) {
                        try {
                            val mapAdapter = moshi.adapter(Map::class.java).lenient()
                            val map = mapAdapter.fromJson(cleaned)
                            if (map != null) listOf(mapToHargaGrosirDto(map)) else emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }
            is Map<*, *> -> {
                listOf(mapToHargaGrosirDto(grosirObj))
            }
            else -> {
                try {
                    val json = moshi.adapter(Any::class.java).lenient().toJson(grosirObj)
                    parseGrosir(json)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> parseListJson(jsonStr: String, clazz: Class<T>): List<T>? {
        val cleaned = cleanJson(jsonStr)
        if (cleaned.isBlank()) return null

        // 1. Try direct list parsing with Moshi
        try {
            val listType = Types.newParameterizedType(List::class.java, clazz)
            val adapter: JsonAdapter<List<T>> = moshi.adapter<List<T>>(listType).lenient()
            val parsed = adapter.fromJson(cleaned)
            if (!parsed.isNullOrEmpty()) {
                logD("Successfully parsed direct JSON array of ${clazz.simpleName} (count: ${parsed.size})")
                return parsed
            } else if (parsed != null && cleaned.trim().startsWith("[")) {
                return parsed
            }
        } catch (e: Exception) {
            logD("Direct list parsing as ${clazz.simpleName} failed: ${e.message}, trying map / fallback parsing")
        }

        // 2. Try parsing as List of Maps or Map of Lists with DTO dynamic mapping
        try {
            if (cleaned.startsWith("[")) {
                val mapListType = Types.newParameterizedType(List::class.java, Map::class.java)
                val mapListAdapter = moshi.adapter<List<Map<String, Any?>>>(mapListType).lenient()
                val mapList = mapListAdapter.fromJson(cleaned)
                if (mapList != null) {
                    val resultList = mutableListOf<T>()
                    for (mapItem in mapList) {
                        val dto = mapToDto(mapItem, clazz)
                        if (dto != null) {
                            resultList.add(dto)
                        }
                    }
                    logD("Successfully parsed List<Map> into ${clazz.simpleName} (count: ${resultList.size})")
                    return resultList
                }
            } else {
                val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(mapType).lenient()
                val map = mapAdapter.fromJson(cleaned)
                if (map != null) {
                    val candidateKeys = listOf("data", "barang", "pelanggan", "transaksi", "history", "result", "items", "content", "list")
                    for (key in candidateKeys) {
                        val value = map[key]
                        if (value is List<*>) {
                            val resultList = mutableListOf<T>()
                            for (item in value) {
                                if (item is Map<*, *>) {
                                    val dto = mapToDto(item, clazz)
                                    if (dto != null) {
                                        resultList.add(dto)
                                    }
                                } else if (item != null) {
                                    try {
                                        val itemJson = moshi.adapter(Any::class.java).lenient().toJson(item)
                                        val obj = moshi.adapter(clazz).lenient().fromJson(itemJson)
                                        if (obj != null) {
                                            resultList.add(obj)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            logD("Successfully extracted list of ${clazz.simpleName} from wrapper key: '$key' (count: ${resultList.size})")
                            return resultList
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logD("Fallback map list parsing for ${clazz.simpleName} failed: ${e.message}")
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mapToDto(map: Map<*, *>, clazz: Class<T>): T? {
        return when (clazz) {
            BarangDto::class.java -> mapToBarangDto(map) as T
            PelangganDto::class.java -> mapToPelangganDto(map) as T
            HargaGrosirDto::class.java -> mapToHargaGrosirDto(map) as T
            TransaksiDto::class.java -> mapToTransaksiDto(map) as T
            DetailTransaksiDto::class.java -> mapToDetailTransaksiDto(map) as T
            else -> {
                try {
                    val json = moshi.adapter(Any::class.java).lenient().toJson(map)
                    moshi.adapter(clazz).lenient().fromJson(json)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun mapToBarangDto(map: Map<*, *>): BarangDto {
        val id = parseIntValue(map["id"] ?: map["id_barang"] ?: map["barang_id"])
        val kodeBarang = (map["kode_barang"] ?: map["kodeBarang"] ?: map["kode"] ?: map["barcode"])?.toString()
        val namaBarang = (map["nama_barang"] ?: map["namaBarang"] ?: map["nama"] ?: map["barang"] ?: map["title"])?.toString() ?: ""
        val kategori = (map["kategori"] ?: map["category"])?.toString()
        val hargaBeli = parseDoubleValue(map["harga_beli"] ?: map["hargaBeli"] ?: map["hb"] ?: map["harga_pokok"]) ?: 0.0
        val hargaJual = parseDoubleValue(map["harga_jual"] ?: map["hargaJual"] ?: map["hj"] ?: map["harga"] ?: map["price"]) ?: 0.0
        val stok = parseIntValue(map["stok"])
        val stock = parseIntValue(map["stock"])
        val qty = parseIntValue(map["qty"])
        val jumlah = parseIntValue(map["jumlah"])
        val satuan = (map["satuan"] ?: map["unit"])?.toString() ?: "pcs"
        val barcode = (map["barcode"] ?: map["kode_barcode"])?.toString()
        val isArchived = parseBooleanValue(map["is_archived"] ?: map["isArchived"] ?: map["archived"])
        val rawGrosir = map["harga_grosir"] ?: map["grosir"] ?: map["hargaGrosir"]
        val grosirList = parseGrosir(rawGrosir)
        val updatedAt = (map["updated_at"] ?: map["updatedAt"] ?: map["created_at"])?.toString()

        return BarangDto(
            id = id,
            kodeBarang = kodeBarang,
            namaBarang = namaBarang,
            kategori = kategori,
            hargaBeli = hargaBeli,
            hargaJual = hargaJual,
            stok = stok,
            stock = stock,
            qty = qty,
            jumlah = jumlah,
            satuan = satuan,
            barcode = barcode,
            hargaGrosirList = grosirList,
            grosirList = grosirList,
            isArchived = isArchived,
            updatedAt = updatedAt
        )
    }

    fun mapToPelangganDto(map: Map<*, *>): PelangganDto {
        val id = parseIntValue(map["id"] ?: map["id_pelanggan"] ?: map["pelanggan_id"])
        val nama = (map["nama"] ?: map["nama_pelanggan"] ?: map["namaPelanggan"] ?: map["pelanggan"] ?: map["name"])?.toString() ?: "Pelanggan"
        val telepon = (map["telepon"] ?: map["no_telepon"] ?: map["no_telp"] ?: map["telp"] ?: map["phone"] ?: map["hp"] ?: map["mobile"])?.toString()
        val alamat = (map["alamat"] ?: map["address"])?.toString()
        val catatan = (map["catatan"] ?: map["note"] ?: map["keterangan"] ?: map["description"])?.toString()
        val updatedAt = (map["updated_at"] ?: map["updatedAt"] ?: map["created_at"])?.toString()

        return PelangganDto(
            id = id,
            nama = nama,
            telepon = telepon,
            alamat = alamat,
            catatan = catatan,
            updatedAt = updatedAt
        )
    }

    fun mapToHargaGrosirDto(map: Map<*, *>): HargaGrosirDto {
        val id = parseIntValue(map["id"] ?: map["id_grosir"])
        val barangId = parseIntValue(map["barang_id"] ?: map["barangId"] ?: map["id_barang"])
        val minQty = parseIntValue(map["min_qty"] ?: map["minQty"] ?: map["qty"] ?: map["min"] ?: map["minimum_qty"] ?: map["minimal"]) ?: 1
        val harga = parseDoubleValue(map["harga"] ?: map["harga_grosir"] ?: map["hargaGrosir"] ?: map["price"] ?: map["harga_satuan"] ?: map["price_per_unit"]) ?: 0.0
        return HargaGrosirDto(
            id = id,
            barangId = barangId,
            minQty = minQty,
            harga = harga
        )
    }

    fun mapToTransaksiDto(map: Map<*, *>): TransaksiDto {
        val paymentMap = map["payment"] as? Map<*, *>

        val id = parseIntValue(map["id"] ?: map["transaksi_id"] ?: map["id_transaksi"] ?: paymentMap?.get("id"))
        val noFaktur = (map["no_faktur"] ?: map["noFaktur"] ?: map["faktur"] ?: map["invoice"] ?: map["no_nota"])?.toString()
            ?: if (id != null) "INV-$id" else ""
        val pelangganId = parseIntValue(map["pelanggan_id"] ?: map["pelangganId"] ?: map["id_pelanggan"] ?: paymentMap?.get("pelanggan_id"))
        val namaPelanggan = (
            map["nama_pembeli"]
                ?: map["nama_pelanggan"]
                ?: map["namaPelanggan"]
                ?: map["buyerName"]
                ?: map["buyer_name"]
                ?: paymentMap?.get("nama_pembeli")
                ?: paymentMap?.get("buyerName")
                ?: paymentMap?.get("nama_pelanggan")
                ?: map["pelanggan"]
                ?: map["nama"]
        )?.toString()
        val tanggal = (map["waktu_transaksi"] ?: map["tanggal"] ?: map["created_at"] ?: map["date"] ?: map["tgl"])?.toString()
        val totalHarga = parseDoubleValue(
            map["total_akhir"]
                ?: map["total_belanja"]
                ?: map["total_harga"]
                ?: map["totalHarga"]
                ?: map["grand_total"]
                ?: map["total"]
                ?: map["finalTotal"]
                ?: map["final_total"]
                ?: paymentMap?.get("total_akhir")
                ?: paymentMap?.get("total_belanja")
                ?: paymentMap?.get("finalTotal")
                ?: paymentMap?.get("final_total")
                ?: paymentMap?.get("total")
                ?: paymentMap?.get("total_harga")
        ) ?: 0.0
        val diskonVal = parseDoubleValue(
            map["diskon"]
                ?: map["discount"]
                ?: paymentMap?.get("diskon")
                ?: paymentMap?.get("discount")
        )
        val uangBayar = parseDoubleValue(
            map["uang_bayar"]
                ?: map["bayar"]
                ?: map["paid"]
                ?: map["cash"]
                ?: paymentMap?.get("uang_bayar")
                ?: paymentMap?.get("bayar")
                ?: paymentMap?.get("paid")
        ) ?: 0.0
        val dp = parseDoubleValue(
            map["dp"]
                ?: map["uang_dp"]
                ?: paymentMap?.get("dp")
                ?: paymentMap?.get("uang_dp")
        ) ?: 0.0
        val sisa = parseDoubleValue(
            map["sisa"]
                ?: map["uang_sisa"]
                ?: paymentMap?.get("sisa")
                ?: paymentMap?.get("uang_sisa")
        ) ?: 0.0
        val kembali = parseDoubleValue(
            map["kembali"]
                ?: map["change"]
                ?: paymentMap?.get("change")
                ?: paymentMap?.get("kembali")
        ) ?: 0.0
        val metodePembayaran = (
            map["metode_pembayaran"]
                ?: map["metodePembayaran"]
                ?: map["metode"]
                ?: map["payment_method"]
                ?: paymentMap?.get("metode_pembayaran")
                ?: paymentMap?.get("metodePembayaran")
                ?: paymentMap?.get("metode")
                ?: paymentMap?.get("payment_method")
                ?: paymentMap?.get("method")
        )?.toString()?.ifBlank { null } ?: "TUNAI"

        val rawDetails = map["items"] ?: map["detail"] ?: map["details"] ?: map["item"]
        val detailsList = mutableListOf<DetailTransaksiDto>()
        if (rawDetails is List<*>) {
            for (d in rawDetails) {
                if (d is Map<*, *>) {
                    detailsList.add(mapToDetailTransaksiDto(d))
                }
            }
        }

        val paymentDto = if (paymentMap != null) {
            PaymentDto(
                total = parseDoubleValue(paymentMap["total"] ?: paymentMap["total_belanja"] ?: paymentMap["total_akhir"]),
                discount = parseDoubleValue(paymentMap["discount"] ?: paymentMap["diskon"]),
                diskon = parseDoubleValue(paymentMap["diskon"] ?: paymentMap["discount"]),
                finalTotal = parseDoubleValue(paymentMap["finalTotal"] ?: paymentMap["final_total"] ?: paymentMap["total_akhir"] ?: paymentMap["total"]),
                method = (paymentMap["method"] ?: paymentMap["metode_pembayaran"] ?: paymentMap["metodePembayaran"] ?: paymentMap["metode"] ?: paymentMap["payment_method"])?.toString(),
                paid = parseDoubleValue(paymentMap["paid"] ?: paymentMap["uang_bayar"] ?: paymentMap["bayar"]),
                change = parseDoubleValue(paymentMap["change"] ?: paymentMap["kembalian"] ?: paymentMap["kembali"]),
                buyerName = (paymentMap["buyerName"] ?: paymentMap["nama_pembeli"])?.toString(),
                dp = parseDoubleValue(paymentMap["dp"] ?: paymentMap["uang_dp"]),
                sisa = parseDoubleValue(paymentMap["sisa"] ?: paymentMap["uang_sisa"])
            )
        } else null

        return TransaksiDto(
            id = id,
            noFaktur = noFaktur,
            pelangganId = pelangganId,
            rawNamaPelanggan = namaPelanggan,
            rawTanggal = tanggal,
            rawTotalHarga = totalHarga,
            rawDiskon = diskonVal,
            discountAlt = diskonVal,
            rawBayar = uangBayar,
            uangBayarAlt = uangBayar,
            rawDp = dp,
            uangDp = dp,
            rawSisa = sisa,
            rawKembali = kembali,
            rawMetodePembayaran = metodePembayaran,
            payment = paymentDto,
            rawDetails = detailsList
        )
    }

    fun mapToDetailTransaksiDto(map: Map<*, *>): DetailTransaksiDto {
        val id = parseIntValue(map["id"] ?: map["detail_id"] ?: map["id_detail"])
        val transaksiId = parseIntValue(map["transaksi_id"] ?: map["transaksiId"] ?: map["id_transaksi"])
        val barangId = parseIntValue(map["barang_id"] ?: map["barangId"] ?: map["id_barang"] ?: map["id"]) ?: 0
        val namaBarang = (
            map["nama_barang_saat_transaksi"]
                ?: map["nama_barang"]
                ?: map["namaBarang"]
                ?: map["nama"]
                ?: map["barang"]
                ?: map["title"]
        )?.toString() ?: ""
        val jumlah = parseIntValue(map["qty"] ?: map["jumlah"] ?: map["quantity"] ?: map["count"]) ?: 0
        val hargaSatuan = parseDoubleValue(
            map["harga_saat_transaksi"]
                ?: map["harga_satuan"]
                ?: map["hargaSatuan"]
                ?: map["harga"]
                ?: map["price"]
        ) ?: 0.0
        val subtotal = parseDoubleValue(map["subtotal"] ?: map["total"]) ?: (jumlah * hargaSatuan)

        return DetailTransaksiDto(
            id = id,
            transaksiId = transaksiId,
            barangId = barangId,
            rawNamaBarang = namaBarang,
            rawJumlah = jumlah,
            rawHargaSatuan = hargaSatuan,
            rawSubtotal = subtotal
        )
    }

    fun parseIntValue(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) return value.toInt()
        if (value is Boolean) return if (value) 1 else 0
        val str = value.toString().trim()
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
        return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
    }

    fun parseDoubleValue(value: Any?): Double? {
        if (value == null) return null
        if (value is Number) return value.toDouble()
        if (value is Boolean) return if (value) 1.0 else 0.0
        val str = value.toString().trim().replace(',', '.')
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
        return str.toDoubleOrNull()
    }

    fun parseBooleanValue(value: Any?): Boolean? {
        if (value == null) return null
        if (value is Boolean) return value
        if (value is Number) return value.toInt() != 0
        val str = value.toString().trim()
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
        return str.equals("1", ignoreCase = true) ||
               str.equals("true", ignoreCase = true) ||
               str.equals("yes", ignoreCase = true) ||
               str.equals("y", ignoreCase = true)
    }

    fun parseSyncResponse(jsonStr: String): SyncResponseDto? {
        val cleaned = cleanJson(jsonStr)
        if (cleaned.isBlank()) return null

        try {
            val adapter = moshi.adapter(SyncResponseDto::class.java).lenient()
            val parsed = adapter.fromJson(cleaned)
            if (parsed != null && (parsed.status != null || !parsed.syncedIds.isNullOrEmpty() || !parsed.syncedIdsAlt.isNullOrEmpty() || parsed.id != null || parsed.insertedId != null)) {
                logD("Successfully parsed SyncResponseDto directly")
                return parsed
            }
        } catch (e: Exception) {
            logD("Direct SyncResponseDto parsing failed: ${e.message}, trying map fallback")
        }

        try {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>(mapType).lenient()
            val map = mapAdapter.fromJson(cleaned)
            if (map != null) {
                val status = map["status"] ?: map["success"]
                val message = map["message"] as? String
                val syncedIds = (map["synced_ids"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() }
                    ?: (map["syncedIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() }
                val id = map["id"]
                val insertedId = map["inserted_id"] ?: map["insertedId"]

                logD("Successfully parsed SyncResponseDto from map fallback")
                return SyncResponseDto(
                    status = status,
                    message = message,
                    syncedIds = syncedIds,
                    id = id,
                    insertedId = insertedId
                )
            }
        } catch (e: Exception) {
            logD("Map fallback SyncResponseDto parsing failed: ${e.message}")
        }

        return null
    }

    /**
     * Pull Barang & Grosir from server API and save into Room.
     */
    suspend fun pullBarang(keyword: String? = null): SyncResult<Int> = withContext(Dispatchers.IO) {
        val barangUrl = "barang.php?action=baca${if (keyword != null) "&keyword=$keyword" else ""}"
        logD("SyncStep [Pull Barang] - Request URL: $barangUrl")
        try {
            val response = apiService.getBarang("baca", keyword)
            val code = response.code()
            val rawBody = response.body()?.string() ?: response.errorBody()?.string()
            val cleanedBody = cleanJson(rawBody)
            logD("SyncStep [Pull Barang] - Response Code: $code, Raw Body Length: ${rawBody?.length ?: 0}")

            val htmlError = checkHtmlOrError(cleanedBody.ifBlank { rawBody }, code)
            if (htmlError != null) {
                logE("SyncStep [Pull Barang] Error: $htmlError")
                return@withContext SyncResult.Error(Exception(htmlError), htmlError)
            } else if (response.isSuccessful && rawBody != null) {
                val dtoList = parseListJson(rawBody, BarangDto::class.java)
                if (dtoList != null) {
                    val barangCount = dtoList.size
                    logD("SyncStep [Pull Barang] - Parsing Status: Success ($barangCount items parsed)")

                    val barangEntities = dtoList.map { dto ->
                        BarangEntity(
                            id = dto.id ?: 0,
                            kodeBarang = dto.kodeBarang ?: "BRG-${dto.id ?: System.currentTimeMillis()}",
                            namaBarang = dto.namaBarang,
                            kategori = dto.kategori ?: "",
                            hargaBeli = dto.hargaBeli ?: 0.0,
                            hargaJual = dto.hargaJual ?: 0.0,
                            stok = dto.effectiveStok,
                            satuan = dto.satuan ?: "pcs",
                            barcode = dto.barcode,
                            isSynced = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    }

                    val hargaGrosirEntities = mutableListOf<HargaGrosirEntity>()
                    dtoList.forEach { dto ->
                        val barangId = dto.id ?: 0
                        val grosirList = dto.effectiveHargaGrosirList ?: emptyList()
                        grosirList.forEach { grosirDto ->
                            hargaGrosirEntities.add(
                                HargaGrosirEntity(
                                    id = grosirDto.id ?: 0,
                                    barangId = barangId,
                                    minQty = grosirDto.minQty,
                                    hargaGrosir = grosirDto.harga,
                                    isSynced = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    if (barangEntities.isNotEmpty()) {
                        barangDao.insertBarangList(barangEntities)
                    }
                    if (hargaGrosirEntities.isNotEmpty()) {
                        hargaGrosirDao.insertHargaGrosirList(hargaGrosirEntities)
                    }

                    // Authoritative deletion of local barang missing from server response (when keyword is null)
                    if (keyword == null) {
                        try {
                            val serverIds = dtoList.mapNotNull { it.id }.toSet()
                            val localBarang = barangDao.getAllBarangList()
                            for (local in localBarang) {
                                if (local.id > 0 && !serverIds.contains(local.id)) {
                                    barangDao.deleteBarang(local)
                                    hargaGrosirDao.deleteHargaGrosirByBarangId(local.id)
                                }
                            }
                        } catch (e: Exception) {
                            logE("Error during authoritative barang cleanup: ${e.message}", e)
                        }
                    }

                    return@withContext SyncResult.Success(barangCount, "Pulled $barangCount barang")
                } else {
                    return@withContext SyncResult.Error(Exception("Failed to parse barang response data"))
                }
            } else {
                return@withContext SyncResult.Error(Exception("Pull barang failed with HTTP code $code"))
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.toString()
            logE("SyncStep [Pull Barang] - Exception: $errMessage", e)
            return@withContext SyncResult.Error(e, "Pull barang error: $errMessage")
        }
    }

    /**
     * Pull Pelanggan from server API and save into Room.
     */
    suspend fun pullPelanggan(): SyncResult<Int> = withContext(Dispatchers.IO) {
        val pelangganUrl = "pelanggan.php?action=baca"
        logD("SyncStep [Pull Pelanggan] - Request URL: $pelangganUrl")
        try {
            val response = apiService.getPelanggan("baca")
            val code = response.code()
            val rawBody = response.body()?.string() ?: response.errorBody()?.string()
            val cleanedBody = cleanJson(rawBody)
            logD("SyncStep [Pull Pelanggan] - Response Code: $code, Raw Body Length: ${rawBody?.length ?: 0}")

            val htmlError = checkHtmlOrError(cleanedBody.ifBlank { rawBody }, code)
            if (htmlError != null) {
                logE("SyncStep [Pull Pelanggan] Error: $htmlError")
                return@withContext SyncResult.Error(Exception(htmlError), htmlError)
            } else if (response.isSuccessful && rawBody != null) {
                val dtoList = parseListJson(rawBody, PelangganDto::class.java)
                if (dtoList != null) {
                    val pelangganCount = dtoList.size
                    logD("SyncStep [Pull Pelanggan] - Parsing Status: Success ($pelangganCount items parsed)")

                    val pelangganEntities = dtoList.map { dto ->
                        PelangganEntity(
                            id = dto.id ?: 0,
                            nama = dto.nama.ifBlank { "Pelanggan" },
                            telepon = dto.telepon,
                            alamat = dto.alamat,
                            isSynced = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    }

                    if (pelangganEntities.isNotEmpty()) {
                        pelangganDao.insertPelangganList(pelangganEntities)
                    }

                    // Authoritative deletion of local pelanggan missing from server response
                    try {
                        val serverIds = dtoList.mapNotNull { it.id }.toSet()
                        val localPelanggan = pelangganDao.getAllPelangganList()
                        for (local in localPelanggan) {
                            if (local.id > 0 && !serverIds.contains(local.id)) {
                                pelangganDao.deletePelanggan(local)
                            }
                        }
                    } catch (e: Exception) {
                        logE("Error during authoritative pelanggan cleanup: ${e.message}", e)
                    }

                    return@withContext SyncResult.Success(pelangganCount, "Pulled $pelangganCount pelanggan")
                } else {
                    return@withContext SyncResult.Error(Exception("Failed to parse pelanggan response data"))
                }
            } else {
                return@withContext SyncResult.Error(Exception("Pull pelanggan failed with HTTP code $code"))
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.toString()
            logE("SyncStep [Pull Pelanggan] - Exception: $errMessage", e)
            return@withContext SyncResult.Error(e, "Pull pelanggan error: $errMessage")
        }
    }

    /**
     * Pull master data (Barang & Grosir, Pelanggan) from server API and saves/upserts into Room database.
     */
    suspend fun pullMasterData(keyword: String? = null): SyncResult<SyncSummary> = withContext(Dispatchers.IO) {
        val barangRes = pullBarang(keyword)
        val pelangganRes = pullPelanggan()

        val barangCount = if (barangRes is SyncResult.Success) barangRes.data else 0
        val pelangganCount = if (pelangganRes is SyncResult.Success) pelangganRes.data else 0

        val err1 = if (barangRes is SyncResult.Error) barangRes.message else null
        val err2 = if (pelangganRes is SyncResult.Error) pelangganRes.message else null

        val pullError = listOfNotNull(err1, err2).joinToString("; ").ifBlank { null }

        val summary = SyncSummary(
            barangPulled = barangCount,
            pelangganPulled = pelangganCount,
            isSuccess = pullError == null || (barangCount > 0 || pelangganCount > 0),
            errorMessage = pullError
        )

        if (pullError != null && barangCount == 0 && pelangganCount == 0) {
            SyncResult.Error(Exception(pullError), pullError)
        } else {
            SyncResult.Success(summary, pullError ?: "Master data pulled successfully")
        }
    }

    /**
     * Push unsynced transactions (isSynced == false) from Room database to server API using SimpanTransaksiRequest with PaymentDto and CartItemDto.
     */
    suspend fun pushUnsyncedTransactions(): SyncResult<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val unsyncedList = transaksiDao.getUnsyncedTransaksiWithDetails()
            logD("SyncStep [Push Transaksi] - Unsynced transactions count: ${unsyncedList.size}")
            if (unsyncedList.isEmpty()) {
                return@withContext SyncResult.Success(
                    SyncSummary(transaksiPushed = 0, isSuccess = true),
                    "No unsynced transactions found"
                )
            }

            var pushedCount = 0
            val syncedIds = mutableListOf<Int>()

            for (item in unsyncedList) {
                val paymentDto = PaymentDto(
                    total = item.transaksi.totalHarga,
                    discount = 0.0,
                    finalTotal = item.transaksi.totalHarga,
                    method = item.transaksi.metodePembayaran,
                    paid = item.transaksi.uangBayar,
                    change = item.transaksi.kembali,
                    buyerName = item.transaksi.namaPelanggan,
                    dp = item.transaksi.dp,
                    sisa = item.transaksi.sisa
                )

                val cartItemDtos = item.details.map { detail ->
                    val barang = barangDao.getBarangById(detail.barangId)
                    CartItemDto(
                        id = detail.barangId,
                        namaBarang = detail.namaBarang,
                        barcode = barang?.barcode,
                        hargaBeli = barang?.hargaBeli ?: 0.0,
                        hargaJual = detail.hargaSatuan,
                        catatan = null,
                        kategori = barang?.kategori,
                        image = null,
                        isArchived = null,
                        grosir = null,
                        qty = detail.jumlah,
                        hargaAsli = detail.hargaSatuan
                    )
                }

                val request = SimpanTransaksiRequest(
                    payment = paymentDto,
                    cart = cartItemDtos
                )

                val pushUrl = "transaksi.php?action=simpan"
                logD("SyncStep [Push Transaksi] - Request URL: $pushUrl, faktur: ${item.transaksi.noFaktur}")
                try {
                    val response = apiService.simpanTransaksi("simpan", request)
                    val code = response.code()
                    val rawBody = response.body()?.string() ?: response.errorBody()?.string()
                    val cleanedBody = cleanJson(rawBody)
                    logD("SyncStep [Push Transaksi] - Response Code: $code, Raw Body: $rawBody")

                    val htmlError = checkHtmlOrError(cleanedBody.ifBlank { rawBody }, code)
                    if (htmlError != null) {
                        logE("SyncStep [Push Transaksi] Error for ${item.transaksi.noFaktur}: $htmlError")
                    } else if (response.isSuccessful && rawBody != null) {
                        val syncResp = parseSyncResponse(rawBody)
                        val returnedId = syncResp?.effectiveSyncedIds?.firstOrNull() ?: item.transaksi.id
                        if (returnedId != null) {
                            syncedIds.add(returnedId)
                            pushedCount++
                            logD("SyncStep [Push Transaksi] - Success for faktur ${item.transaksi.noFaktur}")
                        } else if (item.transaksi.id > 0) {
                            syncedIds.add(item.transaksi.id)
                            pushedCount++
                            logD("SyncStep [Push Transaksi] - Success (assumed id ${item.transaksi.id}) for faktur ${item.transaksi.noFaktur}")
                        }
                    }
                } catch (e: Exception) {
                    logE("SyncStep [Push Transaksi] - Exception for faktur ${item.transaksi.noFaktur}: ${e.message}", e)
                }
            }

            if (syncedIds.isNotEmpty()) {
                transaksiDao.markSynced(syncedIds)
            }

            val summary = SyncSummary(
                transaksiPushed = pushedCount,
                isSuccess = pushedCount > 0 || unsyncedList.isEmpty(),
                errorMessage = if (pushedCount == 0 && unsyncedList.isNotEmpty()) "Failed to push transactions to server" else null
            )
            if (pushedCount == 0 && unsyncedList.isNotEmpty()) {
                SyncResult.Error(Exception("Failed to push transactions to server"), "Failed to push transactions to server")
            } else {
                SyncResult.Success(summary, "Pushed $pushedCount transaction(s)")
            }
        } catch (e: Exception) {
            logE("Failed to push transactions: ${e.localizedMessage}", e)
            SyncResult.Error(e, "Failed to push transactions: ${e.localizedMessage}")
        }
    }

    /**
     * Process and cache transaction history items into Room, and perform authoritative deletion of local transactions missing from server history (when search is null).
     */
    private suspend fun processAndCacheTransaksiHistory(historyList: List<TransaksiDto>, search: String? = null) {
        val serverIds = mutableSetOf<Int>()
        for (dto in historyList) {
            try {
                val transId = dto.id
                var finalDto = dto
                if (transId != null && transId > 0) {
                    serverIds.add(transId)
                    try {
                        val detailResult = getTransaksiDetail(transId.toLong())
                        if (detailResult is SyncResult.Success) {
                            val detailDto = detailResult.data
                            val detailMetode = detailDto.rawMetodePembayaran?.ifBlank { null }
                                ?: detailDto.method?.ifBlank { null }
                                ?: detailDto.payment?.effectiveMethod?.ifBlank { null }

                            val combinedDiskon = listOfNotNull(detailDto.diskon, dto.diskon).firstOrNull { it != 0.0 } ?: detailDto.diskon
                            finalDto = dto.copy(
                                noFaktur = detailDto.noFaktur.ifBlank { dto.noFaktur },
                                pelangganId = detailDto.pelangganId ?: dto.pelangganId,
                                rawNamaPelanggan = detailDto.namaPelanggan ?: dto.namaPelanggan,
                                rawTanggal = detailDto.tanggal ?: dto.tanggal,
                                rawTotalHarga = if (detailDto.totalHarga > 0.0) detailDto.totalHarga else dto.totalHarga,
                                rawDiskon = combinedDiskon,
                                discountAlt = combinedDiskon,
                                rawBayar = if (detailDto.bayar > 0.0) detailDto.bayar else dto.bayar,
                                rawKembali = if (detailDto.kembali > 0.0) detailDto.kembali else dto.kembali,
                                rawMetodePembayaran = detailMetode ?: dto.rawMetodePembayaran ?: dto.method ?: dto.payment?.effectiveMethod,
                                rawDetails = if (detailDto.details.isNotEmpty()) detailDto.details else dto.details
                            )
                        }
                    } catch (e: Exception) {
                        logE("Error fetching detail for transaction $transId: ${e.message}", e)
                    }
                } else if (dto.id != null) {
                    serverIds.add(dto.id)
                }

                val localExisting = transaksiDao.getTransaksiById(finalDto.id ?: 0)?.transaksi
                val newWaktu = if (!finalDto.waktuTransaksi.isNullOrBlank()) finalDto.waktuTransaksi 
                    else if (!finalDto.rawTanggal.isNullOrBlank()) finalDto.rawTanggal 
                    else localExisting?.waktuTransaksi
                val parsedTanggal = parseDateToLong(finalDto.tanggal, fallback = 0L)
                val finalTanggal = if (parsedTanggal > 0L) parsedTanggal 
                    else if (localExisting != null && localExisting.tanggal > 0L) localExisting.tanggal 
                    else parseDateToLong(finalDto.tanggal, fallback = 0L).let { if (it > 0L) it else (localExisting?.tanggal ?: 0L) }
                val transaksiEntity = TransaksiEntity(
                    id = finalDto.id ?: 0,
                    noFaktur = finalDto.noFaktur.ifBlank { if (finalDto.id != null) "INV-${finalDto.id}" else "INV-0" },
                    pelangganId = finalDto.pelangganId,
                    namaPelanggan = finalDto.namaPelanggan,
                    tanggal = finalTanggal,
                    waktuTransaksi = newWaktu,
                    totalHarga = finalDto.totalHarga,
                    diskon = finalDto.diskon,
                    bayar = finalDto.bayar,
                    uangBayar = finalDto.uangBayar,
                    dp = finalDto.dp,
                    sisa = finalDto.sisa,
                    kembali = finalDto.kembali,
                    metodePembayaran = finalDto.metodePembayaran,
                    isSynced = true,
                    updatedAt = localExisting?.updatedAt ?: System.currentTimeMillis()
                )
                val detailEntities = finalDto.details.map { d ->
                    DetailTransaksiEntity(
                        id = d.id ?: 0,
                        transaksiId = finalDto.id ?: 0,
                        barangId = d.barangId,
                        namaBarang = d.namaBarang,
                        jumlah = d.jumlah,
                        hargaSatuan = d.hargaSatuan,
                        subtotal = d.subtotal
                    )
                }
                transaksiDao.insertOrReplaceSyncedTransaksi(transaksiEntity, detailEntities)
            } catch (e: Exception) {
                logE("Error caching transaction history item: ${e.message}", e)
            }
        }

        // Authoritative deletion of local transactions missing from server history (when search is null)
        if (search == null) {
            try {
                val localTransactions = transaksiDao.getAllTransaksiList()
                for (localTx in localTransactions) {
                    if (localTx.id > 0 && !serverIds.contains(localTx.id)) {
                        transaksiDao.deleteTransaksiWithDetails(localTx.id)
                    }
                }
            } catch (e: Exception) {
                logE("Error during authoritative transaction cleanup: ${e.message}", e)
            }
        }
    }

    /**
     * Get transaction history from server API: transaksi.php?action=histori&search={search} (GET)
     */
    suspend fun getTransaksiHistori(search: String? = null): SyncResult<List<TransaksiDto>> = withContext(Dispatchers.IO) {
        val historiUrl = "transaksi.php?action=histori${if (search != null) "&search=$search" else ""}"
        logD("SyncStep [Get Histori] - Request URL: $historiUrl")
        try {
            val response = apiService.getHistoriTransaksi("histori", search)
            val code = response.code()
            val rawBody = response.body()?.string() ?: response.errorBody()?.string()
            val cleanedBody = cleanJson(rawBody)
            logD("SyncStep [Get Histori] - Response Code: $code, Raw Body Length: ${rawBody?.length ?: 0}")
            logD("SyncStep [Get Histori] - Raw Body: $rawBody")

            val htmlError = checkHtmlOrError(cleanedBody.ifBlank { rawBody }, code)
            if (htmlError != null) {
                logE("SyncStep [Get Histori] Error: $htmlError")
                return@withContext SyncResult.Error(Exception(htmlError), htmlError)
            }

            if (response.isSuccessful && rawBody != null) {
                val list = parseListJson(rawBody, TransaksiDto::class.java)
                if (list != null) {
                    logD("SyncStep [Get Histori] - Parsing Status: Success (${list.size} items)")
                    processAndCacheTransaksiHistory(list, search)
                    SyncResult.Success(list, "Transaction history fetched successfully")
                } else {
                    logE("SyncStep [Get Histori] - Parsing Status: Failed to parse transaction history")
                    SyncResult.Error(Exception("Failed to parse transaction history response"), "Failed to parse transaction history response")
                }
            } else {
                val err = "Get transaction history failed with HTTP code $code"
                logE("SyncStep [Get Histori] Error: $err")
                SyncResult.Error(Exception(err), err)
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.toString()
            logE("SyncStep [Get Histori] - Exception: $errMessage", e)
            SyncResult.Error(e, errMessage)
        }
    }

    fun parseDateToLong(dateStr: String?, fallback: Long = 0L): Long {
        return com.syarif.kasir.util.parseDateToLong(dateStr, fallback)
    }

    /**
     * Executes both push and pull sync, including transaction history.
     */
    suspend fun syncAll(context: android.content.Context? = null): SyncResult<SyncSummary> = withContext(Dispatchers.IO) {
        logD("SyncStep [SyncAll] - Starting full synchronization (Push + Pull + History)")
        if (context != null) {
            try {
                com.syarif.kasir.data.remote.InfinityFreeCookieFetcher.fetchCookie(context)
            } catch (e: Exception) {
                logE("Error fetching cookie before syncAll", e)
            }
        }
        val pushResult = pushUnsyncedTransactions()
        val pullResult = pullMasterData()
        val historiResult = getTransaksiHistori()

        val pushed = if (pushResult is SyncResult.Success) pushResult.data.transaksiPushed else 0
        val (pulledBarang, pulledPelanggan) = if (pullResult is SyncResult.Success) {
            Pair(pullResult.data.barangPulled, pullResult.data.pelangganPulled)
        } else {
            Pair(0, 0)
        }

        val pulledTransaksi = if (historiResult is SyncResult.Success) historiResult.data.size else 0

        val hasError = pushResult is SyncResult.Error || pullResult is SyncResult.Error || historiResult is SyncResult.Error
        val errorMsg = when {
            pushResult is SyncResult.Error && pullResult is SyncResult.Error ->
                "${pushResult.message}; ${pullResult.message}"
            pushResult is SyncResult.Error -> pushResult.message
            pullResult is SyncResult.Error -> pullResult.message
            historiResult is SyncResult.Error -> historiResult.message
            else -> null
        }

        logD("SyncStep [SyncAll] - Completed. Pushed: $pushed, Pulled Barang: $pulledBarang, Pulled Pelanggan: $pulledPelanggan, Pulled Transaksi: $pulledTransaksi, HasError: $hasError")

        val summary = SyncSummary(
            barangPulled = pulledBarang,
            pelangganPulled = pulledPelanggan,
            transaksiPushed = pushed,
            transaksiPulled = pulledTransaksi,
            isSuccess = !hasError || (pushed > 0 || pulledBarang > 0 || pulledPelanggan > 0 || pulledTransaksi > 0),
            errorMessage = errorMsg
        )

        if (hasError && pushed == 0 && pulledBarang == 0 && pulledPelanggan == 0 && pulledTransaksi == 0) {
            SyncResult.Error(
                Exception(errorMsg ?: "Sync failed"),
                errorMsg ?: "Sync failed"
            )
        } else {
            SyncResult.Success(summary, errorMsg ?: "Sync completed successfully")
        }
    }

    /**
     * Get transaction detail by ID from server API: transaksi.php?action=detail&id={id} (GET)
     */
    suspend fun getTransaksiDetail(id: Long): SyncResult<TransaksiDto> = withContext(Dispatchers.IO) {
        val detailUrl = "transaksi.php?action=detail&id=$id"
        logD("SyncStep [Get Detail Transaksi] - Request URL: $detailUrl")
        try {
            val response = apiService.getTransaksiDetail("detail", id)
            val code = response.code()
            val rawBody = response.body()?.string() ?: response.errorBody()?.string()
            logD("SyncStep [Get Detail Transaksi] - Response Code: $code, Raw Body: $rawBody")

            val htmlError = checkHtmlOrError(rawBody, code)
            if (htmlError != null) {
                return@withContext SyncResult.Error(Exception(htmlError))
            }

            val cleaned = cleanJson(rawBody)
            if (cleaned.isBlank()) {
                return@withContext SyncResult.Error(Exception("Empty response body"))
            }

            var dto = try {
                moshi.adapter(TransaksiDto::class.java).lenient().fromJson(cleaned)
            } catch (_: Exception) {
                null
            }

            if (dto == null || (dto.id == null && dto.details.isEmpty() && dto.totalHarga == 0.0)) {
                logD("Direct TransaksiDto parsing yielded empty DTO, trying map fallback")
                val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val map = moshi.adapter<Map<String, Any?>>(mapType).lenient().fromJson(cleaned)
                if (map != null) {
                    val innerMap = (map["data"] as? Map<*, *>) ?: (map["transaksi"] as? Map<*, *>) ?: map
                    dto = mapToTransaksiDto(innerMap)
                }
            }

            if (dto != null) {
                logD("Successfully parsed TransaksiDto for id $id")
                return@withContext SyncResult.Success(dto)
            } else {
                return@withContext SyncResult.Error(Exception("Failed to parse transaction detail"))
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.toString()
            logE("SyncStep [Get Detail Transaksi] - Exception: $errMessage", e)
            SyncResult.Error(e, errMessage)
        }
    }
}
