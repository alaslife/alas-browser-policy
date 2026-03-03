package com.sun.alasbrowser.utils

import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple in-memory cache to temporarily hold WebView states (Bundles)
 * when the app goes into the background or when WebViews are recycled.
 * 
 * This helps in restoring the exact state (scroll position, form inputs)
 * when the WebView is recreated.
 */
object WebViewStateCache {
    private val stateCache = ConcurrentHashMap<String, Bundle>()

    fun saveState(tabId: String, state: Bundle) {
        stateCache[tabId] = state
    }

    fun getState(tabId: String): Bundle? {
        return stateCache[tabId]
    }

    fun clearState(tabId: String) {
        stateCache.remove(tabId)
    }

    fun clearAll() {
        stateCache.clear()
    }
}
