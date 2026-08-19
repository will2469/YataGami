package com.yatagami.ui.components.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.R
import com.yatagami.data.model.LibraryDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentActionBottomSheet(
    document: LibraryDocument,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

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
        ) {
            // Header: Title & Page Count
            Text(
                text = document.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${document.pageCount} Halaman • ${document.primaryDocType.name} • ${document.formattedFileSize()}",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF2C2C2C))
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Share
            ActionItem(
                icon = Icons.Default.Share,
                title = stringResource(R.string.action_share),
                iconTint = Color(0xFF00E676),
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            // 2. Rename
            ActionItem(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.action_rename),
                iconTint = Color.White,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )

            // 3. Duplicate
            ActionItem(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.action_duplicate),
                iconTint = Color.White,
                onClick = {
                    onDismiss()
                    onDuplicate()
                }
            )

            // 4. Delete
            ActionItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.action_delete),
                iconTint = Color(0xFFFF5252),
                textColor = Color(0xFFFF5252),
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
