package com.yatagami.ui.components.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.data.model.ScannedPage

@Composable
fun ThumbnailStrip(
    pages: List<ScannedPage>,
    currentPageIdx: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(pages, key = { _, p -> p.id }) { idx, p ->
            val isSelected = idx == currentPageIdx
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
                    .clickable { onPageSelected(idx) },
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
                    .clickable { onAddPage() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Tambah",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Tambah", color = Color.White, fontSize = 9.sp)
                }
            }
        }
    }
}
