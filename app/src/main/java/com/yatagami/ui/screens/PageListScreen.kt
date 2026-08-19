package com.yatagami.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.ui.components.review.BeforeAfterComparisonView
import com.yatagami.ui.components.review.ExportModalBottomSheet
import com.yatagami.ui.components.review.ExportSuccessDialog
import com.yatagami.ui.components.review.FilterModeChips
import com.yatagami.ui.components.review.PageActionToolbar
import com.yatagami.ui.components.review.ThumbnailStrip
import com.yatagami.ui.viewmodel.ScanEvent
import com.yatagami.ui.viewmodel.ScanViewModel
import com.yatagami.utils.ShareHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PageListScreen(navController: NavController, viewModel: ScanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pages = viewModel.pages

    var showExportBottomSheet by remember { mutableStateOf(false) }
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
                    successMessage = "PDF berhasil disimpan di:\n${event.path}"
                    showSuccessDialog = true
                }
                is ScanEvent.ImagesSaved -> {
                    successMessage = "${event.count} gambar berhasil disimpan ke Galeri (Pictures/YataGami)!"
                    showSuccessDialog = true
                }
                is ScanEvent.SharePayloadReady -> {
                    ShareHelper.sharePayload(context, event.payload)
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
                    val titleText = if (pages.isNotEmpty()) "Review (${pagerState.currentPage + 1}/${pages.size})" else "Review Dokumen"
                    Text(titleText, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Toggle Before / After Split Slider
                    IconButton(onClick = { showSplitCompare = !showSplitCompare }) {
                        Text(text = if (showSplitCompare) "🌓" else "🌑", fontSize = 18.sp)
                    }
                    IconButton(onClick = { showHiOsDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Optimasi HiOS")
                    }
                    IconButton(onClick = { showExportBottomSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Ekspor & Bagikan")
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
                        colors = ButtonDefaults.buttonColors(
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
                // 1. Horizontal Carousel Pager (Preview-Res)
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
                                    BeforeAfterComparisonView(
                                        beforeBitmap = beforeBitmap,
                                        afterBitmap = afterBitmap,
                                        splitProgress = splitProgress,
                                        onSplitProgressChange = { splitProgress = it }
                                    )
                                } else {
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

                // 2. Action Toolbar & Filter Chips
                val currentPageIdx = pagerState.currentPage
                val currentPage = pages.getOrNull(currentPageIdx)

                if (currentPage != null) {
                    PageActionToolbar(
                        currentPageIdx = currentPageIdx,
                        totalPages = pages.size,
                        onEditCorner = { navController.navigate("crop/${currentPage.id}") },
                        onRotate = { viewModel.rotatePage(currentPage.id) },
                        onMoveLeft = {
                            if (currentPageIdx > 0) {
                                viewModel.movePage(currentPageIdx, currentPageIdx - 1)
                                scope.launch { pagerState.animateScrollToPage(currentPageIdx - 1) }
                            }
                        },
                        onMoveRight = {
                            if (currentPageIdx < pages.size - 1) {
                                viewModel.movePage(currentPageIdx, currentPageIdx + 1)
                                scope.launch { pagerState.animateScrollToPage(currentPageIdx + 1) }
                            }
                        },
                        onDelete = {
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
                    )

                    FilterModeChips(
                        currentMode = currentPage.filterMode,
                        onFilterSelected = { viewModel.updateFilter(currentPage.id, it) }
                    )
                }

                // 3. Bottom Thumbnail Strip
                ThumbnailStrip(
                    pages = pages,
                    currentPageIdx = pagerState.currentPage,
                    onPageSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                    onAddPage = { navController.popBackStack() },
                    listState = thumbnailListState
                )
            }
        }
    }

    // Modal Bottom Sheet untuk Ekspor & Sharing
    if (showExportBottomSheet) {
        ExportModalBottomSheet(
            pages = pages,
            initialTitle = viewModel.currentTitle.value.ifBlank { viewModel.generateSuggestedTitle() },
            isProcessing = viewModel.isProcessing.value,
            onDismiss = { showExportBottomSheet = false },
            onSavePdf = { selectedIds, compressionTier, title ->
                viewModel.exportPdf(selectedIds, compressionTier, title)
            },
            onSharePdf = { selectedIds, compressionTier, title ->
                viewModel.sharePdf(selectedIds, compressionTier, title)
            },
            onSaveImages = { selectedIds, format, title ->
                viewModel.exportImages(selectedIds, format, title)
            },
            onShareImages = { selectedIds, format, title ->
                viewModel.shareImages(selectedIds, format, title)
            }
        )
    }

    if (showSuccessDialog) {
        ExportSuccessDialog(
            message = successMessage,
            onDismiss = { showSuccessDialog = false }
        )
    }
}
