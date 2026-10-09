package com.syarif.kasir.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

object ReceiptImageSharer {

    fun generateReceiptBitmap(context: Context, transaction: TransaksiWithDetails): Bitmap {
        val tx = transaction.transaksi
        val details = transaction.details
        val calculatedSisa = transaction.effectiveSisa
        val effectiveUangBayar = if (tx.uangBayar > 0.0) tx.uangBayar else tx.bayar
        val effectiveKembalian = if (tx.kembali > 0.0) tx.kembali else ((effectiveUangBayar - tx.totalHarga).coerceAtLeast(0.0))
        val isCashMethod = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
        val isBonOrDp = tx.metodePembayaran.equals("bon", ignoreCase = true) || tx.metodePembayaran.equals("dp", ignoreCase = true)
        val isTunaiOrTransfer = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("TRANSFER", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
        val isFullTunaiOrTransfer = isTunaiOrTransfer && (tx.sisa <= 0.0 || tx.bayar >= tx.totalHarga || calculatedSisa <= 0.0)

        val scale = 3f
        val bitmapWidth = (512 * scale).toInt()
        val margin = 32f * scale
        val usableWidth = bitmapWidth - (margin * 2)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val regularCenterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val thankYouPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val boldLeftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }

        val boldRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val regularLeftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }

