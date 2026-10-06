package com.syarif.kasir.data.local.dao

import androidx.room.*
import com.syarif.kasir.data.local.entity.PelangganEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PelangganDao {

    @Query("SELECT * FROM pelanggan ORDER BY nama ASC")
    fun getAllPelanggan(): Flow<List<PelangganEntity>>

    @Query("SELECT * FROM pelanggan WHERE id = :id")
    suspend fun getPelangganById(id: Int): PelangganEntity?

    @Query("SELECT * FROM pelanggan WHERE isSynced = 0")
    suspend fun getUnsyncedPelanggan(): List<PelangganEntity>

    @Query("SELECT * FROM pelanggan")
    suspend fun getAllPelangganList(): List<PelangganEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPelanggan(pelanggan: PelangganEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPelangganList(pelangganList: List<PelangganEntity>)

    @Update
    suspend fun updatePelanggan(pelanggan: PelangganEntity)

    @Delete
    suspend fun deletePelanggan(pelanggan: PelangganEntity)

    @Query("DELETE FROM pelanggan")
    suspend fun deleteAllPelanggan()
}
