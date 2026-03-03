package com.sun.alasbrowser.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Site Compatibility Manager - Coordinates rule syncing and issue reporting
 * 
 * Singleton that manages:
 * - Remote rule fetching and caching
 * - Issue detection and reporting
 * - Background synchronization
 * 
 * Usage:
 * ```
 * // Initialize on app startup
 * SiteCompatibilityManager.init(context)
 * await SiteCompatibilityManager.syncRules()
 * 
 * // During navigation
 * SiteCompatibilityManager.analyzer.recordDropdownFailure(url, details)
 * ```
 */
object SiteCompatibilityManager {
    private const val TAG = "SiteCompatManager"
    
    private var remoteRulesService: RemoteRulesService? = null
    val analyzer = BrokenSiteAnalyzer()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isSyncing = false

    /**
     * Initialize the manager
     * Call once on app startup
     */
    fun init(context: Context) {
        if (remoteRulesService != null) return
        
        remoteRulesService = RemoteRulesService(context)
        Log.d(TAG, "✓ Initialized Site Compatibility Manager")
        
        // Start background sync
        startBackgroundSync()
    }

    /**
     * Manually sync rules from remote server
     * Safe to call multiple times (debounced)
     */
    suspend fun syncRules() {
        if (isSyncing) {
            Log.d(TAG, "⏭ Already syncing, skipping...")
            return
        }

        isSyncing = true
        try {
            val rulesService = remoteRulesService ?: return
            
            Log.d(TAG, "🔄 Syncing rules from remote...")
            val json = rulesService.fetchRules()
            
            if (json != null) {
                val remoteRules = rulesService.parseRulesJson(json)
                SiteCompatibilityRegistry.updateRemoteRules(remoteRules)
                Log.d(TAG, "✓ Synced ${remoteRules.size} rules from remote")
            } else {
                Log.w(TAG, "⚠ No rules received from remote")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing rules", e)
        } finally {
            isSyncing = false
        }
    }

    /**
     * Background sync task
     * Runs every 12 hours in background
     */
    private fun startBackgroundSync() {
        scope.launch {
            // Wait 1 minute on startup before first sync (let UI settle)
            delay(60000)
            
            while (true) {
                try {
                    syncRules()
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed", e)
                }
                
                // Wait 12 hours before next sync
                delay(43200000)  // 12 * 60 * 60 * 1000
            }
        }
    }

    /**
     * Report broken sites to server
     * Happens periodically or when app goes to background
     */
    suspend fun reportBrokenSites() {
        try {
            analyzer.reportIssues { newRules ->
                SiteCompatibilityRegistry.updateRemoteRules(newRules)
                Log.d(TAG, "✓ Received ${newRules.size} auto-generated rules from server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report issues", e)
        }
    }

    /**
     * Get all active rules (for debugging/UI)
     */
    fun getAllRules(): List<SiteCompatibilityRule> {
        return SiteCompatibilityRegistry.getAllRules()
    }

    /**
     * Get cache age in seconds
     * Returns -1 if no cache
     */
    fun getCacheAgeSeconds(): Long {
        return remoteRulesService?.getCacheAgeSeconds() ?: -1
    }

    /**
     * Clear all caches and issues (for testing)
     */
    fun clearAll() {
        remoteRulesService?.clearCache()
        analyzer.clear()
        Log.d(TAG, "✓ Cleared all caches")
    }

    /**
     * Get sync status
     */
    fun isSyncInProgress(): Boolean = isSyncing
}
