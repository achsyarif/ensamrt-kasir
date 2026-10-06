package com.syarif.kasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.syarif.kasir.data.local.dao.BarangDao
import com.syarif.kasir.data.local.dao.HargaGrosirDao
import com.syarif.kasir.data.local.dao.PelangganDao
import com.syarif.kasir.data.local.dao.TransaksiDao
import com.syarif.kasir.data.local.entity.*

@Database(
    entities = [
        BarangEntity::class,
        HargaGrosirEntity::class,
        PelangganEntity::class,
        TransaksiEntity::class,
        DetailTransaksiEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun barangDao(): BarangDao
    abstract fun hargaGrosirDao(): HargaGrosirDao
    abstract fun pelangganDao(): PelangganDao
    abstract fun transaksiDao(): TransaksiDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasir_database",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
