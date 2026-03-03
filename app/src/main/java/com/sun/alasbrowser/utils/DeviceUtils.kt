package com.sun.alasbrowser.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceUtils {
    private var isLowEndDeviceCached: Boolean? = null

    fun isLowEndDevice(context: Context): Boolean {
        isLowEndDeviceCached?.let { return it }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // 1. Check strict low RAM flag
        if (activityManager.isLowRamDevice) {
            isLowEndDeviceCached = true
            return true
        }

        // 2. Check total RAM < 3.5 GB (safe threshold for "low end" in modern Android)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        
        val isLowEnd = totalRamGb <= 3.5
        isLowEndDeviceCached = isLowEnd
        return isLowEnd
    }
}
