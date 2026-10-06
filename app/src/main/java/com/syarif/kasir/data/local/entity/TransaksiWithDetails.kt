package com.syarif.kasir.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TransaksiWithDetails(
    @Embedded val transaksi: TransaksiEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transaksiId"
    )
    val details: List<DetailTransaksiEntity>
)
