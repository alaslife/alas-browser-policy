package com.sun.alasbrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Represents a browser session snapshot.
 * Used to restore the user's browsing state after process death or backgrounding.
 */
data class BrowserSession(
    val url: String,
    val scrollY: Int,
    val timestamp: Long,
    val version: Int = CURRENT_VERSION
) {
    companion object {
        const val CURRENT_VERSION = 1
        private const val PREFS_NAME = "browser_session"
        private const val KEY_URL = "session_url"
        private const val KEY_SCROLL_Y = "session_scroll_y"
        private const val KEY_TIMESTAMP = "session_timestamp"
        private const val KEY_VERSION = "session_version"
        
        /**
         * Maximum age of a session before it's considered stale (10 minutes)
         */
        const val MAX_SESSION_AGE_MS = 10 * 60 * 1000L
        
        /**
         * Load the saved session from SharedPreferences
         */
        fun load(context: Context): BrowserSession? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val url = prefs.getString(KEY_URL, null) ?: return null
            val scrollY = prefs.getInt(KEY_SCROLL_Y, 0)
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
            val version = prefs.getInt(KEY_VERSION, 1)
            
            // Return null if URL is empty or invalid
            if (url.isEmpty()) return null
            
            return BrowserSession(url, scrollY, timestamp, version)
        }
        
        /**
         * Save the session to SharedPreferences
         */
        fun save(context: Context, session: BrowserSession) {
            // Guard: Don't save invalid URLs
            if (!session.url.startsWith("http")) {
                return
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_URL, session.url)
                putInt(KEY_SCROLL_Y, session.scrollY)
                putLong(KEY_TIMESTAMP, session.timestamp)
                putInt(KEY_VERSION, session.version)
            }
        }
        
        /**
         * Clear the saved session
         */
        fun clear(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                clear()
            }
        }
    }
    
    /**
     * Check if this session is still valid based on age
     */
    fun isValid(): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age < MAX_SESSION_AGE_MS
    }
}
