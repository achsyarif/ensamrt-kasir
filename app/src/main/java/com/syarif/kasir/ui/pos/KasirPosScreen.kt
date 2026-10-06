package com.syarif.kasir.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.LifecycleCameraController
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.shape.CircleShape
import com.syarif.kasir.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.syarif.kasir.util.StrukOcrParser
import com.syarif.kasir.util.GeminiStrukScanner
import android.net.Uri
import com.syarif.kasir.data.local.entity.BarangWithHargaGrosir
import com.syarif.kasir.data.local.entity.PelangganEntity
import com.syarif.kasir.data.local.entity.TransaksiWithDetails
import com.syarif.kasir.util.SortOption
import com.syarif.kasir.util.calculateSisa
import com.syarif.kasir.util.effectiveSisa
import com.syarif.kasir.util.formatDateTime
import com.syarif.kasir.util.formatTransactionDate
import com.syarif.kasir.util.toRupiah
import com.syarif.kasir.util.EpposPrinterManager
import com.syarif.kasir.util.ReceiptImageSharer
import com.syarif.kasir.ui.components.EmbeddedBarcodeScannerView
import com.syarif.kasir.ui.components.BarcodeScannerDialog
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirPosScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var editingQuantityCartItem by remember { mutableStateOf<CartItem?>(null) }
    var deletingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var showClearCartConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.snackbarMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kasir",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BadgeButton(
                                text = "Tertunda (${uiState.unsyncedCount + uiState.heldTransactions.size})",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { viewModel.openHeldTransactionsDialog() }
                            )
                            val customerName = uiState.selectedPelanggan?.nama ?: "Pelanggan Umum"
                            OutlinedButton(
                                onClick = { viewModel.openCustomerSelectionDialog() },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Pilih Pelanggan",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = customerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 0.dp)
        ) {
            val isWideScreen = maxWidth >= 840.dp

            if (isWideScreen) {
                // Split Two-Column Layout for Tablet / Desktop / Landscape
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column: Input Nama Barang & Search / Products
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        LeftColumnSection(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }

                    // Right Column: Barang yang Sudah Masuk (Cart & Actions)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        RightColumnSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onEditPrice = { editingCartItem = it },
                            onEditQuantity = { editingQuantityCartItem = it },
                            onRemove = { deletingCartItem = it },
                            onClearCart = { showClearCartConfirmation = true }
                        )
                    }
                }
            } else {
                // Responsive Stacked / Split Layout for Phones
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Column Products (takes upper or remaining weight)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                    ) {
                        LeftColumnSection(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }

                    // Right Column Cart (takes lower weight / bottom card)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        RightColumnSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onEditPrice = { editingCartItem = it },
                            onEditQuantity = { editingQuantityCartItem = it },
                            onRemove = { deletingCartItem = it },
                            onClearCart = { showClearCartConfirmation = true }
                        )
                    }
                }
            }
        }

        // Edit Price Dialog
        editingCartItem?.let { item ->
            EditPriceDialog(
                cartItem = item,
                onConfirm = { newPrice ->
                    viewModel.updateCartItemPrice(item.barangWithGrosir, newPrice)
                    editingCartItem = null
                },
                onDismiss = { editingCartItem = null }
            )
        }

        // Edit Quantity Dialog
        editingQuantityCartItem?.let { item ->
            EditQuantityDialog(
                cartItem = item,
                onConfirm = { newQty ->
                    viewModel.updateCartQuantity(item.barangWithGrosir, newQty)
                    editingQuantityCartItem = null
                },
                onDismiss = { editingQuantityCartItem = null }
            )
        }

        // Delete Cart Item Confirmation Dialog
        deletingCartItem?.let { item ->
            DeleteCartItemConfirmationDialog(
                cartItem = item,
                onConfirm = {
                    viewModel.removeFromCart(item.barangWithGrosir)
                    deletingCartItem = null
                },
                onDismiss = { deletingCartItem = null }
            )
        }

        // Clear Cart Confirmation Dialog
        if (showClearCartConfirmation) {
            ClearCartConfirmationDialog(
                onConfirm = {
                    viewModel.clearCart()
                    showClearCartConfirmation = false
                },
                onDismiss = { showClearCartConfirmation = false }
            )
        }

        // Customer Selection Dialog
        if (uiState.isSelectCustomerDialogOpen) {
            SelectCustomerDialog(
                pelangganList = uiState.pelangganList,
                selectedPelanggan = uiState.selectedPelanggan,
                onSelect = { viewModel.selectPelanggan(it) },
                onAddQuickCustomer = { viewModel.addQuickCustomerAndSelect(it) },
                onDismiss = { viewModel.closeCustomerSelectionDialog() }
            )
        }

        // Held Transactions / Tertunda Dialog
        if (uiState.isHeldTransactionsDialogOpen) {
            HeldTransactionsDialog(
                heldTransactions = uiState.heldTransactions,
                onRestore = { viewModel.restoreHeldTransaction(it) },
                onDelete = { viewModel.deleteHeldTransaction(it.id) },
                onDismiss = { viewModel.closeHeldTransactionsDialog() }
            )
        }

        // History Dialog
        if (uiState.isHistoryDialogOpen) {
            HistoryDialog(
                onDismiss = { viewModel.closeHistoryDialog() }
            )
        }

        // Checkout Dialog
        if (uiState.isCheckoutDialogOpen) {
            CheckoutDialog(
                checkoutState = uiState.checkoutState,
                finalAmount = uiState.finalAmount,
                onPaymentMethodChanged = { viewModel.updatePaymentMethod(it) },
                onNominalPaidChanged = { viewModel.updateNominalPaid(it) },
                onDiscountChanged = { viewModel.updateDiscount(it) },
                onConfirm = { viewModel.processCheckout() },
                onDismiss = { viewModel.closeCheckoutDialog() }
            )
        }

        // Receipt Details Dialog (Completed Transaction)
        uiState.completedTransaction?.let { transactionWithDetails ->
            ReceiptDialog(
                transaction = transactionWithDetails,
                onDismiss = { viewModel.closeReceiptDialog() }
            )
        }

        // Category Selection Dialog
        if (uiState.isCategoryDialogOpen) {
            CategorySelectionDialog(
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                onSelect = { viewModel.onCategorySelected(it) },
                onDismiss = { viewModel.closeCategoryDialog() }
            )
        }

        // Quick Product Dialog
        if (uiState.isQuickProductDialogOpen) {
            TambahBarangCepatDialog(
                initialNama = uiState.searchQuery,
                onConfirm = { nama, harga ->
                    viewModel.addQuickProduct(nama, harga)
                },
                onDismiss = { viewModel.closeQuickProductDialog() }
            )
        }
    }

    if (uiState.isAiAnalyzing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "AI Sedang Menganalisis Struk...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Mengekstrak teks, kuantitas, dan harga barang secara otomatis...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
}

