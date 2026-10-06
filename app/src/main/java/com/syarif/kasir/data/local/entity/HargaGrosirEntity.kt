package com.syarif.kasir.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "harga_grosir",
    foreignKeys = [
        ForeignKey(
            entity = BarangEntity::class,
            parentColumns = ["id"],
            childColumns = ["barangId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("barangId")]
)
data class HargaGrosirEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val barangId: Int,
    val minQty: Int,
    val hargaGrosir: Double,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
