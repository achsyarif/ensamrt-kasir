package com.syarif.kasir.ui.riwayat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.ui.pos.ReceiptDialog
import com.syarif.kasir.util.formatTransactionDate
import com.syarif.kasir.util.toRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatTransaksiScreen(
    viewModel: RiwayatViewModel,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onEditTransaksi: ((TransaksiWithDetails) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.snackbarMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Riwayat Transaksi",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Navigasi"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("cari transaksi (id, pelanggan)") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredTransaksiList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum ada riwayat transaksi",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
                ) {
                    items(
                        items = uiState.filteredTransaksiList,
                        key = { it.transaksi.id }
                    ) { transaction ->
                        TransaksiCard(
                            transaction = transaction,
                            onClick = { viewModel.selectTransaksi(transaction) }
                        )
                    }
                }
            }
        }

        uiState.selectedTransaksi?.let { selected ->
            ReceiptDialog(
                transaction = selected,
                onDismiss = { viewModel.selectTransaksi(null) },
                onDeleteClick = { showDeleteConfirmDialog = true },
                onEditClick = { showEditConfirmDialog = true }
            )
        }

        if (showDeleteConfirmDialog && uiState.selectedTransaksi != null) {
            val transactionToDelete = uiState.selectedTransaksi!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Konfirmasi Hapus") },
                text = { Text("Apakah Anda yakin ingin menghapus transaksi ini?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteTransaksi(transactionToDelete.transaksi.id.toLong())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Hapus")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showEditConfirmDialog && uiState.selectedTransaksi != null) {
            val transactionToEdit = uiState.selectedTransaksi!!
            AlertDialog(
                onDismissRequest = { showEditConfirmDialog = false },
                title = { Text("Konfirmasi Edit Transaksi") },
                text = {
                    Text("Apakah Anda yakin ingin mengedit transaksi ini? Transaksi lama akan dihapus dan seluruh barang beserta nama pelanggan akan dimasukkan kembali ke Kasir.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEditConfirmDialog = false
                            viewModel.selectTransaksi(null)
                            onEditTransaksi?.invoke(transactionToEdit)
                        }
                    ) {
                        Text("Ya, Edit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditConfirmDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun TransaksiCard(
    transaction: TransaksiWithDetails,
    onClick: () -> Unit
) {
    val tx = transaction.transaksi
    val namaPembeli = if (!tx.namaPelanggan.isNullOrBlank()) tx.namaPelanggan else "Pelanggan Umum"

    val formattedPaymentMethod = when (tx.metodePembayaran.trim().lowercase()) {
        "pas" -> "Pas"
        "bon" -> "Bon"
        "tunai" -> "Tunai"
        "whatsapp" -> "WhatsApp"
        "qris" -> "QRIS"
        "transfer" -> "Transfer"
        "debit" -> "Debit"
        else -> tx.metodePembayaran.trim().ifBlank { "Tunai" }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = namaPembeli,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatTransactionDate(tx.waktuTransaksi, tx.tanggal)} #${tx.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tx.noFaktur.isNotBlank()) {
                        Text(
                            text = tx.noFaktur,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = formattedPaymentMethod,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = tx.totalHarga.toRupiah(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Badge(
                    containerColor = if (tx.isSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (tx.isSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(if (tx.isSynced) "Synced" else "Offline")
                }
            }
        }
    }
}
