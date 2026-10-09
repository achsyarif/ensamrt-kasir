package com.syarif.kasir.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.syarif.kasir.ui.pos.PosViewModel
import com.syarif.kasir.util.GeminiStrukScanner
import com.syarif.kasir.util.ParsedReceiptItem
import com.syarif.kasir.util.StrukOcrParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

data class EditableReceiptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val namaBarang: String = "",
    val qty: String = "1",
    val harga: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStrukScreen(
    viewModel: PosViewModel,
    onMenuClick: () -> Unit,
    onNavigateToPos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var apiKeyInput by remember { mutableStateOf(GeminiStrukScanner.getApiKey(context) ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val reviewedItems = remember { mutableStateListOf<EditableReceiptItem>() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val photoFile = File.createTempFile("struk_camera_", ".jpg", context.cacheDir)
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, photoFile)
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuat file foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto struk", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scan Struk AI & Revisi") },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: API Key Configuration
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Konfigurasi Google Gemini API Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Masukkan Gemini API Key Anda untuk mengaktifkan pemindaian struk berbasis AI OCR. Dapatkan kunci gratis di aistudio.google.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Gemini API Key") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(if (showApiKey) "Sembunyikan" else "Lihat")
                                }
                            }
                        )

                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    GeminiStrukScanner.saveApiKey(context, apiKeyInput.trim())
                                    Toast.makeText(context, "API Key berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "API Key tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Simpan API Key")
                        }
                    }
                }

                // Card 2: Receipt Image & Scan Action with Dual Input Options
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Pilih Foto Struk",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Struk Foto",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Belum ada foto struk yang dipilih",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Dual Input Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        try {
                                            val photoFile = File.createTempFile("struk_camera_", ".jpg", context.cacheDir)
                                            val authority = "${context.packageName}.fileprovider"
                                            val uri = FileProvider.getUriForFile(context, authority, photoFile)
                                            cameraImageUri = uri
                                            cameraLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal membuat file foto: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kamera")
                            }

                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeri")
                            }
                        }

                        Button(
                            onClick = {
                                val currentKey = GeminiStrukScanner.getApiKey(context)
                                if (currentKey.isNullOrBlank()) {
                                    Toast.makeText(context, "Harap masukkan dan simpan Gemini API Key terlebih dahulu!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val uri = selectedImageUri
                                if (uri == null) {
                                    Toast.makeText(context, "Pilih foto struk terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                scope.launch {
                                    viewModel.setAiAnalyzing(true)
                                    try {
                                        val items = GeminiStrukScanner.scanReceipt(context, uri, currentKey)
                                        reviewedItems.clear()
                                        items.forEach { item ->
                                            reviewedItems.add(
                                                EditableReceiptItem(
                                                    namaBarang = item.namaBarang,
                                                    qty = item.quantity.toString(),
                                                    harga = item.harga.toInt().toString()
                                                )
                                            )
                                        }
                                        viewModel.setAiAnalyzing(false)
                                        if (items.isNotEmpty()) {
                                            Toast.makeText(context, "Berhasil memindai ${items.size} item. Silakan tinjau & revisi.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Tidak ada item terdeteksi. Silakan tambah manual.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        viewModel.setAiAnalyzing(false)
                                        try {
                                            val image = InputImage.fromFilePath(context, uri)
                                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                            val visionText = recognizer.process(image).await()
                                            val offlineItems = StrukOcrParser.parse(visionText.text)
                                            reviewedItems.clear()
                                            offlineItems.forEach { item ->
                                                reviewedItems.add(
                                                    EditableReceiptItem(
                                                        namaBarang = item.namaBarang,
                                                        qty = item.quantity.toString(),
                                                        harga = item.harga.toInt().toString()
                                                    )
                                                )
                                            }
                                            Toast.makeText(context, "Beralih ke Offline OCR (ML Kit) - ${offlineItems.size} item ditemukan.", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Silakan masukkan item secara manual.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            enabled = !uiState.isAiAnalyzing && selectedImageUri != null
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analisis AI Struk")
                        }
                    }
                }

                // Card 3: AI Parsing State & Review / Revision Screen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Revisi & Review Item (${reviewedItems.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (reviewedItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada item. Scan Nota Tangan dengan AI di atas atau klik 'Tambah Item' untuk input manual.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            reviewedItems.forEachIndexed { index, item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Item #${index + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            IconButton(
                                                onClick = { reviewedItems.removeAt(index) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus Item",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = item.namaBarang,
                                            onValueChange = { newVal ->
                                                reviewedItems[index] = item.copy(namaBarang = newVal)
                                            },
                                            label = { Text("Nama Barang") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = item.harga,
                                                onValueChange = { newVal ->
                                                    reviewedItems[index] = item.copy(harga = newVal)
                                                },
                                                label = { Text("Harga Satuan (Rp)") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1.5f)
                                            )

                                            OutlinedTextField(
                                                value = item.qty,
                                                onValueChange = { newVal ->
                                                    reviewedItems[index] = item.copy(qty = newVal)
                                                },
                                                label = { Text("Qty") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                reviewedItems.add(EditableReceiptItem(namaBarang = "", qty = "1", harga = ""))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Item")
                        }

                        if (reviewedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val validItems = reviewedItems.mapNotNull { editable ->
                                        val name = editable.namaBarang.trim()
                                        val q = editable.qty.toIntOrNull() ?: 1
                                        val h = editable.harga.toDoubleOrNull() ?: 0.0
                                        if (name.isNotBlank() && h > 0.0) {
                                            ParsedReceiptItem(
                                                namaBarang = name,
                                                quantity = q.coerceAtLeast(1),
                                                harga = h
                                            )
                                        } else null
                                    }

                                    if (validItems.isEmpty()) {
                                        Toast.makeText(context, "Harap isi minimal 1 item dengan Nama dan Harga yang valid!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    scope.launch {
                                        viewModel.processParsedReceiptItems(validItems)
                                        Toast.makeText(context, "Berhasil memasukkan ${validItems.size} item ke Kasir!", Toast.LENGTH_SHORT).show()
                                        onNavigateToPos()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Konfirmasi & Masukkan ke Kasir")
                            }
                        }
                    }
                }
            }

            // Loading Overlay when analyzing
            if (uiState.isAiAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mohon tunggu sebentar sementara Gemini membaca item, qty, dan harga.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resumeWith(Result.success(result))
        }
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) {
            continuation.resumeWith(Result.failure(exception))
        }
    }
}
