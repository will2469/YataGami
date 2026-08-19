package com.yatagami.ui.components.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
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
import com.yatagami.data.model.ScannedPage

@Composable
fun CameraBottomControls(
    isDocumentStable: Boolean,
    detectionConfidence: Float,
    countdownProgress: Float,
    pages: List<ScannedPage>,
    onShutterClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left spacer for symmetric balance
        Box(modifier = Modifier.size(56.dp))

        // Center: Shutter Button with Circular Countdown Progress Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp)
        ) {
            // Countdown Progress Ring (0f..1f)
            if (countdownProgress > 0f) {
                CircularProgressIndicator(
                    progress = { countdownProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF00E676),
                    strokeWidth = 5.dp
                )
            }

            // Inner Shutter Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .border(3.5.dp, Color.White, CircleShape)
                    .padding(5.dp)
                    .background(
                        color = if (isDocumentStable && detectionConfidence >= 0.75f) Color(0xFF00E676) else Color.White,
                        shape = CircleShape
                    )
                    .clickable { onShutterClick() }
            )
        }

        // Right: Mini Thumbnail with Multi-Page Batch Count Badge
        BadgedBox(
            badge = {
                if (pages.isNotEmpty()) {
                    Badge(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black
                    ) {
                        Text("${pages.size}")
                    }
                }
            }
        ) {
            val lastPage = pages.lastOrNull()
            val thumbnailBitmap = lastPage?.getDisplayBitmap()

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .clickable { onThumbnailClick() },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Preview Halaman",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Halaman",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
