package com.yatagami.ui.components.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PageActionToolbar(
    currentPageIdx: Int,
    totalPages: Int,
    onEditCorner: () -> Unit,
    onRotate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Edit Corner (Re-Warp)
        IconButton(onClick = onEditCorner) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Sudut", tint = Color.White)
                Text("Sudut", color = Color.White, fontSize = 10.sp)
            }
        }

        // Rotate 90°
        IconButton(onClick = onRotate) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Refresh, contentDescription = "Rotasi", tint = Color.White)
                Text("Rotasi", color = Color.White, fontSize = 10.sp)
            }
        }

        // Move Left
        IconButton(
            onClick = onMoveLeft,
            enabled = currentPageIdx > 0
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Pindah Kiri",
                    tint = if (currentPageIdx > 0) Color.White else Color.Gray
                )
                Text("Kiri", color = if (currentPageIdx > 0) Color.White else Color.Gray, fontSize = 10.sp)
            }
        }

        // Move Right
        IconButton(
            onClick = onMoveRight,
            enabled = currentPageIdx < totalPages - 1
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Pindah Kanan",
                    tint = if (currentPageIdx < totalPages - 1) Color.White else Color.Gray
                )
                Text("Kanan", color = if (currentPageIdx < totalPages - 1) Color.White else Color.Gray, fontSize = 10.sp)
            }
        }

        // Soft Delete
        IconButton(onClick = onDelete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFFF5252))
                Text("Hapus", color = Color(0xFFFF5252), fontSize = 10.sp)
            }
        }
    }
}
