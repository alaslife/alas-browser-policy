package com.sun.alasbrowser.web.adblock

/**
 * Minimal popup blocker - only blocks window.open calls to known ad domains.
 * No aggressive click interception or overlays.
 */
class PopupGuard(private val popupDomains: Set<String>) {
    
    /**
     * Check if a popup URL should be blocked.
     * Used by JS injection to block window.open calls.
     */
    fun shouldBlock(url: String): Boolean =
        popupDomains.any { url.contains(it) }
    
    /**
     * Get minimal JS injection for popup blocking.
     * Only overrides window.open, nothing else.
     */
    fun getInjectionScript(): String = """
        (function() {
            if (window.__alasPopupGuardInstalled) return;
            window.__alasPopupGuardInstalled = true;
            
            const open = window.open;
            window.open = function(url, ...args) {
                if (!url) return null;
                if (window.__alasPopupBlock && window.__alasPopupBlock(url)) {
                    console.log('[AlasBrowser] Blocked popup:', url);
                    return null;
                }
                return open.call(window, url, ...args);
            };
        })();
    """.trimIndent()
}
