package com.syarif.kasir.data.local.dao

import androidx.room.*
import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TransaksiDao {

    @Query("SELECT * FROM transaksi ORDER BY tanggal DESC")
    fun getAllTransaksi(): Flow<List<TransaksiEntity>>

    @Transaction
    @Query("SELECT * FROM transaksi ORDER BY tanggal DESC")
    fun getAllTransaksiWithDetails(): Flow<List<TransaksiWithDetails>>

    @Transaction
    @Query("SELECT * FROM transaksi WHERE id = :id")
    suspend fun getTransaksiById(id: Int): TransaksiWithDetails?

    @Transaction
    @Query("SELECT * FROM transaksi WHERE isSynced = 0")
    suspend fun getUnsyncedTransaksiWithDetails(): List<TransaksiWithDetails>

    @Query("SELECT * FROM transaksi")
    suspend fun getAllTransaksiList(): List<TransaksiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaksi(transaksi: TransaksiEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetailTransaksiList(details: List<DetailTransaksiEntity>)

    @Transaction
    suspend fun insertFullTransaksi(transaksi: TransaksiEntity, details: List<DetailTransaksiEntity>): Long {
        val transaksiId = insertTransaksi(transaksi).toInt()
        val detailsWithId = details.map { it.copy(transaksiId = transaksiId) }
        insertDetailTransaksiList(detailsWithId)
        return transaksiId.toLong()
    }

    @Query("DELETE FROM detail_transaksi WHERE transaksiId = :transaksiId")
    suspend fun deleteDetailsForTransaksi(transaksiId: Int)

    @Query("DELETE FROM transaksi WHERE id = :id")
    suspend fun deleteTransaksi(id: Int)

    @Transaction
    suspend fun purgeTransaksi(id: Int) {
        deleteDetailsForTransaksi(id)
        deleteTransaksi(id)
    }

    @Transaction
    suspend fun deleteTransaksiWithDetails(id: Int) {
        purgeTransaksi(id)
    }

    @Transaction
    suspend fun purgeTransaction(id: Int) {
        purgeTransaksi(id)
    }

    @Transaction
    suspend fun insertOrReplaceSyncedTransaksi(transaksi: TransaksiEntity, details: List<DetailTransaksiEntity>) {
        val transaksiId = insertTransaksi(transaksi).toInt()
        deleteDetailsForTransaksi(transaksiId)
        val detailsWithId = details.map { it.copy(transaksiId = transaksiId) }
        insertDetailTransaksiList(detailsWithId)
    }

    @Query("UPDATE transaksi SET isSynced = :isSynced WHERE id = :transaksiId")
    suspend fun updateSyncStatus(transaksiId: Int, isSynced: Boolean)

    @Query("UPDATE transaksi SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Int>)
}
