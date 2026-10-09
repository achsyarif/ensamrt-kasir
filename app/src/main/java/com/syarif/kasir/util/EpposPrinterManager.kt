package com.syarif.kasir.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import java.util.UUID

object EpposPrinterManager {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val LINE_WIDTH = 32

    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return emptyList()
            }
        }
        return bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
    }

    fun printReceipt(context: Context, device: BluetoothDevice, transaction: TransaksiWithDetails): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return Result.failure(SecurityException("Bluetooth Connect permission not granted"))
                }
            }
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            socket.connect()

            val outputStream = socket.outputStream

            // ESC/POS Commands optimized for 58mm thermal paper (Font A, 32 columns, larger and bolder)
            val init = byteArrayOf(0x1B, 0x40)
            val fontA = byteArrayOf(0x1B, 0x21, 0x00) // ESC ! 0x00 (Font A normal - 32 cols)
            val fontABold = byteArrayOf(0x1B, 0x21, 0x08) // ESC ! 0x08 (Font A + Bold)
            val fontB = byteArrayOf(0x1B, 0x21, 0x01) // ESC ! 0x01 (Font B - 42 cols)
            val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
            val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
            val feedAndCut = byteArrayOf(0x1B, 0x64, 0x03, 0x1D, 0x56, 0x41, 0x03) // feed 3 lines and cut

            val separator = "-".repeat(LINE_WIDTH) + "\n"

            // Initialize printer and set Font A mode (32 chars) as optimal base for 58mm paper
            outputStream.write(init)
            outputStream.write(fontA)

            // Header (Centered, larger bold header for ENSMART)
            outputStream.write(alignCenter)
            val fontHeader = byteArrayOf(0x1B, 0x21, 0x20) // ESC ! 0x20 (Double width / larger)
            outputStream.write(fontHeader)
            outputStream.write("ENSMART\n".toByteArray(Charsets.UTF_8))
            outputStream.write(fontA)
            outputStream.write("Gelanggang RT 001/008 Pamotan\n".toByteArray(Charsets.UTF_8))
            val tx = transaction.transaksi
            outputStream.write("${formatTransactionDate(tx.waktuTransaksi, tx.tanggal)} #${tx.id}\n".toByteArray(Charsets.UTF_8))
            outputStream.write(separator.toByteArray(Charsets.UTF_8))

            // Items (Left aligned, crisp Font A)
            outputStream.write(alignLeft)
            outputStream.write(fontA)
            for (detail in transaction.details) {
                val name = detail.namaBarang
                var rem = name
                while (rem.isNotEmpty()) {
                    val chunk = rem.take(LINE_WIDTH)
                    outputStream.write("$chunk\n".toByteArray(Charsets.UTF_8))
                    rem = rem.drop(LINE_WIDTH)
                }
                val leftSub = "  ${detail.hargaSatuan.toRupiah()} x ${detail.jumlah}"
                val rightSub = detail.subtotal.toRupiah()
                outputStream.write("${formatLeftRight(leftSub, rightSub, LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }

            outputStream.write(separator.toByteArray(Charsets.UTF_8))

            if (transaction.transaksi.diskon > 0.0) {
                val subtotalVal = transaction.transaksi.totalHarga + transaction.transaksi.diskon
                outputStream.write("${formatLeftRight("Subtotal:", subtotalVal.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
                outputStream.write("${formatLeftRight("Diskon:", "-${transaction.transaksi.diskon.toRupiah()}", LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }

            // Total (Bold Font A)
            outputStream.write(fontABold)
            outputStream.write("${formatLeftRight("TOTAL", transaction.transaksi.totalHarga.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            outputStream.write(fontA)
            outputStream.write(separator.toByteArray(Charsets.UTF_8))

            // Payment details
            val effectiveUangBayar = if (tx.uangBayar > 0.0) tx.uangBayar else tx.bayar
            val effectiveKembalian = if (tx.kembali > 0.0) tx.kembali else ((effectiveUangBayar - tx.totalHarga).coerceAtLeast(0.0))
            val isCashMethod = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
            val isBonOrDp = tx.metodePembayaran.equals("bon", ignoreCase = true) || tx.metodePembayaran.equals("dp", ignoreCase = true)
            val isTunaiOrTransfer = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("TRANSFER", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
            val sisa = transaction.effectiveSisa
            val isFullTunaiOrTransfer = isTunaiOrTransfer && (tx.sisa <= 0.0 || tx.bayar >= tx.totalHarga || sisa <= 0.0)

            outputStream.write("${formatLeftRight("Metode", tx.metodePembayaran.uppercase(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            outputStream.write("${formatLeftRight("Nama", tx.namaPelanggan ?: "Umum", LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            if (!isBonOrDp && (effectiveUangBayar > 0.0 || isCashMethod)) {
                outputStream.write("${formatLeftRight("Bayar:", effectiveUangBayar.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }
            if (effectiveKembalian > 0.0) {
                outputStream.write("${formatLeftRight("Kembalian:", effectiveKembalian.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }
            if (tx.dp > 0.0 && !isFullTunaiOrTransfer) {
                outputStream.write("${formatLeftRight("DP", tx.dp.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }
            if (sisa > 0.0 && !isFullTunaiOrTransfer) {
                outputStream.write("${formatLeftRight("Sisa", sisa.toRupiah(), LINE_WIDTH)}\n".toByteArray(Charsets.UTF_8))
            }

            outputStream.write(separator.toByteArray(Charsets.UTF_8))

            // Footer (Centered, compact WhatsApp lines)
            outputStream.write(alignCenter)
            outputStream.write("Terima kasih telah berbelanja!\n".toByteArray(Charsets.UTF_8))
            outputStream.write(fontB)
            outputStream.write("WhatsApp: 089512224989 / 089512224993\n".toByteArray(Charsets.UTF_8))
            outputStream.write(fontA)

            // Feed and cut
            outputStream.write(feedAndCut)

            outputStream.flush()
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateReceiptText(transaction: TransaksiWithDetails): String {
        val sb = StringBuilder()
        val separator = "-".repeat(LINE_WIDTH) + "\n"
        sb.append("ENSMART\n")
        sb.append("Gelanggang RT 001/008 Pamotan\n")
        val tx = transaction.transaksi
        val effectiveUangBayar = if (tx.uangBayar > 0.0) tx.uangBayar else tx.bayar
        val effectiveKembalian = if (tx.kembali > 0.0) tx.kembali else ((effectiveUangBayar - tx.totalHarga).coerceAtLeast(0.0))
        val isCashMethod = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
        val isBonOrDp = tx.metodePembayaran.equals("bon", ignoreCase = true) || tx.metodePembayaran.equals("dp", ignoreCase = true)
        val isTunaiOrTransfer = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("TRANSFER", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
        val sisa = transaction.effectiveSisa
        val isFullTunaiOrTransfer = isTunaiOrTransfer && (tx.sisa <= 0.0 || tx.bayar >= tx.totalHarga || sisa <= 0.0)

        sb.append("${formatTransactionDate(tx.waktuTransaksi, tx.tanggal)} #${tx.id}\n")
        sb.append(separator)
        for (detail in transaction.details) {
            val name = detail.namaBarang
            var rem = name
            while (rem.isNotEmpty()) {
                val chunk = rem.take(LINE_WIDTH)
                sb.append("$chunk\n")
                rem = rem.drop(LINE_WIDTH)
            }
            val leftSub = "  ${detail.hargaSatuan.toRupiah()} x ${detail.jumlah}"
            val rightSub = detail.subtotal.toRupiah()
            sb.append("${formatLeftRight(leftSub, rightSub, LINE_WIDTH)}\n")
        }
        sb.append(separator)
        if (transaction.transaksi.diskon > 0.0) {
            val subtotalVal = transaction.transaksi.totalHarga + transaction.transaksi.diskon
            sb.append("${formatLeftRight("Subtotal:", subtotalVal.toRupiah(), LINE_WIDTH)}\n")
            sb.append("${formatLeftRight("Diskon:", "-${transaction.transaksi.diskon.toRupiah()}", LINE_WIDTH)}\n")
        }
        sb.append("${formatLeftRight("TOTAL", transaction.transaksi.totalHarga.toRupiah(), LINE_WIDTH)}\n")
        sb.append(separator)
        sb.append("${formatLeftRight("Metode", transaction.transaksi.metodePembayaran.uppercase(), LINE_WIDTH)}\n")
        sb.append("${formatLeftRight("Nama", transaction.transaksi.namaPelanggan ?: "Umum", LINE_WIDTH)}\n")
        if (!isBonOrDp && (effectiveUangBayar > 0.0 || isCashMethod)) {
            sb.append("${formatLeftRight("Bayar:", effectiveUangBayar.toRupiah(), LINE_WIDTH)}\n")
        }
        if (effectiveKembalian > 0.0) {
            sb.append("${formatLeftRight("Kembalian:", effectiveKembalian.toRupiah(), LINE_WIDTH)}\n")
        }
        if (transaction.transaksi.dp > 0.0 && !isFullTunaiOrTransfer) {
            sb.append("${formatLeftRight("DP", transaction.transaksi.dp.toRupiah(), LINE_WIDTH)}\n")
        }
        if (sisa > 0.0 && !isFullTunaiOrTransfer) {
            sb.append("${formatLeftRight("Sisa", sisa.toRupiah(), LINE_WIDTH)}\n")
        }
        sb.append(separator)
        sb.append("Terima kasih telah berbelanja!\n")
        sb.append("WhatsApp: 089512224989 / 089512224993\n")
        return sb.toString()
    }

    private fun formatLeftRight(left: String, right: String, width: Int = LINE_WIDTH): String {
        val spaceCount = width - left.length - right.length
        if (spaceCount < 1) {
            return "$left $right"
        }
        return left + " ".repeat(spaceCount) + right
    }
}
