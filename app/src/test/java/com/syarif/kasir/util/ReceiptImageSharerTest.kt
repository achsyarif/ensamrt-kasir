package com.syarif.kasir.util

import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReceiptImageSharerTest {

    @Test
    fun testTransaksiWithDetailsStructure() {
        val transaksi = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0001",
            tanggal = System.currentTimeMillis(),
            totalHarga = 150000.0,
            bayar = 150000.0,
            kembali = 0.0,
            metodePembayaran = "Tunai",
            namaPelanggan = "Pelanggan Umum",
            dp = 0.0,
            sisa = 0.0,
            isSynced = false
        )

        val detail1 = DetailTransaksiEntity(
            id = 1,
            transaksiId = 1,
            barangId = 10,
            namaBarang = "Barang Super Sangat Panjang Sekali Untuk Menguji Text Wrapping Struk Thermal",
            hargaSatuan = 50000.0,
            jumlah = 2,
            subtotal = 100000.0
        )

        val detail2 = DetailTransaksiEntity(
            id = 2,
            transaksiId = 1,
            barangId = 11,
            namaBarang = "Kopi Milk",
            hargaSatuan = 25000.0,
            jumlah = 2,
            subtotal = 50000.0
        )

        val item = TransaksiWithDetails(
            transaksi = transaksi,
            details = listOf(detail1, detail2)
        )

        assertNotNull(item)
        assertEquals(2, item.details.size)
        assertEquals(150000.0, item.transaksi.totalHarga, 0.001)
        assertEquals("INV/20250101/0001", item.transaksi.noFaktur)
    }

    @Test
    fun testTransaksiWithDiscountStructure() {
        val transaksi = TransaksiEntity(
            id = 2,
            noFaktur = "INV/20250101/0002",
            tanggal = System.currentTimeMillis(),
            totalHarga = 130000.0,
            diskon = 20000.0,
            bayar = 130000.0,
            kembali = 0.0,
            metodePembayaran = "Tunai"
        )

        assertEquals(20000.0, transaksi.diskon, 0.001)
        assertEquals(130000.0, transaksi.totalHarga, 0.001)
    }
}
