package com.syarif.kasir.ui.barang

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.syarif.kasir.data.local.entity.BarangEntity
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.HargaGrosirEntity
import com.syarif.kasir.ui.components.BarcodeScannerDialog
import com.syarif.kasir.ui.components.SyncProgressDialog
import com.syarif.kasir.ui.theme.KASIRTheme
import com.syarif.kasir.util.SortOption
import com.syarif.kasir.util.toRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarangScreen(
    viewModel: BarangViewModel,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                        text = "Katalog Barang",
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tambah Barang") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            // Search Bar & Barcode Scan Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Cari") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = { viewModel.openCatalogScannerDialog() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Barcode Katalog",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Category Filter Button & Sorting Chips Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.openCategoryDialog() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(uiState.selectedCategory ?: "Semua Kategori", maxLines = 1)
                    }
                }
                items(SortOption.entries) { option ->
                    FilterChip(
                        selected = uiState.selectedSortOption == option,
                        onClick = { viewModel.onSortSelected(option) },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredBarangList.isEmpty()) {
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
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.searchQuery.isEmpty()) "Belum ada barang di katalog" else "Barang tidak ditemukan",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Klik tombol '+' untuk menambah barang baru",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)
                ) {
                    items(
                        items = uiState.filteredBarangList,
                        key = { it.barang.id }
                    ) { item ->
                        BarangCard(
                            item = item,
                            onEdit = { viewModel.openEditDialog(item) },
                            onDelete = { viewModel.deleteBarang(item.barang) }
                        )
                    }
                }
            }
        }

        if (uiState.isDialogOpen) {
            BarangDialog(
                dialogState = uiState.dialogState,
                onDismiss = { viewModel.closeDialog() },
                onSave = { viewModel.saveBarang() },
                onNamaChange = { viewModel.updateDialogNamaBarang(it) },
                onBarcodeChange = { viewModel.updateDialogBarcode(it) },
                onKategoriChange = { viewModel.updateDialogKategori(it) },
                onHargaBeliChange = { viewModel.updateDialogHargaBeli(it) },
                onHargaJualChange = { viewModel.updateDialogHargaJual(it) },
                onStokChange = { viewModel.updateDialogStok(it) },
                onSatuanChange = { viewModel.updateDialogSatuan(it) },
                onAddTier = { viewModel.addTieredPriceRow() },
                onRemoveTier = { viewModel.removeTieredPriceRow(it) },
                onUpdateTier = { index, minQty, harga -> viewModel.updateTieredPriceRow(index, minQty, harga) }
            )
        }

        if (uiState.isCategoryDialogOpen) {
            CategorySelectionDialog(
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                onSelect = { viewModel.onCategorySelected(it) },
                onDismiss = { viewModel.closeCategoryDialog() }
            )
        }

        SyncProgressDialog(
            isSyncing = uiState.isSyncing,
            showDialog = uiState.showSyncDialog,
            summary = uiState.syncSummary,
            errorMessage = uiState.syncErrorMessage,
            onDismiss = { viewModel.dismissSyncDialog() }
        )

        if (uiState.showCatalogScannerDialog) {
            BarcodeScannerDialog(
                onBarcodeScanned = { scannedCode ->
                    viewModel.onBarcodeScanned(scannedCode)
                },
                onDismiss = { viewModel.closeCatalogScannerDialog() }
            )
        }

        if (uiState.showUnrecognizedBarcodeDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.closeUnrecognizedBarcodeDialog() },
                title = { Text("Barcode Tidak Ditemukan") },
                text = {
                    Text("Barcode '${uiState.unrecognizedBarcode}' tidak terdaftar di katalog. Pilih tindakan:")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.openAddDialogWithBarcode(uiState.unrecognizedBarcode)
                        }
                    ) {
                        Text("Tambahkan Barang Baru")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            viewModel.openProductSelectionForBarcode(uiState.unrecognizedBarcode)
                        }
                    ) {
                        Text("Tambahkan Barcode ke Barang")
                    }
                }
            )
        }

        if (uiState.showProductSelectionDialog) {
            var searchDialogQuery by remember(uiState.showProductSelectionDialog) { mutableStateOf("") }
            val filteredProducts = remember(uiState.barangList, searchDialogQuery) {
                if (searchDialogQuery.isBlank()) {
                    uiState.barangList
                } else {
                    val q = searchDialogQuery.trim()
                    uiState.barangList.filter { item ->
                        item.barang.namaBarang.contains(q, ignoreCase = true) ||
                        item.barang.kodeBarang.contains(q, ignoreCase = true) ||
                        item.barang.kategori.contains(q, ignoreCase = true)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { viewModel.closeProductSelectionDialog() },
                title = { Text("Pilih Barang untuk Barcode") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp)
                    ) {
                        Text(
                            text = "Pilih barang yang akan di-assign barcode '${uiState.unrecognizedBarcode}':",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = searchDialogQuery,
                            onValueChange = { searchDialogQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cari Nama Barang...") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchDialogQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchDialogQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus pencarian")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.barangList.isEmpty()) {
                            Text("Belum ada barang di katalog.")
                        } else if (filteredProducts.isEmpty()) {
                            Text(
                                text = "Barang tidak ditemukan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredProducts, key = { it.barang.id }) { item ->
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.assignBarcodeToProduct(item, uiState.unrecognizedBarcode)
                                            },
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = item.barang.namaBarang,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Kategori: ${item.barang.kategori.ifBlank { "Umum" }} | Stok: ${item.barang.stok} ${item.barang.satuan}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!item.barang.barcode.isNullOrBlank()) {
                                                Text(
                                                    text = "Barcode lama: ${item.barang.barcode}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.closeProductSelectionDialog() }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun CategorySelectionDialog(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Kategori") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedCategory == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Semua Kategori", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(category) },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(category, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun BarangCard(
    item: BarangWithHargaGrosir,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val barang = item.barang
    val hargaGrosirList = item.hargaGrosirList

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus barang '${barang.namaBarang}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header Row: Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = barang.namaBarang,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (barang.kategori.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = barang.kategori,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Barang",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Barang",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pricing & Stock Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Harga Jual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${barang.hargaJual.toRupiah()} / ${barang.satuan}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val stockColor = when {
                    barang.stok > 10 -> MaterialTheme.colorScheme.primary
                    barang.stok in 1..10 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = stockColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = stockColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Stok: ${barang.stok} ${barang.satuan}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = stockColor
                        )
                    }
                }
            }

            // Tiered Price Collapsible Section
            if (hargaGrosirList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Harga Grosir (${hargaGrosirList.size} Tier)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Tiers",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        hargaGrosirList.sortedBy { it.minQty }.forEach { tier ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pembelian ≥ ${tier.minQty} ${barang.satuan}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${tier.hargaGrosir.toRupiah()} / ${barang.satuan}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BarangCardPreview() {
    KASIRTheme {
        BarangCard(
            item = BarangWithHargaGrosir(
                barang = BarangEntity(
                    id = 1,
                    kodeBarang = "BRG001",
                    namaBarang = "Minyak Goreng 1L",
                    kategori = "Sembako",
                    hargaBeli = 12000.0,
                    hargaJual = 15000.0,
                    stok = 25,
                    satuan = "pouch"
                ),
                hargaGrosirList = listOf(
                    HargaGrosirEntity(id = 1, barangId = 1, minQty = 10, hargaGrosir = 14000.0),
                    HargaGrosirEntity(id = 2, barangId = 1, minQty = 50, hargaGrosir = 13500.0)
                )
            ),
            onEdit = {},
            onDelete = {}
        )
    }
}
