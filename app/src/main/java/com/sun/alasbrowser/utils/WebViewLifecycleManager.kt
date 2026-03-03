package com.sun.alasbrowser.utils

import android.webkit.WebView


object WebViewLifecycleManager {
    
    private val activeWebViews = mutableSetOf<WebView>()
    
    fun registerWebView(webView: WebView) {
        activeWebViews.add(webView)

    }
    
    fun unregisterWebView(webView: WebView) {
        activeWebViews.remove(webView)

    }

}
