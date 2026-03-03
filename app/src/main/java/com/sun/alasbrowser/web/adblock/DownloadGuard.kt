package com.sun.alasbrowser.web.adblock

/**
 * LiteApks-safe download protection.
 * Ensures legitimate downloads work while blocking ad redirects.
 */
class DownloadGuard(
    private val cdnWhitelist: Set<String>,
    private val downloadExtensions: Set<String>
) {
    
    /**
     * Check if a URL should be allowed (not blocked).
     * 
     * @param url The URL to check
     * @param previousUrl The previous page URL (for context)
     * @return true if URL should be allowed, false if it can be blocked
     */
    fun allow(url: String, previousUrl: String?): Boolean {
        val lower = url.lowercase()
        
        // Whitelist known download CDNs
        if (cdnWhitelist.any { lower.contains(it) }) return true
        
        // Allow direct download files
        if (downloadExtensions.any { lower.endsWith(it) }) return true
        
        // Allow numbered download steps (e.g., /download/1, /download/2)
        if (previousUrl != null &&
            previousUrl.contains("/download") &&
            Regex("/\\d+$").containsMatchIn(lower)
        ) return true
        
        return false
    }
}
