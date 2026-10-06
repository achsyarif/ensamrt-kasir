package com.syarif.kasir.data.local.dao

import androidx.room.*
import com.syarif.kasir.data.local.entity.HargaGrosirEntity

@Dao
interface HargaGrosirDao {

    @Query("SELECT * FROM harga_grosir WHERE barangId = :barangId ORDER BY minQty ASC")
    suspend fun getHargaGrosirByBarangId(barangId: Int): List<HargaGrosirEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHargaGrosirList(list: List<HargaGrosirEntity>)

    @Query("DELETE FROM harga_grosir WHERE barangId = :barangId")
    suspend fun deleteHargaGrosirByBarangId(barangId: Int)

    @Query("DELETE FROM harga_grosir")
    suspend fun deleteAllHargaGrosir()
}
