package com.syarif.kasir.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BarangWithHargaGrosir(
    @Embedded val barang: BarangEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "barangId"
    )
    val hargaGrosirList: List<HargaGrosirEntity>
)
