package com.syarif.kasir.data.sync

sealed class SyncResult<out T> {
    data class Success<out T>(val data: T, val message: String = "") : SyncResult<T>()
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Sync failed") : SyncResult<Nothing>()
    object Loading : SyncResult<Nothing>()
}

data class SyncSummary(
    val barangPulled: Int = 0,
    val pelangganPulled: Int = 0,
    val transaksiPushed: Int = 0,
    val transaksiPulled: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
