package com.yatagami.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.R
import com.yatagami.data.model.DateCategory
import com.yatagami.data.model.DocumentType
import com.yatagami.data.model.LibraryDocument
import com.yatagami.ui.components.library.DocumentActionBottomSheet
import com.yatagami.ui.components.library.RenameDocumentDialog
import com.yatagami.ui.viewmodel.LibraryEvent
import com.yatagami.ui.viewmodel.LibraryViewModel
import com.yatagami.ui.viewmodel.ScanViewModel
import com.yatagami.utils.ThumbnailManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    libraryViewModel: LibraryViewModel,
    scanViewModel: ScanViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    val selectedTag by libraryViewModel.selectedCategoryTag.collectAsState()
    val isGridView by libraryViewModel.isGridView.collectAsState()
    val groupedDocs by libraryViewModel.groupedDocuments.collectAsState()
    val totalCount by libraryViewModel.totalDocumentCount.collectAsState()
    val isDuplicating by libraryViewModel.isDuplicating.collectAsState()

    var activeDocForMenu by remember { mutableStateOf<LibraryDocument?>(null) }
    var activeDocForRename by remember { mutableStateOf<LibraryDocument?>(null) }

    // Android 15 Photo Picker (max 20 items capped)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scanViewModel.clearPages()
            scanViewModel.importImagesFromUris(context, uris)
            navController.navigate("pages")
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.events.collectLatest { event ->
            when (event) {
                is LibraryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is LibraryEvent.ShowError -> snackbarHostState.showSnackbar("Error: ${event.error}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.library_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.library_item_count, totalCount),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { libraryViewModel.onToggleGridView() }) {
                        Text(
                            text = if (isGridView) "☰" else "☵",
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Import Button
                FloatingActionButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = Color(0xFF2C2C2C),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text("🖼️", fontSize = 18.sp)
                }

                // Camera Scan FAB
                ExtendedFloatingActionButton(
                    onClick = {
                        scanViewModel.clearPages()
                        navController.navigate("camera")
                    },
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_scan_cta), fontWeight = FontWeight.Bold) }
                )
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { libraryViewModel.onSearchQueryChange(it) },
                placeholder = { Text(stringResource(R.string.library_search_placeholder), color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { libraryViewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color(0xFF2C2C2C),
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E676)
                )
            )

            // 2. Category Tag Filter Chips
            CategoryChipsRow(
                selectedTag = selectedTag,
                onTagSelected = { libraryViewModel.onCategoryTagSelected(it) }
            )

            // 3. Progress indicator for duplication
            if (isDuplicating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E676), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_duplicating), fontSize = 12.sp, color = Color.LightGray)
                }
            }

            // 4. Main Body: Empty State vs Documents List/Grid
            if (groupedDocs.isEmpty()) {
                EmptyLibraryView(
                    onScanClick = {
                        scanViewModel.clearPages()
                        navController.navigate("camera")
                    },
                    onImportClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                if (isGridView) {
                    // Grid View
                    val allItems = groupedDocs.flatMap { it.value }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(allItems, key = { it.id }) { doc ->
                            DocumentGridCard(
                                document = doc,
                                onClick = {
                                    libraryViewModel.shareDocument(context, doc)
                                },
                                onMenuClick = { activeDocForMenu = doc }
                            )
                        }
                    }
                } else {
                    // List View with Sticky/Clear Date Headers
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedDocs.forEach { (dateCategory, docs) ->
                            item(key = "header_${dateCategory.name}") {
                                Text(
                                    text = stringResource(dateCategory.titleRes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }
                            items(docs, key = { it.id }) { doc ->
                                DocumentListCard(
                                    document = doc,
                                    onClick = {
                                        libraryViewModel.shareDocument(context, doc)
                                    },
                                    onMenuClick = { activeDocForMenu = doc }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Context Action Bottom Sheet
    activeDocForMenu?.let { doc ->
        DocumentActionBottomSheet(
            document = doc,
            onDismiss = { activeDocForMenu = null },
            onShare = { libraryViewModel.shareDocument(context, doc) },
            onRename = { activeDocForRename = doc },
            onDuplicate = { libraryViewModel.duplicateDocument(doc.id) },
            onDelete = {
                libraryViewModel.deleteDocumentWithUndo(doc) { undoAction ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "${doc.title} dihapus",
                            actionLabel = "BATALKAN",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            undoAction()
                        }
                    }
                }
            }
        )
    }

    // Rename Dialog
    activeDocForRename?.let { doc ->
        RenameDocumentDialog(
            initialTitle = doc.title,
            onConfirm = { newTitle ->
                libraryViewModel.renameDocument(doc.id, newTitle)
                activeDocForRename = null
            },
            onDismiss = { activeDocForRename = null }
        )
    }
}

@Composable
private fun CategoryChipsRow(
    selectedTag: DocumentType?,
    onTagSelected: (DocumentType?) -> Unit
) {
    val allLabel = stringResource(R.string.tag_all)
    val ktpLabel = stringResource(R.string.tag_ktp)
    val a4Label = stringResource(R.string.tag_a4)
    val f4Label = stringResource(R.string.tag_f4)
    val receiptLabel = stringResource(R.string.tag_receipt)
    val generalLabel = stringResource(R.string.tag_general)

    val tags: List<Pair<DocumentType?, String>> = remember(allLabel, ktpLabel, a4Label, f4Label, receiptLabel, generalLabel) {
        listOf(
            null to allLabel,
            DocumentType.KTP to ktpLabel,
            DocumentType.A4 to a4Label,
            DocumentType.F4 to f4Label,
            DocumentType.RECEIPT to receiptLabel,
            DocumentType.SQUARE to generalLabel
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        items(tags) { (type, label) ->
            val isSelected = selectedTag == type
            FilterChip(
                selected = isSelected,
                onClick = { onTagSelected(type) },
                label = { Text(text = label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E676),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF1E1E1E),
                    labelColor = Color.LightGray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color(0xFF2C2C2C),
                    selectedBorderColor = Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun DocumentListCard(
    document: LibraryDocument,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(document.updatedAt) { dateFormat.format(Date(document.updatedAt)) }
    val thumbnailBmp = remember(document.thumbnailPath) { ThumbnailManager.getThumbnailBitmap(document.thumbnailPath) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBmp != null && !thumbnailBmp.isRecycled) {
                Image(
                    bitmap = thumbnailBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("📄", fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Document Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedDate,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Page Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2A3830), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.library_pages_badge, document.pageCount),
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // DocType Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = document.primaryDocType.name,
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 3-Dots Menu
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Gray)
        }
    }
}

@Composable
private fun DocumentGridCard(
    document: LibraryDocument,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val thumbnailBmp = remember(document.thumbnailPath) { ThumbnailManager.getThumbnailBitmap(document.thumbnailPath) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Thumbnail Cover
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBmp != null && !thumbnailBmp.isRecycled) {
                Image(
                    bitmap = thumbnailBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("📄", fontSize = 32.sp)
            }

            // Page count badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("${document.pageCount} Hal", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = document.primaryDocType.name,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📑", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.library_empty_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.library_empty_desc),
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.library_scan_cta), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🖼️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.library_import_gallery))
                }
            }
        }
    }
}
