package com.syarif.kasir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pelanggan")
data class PelangganEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nama: String,
    val telepon: String? = null,
    val alamat: String? = null,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
