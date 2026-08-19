package com.yatagami.ui.components.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.data.model.DocumentType
import com.yatagami.ui.components.TorchMode

val docPresetsList = listOf(
    "Auto" to null,
    "A4" to DocumentType.A4,
    "KTP / ID" to DocumentType.KTP,
    "F4 / Folio" to DocumentType.F4,
    "Struk" to DocumentType.RECEIPT,
    "Foto" to DocumentType.SQUARE
)

@Composable
fun CameraTopControls(
    torchMode: TorchMode,
    onTorchToggle: () -> Unit,
    showAlignmentGuide: Boolean,
    onGuideToggle: () -> Unit,
    isDocumentStable: Boolean,
    detectionConfidence: Float,
    autoCaptureEnabled: Boolean,
    onAutoCaptureToggle: () -> Unit,
    selectedPreset: DocumentType?,
    onPresetSelected: (DocumentType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
    ) {
        // Top Bar: Torch, Grid Guide, Status Badge, Auto-Shutter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Torch Toggle Button
            IconButton(
                onClick = onTorchToggle,
                modifier = Modifier
                    .background(Color(0x99000000), CircleShape)
                    .size(40.dp)
            ) {
                Text(
                    text = when (torchMode) {
                        TorchMode.OFF -> "🚫🔦"
                        TorchMode.AUTO -> "⚡🔦"
                        TorchMode.ON -> "🔦"
                    },
                    fontSize = 16.sp
                )
            }

            // Alignment Guide Toggle
            IconButton(
                onClick = onGuideToggle,
                modifier = Modifier
                    .background(
                        if (showAlignmentGuide) Color(0xCC2E7D32) else Color(0x99000000),
                        CircleShape
                    )
                    .size(40.dp)
            ) {
                Text("📐", fontSize = 16.sp)
            }

            // Real-time Detection Status Badge
            Row(
                modifier = Modifier
                    .background(
                        color = if (isDocumentStable && detectionConfidence >= 0.75f) Color(0xCC00E676)
                        else if (detectionConfidence >= 0.40f) Color(0xCCFFD600)
                        else Color(0x99000000),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isDocumentStable && detectionConfidence >= 0.75f) Color.White else Color.Black,
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (isDocumentStable && detectionConfidence >= 0.75f) " Siap"
                    else if (detectionConfidence >= 0.40f) " Menyesuaikan..."
                    else " Arahkan Dokumen",
                    color = if (isDocumentStable && detectionConfidence >= 0.75f) Color.Black else Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Auto Capture Toggle Chip
            FilterChip(
                selected = autoCaptureEnabled,
                onClick = onAutoCaptureToggle,
                label = { Text(if (autoCaptureEnabled) "Auto ON" else "Auto OFF", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xCC2E7D32),
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Document Type Preset Quick Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            docPresetsList.forEach { (label, presetType) ->
                val isSelected = selectedPreset == presetType
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFF00E676) else Color(0x99000000),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onPresetSelected(presetType) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
