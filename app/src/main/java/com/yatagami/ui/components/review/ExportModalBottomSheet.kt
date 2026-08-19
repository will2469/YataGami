package com.yatagami.ui.components.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.R
import com.yatagami.data.model.ImageExportFormat
import com.yatagami.data.model.PdfCompressionTier
import com.yatagami.data.model.ScannedPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModalBottomSheet(
    pages: List<ScannedPage>,
    initialTitle: String,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSavePdf: (selectedPageIds: Set<String>?, compressionTier: PdfCompressionTier, title: String) -> Unit,
    onSharePdf: (selectedPageIds: Set<String>?, compressionTier: PdfCompressionTier, title: String) -> Unit,
    onSaveImages: (selectedPageIds: Set<String>?, format: ImageExportFormat, title: String) -> Unit,
    onShareImages: (selectedPageIds: Set<String>?, format: ImageExportFormat, title: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = PDF, 1 = Gambar
    var documentTitle by remember { mutableStateOf(initialTitle) }
    var selectedCompressionTier by remember { mutableStateOf(PdfCompressionTier.STANDARD) }
    var selectedImageFormat by remember { mutableStateOf(ImageExportFormat.JPG_90) }

    var isAllPagesSelected by remember { mutableStateOf(true) }
    val selectedPageIds = remember { mutableStateOf(pages.map { it.id }.toMutableSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = stringResource(R.string.export_dialog_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs: PDF vs Gambar
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF2C2C2C),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF00E676)
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            stringResource(R.string.export_tab_pdf),
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            stringResource(R.string.export_tab_images),
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. File Name Field
            OutlinedTextField(
                value = documentTitle,
                onValueChange = { documentTitle = it },
                label = { Text(stringResource(R.string.export_filename_label), color = Color.LightGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E676)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Selective Page Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.export_pages_section),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray
                )
                Row {
                    TextButton(onClick = {
                        isAllPagesSelected = true
                        selectedPageIds.value = pages.map { it.id }.toMutableSet()
                    }) {
                        Text(
                            "Semua",
                            color = if (isAllPagesSelected) Color(0xFF00E676) else Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { isAllPagesSelected = false }) {
                        Text(
                            "Pilih (${selectedPageIds.value.size}/${pages.size})",
                            color = if (!isAllPagesSelected) Color(0xFF00E676) else Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (!isAllPagesSelected) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        val isSelected = selectedPageIds.value.contains(page.id)
                        val bmp = page.getDisplayBitmap()

                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 92.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF00E676) else Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val currentSet = selectedPageIds.value.toMutableSet()
                                    if (isSelected) {
                                        currentSet.remove(page.id)
                                    } else {
                                        currentSet.add(page.id)
                                    }
                                    selectedPageIds.value = currentSet
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${page.pageNumber}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )

                            // Page Number Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("${page.pageNumber}", color = Color.White, fontSize = 10.sp)
                            }

                            // Selection Checkmark
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .background(Color(0xFF00E676), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Format Specific Options
            if (selectedTabIndex == 0) {
                // PDF Compression Tier
                Text(
                    text = stringResource(R.string.export_compression_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                PdfCompressionTier.entries.forEach { tier ->
                    val isSelected = selectedCompressionTier == tier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF2A3830) else Color(0xFF252525))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00E676) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedCompressionTier = tier }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedCompressionTier = tier },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00E676),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = stringResource(tier.titleRes),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(tier.descRes),
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            } else {
                // Image Format Selector
                Text(
                    text = stringResource(R.string.export_format_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                ImageExportFormat.entries.forEach { fmt ->
                    val isSelected = selectedImageFormat == fmt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF2A3830) else Color(0xFF252525))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00E676) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedImageFormat = fmt }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedImageFormat = fmt },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00E676),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(fmt.titleRes),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Action Buttons (Save & Share)
            val effectivePageIds = if (isAllPagesSelected) null else selectedPageIds.value
            val isButtonEnabled = !isProcessing && (isAllPagesSelected || selectedPageIds.value.isNotEmpty())

            if (isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00E676),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.export_processing),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tombol Simpan
                OutlinedButton(
                    onClick = {
                        if (selectedTabIndex == 0) {
                            onSavePdf(effectivePageIds, selectedCompressionTier, documentTitle)
                        } else {
                            onSaveImages(effectivePageIds, selectedImageFormat, documentTitle)
                        }
                        onDismiss()
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, if (isButtonEnabled) Color(0xFF00E676) else Color.DarkGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676))
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.export_action_save),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Tombol Bagikan
                Button(
                    onClick = {
                        if (selectedTabIndex == 0) {
                            onSharePdf(effectivePageIds, selectedCompressionTier, documentTitle)
                        } else {
                            onShareImages(effectivePageIds, selectedImageFormat, documentTitle)
                        }
                        onDismiss()
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black,
                        disabledContainerColor = Color.DarkGray,
                        disabledContentColor = Color.LightGray
                    )
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.export_action_share),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
