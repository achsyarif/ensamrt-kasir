package com.syarif.kasir.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncResponseDto(
    @Json(name = "status") val status: Any? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "synced_ids") val syncedIds: List<Int>? = null,
    @Json(name = "syncedIds") val syncedIdsAlt: List<Int>? = null,
    @Json(name = "id") val id: Any? = null,
    @Json(name = "inserted_id") val insertedId: Any? = null
) {
    val effectiveSyncedIds: List<Int>
        get() {
            if (!syncedIds.isNullOrEmpty()) return syncedIds
            if (!syncedIdsAlt.isNullOrEmpty()) return syncedIdsAlt
            val singleId = when (id) {
                is Number -> id.toInt()
                is String -> id.toIntOrNull()
                else -> null
            }
            val insId = when (insertedId) {
                is Number -> insertedId.toInt()
                is String -> insertedId.toIntOrNull()
                else -> null
            }
            return listOfNotNull(singleId, insId)
        }
}
