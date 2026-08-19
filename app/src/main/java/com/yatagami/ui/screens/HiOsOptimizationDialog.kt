package com.yatagami.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HiOsOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Optimasi HiOS 15 (Tecno Pova 7)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    "Agar HiOS 15 tidak mematikan pemrosesan dokumen di background saat menyusun banyak halaman:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                SetupStepItem(
                    number = "1",
                    title = "Baterai Tanpa Batasan (Unrestricted)",
                    desc = "Buka pengaturan baterai & pilih 'Tanpa Batasan / No Restrictions'."
                )
                Spacer(modifier = Modifier.height(8.dp))

                SetupStepItem(
                    number = "2",
                    title = "Kunci di Recent Apps (Gembok)",
                    desc = "Buka recent apps, tekan tahan / swipe ikon YataGami lalu klik ikon 'Gembok' (Lock)."
                )
                Spacer(modifier = Modifier.height(8.dp))

                SetupStepItem(
                    number = "3",
                    title = "Auto Start Management (Phone Manager)",
                    desc = "Buka aplikasi Phone Manager bawaan Tecno → Auto-start → Berikan izin YataGami."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openBatteryOptimizationSettings(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Buka Pengaturan Baterai")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun SetupStepItem(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        BoxBadge(number)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxBadge(number: String) {
    Column(
        modifier = Modifier
            .size(20.dp)
            .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(number, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
