package com.sun.alasbrowser.utils

import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks crashes per tab to detect crash loops
 */
data class CrashEvent(
    val tabId: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val crashCount: Int = 1
)

class CrashTracker {
    private val crashHistory = ConcurrentHashMap<String, MutableList<CrashEvent>>()
    private val CRASH_LOOP_THRESHOLD = 3              // 3 crashes
    private val CRASH_LOOP_WINDOW_MS = 30_000L       // In 30 seconds
    
    /**
     * Record a crash for a tab
     */
    fun recordCrash(tabId: String, url: String): Boolean {
        val now = System.currentTimeMillis()
        val events = crashHistory.getOrPut(tabId) { mutableListOf() }
        
        // Add new crash event
        events.add(CrashEvent(tabId, url, now))
        
        // Remove events older than window
        events.removeAll { (now - it.timestamp) > CRASH_LOOP_WINDOW_MS }
        
        // Return true if crash loop detected
        return events.size >= CRASH_LOOP_THRESHOLD
    }
    
    /**
     * Get crash count for a tab within the window
     */
    fun getCrashCount(tabId: String): Int {
        val events = crashHistory[tabId] ?: return 0
        val now = System.currentTimeMillis()
        return events.count { (now - it.timestamp) <= CRASH_LOOP_WINDOW_MS }
    }
    
    /**
     * Check if tab is in crash loop
     */
    fun isInCrashLoop(tabId: String): Boolean {
        return getCrashCount(tabId) >= CRASH_LOOP_THRESHOLD
    }
    
    /**
     * Clear crash history for a tab
     */
    fun clearCrashes(tabId: String) {
        crashHistory.remove(tabId)
    }
    
    /**
     * Clear all crash history
     */
    fun clearAll() {
        crashHistory.clear()
    }
    
    /**
     * Get last crash URL for a tab
     */
    fun getLastCrashUrl(tabId: String): String? {
        return crashHistory[tabId]?.lastOrNull()?.url
    }
    
    /**
     * Get all crash events for a tab (for logging)
     */
    fun getCrashEvents(tabId: String): List<CrashEvent> {
        val events = crashHistory[tabId] ?: return emptyList()
        val now = System.currentTimeMillis()
        return events.filter { (now - it.timestamp) <= CRASH_LOOP_WINDOW_MS }
    }
}
