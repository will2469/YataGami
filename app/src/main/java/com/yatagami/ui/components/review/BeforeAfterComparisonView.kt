package com.yatagami.ui.components.review

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun BeforeAfterComparisonView(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    splitProgress: Float,
    onSplitProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
                            onSplitProgressChange(
                                (splitProgress + dragAmount.x / 400f).coerceIn(0.05f, 0.95f)
                            )
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
}
