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
                    // Tecno Pova 7 (7000 mAh, 6nm TSMC) has higher thermal dissipation headroom -> throttle only at SEVERE
                    val throttling = (status >= PowerManager.THERMAL_STATUS_SEVERE)
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
        // 8GB RAM LPDDR4X on Tecno Pova 7 provides ample memory headroom
        return getUsedMemoryMB() > 500
    }
}
