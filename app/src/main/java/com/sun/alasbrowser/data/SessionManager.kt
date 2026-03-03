package com.sun.alasbrowser.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages browser session persistence.
 * Handles saving, loading, and clearing session snapshots.
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Save the current browsing session
     */
    private var lastSaveTime = 0L
    private var lastSavedScrollY = 0

    /**
     * Save the current browsing session
     */
    fun saveSession(url: String, scrollY: Int) {
        if (shouldSave(scrollY)) {
            scope.launch {
                performSave(url, scrollY)
            }
        }
    }

    private fun shouldSave(currentScrollY: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - lastSaveTime
        val scrollDelta = kotlin.math.abs(currentScrollY - lastSavedScrollY)

        // Save if:
        // 1. More than 3 seconds has passed (Throttle)
        // 2. Scroll changed significantly (> 1000px) (Responsiveness)
        // 3. Just started (first save)
        return (lastSaveTime == 0L) || (timeDelta > 3000) || (scrollDelta > 1000)
    }

    private fun performSave(url: String, scrollY: Int) {
        try {
            val session = BrowserSession(
                url = url,
                scrollY = scrollY,
                timestamp = System.currentTimeMillis()
            )
            BrowserSession.save(context, session)
            
            lastSaveTime = System.currentTimeMillis()
            lastSavedScrollY = scrollY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }
    
    /**
     * Load the last saved session if it's still valid
     */
    fun loadSession(): BrowserSession? {
        return try {
            val session = BrowserSession.load(context)
            
            // Check if session is valid (not too old)
            if (session != null && session.isValid()) {
                session
            } else {
                clearSession()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session", e)
            null
        }
    }
    
    /**
     * Clear the saved session
     */
    fun clearSession() {
        scope.launch {
            try {
                BrowserSession.clear(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear session", e)
            }
        }
    }
}