        val regularRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val labelSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }

        val totalLeftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }

        val totalRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val errorRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
        }

        // Measure height pass
        var y = 24f * scale

        // Header
        y += 34f * scale // ENSMART
        y += 24f * scale // Address
        y += 24f * scale // Date

        y += 8f * scale // divider spacing
        y += 14f * scale

        // Items
        for (detail in details) {
            val subtotalText = detail.subtotal.toRupiah()
            val subtotalWidth = boldRightPaint.measureText(subtotalText)
            val maxNameWidth = usableWidth - subtotalWidth - (16f * scale)

            val nameLines = wrapText(detail.namaBarang, boldLeftPaint, maxNameWidth)
            y += (nameLines.size * 22f * scale)
            y += 18f * scale // qty x price line
            y += 14f * scale // distinct clean spacing between individual product items
        }

        y += 6f * scale
        y += 14f * scale // divider

        // Total
        if (tx.diskon > 0.0) {
            y += 24f * scale // Subtotal
            y += 24f * scale // Diskon
        }
        y += 28f * scale

        y += 6f * scale
        y += 14f * scale // divider

        // Payment Details
        y += 24f * scale // Metode
        y += 24f * scale // Nama
        if (!isBonOrDp && (effectiveUangBayar > 0.0 || isCashMethod)) {
            y += 24f * scale // Bayar
        }
        if (effectiveKembalian > 0.0) {
            y += 24f * scale // Kembalian
        }
        if (tx.dp > 0.0 && !isFullTunaiOrTransfer) {
            y += 24f * scale // DP
        }
        if (calculatedSisa > 0.0 && !isFullTunaiOrTransfer) {
            y += 24f * scale // Sisa
        }

        y += 6f * scale
        y += 14f * scale // divider

        // Footer
        y += 24f * scale // Terima kasih
        y += 24f * scale // WA

        y += 28f * scale // bottom padding

        val bitmapHeight = ceil(y).toInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val leftX = margin
        val rightX = bitmapWidth - margin
        val centerX = bitmapWidth / 2f

        // Draw pass
        var drawY = 24f * scale

        // Header
        canvas.drawText("ENSMART", centerX, drawY + (18f * scale), titlePaint)
        drawY += 34f * scale

        canvas.drawText("Gelanggang RT 001/008 Pamotan", centerX, drawY + (15f * scale), subtitlePaint)
        drawY += 24f * scale

        canvas.drawText("${formatTransactionDate(tx.waktuTransaksi, tx.tanggal)} #${tx.id}", centerX, drawY + (15f * scale), regularCenterPaint)
        drawY += 24f * scale

        drawY += 8f * scale
        canvas.drawLine(leftX, drawY, rightX, drawY, dividerPaint)
        drawY += 14f * scale

        // Items
        for (detail in details) {
            val subtotalText = detail.subtotal.toRupiah()
            val subtotalWidth = boldRightPaint.measureText(subtotalText)
            val maxNameWidth = usableWidth - subtotalWidth - (16f * scale)

            val nameLines = wrapText(detail.namaBarang, boldLeftPaint, maxNameWidth)
            for (i in nameLines.indices) {
                canvas.drawText(nameLines[i], leftX, drawY + (15f * scale), boldLeftPaint)
                if (i == 0) {
                    canvas.drawText(subtotalText, rightX, drawY + (15f * scale), boldRightPaint)
                }
                drawY += 22f * scale
            }

            val qtyPriceText = "${detail.hargaSatuan.toRupiah()} x ${detail.jumlah}"
            canvas.drawText(qtyPriceText, leftX, drawY + (13f * scale), labelSmallPaint)
            drawY += 18f * scale
            drawY += 14f * scale // distinct clean spacing between individual product items
        }

        drawY += 6f * scale
        canvas.drawLine(leftX, drawY, rightX, drawY, dividerPaint)
        drawY += 14f * scale

        // Total
        if (tx.diskon > 0.0) {
            val subtotalVal = tx.totalHarga + tx.diskon
            canvas.drawText("Subtotal:", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText(subtotalVal.toRupiah(), rightX, drawY + (15f * scale), regularRightPaint)
            drawY += 24f * scale

            canvas.drawText("Diskon:", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText("-${tx.diskon.toRupiah()}", rightX, drawY + (15f * scale), regularRightPaint)
            drawY += 24f * scale
        }

        canvas.drawText("TOTAL", leftX, drawY + (18f * scale), totalLeftPaint)
        canvas.drawText(tx.totalHarga.toRupiah(), rightX, drawY + (18f * scale), totalRightPaint)
        drawY += 28f * scale

        drawY += 6f * scale
        canvas.drawLine(leftX, drawY, rightX, drawY, dividerPaint)
        drawY += 14f * scale

        // Payment Details
        canvas.drawText("Metode", leftX, drawY + (15f * scale), regularLeftPaint)
        canvas.drawText(tx.metodePembayaran.uppercase(), rightX, drawY + (15f * scale), regularRightPaint)
        drawY += 24f * scale

        canvas.drawText("Nama", leftX, drawY + (15f * scale), regularLeftPaint)
        canvas.drawText(tx.namaPelanggan ?: "Umum", rightX, drawY + (15f * scale), regularRightPaint)
        drawY += 24f * scale

        if (!isBonOrDp && (effectiveUangBayar > 0.0 || isCashMethod)) {
            canvas.drawText("Bayar:", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText(effectiveUangBayar.toRupiah(), rightX, drawY + (15f * scale), regularRightPaint)
            drawY += 24f * scale
        }

        if (effectiveKembalian > 0.0) {
            canvas.drawText("Kembalian:", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText(effectiveKembalian.toRupiah(), rightX, drawY + (15f * scale), regularRightPaint)
            drawY += 24f * scale
        }

        if (tx.dp > 0.0 && !isFullTunaiOrTransfer) {
            canvas.drawText("DP", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText(tx.dp.toRupiah(), rightX, drawY + (15f * scale), regularRightPaint)
            drawY += 24f * scale
        }

        if (calculatedSisa > 0.0 && !isFullTunaiOrTransfer) {
            canvas.drawText("Sisa", leftX, drawY + (15f * scale), regularLeftPaint)
            canvas.drawText(calculatedSisa.toRupiah(), rightX, drawY + (15f * scale), errorRightPaint)
            drawY += 24f * scale
        }

        drawY += 6f * scale
        canvas.drawLine(leftX, drawY, rightX, drawY, dividerPaint)
        drawY += 14f * scale

        // Footer
        canvas.drawText("Terima kasih telah berbelanja!", centerX, drawY + (15f * scale), thankYouPaint)
        drawY += 24f * scale

        canvas.drawText("WhatsApp: 089512224989 / 089512224993", centerX, drawY + (15f * scale), thankYouPaint)

        return bitmap
    }

    private fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return if (lines.isEmpty()) listOf(text) else lines
    }

    fun shareReceiptImage(context: Context, transaction: TransaksiWithDetails) {
        try {
            val bitmap = generateReceiptBitmap(context, transaction)
            val receiptsDir = File(context.cacheDir, "receipts")
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs()
            }
            val imageFile = File(receiptsDir, "receipt.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, imageFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Struk PNG")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan struk: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
