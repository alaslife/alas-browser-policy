package com.sun.alasbrowser.web.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.sun.alasbrowser.web.adblock.RequestClassifier
import com.sun.alasbrowser.web.adblock.RequestType
import com.sun.alasbrowser.web.adblock.WebRequest
import java.io.ByteArrayInputStream

/**
 * WebView integration adapter for the high-performance ad blocker.
 * Provides request interception for WebView's shouldInterceptRequest.
 */
object WebViewAdBlockAdapter {
    
    /**
     * Check if a WebView request should be blocked.
     * Call this from WebViewClient.shouldInterceptRequest().
     * 
     * @param request The WebResourceRequest from WebView
     * @param previousUrl The previous page URL for context
     * @return WebResourceResponse if blocked, null if allowed
     */
    fun shouldInterceptRequest(
        request: WebResourceRequest,
        previousUrl: String? = null
    ): WebResourceResponse? {
        
        val engine = try {
            AdBlockEngineFactory.getEngine()
        } catch (e: IllegalStateException) {
            // Engine not initialized, allow request
            return null
        }
        
        val url = request.url.toString()
        val host = request.url.host ?: ""
        
        // Classify request type based on URL and headers
        val requestType = classifyWebViewRequest(request)
        
        val webRequest = WebRequest(
            url = url,
            host = host,
            type = requestType,
            isThirdParty = isThirdParty(request, previousUrl)
        )
        
        return if (engine.shouldBlock(webRequest, previousUrl)) {
            // Return empty response to block the request
            createBlockedResponse()
        } else {
            null // Allow the request
        }
    }
    
    /**
     * Classify WebView request type based on URL and headers.
     */
    private fun classifyWebViewRequest(request: WebResourceRequest): RequestType {
        val url = request.url.toString()
        
        // Check Accept header for type hints
        val acceptHeader = request.requestHeaders["Accept"]?.lowercase()
        
        return when {
            request.isForMainFrame -> RequestType.DOCUMENT
            acceptHeader?.contains("text/css") == true -> RequestType.STYLE
            acceptHeader?.contains("image") == true -> RequestType.IMAGE
            acceptHeader?.contains("video") == true || acceptHeader?.contains("audio") == true -> RequestType.MEDIA
            acceptHeader?.contains("font") == true -> RequestType.FONT
            acceptHeader?.contains("application/javascript") == true -> RequestType.SCRIPT
            
            // Fallback to URL-based detection
            url.contains(".js") -> RequestType.SCRIPT
            url.contains(".css") -> RequestType.STYLE
            url.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|svg).*")) -> RequestType.IMAGE
            url.matches(Regex(".*\\.(mp4|webm|ogg|mp3|wav).*")) -> RequestType.MEDIA
            url.matches(Regex(".*\\.(woff|woff2|ttf|otf|eot).*")) -> RequestType.FONT
            
            else -> RequestType.OTHER
        }
    }
    
    /**
     * Check if request is third-party.
     */
    private fun isThirdParty(request: WebResourceRequest, previousUrl: String?): Boolean {
        if (previousUrl == null) return false
        
        val requestHost = request.url.host ?: return false
        val previousHost = try {
            Uri.parse(previousUrl).host
        } catch (e: Exception) {
            null
        } ?: return false
        
        return requestHost != previousHost
    }
    
    /**
     * Create an empty response to block the request.
     */
    private fun createBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
