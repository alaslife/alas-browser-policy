package com.sun.alasbrowser.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.net.URI
import java.net.URISyntaxException

private const val TAG = "SiteEngine"

enum class EngineType {
    GECKO_VIEW,
    WEB_VIEW
}

enum class EnginePreference {
    DEFAULT,
    WEB_VIEW,
    GECKO_VIEW
}

object SiteEnginePolicy {
    private const val PREFS_NAME = "site_engine_prefs"
    private const val KEY_DEFAULT_ENGINE = "default_engine_v3"
    private const val KEY_WEBVIEW_SITES = "webview_sites"
    private const val KEY_GECKO_SITES = "gecko_sites"
    private const val KEY_DISMISSED_HINTS = "dismissed_hints"
    
    private lateinit var prefs: SharedPreferences
    
    var defaultEngine: EngineType = EngineType.WEB_VIEW
        private set
    
    private val siteEngines = mutableMapOf<String, EnginePreference>()
    private val dismissedHints = mutableSetOf<String>()
    
    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Force default to WebView
        defaultEngine = try {
            val savedEngine = prefs.getString(KEY_DEFAULT_ENGINE, EngineType.WEB_VIEW.name)
            EngineType.valueOf(savedEngine ?: EngineType.WEB_VIEW.name)
        } catch (e: IllegalArgumentException) {
            EngineType.WEB_VIEW
        } catch (e: Exception) {
            EngineType.WEB_VIEW
        }
        
        loadSitePreferences()
        loadDismissedHints()
        
        Log.d(TAG, "Loaded ${siteEngines.size} site engine prefs (default=$defaultEngine), ${dismissedHints.size} dismissed hints")
    }
    
    private fun loadSitePreferences() {
        siteEngines.clear()
        
        val webViewSites = prefs.getStringSet(KEY_WEBVIEW_SITES, emptySet()) ?: emptySet()
        webViewSites.forEach { siteEngines[it] = EnginePreference.WEB_VIEW }
        
        val geckoSites = prefs.getStringSet(KEY_GECKO_SITES, emptySet()) ?: emptySet()
        geckoSites.forEach { siteEngines[it] = EnginePreference.GECKO_VIEW }
    }
    
    private fun loadDismissedHints() {
        dismissedHints.clear()
        dismissedHints.addAll(prefs.getStringSet(KEY_DISMISSED_HINTS, emptySet()) ?: emptySet())
    }
    
    fun getPreferenceForSite(hostname: String): EnginePreference {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return EnginePreference.DEFAULT
        }
        
        val host = hostname.lowercase()
        return siteEngines.entries
            .firstOrNull { host.endsWith(it.key) }
            ?.value ?: EnginePreference.DEFAULT
    }
    
    fun getEngineForSite(hostname: String): EngineType {
        return when (getPreferenceForSite(hostname)) {
            EnginePreference.WEB_VIEW -> EngineType.WEB_VIEW
            EnginePreference.GECKO_VIEW -> EngineType.GECKO_VIEW
            EnginePreference.DEFAULT -> defaultEngine
        }
    }
    
    fun getEngineForUrl(url: String): EngineType {
        val hostname = extractHostname(url) ?: return defaultEngine
        return getEngineForSite(hostname)
    }
    
    fun getEngineForTab(url: String, isPrivate: Boolean): EngineType {
        if (isPrivate) return defaultEngine
        return getEngineForUrl(url)
    }
    
    fun setEngineForSite(hostname: String, engine: EngineType) {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return
        }
        
        val host = normalizeHostname(hostname) ?: return
        val pref = when (engine) {
            EngineType.WEB_VIEW -> EnginePreference.WEB_VIEW
            EngineType.GECKO_VIEW -> EnginePreference.GECKO_VIEW
        }
        
        siteEngines[host] = pref
        persistSiteEngines()
        Log.d(TAG, "Engine for $host set to: $engine")
    }
    
    fun resetSiteToDefault(hostname: String) {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return
        }
        
        val host = normalizeHostname(hostname) ?: return
        siteEngines.remove(host)
        persistSiteEngines()
        Log.d(TAG, "Reset $host to default engine")
    }
    
    fun setDefaultEngine(engine: EngineType) {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return
        }
        
        defaultEngine = engine
        prefs.edit { putString(KEY_DEFAULT_ENGINE, engine.name) }
        Log.d(TAG, "Default engine set to: $engine")
    }
    
    fun isUsingWebView(hostname: String): Boolean {
        return getEngineForSite(hostname) == EngineType.WEB_VIEW
    }
    
    fun getAllWebViewSites(): Set<String> {
        return siteEngines
            .filter { it.value == EnginePreference.WEB_VIEW }
            .keys
            .toSet()
    }
    
    fun getAllGeckoSites(): Set<String> {
        return siteEngines
            .filter { it.value == EnginePreference.GECKO_VIEW }
            .keys
            .toSet()
    }
    
    fun getAllCustomSites(): Map<String, EnginePreference> = siteEngines.toMap()
    
    fun clearAll() {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return
        }
        
        siteEngines.clear()
        dismissedHints.clear()
        
        prefs.edit {
            putStringSet(KEY_WEBVIEW_SITES, emptySet())
            putStringSet(KEY_GECKO_SITES, emptySet())
            putStringSet(KEY_DISMISSED_HINTS, emptySet())
        }
        
        Log.d(TAG, "Cleared all engine preferences")
    }
    
    fun shouldShowHint(hostname: String): Boolean {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return false
        }
        
        val host = normalizeHostname(hostname) ?: return false
        if (dismissedHints.contains(host)) return false
        if (getPreferenceForSite(host) != EnginePreference.DEFAULT) return false
        return true
    }
    
    fun dismissHint(hostname: String) {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "SiteEnginePolicy not initialized!")
            return
        }
        
        val host = normalizeHostname(hostname) ?: return
        dismissedHints.add(host)
        prefs.edit { putStringSet(KEY_DISMISSED_HINTS, dismissedHints.toSet()) }
        Log.d(TAG, "Dismissed hint for $host")
    }
    
    @Deprecated("Use dismissHint() instead", ReplaceWith("dismissHint(hostname)"))
    fun dismissHintForever(hostname: String) {
        dismissHint(hostname)
    }
    
    private fun persistSiteEngines() {
        val webViewSet = siteEngines
            .filter { it.value == EnginePreference.WEB_VIEW }
            .keys
            .toSet()
        
        val geckoSet = siteEngines
            .filter { it.value == EnginePreference.GECKO_VIEW }
            .keys
            .toSet()
        
        prefs.edit {
            putStringSet(KEY_WEBVIEW_SITES, webViewSet)
            putStringSet(KEY_GECKO_SITES, geckoSet)
        }
    }
    
    fun extractHostname(url: String): String? {
        if (url.isBlank()) return null
        
        return try {
            // Handle malformed URLs
            val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "http://$url"
            }
            
            val uri = URI(cleanUrl)
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (e: URISyntaxException) {
            // Fallback to manual parsing for invalid URIs
            try {
                url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .split("/", "?", "#").firstOrNull()
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() }
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun normalizeHostname(hostname: String): String? {
        if (hostname.isBlank()) return null
        
        return hostname.lowercase()
            .removePrefix("www.")
            .removePrefix("http://")
            .removePrefix("https://")
            .split("/", "?", "#").firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
    
    fun isInitialized(): Boolean = ::prefs.isInitialized
    
    fun reset() {
        if (::prefs.isInitialized) {
            clearAll()
            setDefaultEngine(EngineType.WEB_VIEW)
        }
    }
}