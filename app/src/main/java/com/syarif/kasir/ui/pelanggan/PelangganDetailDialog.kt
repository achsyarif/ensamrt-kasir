package com.syarif.kasir.ui.pelanggan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.ui.pos.ReceiptDialog
import com.syarif.kasir.util.effectiveSisa
import com.syarif.kasir.util.formatTransactionDate
import com.syarif.kasir.util.parseDateToLong
import com.syarif.kasir.util.toRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementDialog(
    transaction: TransaksiWithDetails,
    onDismiss: () -> Unit,
    onConfirm: (metode: String, uangDp: Double, sisa: Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("TUNAI") }
    val totalHarga = transaction.transaksi.totalHarga
    val currentSisa = transaction.effectiveSisa
    var uangDpText by remember { mutableStateOf("") }

    val uangDp = uangDpText.toDoubleOrNull() ?: 0.0
    val calculatedSisa = if (selectedMethod == "DP") {
        maxOf(0.0, totalHarga - uangDp)
    } else {
        0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Metode Pelunasan") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Faktur: ${transaction.transaksi.noFaktur}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total: ${totalHarga.toRupiah()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (currentSisa > 0) {
                    Text(
                        text = "Sisa Bon: ${currentSisa.toRupiah()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Pilih Metode:", style = MaterialTheme.typography.labelLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TUNAI", "TRANSFER", "DP").forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedMethod == "DP") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uangDpText,
                        onValueChange = { uangDpText = it },
                        label = { Text("Jumlah Uang DP (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sisa Piutang: ${calculatedSisa.toRupiah()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dpVal = if (selectedMethod == "DP") uangDp else 0.0
                    val sisaVal = if (selectedMethod == "DP") calculatedSisa else 0.0
                    onConfirm(selectedMethod, dpVal, sisaVal)
                }
            ) {
                Text("Simpan Pelunasan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun PelangganDetailDialog(
    pelanggan: PelangganEntity,
    transactions: List<TransaksiWithDetails>,
    onDismiss: () -> Unit,
    onMarkLunas: (Int) -> Unit,
    onMarkPelunasan: (Long, String, Double, Double) -> Unit = { id, metode, dp, sisa ->
        onMarkLunas(id.toInt())
    }
) {
    var settlementTx by remember { mutableStateOf<TransaksiWithDetails?>(null) }
    var selectedReceiptTransaction by remember { mutableStateOf<TransaksiWithDetails?>(null) }
    val oneYearAgo = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L

    if (settlementTx != null) {
        SettlementDialog(
            transaction = settlementTx!!,
            onDismiss = { settlementTx = null },
            onConfirm = { metode, uangDp, sisa ->
                val txId = settlementTx!!.transaksi.id
                settlementTx = null
                onMarkPelunasan(txId.toLong(), metode, uangDp, sisa)
            }
        )
    }

    if (selectedReceiptTransaction != null) {
        ReceiptDialog(
            transaction = selectedReceiptTransaction!!,
            onDismiss = { selectedReceiptTransaction = null }
        )
    }

    // Total spending over past 1 year
    val totalSpending1Year = transactions
        .filter { tx ->
            val txTime = if (tx.transaksi.tanggal > 0L) tx.transaksi.tanggal else parseDateToLong(tx.transaksi.waktuTransaksi, 0L)
            txTime >= oneYearAgo || txTime == 0L
        }
        .sumOf { it.transaksi.totalHarga }

    // Total Piutang (Bon) - outstanding credit balance across all transactions for this customer
    val totalPiutang = transactions.sumOf { it.effectiveSisa }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = pelanggan.nama,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!pelanggan.telepon.isNullOrBlank()) {
                                Text(
                                    text = pelanggan.telepon,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Profile Address if available
                if (!pelanggan.alamat.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pelanggan.alamat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Summary Cards Row (Ringkasan 1 Tahun Terakhir & Total Piutang)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ringkasan 1 Tahun Terakhir
                    OutlinedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Belanja 1 Thn Terakhir",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = totalSpending1Year.toRupiah(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Total Piutang (Bon)
                    OutlinedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (totalPiutang > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(0.5.dp, if (totalPiutang > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Total Piutang (Bon)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (totalPiutang > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = totalPiutang.toRupiah(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totalPiutang > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Riwayat Struk / Transaksi (1 Tahun Terakhir)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada riwayat transaksi untuk pelanggan ini",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = transactions,
                            key = { it.transaksi.id }
                        ) { txWithDetails ->
                            val tx = txWithDetails.transaksi
                            val sisa = txWithDetails.effectiveSisa
                            val isBonOrHasPiutang = sisa > 0.0 || tx.metodePembayaran.equals("Bon", ignoreCase = true)

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedReceiptTransaction = txWithDetails },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tx.noFaktur,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = formatTransactionDate(tx.waktuTransaksi, tx.tanggal),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Total: ${tx.totalHarga.toRupiah()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Metode: ${tx.metodePembayaran}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (sisa > 0.0) {
                                                Text(
                                                    text = "Sisa Piutang: ${sisa.toRupiah()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }

                                        if (isBonOrHasPiutang) {
                                            Button(
                                                onClick = { settlementTx = txWithDetails },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Lunas", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
