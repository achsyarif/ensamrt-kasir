package com.syarif.kasir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaksi")
data class TransaksiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val noFaktur: String,
    val pelangganId: Int? = null,
    val namaPelanggan: String? = null,
    val tanggal: Long = System.currentTimeMillis(),
    val waktuTransaksi: String? = null,
    val totalHarga: Double,
    val diskon: Double = 0.0,
    val bayar: Double,
    val uangBayar: Double = bayar,
    val dp: Double = 0.0,
    val sisa: Double = 0.0,
    val kembali: Double,
    val metodePembayaran: String = "TUNAI",
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
