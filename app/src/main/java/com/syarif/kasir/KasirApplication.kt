package com.syarif.kasir

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.syarif.kasir.data.local.AppDatabase
import com.syarif.kasir.data.remote.InfinityFreeCookieFetcher
import com.syarif.kasir.data.remote.NetworkClient
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.data.sync.KasirSyncWorker
import java.util.concurrent.TimeUnit

class KasirApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val apiService by lazy { NetworkClient.createApiService() }
    val repository by lazy { KasirRepository(database, apiService) }

    override fun onCreate() {
        super.onCreate()
        InfinityFreeCookieFetcher.fetchCookie(this)
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<KasirSyncWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "KasirSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }
}
