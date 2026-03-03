package com.sun.alasbrowser.utils

import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Configure WebView settings for native Chromium PiP support
 */
object WebViewPipSettings {
    
    /**
     * Enable all necessary settings for PiP to work with HTML5 videos
     */
    fun enablePictureInPicture(webView: WebView) {
        webView.settings.apply {
            // Enable JavaScript (required for PiP)
            javaScriptEnabled = true
            
            // Allow autoplay without user gesture
            // This is crucial for PiP to continue playback
            mediaPlaybackRequiresUserGesture = false
            
            // Enable DOM storage for video state
            domStorageEnabled = true
            
            // Support multiple windows (for fullscreen)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            
            // Allow file access for local videos
            allowFileAccess = true
            allowContentAccess = true
            
            // Enable mixed content (HTTP videos on HTTPS pages)
       mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            
            // Use wide viewport for better video display
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Enable built-in zoom controls
            builtInZoomControls = true
            displayZoomControls = false
            
            // Set user agent to support modern features
            userAgentString = getUserAgentForPip()
        }
    }
    
    /**
     * Get user agent string that supports PiP
     * Uses Chrome's user agent to ensure compatibility
     */
    private fun getUserAgentForPip(): String {
        return com.sun.alasbrowser.engine.ChromiumCompat.DESKTOP_UA
    }
    
    /**
     * Alternative: Keep mobile user agent but ensure PiP support
     */
    fun getUserAgentMobile(): String {
        return com.sun.alasbrowser.engine.ChromiumCompat.MOBILE_UA
    }
    
    /**
     * Inject PiP support meta tags into page
     */
    fun injectPipMetaTags(webView: WebView) {
        val script = """
            (function() {
                // Add meta tags for PiP support
                const meta = document.createElement('meta');
                meta.name = 'mobile-web-app-capable';
                meta.content = 'yes';
                document.head.appendChild(meta);
                
                // Feature policy for PiP
                const featurePolicy = document.createElement('meta');
                featurePolicy.httpEquiv = 'Feature-Policy';
                featurePolicy.content = 'picture-in-picture *';
                document.head.appendChild(featurePolicy);
                
                console.log('[PiP] Meta tags injected');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
}
