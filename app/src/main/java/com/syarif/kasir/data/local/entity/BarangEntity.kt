package com.syarif.kasir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barang")
data class BarangEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val kodeBarang: String,
    val namaBarang: String,
    val kategori: String = "",
    val hargaBeli: Double = 0.0,
    val hargaJual: Double = 0.0,
    val stok: Int = 0,
    val satuan: String = "pcs",
    val barcode: String? = null,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
