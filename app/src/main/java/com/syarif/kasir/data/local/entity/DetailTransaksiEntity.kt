package com.syarif.kasir.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detail_transaksi",
    foreignKeys = [
        ForeignKey(
            entity = TransaksiEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaksiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transaksiId"), Index("barangId")]
)
data class DetailTransaksiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val transaksiId: Int,
    val barangId: Int,
    val namaBarang: String,
    val jumlah: Int,
    val hargaSatuan: Double,
    val subtotal: Double
)
