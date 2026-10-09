package com.syarif.kasir.ui.barang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syarif.kasir.ui.components.BarcodeScannerDialog

@Composable
fun BarangDialog(
    dialogState: BarangDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNamaChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onKategoriChange: (String) -> Unit,
    onHargaBeliChange: (String) -> Unit,
    onHargaJualChange: (String) -> Unit,
    onStokChange: (String) -> Unit,
    onSatuanChange: (String) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (Int) -> Unit,
    onUpdateTier: (Int, String, String) -> Unit
) {
    var showScannerDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (dialogState.isEditing) "Edit Barang" else "Tambah Barang Baru",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                dialogState.errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = dialogState.namaBarang,
                    onValueChange = onNamaChange,
                    label = { Text("Nama Barang *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dialogState.barcode,
                        onValueChange = onBarcodeChange,
                        label = { Text("Barcode / Kode Barang") },
                        placeholder = { Text("Scan atau masukkan barcode") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showScannerDialog = true },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dialogState.kategori,
                    onValueChange = onKategoriChange,
                    label = { Text("Kategori") },
                    placeholder = { Text("e.g. Makanan, Minuman") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hargaBeliText = if (dialogState.hargaBeli == "0" || dialogState.hargaBeli == "0.0" || dialogState.hargaBeli.isBlank()) "" else dialogState.hargaBeli
                    val hargaJualText = if (dialogState.hargaJual == "0" || dialogState.hargaJual == "0.0" || dialogState.hargaJual.isBlank()) "" else dialogState.hargaJual

                    OutlinedTextField(
                        value = hargaBeliText,
                        onValueChange = onHargaBeliChange,
                        label = { Text("Harga Beli (Rp)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hargaJualText,
                        onValueChange = onHargaJualChange,
                        label = { Text("Harga Jual * (Rp)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val stokText = if (dialogState.stok == "0" || dialogState.stok == "0.0" || dialogState.stok.isBlank()) "" else dialogState.stok

                    OutlinedTextField(
                        value = stokText,
                        onValueChange = onStokChange,
                        label = { Text("Stok") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dialogState.satuan,
                        onValueChange = onSatuanChange,
                        label = { Text("Satuan") },
                        placeholder = { Text("pcs, dus, kg") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sell,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Harga Grosir (Tiered Pricing)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (dialogState.tieredPrices.isEmpty()) {
                    Text(
                        text = "Belum ada tier harga grosir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    dialogState.tieredPrices.forEachIndexed { index, tier ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val minQtyText = if (tier.minQty == "0" || tier.minQty.isBlank()) "" else tier.minQty
                            val hargaGrosirText = if (tier.hargaGrosir == "0" || tier.hargaGrosir == "0.0" || tier.hargaGrosir.isBlank()) "" else tier.hargaGrosir

                            OutlinedTextField(
                                value = minQtyText,
                                onValueChange = { newMinQty ->
                                    onUpdateTier(index, newMinQty, tier.hargaGrosir)
                                },
                                label = { Text("Min Qty") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = hargaGrosirText,
                                onValueChange = { newHarga ->
                                    onUpdateTier(index, tier.minQty, newHarga)
                                },
                                label = { Text("Harga Grosir") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )

                            IconButton(
                                onClick = { onRemoveTier(index) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Tier",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onAddTier,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Tier Harga Grosir")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text(if (dialogState.isEditing) "Simpan" else "Tambah")
                    }
                }
            }
        }
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            onBarcodeScanned = { scannedCode ->
                onBarcodeChange(scannedCode)
                showScannerDialog = false
            },
            onDismiss = { showScannerDialog = false }
        )
    }
}
