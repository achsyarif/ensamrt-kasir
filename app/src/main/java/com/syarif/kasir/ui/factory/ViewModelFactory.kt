package com.syarif.kasir.ui.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.syarif.kasir.data.repository.KasirRepository
import com.syarif.kasir.ui.barang.BarangViewModel
import com.syarif.kasir.ui.laporan.LaporanViewModel
import com.syarif.kasir.ui.pelanggan.PelangganViewModel
import com.syarif.kasir.ui.pos.PosViewModel
import com.syarif.kasir.ui.riwayat.RiwayatViewModel

class ViewModelFactory(
    private val repository: KasirRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BarangViewModel::class.java) -> {
                BarangViewModel(repository) as T
            }
            modelClass.isAssignableFrom(PelangganViewModel::class.java) -> {
                PelangganViewModel(repository) as T
            }
            modelClass.isAssignableFrom(PosViewModel::class.java) -> {
                PosViewModel(repository) as T
            }
            modelClass.isAssignableFrom(RiwayatViewModel::class.java) -> {
                RiwayatViewModel(repository) as T
            }
            modelClass.isAssignableFrom(LaporanViewModel::class.java) -> {
                LaporanViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
