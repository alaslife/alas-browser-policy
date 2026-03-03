package com.sun.alasbrowser.web.adblock

import android.content.Context
import android.util.Log
import com.sun.alasbrowser.web.FilterListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Factory for creating and managing the AdBlockEngine.
 * Handles initialization, lifecycle, and background learning.
 */
object AdBlockEngineFactory {
    
    private const val TAG = "AdBlockEngineFactory"
    
    // Proper coroutine scope with SupervisorJob (not GlobalScope!)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var engine: AdBlockEngine? = null
    
    /**
     * Initialize the ad blocking engine.
     * Should be called once during app startup.
     */
    fun initialize(context: Context) {
        if (engine != null) {
            Log.w(TAG, "Engine already initialized")
            return
        }
        
        Log.d(TAG, "Initializing high-performance ad blocker...")
        
        // Load ad domains from SimpleAdBlocker
        val adDomains = loadAdDomains(context)
        val adPatterns = loadAdPatterns()
        val popupDomains = loadPopupDomains()
        val cdnWhitelist = loadCdnWhitelist()
        val downloadExtensions = loadDownloadExtensions()
        
        // Create modular engines
        val domainBlocker = DomainBlocker(adDomains)
        val patternBlocker = PatternBlocker(adPatterns)
        val downloadGuard = DownloadGuard(cdnWhitelist, downloadExtensions)
        val popupGuard = PopupGuard(popupDomains)
        val learningEngine = LearningEngine()
        
        engine = AdBlockEngine(
            domainBlocker,
            patternBlocker,
            downloadGuard,
            popupGuard,
            learningEngine
        )
        
        // Start background learning processor
        startLearningProcessor(learningEngine)
        
        Log.d(TAG, "Ad blocker initialized with ${adDomains.size} domains, ${adPatterns.size} patterns")
    }
    
    /**
     * Get the initialized engine.
     */
    fun getEngine(): AdBlockEngine {
        return engine ?: throw IllegalStateException("AdBlockEngine not initialized. Call initialize() first.")
    }
    
    /**
     * Shutdown the engine and cancel background tasks.
     * Should be called when app is destroyed.
     */
    fun shutdown() {
        scope.cancel()
        engine = null
        Log.d(TAG, "Ad blocker shutdown")
    }
    
    /**
     * Background processor for learning engine.
     * Runs every 60 seconds to process learned domains.
     */
    private fun startLearningProcessor(learningEngine: LearningEngine) {
        scope.launch {
            while (isActive) {
                delay(60_000) // 60 seconds
                
                val learnedDomains = learningEngine.drain()
                if (learnedDomains.isNotEmpty()) {
                    Log.d(TAG, "Learned ${learnedDomains.size} new ad domains")
                    // TODO: Persist learned domains for future sessions
                }
            }
        }
    }
    
    private fun loadAdDomains(context: Context? = null): Set<String> {
        val domains = mutableSetOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
            "outbrain.com", "taboola.com", "advertising.com", "adnxs.com",
            "exdynsrv.com", "tsyndicate.com", "monetag.com", "clickadu.com",
            "adsterra.com", "hilltopads.com", "propellerads.com", "popcash.net",
            "juicyads.com", "exoclick.com", "trafficjunky.com", "admaven.com"
        )
        
        if (context != null) {
            val hostsListIds = listOf("stevenblack-hosts", "adaway-hosts")
            for (id in hostsListIds) {
                try {
                    val content = FilterListManager.loadCachedFilter(context, id)
                    if (content != null) {
                        parseHostsFile(content, domains)
                        Log.d(TAG, "Loaded hosts file: $id (${domains.size} total domains)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load hosts file $id", e)
                }
            }
        }
        
        return domains
    }

    private fun parseHostsFile(content: String, domains: MutableSet<String>) {
        content.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                    val domain = parts[1].trim()
                    if (domain != "localhost" && domain != "local" && 
                        domain != "broadcasthost" && domain.contains(".")) {
                        domains.add(domain)
                    }
                }
            }
    }
    
    private fun loadAdPatterns(): Set<String> = setOf(
        "/pagead/", "/ads?", "/banner", "banner.php", "banner.js",
        "/redirect.php", "/redir.php", "/out.php", "/go.php",
        "adserver", "ad-server", "/adframe", "/popup", "/popunder"
    )
    
    private fun loadPopupDomains(): Set<String> = setOf(
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "trafficjunky.net", "adsterra.com", "clickadu.com"
    )
    
    private fun loadCdnWhitelist(): Set<String> = setOf(
        "liteapks.com", "apkpure.com", "apkmirror.com", "cloudflare.com",
        "akamai.net", "fastly.net", "jsdelivr.net", "github.com",
        "drive.google.com", "dropbox.com", "mega.nz",
        "9mod.cloud", "9mod.space", "cloud.9mod.space"
    )
    
    private fun loadDownloadExtensions(): Set<String> = setOf(
        ".apk", ".xapk", ".zip", ".rar", ".7z", ".exe", ".msi",
        ".pdf", ".mp3", ".mp4", ".avi", ".mkv"
    )
}
