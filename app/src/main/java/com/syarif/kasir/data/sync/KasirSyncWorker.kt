package com.syarif.kasir.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.syarif.kasir.KasirApplication
import com.syarif.kasir.data.local.AppDatabase
import com.syarif.kasir.data.remote.NetworkClient
import com.syarif.kasir.data.repository.KasirRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KasirSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as? KasirApplication
            val repository = app?.repository ?: KasirRepository(
                AppDatabase.getDatabase(applicationContext),
                NetworkClient.createApiService()
            )

            val result = repository.syncAll(applicationContext)
            when (result) {
                is SyncResult.Success -> Result.success()
                is SyncResult.Error -> {
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                is SyncResult.Loading -> Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
