package com.yatagami.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.data.model.FilterMode
import com.yatagami.ui.viewmodel.ScanEvent
import com.yatagami.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val reviewFilterList = listOf(
    "Auto" to FilterMode.AUTO,
    "Normal" to FilterMode.NONE,
    "Magic Color" to FilterMode.MAGIC_COLOR,
    "B&W" to FilterMode.BLACK_WHITE,
    "Grayscale" to FilterMode.GRAYSCALE,
    "Sharpen" to FilterMode.SHARPEN
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PageListScreen(navController: NavController, viewModel: ScanViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pages = viewModel.pages

    var showTitleDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showHiOsDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showSplitCompare by remember { mutableStateOf(false) }
    var splitProgress by remember { mutableFloatStateOf(0.5f) }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val thumbnailListState = rememberLazyListState()

    LaunchedEffect(pagerState.currentPage) {
        if (pages.isNotEmpty()) {
            thumbnailListState.animateScrollToItem(pagerState.currentPage)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ScanEvent.PdfSaved -> {
                    successMessage = "PDF tersimpan di:\n${event.path}"
                    showSuccessDialog = true
                }
                is ScanEvent.ImagesSaved -> {
                    successMessage = "${event.count} gambar berhasil disimpan ke Galeri (Pictures/YataGami)!"
                    showSuccessDialog = true
                }
                is ScanEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (pages.isNotEmpty()) {
                        Text("Review (${pagerState.currentPage + 1}/${pages.size})", fontSize = 18.sp)
                    } else {
                        Text("Review Dokumen", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Split Before/After Toggle
                    IconButton(onClick = { showSplitCompare = !showSplitCompare }) {
                        Text(
                            text = if (showSplitCompare) "🌓" else "🌑",
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = { showHiOsDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Optimasi HiOS")
                    }
                    IconButton(onClick = { showTitleDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Simpan PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        if (showHiOsDialog) {
            HiOsOptimizationDialog(onDismiss = { showHiOsDialog = false })
        }

        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Semua halaman telah dihapus.", color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pindai Dokumen Baru")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 1. Horizontal Carousel Pager
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val page = pages.getOrNull(pageIndex)
                        if (page != null) {
                            val beforeBitmap = page.croppedBitmap ?: page.originalBitmap
                            val afterBitmap = page.processedBitmap ?: beforeBitmap

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showSplitCompare) {
                                    // Interactive Before / After Split Slider
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clipToBounds()
                                    ) {
                                        // After (Enhanced) background
                                        Image(
                                            bitmap = afterBitmap.asImageBitmap(),
                                            contentDescription = "After",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )

                                        // Before (Warped raw) clipped left side
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(splitProgress)
                                                .clipToBounds()
                                        ) {
                                            Image(
                                                bitmap = beforeBitmap.asImageBitmap(),
                                                contentDescription = "Before",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        }

                                        // Split divider line & handle
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(splitProgress)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .width(2.5.dp)
                                                    .fillMaxHeight()
                                                    .background(Color(0xFF00E676))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .size(36.dp)
                                                    .offset(x = 18.dp)
                                                    .background(Color(0xFF00E676), CircleShape)
                                                    .pointerInput(Unit) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            splitProgress = (splitProgress + dragAmount.x / 400f).coerceIn(0.05f, 0.95f)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("↔", color = Color.Black, fontSize = 16.sp)
                                            }
                                        }

                                        // Split labels
                                        Text(
                                            text = "ASLI",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        Text(
                                            text = "ENHANCED",
                                            color = Color(0xFF00E676),
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    // Single Image Display
                                    Image(
                                        bitmap = afterBitmap.asImageBitmap(),
                                        contentDescription = "Halaman ${page.pageNumber}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Per-Page Action Toolbar
                val currentPageIdx = pagerState.currentPage
                val currentPage = pages.getOrNull(currentPageIdx)

                if (currentPage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Corner (Re-Warp)
                        IconButton(onClick = { navController.navigate("crop/${currentPage.id}") }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Sudut", tint = Color.White)
                                Text("Sudut", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        // Rotate 90°
                        IconButton(onClick = { viewModel.rotatePage(currentPage.id) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rotasi", tint = Color.White)
                                Text("Rotasi", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        // Move Left
                        IconButton(
                            onClick = {
                                if (currentPageIdx > 0) {
                                    viewModel.movePage(currentPageIdx, currentPageIdx - 1)
                                    scope.launch { pagerState.animateScrollToPage(currentPageIdx - 1) }
                                }
                            },
                            enabled = currentPageIdx > 0
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Pindah Kiri", tint = if (currentPageIdx > 0) Color.White else Color.Gray)
                                Text("Kiri", color = if (currentPageIdx > 0) Color.White else Color.Gray, fontSize = 10.sp)
                            }
                        }

                        // Move Right
                        IconButton(
                            onClick = {
                                if (currentPageIdx < pages.size - 1) {
                                    viewModel.movePage(currentPageIdx, currentPageIdx + 1)
                                    scope.launch { pagerState.animateScrollToPage(currentPageIdx + 1) }
                                }
                            },
                            enabled = currentPageIdx < pages.size - 1
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Pindah Kanan", tint = if (currentPageIdx < pages.size - 1) Color.White else Color.Gray)
                                Text("Kanan", color = if (currentPageIdx < pages.size - 1) Color.White else Color.Gray, fontSize = 10.sp)
                            }
                        }

                        // Soft Delete with Undo
                        IconButton(
                            onClick = {
                                val deleteResult = viewModel.deletePage(currentPage.id)
                                if (deleteResult != null) {
                                    val (deletedPage, deletedIdx) = deleteResult
                                    scope.launch {
                                        val snackResult = snackbarHostState.showSnackbar(
                                            message = "Halaman ${deletedIdx + 1} dihapus",
                                            actionLabel = "BATALKAN",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (snackResult == SnackbarResult.ActionPerformed) {
                                            viewModel.restorePage(deletedPage, deletedIdx)
                                        }
                                    }
                                }
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFFF5252))
                                Text("Hapus", color = Color(0xFFFF5252), fontSize = 10.sp)
                            }
                        }
                    }

                    // 3. Fast Filter Mode Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reviewFilterList.forEach { (label, mode) ->
                            FilterChip(
                                selected = currentPage.filterMode == mode,
                                onClick = { viewModel.updateFilter(currentPage.id, mode) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E676),
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF2C2C2C),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // 4. Bottom Thumbnail Strip (LazyRow)
                LazyRow(
                    state = thumbnailListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { idx, p ->
                        val isSelected = idx == pagerState.currentPage
                        val thumbBitmap = p.getDisplayBitmap()

                        Box(
                            modifier = Modifier
                                .size(50.dp, 68.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF00E676) else Color.DarkGray,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!thumbBitmap.isRecycled) {
                                Image(
                                    bitmap = thumbBitmap.asImageBitmap(),
                                    contentDescription = "Halaman ${idx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = "${idx + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xCC000000), RoundedCornerShape(topStart = 4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Add more pages button
                    item {
                        Box(
                            modifier = Modifier
                                .size(50.dp, 68.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2C2C2C))
                                .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                .clickable { navController.popBackStack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("Tambah", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // PDF Title Dialog
    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Simpan Dokumen PDF") },
            text = {
                Column {
                    Text("Beri nama file PDF Anda:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.currentTitle.value,
                        onValueChange = { viewModel.currentTitle.value = it },
                        label = { Text("Nama File") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTitleDialog = false
                        viewModel.savePdf()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Simpan PDF (${pages.size} Hal)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Batal") }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Berhasil!") },
            text = { Text(successMessage) },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black
                    )
                ) {
                    Text("OK")
                }
            }
        )
    }
}
