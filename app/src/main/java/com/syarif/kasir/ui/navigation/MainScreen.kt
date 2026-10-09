package com.syarif.kasir.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.syarif.kasir.KasirApplication
import com.syarif.kasir.ui.ai.AiStrukScreen
import com.syarif.kasir.ui.barang.BarangScreen
import com.syarif.kasir.ui.barang.BarangViewModel
import com.syarif.kasir.ui.components.SyncProgressDialog
import com.syarif.kasir.ui.factory.ViewModelFactory
import com.syarif.kasir.ui.laporan.LaporanScreen
import com.syarif.kasir.ui.laporan.LaporanViewModel
import com.syarif.kasir.ui.pelanggan.PelangganScreen
import com.syarif.kasir.ui.pelanggan.PelangganViewModel
import com.syarif.kasir.ui.pos.KasirPosScreen
import com.syarif.kasir.ui.pos.PosViewModel
import com.syarif.kasir.ui.riwayat.RiwayatTransaksiScreen
import com.syarif.kasir.ui.riwayat.RiwayatViewModel

enum class NavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    KATALOG("Katalog", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    POS("Kasir", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
    AI_STRUK("Scan Struk AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PELANGGAN("Pelanggan", Icons.Filled.People, Icons.Outlined.PeopleOutline),
    RIWAYAT("Riwayat", Icons.Filled.Receipt, Icons.Outlined.Receipt),
    LAPORAN("Laporan", Icons.Filled.Assessment, Icons.Outlined.Assessment)
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KasirApplication
    val factory = remember { ViewModelFactory(app.repository) }

    LaunchedEffect(Unit) {
        com.syarif.kasir.data.remote.InfinityFreeCookieFetcher.fetchCookie(context)
    }

    val barangViewModel: BarangViewModel = viewModel(factory = factory)
    val barangUiState by barangViewModel.uiState.collectAsState()
    val pelangganViewModel: PelangganViewModel = viewModel(factory = factory)
    val posViewModel: PosViewModel = viewModel(factory = factory)
    val riwayatViewModel: RiwayatViewModel = viewModel(factory = factory)
    val laporanViewModel: LaporanViewModel = viewModel(factory = factory)

    var currentTab by remember { mutableStateOf(NavTab.POS) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var backPressedTime by remember { mutableStateOf(0L) }

    BackHandler(enabled = isMenuOpen) {
        isMenuOpen = false
    }

    BackHandler(enabled = !isMenuOpen) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val edgeThreshold = 48.dp.toPx()
                var isNearRightEdge = false
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isNearRightEdge = offset.x >= (size.width - edgeThreshold)
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (isNearRightEdge && dragAmount < -10) {
                            isMenuOpen = true
                        }
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                NavTab.KATALOG -> BarangScreen(
                    viewModel = barangViewModel,
                    onMenuClick = { isMenuOpen = true }
                )
                NavTab.POS -> KasirPosScreen(
                    viewModel = posViewModel,
                    onMenuClick = { isMenuOpen = true }
                )
                NavTab.AI_STRUK -> AiStrukScreen(
                    viewModel = posViewModel,
                    onMenuClick = { isMenuOpen = true },
                    onNavigateToPos = { currentTab = NavTab.POS }
                )
                NavTab.PELANGGAN -> PelangganScreen(
                    viewModel = pelangganViewModel,
                    onMenuClick = { isMenuOpen = true }
                )
                NavTab.RIWAYAT -> RiwayatTransaksiScreen(
                    viewModel = riwayatViewModel,
                    onMenuClick = { isMenuOpen = true },
                    onEditTransaksi = { transaction ->
                        riwayatViewModel.deleteTransaksi(transaction.transaksi.id.toLong())
                        posViewModel.restoreTransactionForEdit(transaction)
                        currentTab = NavTab.POS
                    }
                )
                NavTab.LAPORAN -> LaporanScreen(
                    viewModel = laporanViewModel,
                    onMenuClick = { isMenuOpen = true }
                )
            }
        }

        // Semi-transparent dark scrim overlay
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isMenuOpen = false
                    }
            )
        }

        // Right-to-Left Sliding Menu Panel
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevents clicking inside drawer from dismissing scrim
                    ),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Menu Navigasi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { isMenuOpen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Menu"
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    NavTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                currentTab = tab
                                isMenuOpen = false
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedContainerColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    NavigationDrawerItem(
                        label = { Text("Sinkronisasi") },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sinkronisasi") },
                        selected = false,
                        onClick = {
                            isMenuOpen = false
                            barangViewModel.triggerSync()
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        SyncProgressDialog(
            isSyncing = barangUiState.isSyncing,
            showDialog = barangUiState.showSyncDialog,
            summary = barangUiState.syncSummary,
            errorMessage = barangUiState.syncErrorMessage,
            onDismiss = { barangViewModel.dismissSyncDialog() }
        )
    }
}
