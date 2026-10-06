package com.syarif.kasir.data.local.dao

import androidx.room.*
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import kotlinx.coroutines.flow.Flow

@Dao
interface BarangDao {

    @Query("SELECT * FROM barang ORDER BY namaBarang ASC")
    fun getAllBarang(): Flow<List<BarangEntity>>

    @Transaction
    @Query("SELECT * FROM barang ORDER BY namaBarang ASC")
    fun getBarangWithHargaGrosir(): Flow<List<BarangWithHargaGrosir>>

    @Query("SELECT * FROM barang WHERE id = :id")
    suspend fun getBarangById(id: Int): BarangEntity?

    @Query("SELECT * FROM barang WHERE kodeBarang = :kode")
    suspend fun getBarangByKode(kode: String): BarangEntity?

    @Query("SELECT * FROM barang WHERE barcode = :barcode")
    suspend fun getBarangByBarcode(barcode: String): BarangEntity?

    @Query("SELECT * FROM barang WHERE isSynced = 0")
    suspend fun getUnsyncedBarang(): List<BarangEntity>

    @Query("SELECT * FROM barang")
    suspend fun getAllBarangList(): List<BarangEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarang(barang: BarangEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarangList(barangList: List<BarangEntity>)

    @Update
    suspend fun updateBarang(barang: BarangEntity)

    @Delete
    suspend fun deleteBarang(barang: BarangEntity)

    @Query("DELETE FROM barang")
    suspend fun deleteAllBarang()
}
