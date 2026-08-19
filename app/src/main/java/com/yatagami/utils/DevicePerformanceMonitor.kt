package com.yatagami.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

object DevicePerformanceMonitor {
    private const val TAG = "PerformanceMonitor"
    
    val isThermalThrottling = mutableStateOf(false)
    val thermalStatus = mutableIntStateOf(0)
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.addThermalStatusListener { status ->
                    thermalStatus.intValue = status
                    val throttling = (status >= PowerManager.THERMAL_STATUS_MODERATE)
                    isThermalThrottling.value = throttling
                    Log.i(TAG, "Thermal status updated: $status (throttling: $throttling)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register thermal status listener", e)
            }
        }
    }

    fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    fun isUnderMemoryPressure(): Boolean {
        return getUsedMemoryMB() > 220
    }
}
