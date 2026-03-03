package com.sun.alasbrowser.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Remote rules service - fetches and caches site compatibility rules from a JSON endpoint
 * 
 * Inspired by:
 * - Firefox Remote Settings (normandy)
 * - Chromium Component Updater
 * 
 * Allows dynamic rule updates without rebuilding the app.
 * Includes local caching with TTL and fallback to built-in rules.
 */
class RemoteRulesService(private val context: Context) {
    private companion object {
        private const val TAG = "RemoteRules"
        private const val REMOTE_RULES_URL = "https://alas-browser.onrender.com/api/compatibility-rules"
        private const val CACHE_FILENAME = "site_compatibility_rules.json"
        private const val CACHE_TTL_HOURS = 24
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 30000
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 3000L
    }

    private val cacheFile = File(context.cacheDir, CACHE_FILENAME)

    /**
     * Fetch rules from remote server with local caching
     * 
     * Strategy:
     * 1. Check if cache exists and is fresh (< 24h)
     * 2. If fresh, return cached rules (fast)
     * 3. If stale/missing, fetch from server in background
     * 4. Fallback to cache (even if stale) if fetch fails
     * 
     * @return JSON string of rules, or null if unavailable
     */
    suspend fun fetchRules(): String? = withContext(Dispatchers.IO) {
        try {
            // Check cache freshness
            if (isCacheFresh()) {
                Log.d(TAG, "✓ Using cached rules (fresh)")
                return@withContext cacheFile.readText()
            }

            // Try to fetch from remote
            Log.d(TAG, "🔄 Fetching remote rules from $REMOTE_RULES_URL")
            val json = fetchFromRemote()
            
            if (json != null) {
                // Cache the result
                cacheFile.writeText(json)
                Log.d(TAG, "✓ Cached remote rules (${json.length} bytes)")
                return@withContext json
            }

            // Fallback to stale cache if available
            if (cacheFile.exists()) {
                Log.w(TAG, "⚠ Using stale cached rules (fetch failed)")
                return@withContext cacheFile.readText()
            }

            Log.w(TAG, "✗ No rules available (no cache, fetch failed)")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rules", e)
            
            // Fallback to cache even if corrupted
            if (cacheFile.exists()) {
                try {
                    return@withContext cacheFile.readText()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error reading cache", e2)
                }
            }
            null
        }
    }

    /**
     * Parse JSON rules into SiteCompatibilityRule objects
     * 
     * Expected JSON format:
     * {
     *   "version": "1.0",
     *   "lastUpdated": "2026-02-08T12:00:00Z",
     *   "rules": [
     *     {
     *       "hostPattern": "liteapks\\.com",
     *       "disableJsInjection": true,
     *       "allowWindowOpen": true,
     *       "allowAggressiveRedirects": true,
     *       "notes": "APK distribution with dropdowns"
     *     }
     *   ]
     * }
     */
    fun parseRulesJson(json: String): List<SiteCompatibilityRule> {
        return try {
            val rules = mutableListOf<SiteCompatibilityRule>()
            
            // Simple JSON parsing (avoid adding okhttp/gson dependency)
            val rulesJson = json.substringAfter("\"rules\":[").substringBefore("]")
            val ruleObjects = rulesJson.split("},{")
            
            for (ruleStr in ruleObjects) {
                try {
                    val rule = parseRuleJson(ruleStr.trim())
                    if (rule != null) {
                        rules.add(rule)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse rule", e)
                }
            }
            
            Log.d(TAG, "✓ Parsed ${rules.size} remote rules")
            rules
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing rules JSON", e)
            emptyList()
        }
    }

    /**
     * Parse a single rule from JSON string
     * Simple regex-based parsing (no library dependency)
     */
    private fun parseRuleJson(ruleStr: String): SiteCompatibilityRule? {
        try {
            val hostPattern = extractJsonString(ruleStr, "hostPattern")
                ?.let { Regex(it) } ?: return null
            
            val disableJsInjection = extractJsonBoolean(ruleStr, "disableJsInjection") ?: false
            val allowWindowOpen = extractJsonBoolean(ruleStr, "allowWindowOpen") ?: false
            val forceDesktopUA = extractJsonBoolean(ruleStr, "forceDesktopUA") ?: false
            val disablePopupBlocking = extractJsonBoolean(ruleStr, "disablePopupBlocking") ?: false
            val allowAggressiveRedirects = extractJsonBoolean(ruleStr, "allowAggressiveRedirects") ?: false
            val notes = extractJsonString(ruleStr, "notes") ?: ""
            
            return SiteCompatibilityRule(
                hostPattern = hostPattern,
                disableJsInjection = disableJsInjection,
                allowWindowOpen = allowWindowOpen,
                forceDesktopUA = forceDesktopUA,
                disablePopupBlocking = disablePopupBlocking,
                allowAggressiveRedirects = allowAggressiveRedirects,
                isRemote = true,  // Mark as remote rule
                notes = notes
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse rule: $ruleStr", e)
            return null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonBoolean(json: String, key: String): Boolean? {
        val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toBoolean()
    }

    private fun isCacheFresh(): Boolean {
        if (!cacheFile.exists()) return false
        
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        val maxAge = TimeUnit.HOURS.toMillis(CACHE_TTL_HOURS.toLong())
        
        return age < maxAge
    }

    private fun fetchFromRemote(): String? {
        for (attempt in 1..MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(REMOTE_RULES_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Remote returned HTTP $responseCode (attempt $attempt/$MAX_RETRIES)")
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS)
                        continue
                    }
                    return null
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }

                if (response.contains("\"rules\"") && response.contains("[") && response.contains("]")) {
                    Log.d(TAG, "✓ Successfully fetched rules from remote (${response.length} bytes)")
                    return response
                } else {
                    Log.w(TAG, "Invalid rules response format")
                    return null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch from remote (attempt $attempt/$MAX_RETRIES): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) {}
                }
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /**
     * Clear cache (e.g., for testing or manual update)
     */
    fun clearCache() {
        if (cacheFile.exists()) {
            cacheFile.delete()
            Log.d(TAG, "✓ Cleared rules cache")
        }
    }

    /**
     * Get cache age in seconds
     */
    fun getCacheAgeSeconds(): Long {
        if (!cacheFile.exists()) return -1
        return (System.currentTimeMillis() - cacheFile.lastModified()) / 1000
    }
}
