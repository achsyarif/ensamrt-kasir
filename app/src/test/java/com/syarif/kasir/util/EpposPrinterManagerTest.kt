package com.syarif.kasir.util

import com.syarif.kasir.data.local.entity.DetailTransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import org.junit.Assert.assertTrue
import org.junit.Test

class EpposPrinterManagerTest {

    @Test
    fun testGenerateReceiptTextFontAWidth() {
        val transaksi = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0001",
            tanggal = 1700000000000L,
            totalHarga = 150000.0,
            bayar = 150000.0,
            kembali = 0.0,
            metodePembayaran = "Tunai",
            namaPelanggan = "Pelanggan Umum",
            dp = 0.0,
            sisa = 0.0,
            isSynced = false
        )

        val detail = DetailTransaksiEntity(
            id = 1,
            transaksiId = 1,
            barangId = 10,
            namaBarang = "Barang Super Sangat Panjang Sekali Untuk Menguji Text Wrapping Struk Thermal",
            hargaSatuan = 75000.0,
            jumlah = 2,
            subtotal = 150000.0
        )

        val transaction = TransaksiWithDetails(
            transaksi = transaksi,
            details = listOf(detail)
        )

        val receiptText = EpposPrinterManager.generateReceiptText(transaction)

        assertTrue(receiptText.contains("ENSMART"))
        assertTrue(receiptText.contains("TUNAI"))
        assertTrue(receiptText.contains("--------------------------------"))

        val lines = receiptText.split("\n")
        for (line in lines) {
            if (line.startsWith("WhatsApp:")) {
                assertTrue("Footer line '$line' length is ${line.length}, exceeding 42 chars", line.length <= 42)
            } else {
                assertTrue("Line '$line' length is ${line.length}, exceeding 32 chars", line.length <= 32)
            }
        }
    }

    @Test
    fun testGenerateReceiptTextWithDiscount() {
        val transaksi = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0002",
            tanggal = 1700000000000L,
            totalHarga = 130000.0,
            diskon = 20000.0,
            bayar = 130000.0,
            kembali = 0.0,
            metodePembayaran = "Tunai",
            namaPelanggan = "Pelanggan Umum"
        )

        val detail = DetailTransaksiEntity(
            id = 1,
            transaksiId = 1,
            barangId = 10,
            namaBarang = "Barang A",
            hargaSatuan = 150000.0,
            jumlah = 1,
            subtotal = 150000.0
        )

        val transaction = TransaksiWithDetails(
            transaksi = transaksi,
            details = listOf(detail)
        )

        val receiptText = EpposPrinterManager.generateReceiptText(transaction)

        assertTrue(receiptText.contains("Subtotal:"))
        assertTrue(receiptText.contains("Diskon:"))
        assertTrue(receiptText.contains("-${transaksi.diskon.toRupiah()}"))

        val totalIdx = receiptText.indexOf("TOTAL")
        val diskonIdx = receiptText.indexOf("Diskon:")
        val subtotalIdx = receiptText.indexOf("Subtotal:")
        assertTrue("Subtotal should appear before Diskon", subtotalIdx < diskonIdx)
        assertTrue("Diskon should appear before TOTAL", diskonIdx < totalIdx)
    }

    @Test
    fun testGenerateReceiptTextWithBayarAndKembalian() {
        val transaksi = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0003",
            tanggal = 1700000000000L,
            totalHarga = 50000.0,
            bayar = 100000.0,
            uangBayar = 100000.0,
            kembali = 50000.0,
            metodePembayaran = "TUNAI",
            namaPelanggan = "Pelanggan Umum"
        )

        val detail = DetailTransaksiEntity(
            id = 1,
            transaksiId = 1,
            barangId = 10,
            namaBarang = "Barang A",
            hargaSatuan = 50000.0,
            jumlah = 1,
            subtotal = 50000.0
        )

        val transaction = TransaksiWithDetails(
            transaksi = transaksi,
            details = listOf(detail)
        )

        val receiptText = EpposPrinterManager.generateReceiptText(transaction)

        assertTrue(receiptText.contains("Bayar:"))
        assertTrue(receiptText.contains("Kembalian:"))
        assertTrue(receiptText.contains(100000.0.toRupiah()))
        assertTrue(receiptText.contains(50000.0.toRupiah()))
    }

    @Test
    fun testReceiptNoBayarLineForBonOrDp() {
        val transaksiBon = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0004",
            tanggal = 1700000000000L,
            totalHarga = 50000.0,
            bayar = 20000.0,
            uangBayar = 20000.0,
            dp = 20000.0,
            sisa = 30000.0,
            kembali = 0.0,
            metodePembayaran = "bon",
            namaPelanggan = "Pelanggan A"
        )
        val detail = DetailTransaksiEntity(
            id = 1, transaksiId = 1, barangId = 10, namaBarang = "Barang A",
            hargaSatuan = 50000.0, jumlah = 1, subtotal = 50000.0
        )
        val txBonText = EpposPrinterManager.generateReceiptText(
            TransaksiWithDetails(transaksi = transaksiBon, details = listOf(detail))
        )

        assertTrue(!txBonText.contains("Bayar:"))
        assertTrue(txBonText.contains("DP"))
        assertTrue(txBonText.contains("Sisa"))

        val transaksiDp = transaksiBon.copy(metodePembayaran = "dp")
        val txDpText = EpposPrinterManager.generateReceiptText(
            TransaksiWithDetails(transaksi = transaksiDp, details = listOf(detail))
        )
        assertTrue(!txDpText.contains("Bayar:"))
    }

    @Test
    fun testReceiptNoDpOrSisaForFullTunaiOrTransfer() {
        val transaksiTunai = TransaksiEntity(
            id = 1,
            noFaktur = "INV/20250101/0005",
            tanggal = 1700000000000L,
            totalHarga = 50000.0,
            bayar = 50000.0,
            uangBayar = 50000.0,
            dp = 20000.0,
            sisa = 0.0,
            kembali = 0.0,
            metodePembayaran = "TUNAI",
            namaPelanggan = "Pelanggan B"
        )
        val detail = DetailTransaksiEntity(
            id = 1, transaksiId = 1, barangId = 10, namaBarang = "Barang A",
            hargaSatuan = 50000.0, jumlah = 1, subtotal = 50000.0
        )
        val txTunaiText = EpposPrinterManager.generateReceiptText(
            TransaksiWithDetails(transaksi = transaksiTunai, details = listOf(detail))
        )

        assertTrue(!txTunaiText.contains("DP"))
        assertTrue(!txTunaiText.contains("Sisa"))

        val transaksiTransfer = transaksiTunai.copy(metodePembayaran = "TRANSFER")
        val txTransferText = EpposPrinterManager.generateReceiptText(
            TransaksiWithDetails(transaksi = transaksiTransfer, details = listOf(detail))
        )
        assertTrue(!txTransferText.contains("DP"))
        assertTrue(!txTransferText.contains("Sisa"))
    }
}
