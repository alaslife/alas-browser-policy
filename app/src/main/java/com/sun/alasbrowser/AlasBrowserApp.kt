package com.sun.alasbrowser

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.engine.SiteEnginePolicy
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlasBrowserApp : Application() {
    
    companion object {
        private const val TAG = "AlasBrowserApp"
        
        // Background timeout for forcing WebView recreation (5 seconds)
        private const val BACKGROUND_TIMEOUT_MS = 5_000L
        
        @Volatile
        var isDatabasePrewarmed = false
            private set
        
        @Volatile
        var isAdBlockerPrewarmed = false
            private set
        
        @Volatile
        var isInBackground = false
            
        @Volatile
        var lastBackgroundTime = 0L

        /**
         * Flag to force WebView recreation on next resume.
         * Set when:
         * - App was in background too long
         * - Memory pressure detected
         */
        @Volatile
        var shouldForceRecreateWebView = false
    }
    
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App coming to foreground
            if (isInBackground && lastBackgroundTime > 0) {
                val duration = android.os.SystemClock.elapsedRealtime() - lastBackgroundTime
                if (duration > BACKGROUND_TIMEOUT_MS) {
                    Log.w(TAG, "App was in background for ${duration}ms - forcing WebView recreation")
                    shouldForceRecreateWebView = true
                }
            }
            isInBackground = false
        }
        
        override fun onStop(owner: LifecycleOwner) {
            // App going to background
            isInBackground = true
            lastBackgroundTime = android.os.SystemClock.elapsedRealtime()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        
        SiteEnginePolicy.initialize(this)
        
        // Pre-warm database
        appScope.launch(Dispatchers.IO) {
            try {
                BrowserDatabase.getDatabase(applicationContext)
                isDatabasePrewarmed = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prewarm database", e)
            }
        }
        
        // Pre-warm ad blocker
        appScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)
                val mode = prefs.getString("ad_blocker_mode", "BALANCED") ?: "BALANCED"
                val enabledLists = prefs.getStringSet("enabled_filter_lists", null) ?: setOf("easylist")
                SimpleAdBlocker.initialize(applicationContext, mode, enabledLists)
                com.sun.alasbrowser.web.adblock.AdBlockEngineFactory.initialize(applicationContext)
                isAdBlockerPrewarmed = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prewarm ad blocker", e)
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "TRIM_MEMORY_UI_HIDDEN")
            }
            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Memory pressure while running (level=$level) - forcing WebView recreation")
                shouldForceRecreateWebView = true
            }
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Background memory pressure (level=$level) - forcing WebView recreation")
                shouldForceRecreateWebView = true
            }
        }
    }
}
