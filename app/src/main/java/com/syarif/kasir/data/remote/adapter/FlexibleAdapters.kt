package com.syarif.kasir.data.remote.adapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

object FlexiblePrimitiveAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val rawType = Types.getRawType(type)
        return when (rawType) {
            Int::class.java, java.lang.Integer::class.java, Int::class.javaObjectType, Int::class.javaPrimitiveType -> FlexibleIntAdapter
            Double::class.java, java.lang.Double::class.java, Double::class.javaObjectType, Double::class.javaPrimitiveType -> FlexibleDoubleAdapter
            Long::class.java, java.lang.Long::class.java, Long::class.javaObjectType, Long::class.javaPrimitiveType -> FlexibleLongAdapter
            Boolean::class.java, java.lang.Boolean::class.java, Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType -> FlexibleBooleanAdapter
            String::class.java, java.lang.String::class.java -> FlexibleStringAdapter
            else -> null
        }
    }
}

object FlexibleIntAdapter : JsonAdapter<Int>() {
    override fun fromJson(reader: JsonReader): Int? {
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (peek == JsonReader.Token.NUMBER) {
            return try {
                reader.nextInt()
            } catch (_: Exception) {
                reader.nextDouble().toInt()
            }
        }
        if (peek == JsonReader.Token.STRING) {
            val str = reader.nextString().trim()
            if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
            return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
        }
        if (peek == JsonReader.Token.BOOLEAN) {
            return if (reader.nextBoolean()) 1 else 0
        }
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: Int?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object FlexibleDoubleAdapter : JsonAdapter<Double>() {
    override fun fromJson(reader: JsonReader): Double? {
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (peek == JsonReader.Token.NUMBER) {
            return reader.nextDouble()
        }
        if (peek == JsonReader.Token.STRING) {
            val str = reader.nextString().trim().replace(',', '.')
            if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
            return str.toDoubleOrNull()
        }
        if (peek == JsonReader.Token.BOOLEAN) {
            return if (reader.nextBoolean()) 1.0 else 0.0
        }
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: Double?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object FlexibleLongAdapter : JsonAdapter<Long>() {
    override fun fromJson(reader: JsonReader): Long? {
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (peek == JsonReader.Token.NUMBER) {
            return try {
                reader.nextLong()
            } catch (_: Exception) {
                reader.nextDouble().toLong()
            }
        }
        if (peek == JsonReader.Token.STRING) {
            val str = reader.nextString().trim()
            if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
            return str.toLongOrNull() ?: str.toDoubleOrNull()?.toLong()
        }
        if (peek == JsonReader.Token.BOOLEAN) {
            return if (reader.nextBoolean()) 1L else 0L
        }
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object FlexibleBooleanAdapter : JsonAdapter<Boolean>() {
    override fun fromJson(reader: JsonReader): Boolean? {
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (peek == JsonReader.Token.BOOLEAN) {
            return reader.nextBoolean()
        }
        if (peek == JsonReader.Token.NUMBER) {
            return reader.nextInt() != 0
        }
        if (peek == JsonReader.Token.STRING) {
            val str = reader.nextString().trim()
            if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
            return str.equals("1", ignoreCase = true) ||
                   str.equals("true", ignoreCase = true) ||
                   str.equals("yes", ignoreCase = true) ||
                   str.equals("y", ignoreCase = true)
        }
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: Boolean?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object FlexibleStringAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (peek == JsonReader.Token.STRING) {
            return reader.nextString()
        }
        if (peek == JsonReader.Token.NUMBER) {
            return try {
                reader.nextString()
            } catch (_: Exception) {
                val d = reader.nextDouble()
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            }
        }
        if (peek == JsonReader.Token.BOOLEAN) {
            return reader.nextBoolean().toString()
        }
        reader.skipValue()
        return null
    }

    override fun toJson(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
