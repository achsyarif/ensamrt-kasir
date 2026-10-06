package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.syarif.kasir.data.remote.adapter.FlexiblePrimitiveAdapterFactory

@JsonClass(generateAdapter = true)
data class BarangDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "kode_barang") val kodeBarang: String? = null,
    @Json(name = "nama_barang") val namaBarang: String = "",
    @Json(name = "kategori") val kategori: String? = null,
    @Json(name = "harga_beli") val hargaBeli: Double? = 0.0,
    @Json(name = "harga_jual") val hargaJual: Double? = 0.0,
    @Json(name = "stok") val stok: Int? = 0,
    @Json(name = "satuan") val satuan: String? = "pcs",
    @Json(name = "barcode") val barcode: String? = null,
    @Json(name = "harga_grosir") val hargaGrosirList: List<HargaGrosirDto>? = null,
    @Json(name = "grosir") val grosirList: List<HargaGrosirDto>? = null,
    @Json(name = "is_archived") val isArchived: Boolean? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
) {
    val effectiveHargaGrosirList: List<HargaGrosirDto>?
        get() = hargaGrosirList ?: grosirList
}

class HargaGrosirListJsonAdapter : JsonAdapter<List<HargaGrosirDto>?>() {
    private val moshi = Moshi.Builder()
        .add(FlexiblePrimitiveAdapterFactory)
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapListType = Types.newParameterizedType(List::class.java, Map::class.java)
    private val mapListAdapter = moshi.adapter<List<Map<*, *>>>(mapListType).lenient()
    private val singleMapAdapter = moshi.adapter<Map<*, *>>(Map::class.java).lenient()

    private fun parseIntValue(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) return value.toInt()
        val str = value.toString().trim()
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
        return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
    }

    private fun parseDoubleValue(value: Any?): Double? {
        if (value == null) return null
        if (value is Number) return value.toDouble()
        val str = value.toString().trim().replace(',', '.')
        if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
        return str.toDoubleOrNull()
    }

    private fun mapToDto(map: Map<*, *>): HargaGrosirDto {
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

    @FromJson
    override fun fromJson(reader: JsonReader): List<HargaGrosirDto>? {
        reader.isLenient = true
        val peek = reader.peek()
        return when (peek) {
            JsonReader.Token.NULL -> {
                reader.nextNull<Void>()
                null
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                try {
                    val mapList = mapListAdapter.fromJson(reader)
                    mapList?.map { mapToDto(it) } ?: emptyList()
                } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    emptyList()
                }
            }
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                if (str.isNullOrBlank() || str.equals("null", ignoreCase = true) || str == "[]") {
                    emptyList()
                } else {
                    val cleanStr = str.trim()
                    try {
                        if (cleanStr.startsWith("[")) {
                            val mapList = mapListAdapter.fromJson(cleanStr)
                            mapList?.map { mapToDto(it) } ?: emptyList()
                        } else if (cleanStr.startsWith("{")) {
                            val map = singleMapAdapter.fromJson(cleanStr)
                            if (map != null) listOf(mapToDto(map)) else emptyList()
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                try {
                    val map = singleMapAdapter.fromJson(reader)
                    if (map != null) listOf(mapToDto(map)) else emptyList()
                } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    null
                }
            }
            else -> {
                try { reader.skipValue() } catch (_: Exception) {}
                null
            }
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: List<HargaGrosirDto>?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.beginArray()
            value.forEach { item ->
                writer.beginObject()
                writer.name("id").value(item.id)
                writer.name("barang_id").value(item.barangId)
                writer.name("min_qty").value(item.minQty)
                writer.name("harga").value(item.harga)
                writer.endObject()
            }
            writer.endArray()
        }
    }
}
