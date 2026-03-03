package com.sun.alasbrowser.web

/**
 * WebView health states for lifecycle coordination.
 */
enum class WebViewHealth {
    /** WebView renderer crashed or was killed - must recreate */
    RENDERER_GONE,
    
    /** App was in background too long - should recreate */
    BACKGROUND_TOO_LONG,
    
    /** System memory pressure - WebView may be killed */
    MEMORY_PRESSURE
}
