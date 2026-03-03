package com.sun.alasbrowser.web.adblock

import android.util.Log

/**
 * Main ad blocking engine - coordinates all blocking strategies.
 * High-performance, request-type aware blocking.
 */
class AdBlockEngine(
    private val domainBlocker: DomainBlocker,
    private val patternBlocker: PatternBlocker,
    private val downloadGuard: DownloadGuard,
    private val popupGuard: PopupGuard,
    private val learningEngine: LearningEngine
) {
    
    companion object {
        private const val TAG = "AdBlockEngine"
    }
    
    /**
     * Determine if a request should be blocked.
     * 
     * Strategy:
     * 1. Check download guard first (whitelist)
     * 2. Block scripts/XHR from known ad domains (primary ad vector)
     * 3. Check URL patterns last (most expensive)
     * 
     * @param request The web request to check
     * @param previousUrl Previous page URL for context
     * @return true if request should be blocked
     */
    fun shouldBlock(
        request: WebRequest,
        previousUrl: String? = null
    ): Boolean {
        
        // Priority 1: Allow legitimate downloads
        if (downloadGuard.allow(request.url, previousUrl)) {
            return false
        }
        
        // Priority 2: Block scripts & XHR from known ad domains
        // These are the primary ad vectors
        if (request.type == RequestType.SCRIPT ||
            request.type == RequestType.XHR ||
            request.type == RequestType.FETCH
        ) {
            if (domainBlocker.isBlocked(request.host)) {
                learningEngine.report(request.host)
                Log.d(TAG, "Blocked ${request.type} from ad domain: ${request.host}")
                return true
            }
        }
        
        // Priority 3: Pattern matching (more expensive)
        if (patternBlocker.matches(request.url)) {
            learningEngine.report(request.host)
            Log.d(TAG, "Blocked by pattern: ${request.url}")
            return true
        }
        
        return false
    }
    
    /**
     * Check if a popup should be blocked.
     * Used by JS injection.
     */
    fun shouldBlockPopup(url: String): Boolean =
        popupGuard.shouldBlock(url)
    
    /**
     * Get popup blocking JS injection.
     */
    fun getPopupInjectionScript(): String =
        popupGuard.getInjectionScript()
}