@Composable
fun BadgeButton(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LeftColumnSection(
    uiState: PosUiState,
    viewModel: PosViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search text field & Barcode Scan Button
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            IconButton(
                onClick = { viewModel.openBarcodeScannerDialog() },
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Barcode",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Products List / Grid or Embedded Barcode Scanner
        if (uiState.isBarcodeScannerDialogOpen) {
            EmbeddedBarcodeScannerView(
                onBarcodeDetected = { scannedCode ->
                    viewModel.onBarcodeScanned(scannedCode)
                },
                onStopScan = { viewModel.closeBarcodeScannerDialog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.filteredBarangList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Barang tidak ditemukan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            onClick = { viewModel.openQuickProductDialog() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "+ Tambah Produk Cepat: '${uiState.searchQuery}'",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp, top = 0.dp)
            ) {
                items(
                    items = uiState.filteredBarangList,
                    key = { it.barang.id }
                ) { item ->
                    PosBarangCard(
                        item = item,
                        onClick = { viewModel.addToCart(item, 1) }
                    )
                }
            }
        }
    }
}

@Composable
fun RightColumnSection(
    uiState: PosUiState,
    viewModel: PosViewModel,
    onEditPrice: (CartItem) -> Unit,
    onEditQuantity: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    onClearCart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Barang yang Sudah Masuk",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (uiState.cartItems.isNotEmpty()) {
                TextButton(
                    onClick = onClearCart,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Kosongkan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cart items list
        if (uiState.cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Keranjang masih kosong",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            val cartItemsSize = uiState.cartItems.size
            LaunchedEffect(cartItemsSize) {
                if (cartItemsSize > 0) {
                    listState.animateScrollToItem(cartItemsSize - 1)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.cartItems, key = { it.barang.id }) { cartItem ->
                    CartItemRow(
                        cartItem = cartItem,
                        onIncrease = { viewModel.updateCartQuantity(cartItem.barangWithGrosir, cartItem.quantity + 1) },
                        onDecrease = { viewModel.updateCartQuantity(cartItem.barangWithGrosir, cartItem.quantity - 1) },
                        onRemove = { onRemove(cartItem) },
                        onEditPrice = { onEditPrice(cartItem) },
                        onEditQuantity = { onEditQuantity(cartItem) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        // Total amount display (Total: Rp X)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total (${uiState.totalItems} item):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.hasGrosirActive) {
                    Text(
                        text = "Hemat ${uiState.totalDiscount.toRupiah()} (Grosir)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Text(
                text = uiState.finalAmount.toRupiah(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Three distinct action buttons at the bottom of the cart:
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Tunda (Yellow / Amber)
            Button(
                onClick = { viewModel.holdCurrentTransaction() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tunda", fontWeight = FontWeight.Bold)
            }

            // 2. Batalkan (Red)
            Button(
                onClick = onClearCart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Batalkan", fontWeight = FontWeight.Bold)
            }

            // 3. Pembayaran (Green)
            Button(
                onClick = { viewModel.openCheckoutDialog() },
                modifier = Modifier.weight(1.2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pembayaran", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PosBarangCard(
    item: BarangWithHargaGrosir,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barang = item.barang
    val hasGrosir = item.hargaGrosirList.isNotEmpty()

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = barang.namaBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasGrosir) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("Grosir", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Stok: ${barang.stok}",
                style = MaterialTheme.typography.labelSmall,
                color = if (barang.stok > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = barang.hargaJual.toRupiah(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CartItemRow(
    cartItem: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onEditPrice: () -> Unit,
    onEditQuantity: () -> Unit
) {
    val barang = cartItem.barang
    val isGrosirActive = cartItem.isGrosirActive

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditPrice() }
            ) {
                Text(
                    text = barang.namaBarang,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (cartItem.customUnitPrice != null) {
                            "Custom (@ ${cartItem.effectiveUnitPrice.toRupiah()})"
                        } else if (isGrosirActive) {
                            "Grosir (@ ${cartItem.effectiveUnitPrice.toRupiah()})"
                        } else {
                            "@ ${cartItem.effectiveUnitPrice.toRupiah()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cartItem.customUnitPrice != null) MaterialTheme.colorScheme.primary else if (isGrosirActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Harga",
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Kurang", modifier = Modifier.size(14.dp))
                }
                
                Surface(
                    onClick = onEditQuantity,
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${cartItem.quantity}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Jumlah",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onIncrease, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(14.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = cartItem.lineSubtotal.toRupiah(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun EditPriceDialog(
    cartItem: CartItem,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var priceText by remember {
        mutableStateOf(
            if (cartItem.customUnitPrice != null) {
                if (cartItem.customUnitPrice % 1.0 == 0.0) cartItem.customUnitPrice.toLong().toString() else cartItem.customUnitPrice.toString()
            } else {
                val current = cartItem.effectiveUnitPrice
                if (current % 1.0 == 0.0) current.toLong().toString() else current.toString()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Harga Satuan", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = cartItem.barang.namaBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Harga Normal: ${cartItem.barang.hargaJual.toRupiah()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Harga Satuan (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val newPrice = priceText.toDoubleOrNull()
                onConfirm(newPrice)
            }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text("Reset Default", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@Composable
fun EditQuantityDialog(
    cartItem: CartItem,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember { mutableStateOf(cartItem.quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Jumlah Barang", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = cartItem.barang.namaBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Jumlah") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = qtyText.toIntOrNull()
                if (qty != null && qty > 0) {
                    onConfirm(qty)
                }
            }) {
                Text("Simpan")
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
fun DeleteCartItemConfirmationDialog(
    cartItem: CartItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus Barang", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = cartItem.barang.namaBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Hapus barang dari keranjang?")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus")
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
fun ClearCartConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kosongkan Keranjang", fontWeight = FontWeight.Bold) },
        text = {
            Text("Apakah Anda yakin ingin mengosongkan seluruh keranjang?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Kosongkan")
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
fun SelectCustomerDialog(
    pelangganList: List<PelangganEntity>,
    selectedPelanggan: PelangganEntity?,
    onSelect: (PelangganEntity?) -> Unit,
    onAddQuickCustomer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    PelangganSelectionDialog(
        pelangganList = pelangganList,
        selectedPelanggan = selectedPelanggan,
        onSelect = onSelect,
        onAddQuickCustomer = onAddQuickCustomer,
        onDismiss = onDismiss
    )
}

@Composable
fun PelangganSelectionDialog(
    pelangganList: List<PelangganEntity>,
    selectedPelanggan: PelangganEntity?,
    onSelect: (PelangganEntity?) -> Unit,
    onAddQuickCustomer: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPelangganList = remember(pelangganList, searchQuery) {
        if (searchQuery.isBlank()) {
            pelangganList
        } else {
            pelangganList.filter {
                it.nama.contains(searchQuery.trim(), ignoreCase = true) ||
                it.telepon?.contains(searchQuery.trim(), ignoreCase = true) == true ||
                it.alamat?.contains(searchQuery.trim(), ignoreCase = true) == true
            }
        }
    }

    val handleSelectOrAdd = {
        val query = searchQuery.trim()
        if (query.isNotBlank()) {
            val existing = pelangganList.find { it.nama.equals(query, ignoreCase = true) }
            if (existing != null) {
                onSelect(existing)
            } else if (onAddQuickCustomer != null) {
                onAddQuickCustomer(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Pelanggan") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedPelanggan == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Pelanggan Umum (Tanpa Member)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari Pelanggan...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { handleSelectOrAdd() }
                        ),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus pencarian")
                                }
                            }
                        }
                    )

                    Button(
                        onClick = { handleSelectOrAdd() },
                        enabled = searchQuery.isNotBlank()
                    ) {
                        Text("Pilih")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredPelangganList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada pelanggan terdaftar" else "Pelanggan tidak ditemukan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPelangganList, key = { it.id }) { pelanggan ->
                            val isSelected = selectedPelanggan?.id == pelanggan.id
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(pelanggan) },
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(pelanggan.nama, fontWeight = FontWeight.Bold)
                                    if (pelanggan.telepon?.isNotBlank() == true) {
                                        Text(pelanggan.telepon, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
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
fun HeldTransactionsDialog(
    heldTransactions: List<HeldTransaction>,
    onRestore: (HeldTransaction) -> Unit,
    onDelete: (HeldTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daftar Transaksi Tertunda") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (heldTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada transaksi tertunda", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(heldTransactions, key = { it.id }) { held ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(held.customerName, fontWeight = FontWeight.Bold)
                                        Text("${held.cartItems.sumOf { it.quantity }} item", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Total: ${held.cartItems.sumOf { it.lineSubtotal }.toRupiah()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(onClick = { onDelete(held) }) {
                                            Text("Hapus", color = MaterialTheme.colorScheme.error)
                                        }
                                        Button(onClick = { onRestore(held) }) {
                                            Text("Pulihkan")
                                        }
                                    }
                                }
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
fun HistoryDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Histori Transaksi 🕒") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Silakan lihat tab Riwayat di menu navigasi bawah untuk detail histori transaksi lengkap.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun CheckoutDialog(
    checkoutState: CheckoutState,
    finalAmount: Double,
    onPaymentMethodChanged: (String) -> Unit,
    onNominalPaidChanged: (String) -> Unit,
    onDiscountChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val methods = listOf("TUNAI", "TRANSFER", "BON")
    val methodTrim = checkoutState.paymentMethod.trim()
    val isTunai = methodTrim.equals("tunai", true)
    val isTransfer = methodTrim.equals("transfer", true)
    val isBon = methodTrim.equals("bon", true)
    val isPas = methodTrim.equals("pas", true)
    val isCashLike = isTunai || isPas
    
    val subtotal = finalAmount
    val discount = checkoutState.discount
    val finalTotal = (subtotal - discount).coerceAtLeast(0.0)
    val paid = checkoutState.nominalPaid
    val calculatedSisa = calculateSisa(finalTotal, if (isBon) paid else 0.0, methodTrim, 0.0)
    val kembalian = if (isCashLike) (paid - finalTotal).coerceAtLeast(0.0) else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkout Pembayaran", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal:", style = MaterialTheme.typography.bodyMedium)
                    Text(subtotal.toRupiah(), style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = checkoutState.discountText,
                    onValueChange = onDiscountChanged,
                    label = { Text("Diskon Rp (Opsional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Belanja:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(finalTotal.toRupiah(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Metode Pembayaran:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(methods) { method ->
                        val isSelected = checkoutState.paymentMethod.equals(method, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPaymentMethodChanged(method) },
                            label = { Text(method) }
                        )
                    }
                }

                if (isCashLike) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = checkoutState.nominalPaidText,
                        onValueChange = onNominalPaidChanged,
                        label = { Text("Uang Diterima (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Kembalian:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = kembalian.toRupiah(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (kembalian < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val suggestions = listOf(finalTotal, 20000.0, 50000.0, 100000.0).distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(suggestions) { amount ->
                            SuggestionChip(
                                onClick = { onNominalPaidChanged(amount.toLong().toString()) },
                                label = { Text(amount.toRupiah()) }
                            )
                        }
                    }
                } else if (isTransfer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Pembayaran Transfer / Non-Tunai",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Total yang harus ditransfer: ${finalTotal.toRupiah()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else if (isBon) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = checkoutState.nominalPaidText,
                        onValueChange = onNominalPaidChanged,
                        label = { Text("Uang DP (Opsional, Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sisa / Kekurangan (Bon):", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = calculatedSisa.toRupiah(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val dpSuggestions = listOf(1000000.0, 2000000.0, 3000000.0, 5000000.0).filter { it <= finalTotal || finalTotal < 1000000 }
                    if (dpSuggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(dpSuggestions) { amount ->
                                SuggestionChip(
                                    onClick = { onNominalPaidChanged(amount.toLong().toString()) },
                                    label = { Text(amount.toRupiah()) }
                                )
                            }
                        }
                    }
                }

                checkoutState.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Simpan & Cetak Struk")
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
fun ReceiptDialog(
    transaction: TransaksiWithDetails,
    onDismiss: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeviceSelectionDialog by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun hasBluetoothPermissions(ctx: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true

        if (allGranted || connectGranted) {
            showDeviceSelectionDialog = true
        } else {
            Toast.makeText(context, "Izin Bluetooth dan Lokasi diperlukan untuk mencetak", Toast.LENGTH_SHORT).show()
        }
    }

    val tx = transaction.transaksi
    val calculatedSisa = transaction.effectiveSisa
    val effectiveUangBayar = if (tx.uangBayar > 0.0) tx.uangBayar else tx.bayar
    val effectiveKembalian = if (tx.kembali > 0.0) tx.kembali else ((effectiveUangBayar - tx.totalHarga).coerceAtLeast(0.0))
    val isCashMethod = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
    val isBonOrDp = tx.metodePembayaran.equals("bon", ignoreCase = true) || tx.metodePembayaran.equals("dp", ignoreCase = true)
    val isTunaiOrTransfer = tx.metodePembayaran.equals("TUNAI", ignoreCase = true) || tx.metodePembayaran.equals("TRANSFER", ignoreCase = true) || tx.metodePembayaran.equals("pas", ignoreCase = true)
    val isFullTunaiOrTransfer = isTunaiOrTransfer && (tx.sisa <= 0.0 || tx.bayar >= tx.totalHarga || calculatedSisa <= 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ENSMART",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Gelanggang RT 001/008 Pamotan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTransactionDate(tx.waktuTransaksi, tx.tanggal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Items list with item name on left, total price on right, and sub-line showing Unit Price x Qty below the name.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transaction.details) { detail ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = detail.namaBarang,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = detail.subtotal.toRupiah(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${detail.hargaSatuan.toRupiah()} x ${detail.jumlah}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (tx.diskon > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subtotal:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = (tx.totalHarga + tx.diskon).toRupiah(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Diskon:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "-${tx.diskon.toRupiah()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // TOTAL row in bold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOTAL",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tx.totalHarga.toRupiah(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Payment details: Metode, Nama, Bayar, Kembalian, DP (if > 0), Sisa (if > 0)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Metode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tx.metodePembayaran.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nama",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tx.namaPelanggan ?: "Umum",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!isBonOrDp && (effectiveUangBayar > 0.0 || isCashMethod)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bayar:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = effectiveUangBayar.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (effectiveKembalian > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kembalian:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = effectiveKembalian.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (tx.dp > 0.0 && !isFullTunaiOrTransfer) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = tx.dp.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (calculatedSisa > 0.0 && !isFullTunaiOrTransfer) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sisa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = calculatedSisa.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Footer: "Terima kasih telah berbelanja!" and "WhatsApp: 089512224989 / 089512224993"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Terima kasih telah berbelanja!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "WhatsApp 089512224989 / 089512224993",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (hasBluetoothPermissions(context)) {
                                showDeviceSelectionDialog = true
                            } else {
                                bluetoothPermissionLauncher.launch(bluetoothPermissions)
                            }
                        },
                        enabled = !isPrinting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPrinting) "Mencetak..." else "Cetak")
                    }
                    OutlinedButton(
                        onClick = {
                            ReceiptImageSharer.shareReceiptImage(context, transaction)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan")
                    }
                }
                if (onDeleteClick != null || onEditClick != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onDeleteClick != null) {
                            OutlinedButton(
                                onClick = onDeleteClick,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus Transaksi", maxLines = 1)
                            }
                        }
                        if (onEditClick != null) {
                            OutlinedButton(
                                onClick = onEditClick,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Transaksi", maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup / Selesai")
                }
            }
        }
    )

    if (showDeviceSelectionDialog) {
        val pairedDevices = remember { EpposPrinterManager.getPairedPrinters(context) }
        AlertDialog(
            onDismissRequest = { showDeviceSelectionDialog = false },
            title = { Text("Pilih Printer EPPOS 58mm") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "Tidak ada perangkat Bluetooth terpasang. Pastikan printer EPPOS 58mm sudah dipairing di Pengaturan Bluetooth perangkat.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Pilih perangkat printer thermal:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pairedDevices) { device ->
                                val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                    "Protected Device"
                                } else {
                                    device.name ?: "Unknown Device"
                                }
                                val deviceAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                    ""
                                } else {
                                    device.address
                                }

                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showDeviceSelectionDialog = false
                                            isPrinting = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val result = EpposPrinterManager.printReceipt(context, device, transaction)
                                                withContext(Dispatchers.Main) {
                                                    isPrinting = false
                                                    val msg = if (result.isSuccess) {
                                                        "Struk berhasil dicetak!"
                                                    } else {
                                                        "Gagal mencetak: ${result.exceptionOrNull()?.localizedMessage}"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = deviceName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        if (deviceAddress.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = deviceAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceSelectionDialog = false }) {
                    Text("Batal")
                }
            }
        )
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
fun TambahBarangCepatDialog(
    initialNama: String,
    onConfirm: (nama: String, hargaJual: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var namaBarang by remember(initialNama) { mutableStateOf(initialNama) }
    var hargaJualText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tambah Produk Cepat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = namaBarang,
                    onValueChange = {
                        namaBarang = it
                        errorMessage = null
                    },
                    label = { Text("Nama Barang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hargaJualText,
                    onValueChange = {
                        hargaJualText = it
                        errorMessage = null
                    },
                    label = { Text("Harga Jual (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (namaBarang.isBlank()) {
                        errorMessage = "Nama barang tidak boleh kosong"
                        return@Button
                    }
                    val harga = hargaJualText.toDoubleOrNull()
                    if (harga == null || harga < 0) {
                        errorMessage = "Harga jual tidak valid"
                        return@Button
                    }
                    onConfirm(namaBarang.trim(), harga)
                }
            ) {
                Text("Simpan & Tambah ke Keranjang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

