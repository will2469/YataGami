package com.yatagami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yatagami.ui.viewmodel.ScanEvent
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    var showTitleDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ScanEvent.PdfSaved -> {
                    successMessage = "PDF berhasil disimpan di:\n${event.path}"
                    showSuccessDialog = true
                }
                is ScanEvent.ImagesSaved -> {
                    successMessage = "${event.count} gambar berhasil disimpan ke Galeri (Pictures/YataGami)!"
                    showSuccessDialog = true
                }
                is ScanEvent.Error -> { /* show toast/error */ }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Halaman (${viewModel.pages.size})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showTitleDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Simpan PDF")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.pages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada halaman. Arahkan kamera ke dokumen!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = viewModel.pages,
                    key = { _, item -> item.id }
                ) { index, page ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { navController.navigate("filter/${page.id}") }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress { change, dragAmount ->
                                    change.consume()
                                    // Simplified reorder trigger
                                }
                            }
                    ) {
                        Box {
                            Image(
                                bitmap = page.getDisplayBitmap().asImageBitmap(),
                                contentDescription = "Halaman ${page.pageNumber}",
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.deletePage(page.id) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = androidx.compose.ui.graphics.Color.Red)
                            }
                            Text(
                                "Halaman ${page.pageNumber}",
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showTitleDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan sebagai PDF")
                        }
                        OutlinedButton(
                            onClick = { viewModel.saveAsImages() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan Semua sebagai JPG ke Galeri")
                        }
                    }
                }
            }
        }
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Judul Dokumen") },
            text = {
                OutlinedTextField(
                    value = viewModel.currentTitle.value,
                    onValueChange = { viewModel.currentTitle.value = it },
                    label = { Text("Judul") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTitleDialog = false
                    viewModel.savePdf()
                }) { Text("Simpan PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Berhasil!") },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) { Text("OK") }
            }
        )
    }
}
