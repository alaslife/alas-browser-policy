package com.sun.alasbrowser.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.TabEntity
import com.sun.alasbrowser.engine.WebViewCache
import com.sun.alasbrowser.utils.ThumbnailCache
import com.sun.alasbrowser.web.SimpleAdBlocker
import com.sun.alasbrowser.web.SmartBackEngine

class BrowserScreenController(
    val state: BrowserScreenState,
    val database: BrowserDatabase,
    val preferences: BrowserPreferences,
    val coroutineScope: CoroutineScope,
    val context: android.content.Context,
    val scrollStateManager: ScrollStateManager,
) {
    fun navigateBack(): Boolean {
        val tab = state.currentTab ?: return false
        val tabId = tab.id
        val currentUrl = tab.url

        // Try WebView navigation first
        if (com.sun.alasbrowser.engine.WebViewCache.goBack(tabId)) {
            return true
        }

        // SmartBack: skip ad-redirect pages
        val safeBackUrl = SmartBackEngine.getSafeBackUrl(tabId, currentUrl)
        if (safeBackUrl != null && safeBackUrl != currentUrl) {
            Log.d("BrowserScreen", "📍 Smart back via loadUrl: $safeBackUrl (current: $currentUrl)")
            SmartBackEngine.popCurrentUrl(tabId)
            SimpleAdBlocker.popCurrentRealUrl(tabId)
            val webView = com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)
            webView?.loadUrl(safeBackUrl)
            return true
        }

        val prevReal = SimpleAdBlocker.getSafeBackUrlForManga(tabId, currentUrl)
        if (prevReal != null && prevReal != currentUrl) {
            Log.d("BrowserScreen", "📍 Legacy smart back: $prevReal (current: $currentUrl)")
            val isOnAdPage = SimpleAdBlocker.isMangaAdPage(currentUrl) ||
                             SimpleAdBlocker.isYouTubeInterstitialAd(currentUrl, prevReal)
            if (!isOnAdPage) {
                SimpleAdBlocker.popCurrentRealUrl(tabId)
            }
            val webView = com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)
            webView?.loadUrl(prevReal)
            return true
        }

        Log.d("BrowserScreen", "📍 Cannot go back: no history available")
        return false
    }

    fun navigateForward() {
        state.currentTab?.let { tab ->
            com.sun.alasbrowser.engine.WebViewCache.getWebView(tab.id)?.goForward()
        }
    }

    fun reloadPage() {
        state.currentTab?.let { tab ->
            com.sun.alasbrowser.engine.WebViewCache.getWebView(tab.id)?.reload()
        }
    }

    fun saveTabs(immediate: Boolean = false) {
        state.saveTabsJob?.cancel()
        state.saveTabsJob = coroutineScope.launch {
            if (!immediate) {
                delay(500)
            }

            try {
                val normalTabs = state.tabs.filter { !it.isPrivate && it.url.isNotEmpty() }
                if (normalTabs.isEmpty()) {
                    withContext(Dispatchers.IO + NonCancellable) {
                        database.tabDao().deleteAll()
                    }
                    return@launch
                }

                withContext(Dispatchers.IO + NonCancellable) {
                    val tabEntities = normalTabs.map { tab ->
                        TabEntity(
                            id = tab.id,
                            url = tab.url,
                            title = tab.title,
                            lastActive = if (tab.id == state.activeTabId) {
                                System.currentTimeMillis()
                            } else {
                                tab.lastAccessedTime
                            }
                        )
                    }
                    database.tabDao().replaceAll(tabEntities)

                    state.activeTabId?.let { tabId ->
                        preferences.lastSelectedTabId = tabId
                    }

                }
            } catch (e: CancellationException) {
            } catch (e: Exception) {

            }
        }
    }

    fun updateTabById(tabId: String, transform: (com.sun.alasbrowser.data.BrowserTab) -> com.sun.alasbrowser.data.BrowserTab) {
        try {
            val idx = state.tabs.indexOfFirst { it.id == tabId }
            if (idx == -1 || idx >= state.tabs.size) return
            val current = state.tabs[idx]
            if (current.id != tabId) return
            state.tabs[idx] = transform(current)
            saveTabs()
        } catch (e: Exception) {
            Log.w("BrowserScreen", "updateTabById failed for $tabId: ${e.message}")
        }
    }

    fun addNewTab(newUrl: String = "", isPrivate: Boolean = false, parentTabId: String? = null) {
        try {
            val validatedUrl = newUrl.trim()

            val engine = if (isPrivate) {
                com.sun.alasbrowser.engine.SiteEnginePolicy.defaultEngine
            } else if (validatedUrl.isNotEmpty()) {
                com.sun.alasbrowser.engine.SiteEnginePolicy.getEngineForUrl(validatedUrl)
            } else {
                com.sun.alasbrowser.engine.SiteEnginePolicy.defaultEngine
            }

            val newTab = com.sun.alasbrowser.data.BrowserTab(
                url = validatedUrl,
                isPrivate = isPrivate,
                lastAccessedTime = System.currentTimeMillis(),
                engineType = engine,
                parentTabId = parentTabId
            )

            if (state.tabs.size >= MAX_TABS) {
                val oldestTab = state.tabs.first()
                removeTab(oldestTab.id)
            }
            state.tabs.add(newTab)

            state.activeTabId = newTab.id
            state.url = validatedUrl
            state.showHomePage = validatedUrl.isEmpty()

            scrollStateManager.reset()

            saveTabs()

        } catch (e: Exception) {

        }
    }

    fun removeTab(tabId: String) {
        try {
            val indexToRemove = state.tabs.indexOfFirst { it.id == tabId }
            if (indexToRemove == -1) return

            val isRemoveActive = (tabId == state.activeTabId)

            if (isRemoveActive && state.tabs.size > 1) {
                // Prefer switching to the parent tab (opener) if it exists
                val removingTab = state.tabs[indexToRemove]
                val parentTab = removingTab.parentTabId?.let { pid -> state.tabs.firstOrNull { it.id == pid } }
                val newTab = parentTab ?: state.tabs[if (indexToRemove == state.tabs.lastIndex) indexToRemove - 1 else indexToRemove + 1]

                state.activeTabId = newTab.id
                state.url = newTab.url
                state.showHomePage = newTab.url.isEmpty()
            }

            state.tabs.removeAt(indexToRemove)

            // ✅ CRITICAL: Cleanup WebView and associated state
            WebViewCache.remove(tabId)
            SimpleAdBlocker.clearNavigationHistory(tabId)
            SmartBackEngine.clearTab(tabId)
            com.sun.alasbrowser.web.AuthCompatibilityEngine.clearTab(tabId)
            com.sun.alasbrowser.web.LoginCompatibilityEngine.clearTab(tabId)

            if (state.tabs.isEmpty()) {
                state.activeTabId = null
                state.showHomePage = true
                state.url = ""
                state.customView = null
            }

            scrollStateManager.reset()
            saveTabs()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun captureCurrentTabThumbnail() {
        // Capture current tab from its WebView
        val tab = state.currentTab
        if (tab != null) {
            captureTabThumbnail(tab.id, tab.url)
        }

        // Pre-load disk-cached thumbnails for all other tabs that lack in-memory thumbnails
        coroutineScope.launch(Dispatchers.IO) {
            for (i in state.tabs.indices) {
                val t = state.tabs[i]
                if (t.thumbnail == null && t.url.isNotBlank() && t.url != "about:blank") {
                    val cached = ThumbnailCache.loadThumbnail(context, t.url)
                    if (cached != null) {
                        withContext(Dispatchers.Main) {
                            if (i < state.tabs.size && state.tabs[i].id == t.id) {
                                state.tabs[i] = state.tabs[i].copy(thumbnail = cached)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun captureTabThumbnail(tabId: String, tabUrl: String) {
        val webView = com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)
        if (webView != null) {
            try {
                if (webView.width > 0 && webView.height > 0) {
                    val scale = THUMBNAIL_WIDTH.toFloat() / webView.width
                    val scaledHeight = (webView.height * scale).toInt().coerceAtMost(THUMBNAIL_HEIGHT)
                    val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, scaledHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.scale(scale, scale)
                    webView.draw(canvas)

                    val idx = state.tabs.indexOfFirst { it.id == tabId }
                    if (idx != -1) {
                        state.tabs[idx] = state.tabs[idx].copy(thumbnail = bitmap)
                    }

                    if (tabUrl.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            ThumbnailCache.saveThumbnail(context, tabUrl, bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BrowserScreen", "WebView thumbnail capture failed", e)
            }
        }
    }
}
