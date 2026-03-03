package com.sun.alasbrowser.ui

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.BrowserTab
import com.sun.alasbrowser.data.History
import com.sun.alasbrowser.data.ScrollingBehaviour
import com.sun.alasbrowser.engine.WebViewCache
import com.sun.alasbrowser.engine.WebViewContainer
import com.sun.alasbrowser.utils.AutofillManager
import com.sun.alasbrowser.utils.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "BrowserContent"

@Composable
fun BrowserContent(
    showHomePage: Boolean,
    currentTab: BrowserTab?,
    showSearchBar: Boolean,
    tabs: SnapshotStateList<BrowserTab>,
    preferences: BrowserPreferences,
    autofillManager: AutofillManager,
    scrollStateManager: ScrollStateManager,
    isListening: Boolean,
    database: BrowserDatabase,
    topBarHeight: Dp,
    topBarAnimatedOffset: Dp,
    bottomBarHeight: Dp,
    url: String,
    progress: Int,
    onNavigate: (String) -> Unit,
    onScanQr: () -> Unit,
    onShowSiteSettings: () -> Unit,
    onShowAdBlocker: () -> Unit,
    onShowIncognito: () -> Unit,
    onStartBrowsing: () -> Unit,
    onOpenSearchPage: () -> Unit,
    onUrlChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSearch: () -> Unit,
    onEditClose: () -> Unit,
    onVoiceSearch: () -> Unit,
    onHomeClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onProgressChange: (Int) -> Unit,
    onDownloadRequested: (String, String?, String?, String?, Long, String?, String?, String?, String?) -> Unit,
    onCreateWindow: (String) -> Unit,
    updateTabById: (String, (BrowserTab) -> BrowserTab) -> Unit,
    updateTopBarHeight: (Dp) -> Unit,
    isCustomViewVisible: Boolean,
    onSessionStateUpdated: (String, Int) -> Unit = { _, _ -> },
    onNavigationStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Track current scroll Y for session saving
    val scrollMetrics by scrollStateManager.scrollMetrics.collectAsState()
    
    // Notify parent of state changes
    LaunchedEffect(currentTab?.url, scrollMetrics.scrollY) {
        if (currentTab != null) {
            onSessionStateUpdated(currentTab.url, scrollMetrics.scrollY)
        }
    }
    
    // Register for memory trim events
    DisposableEffect(Unit) {
        val callbacks = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                ThumbnailCache.clearCache(context)
                System.gc()
            }
            override fun onTrimMemory(level: Int) {
                if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
                    ThumbnailCache.clearCache(context)
                    System.gc()
                }
            }
        }
        context.registerComponentCallbacks(callbacks)
        
        onDispose {
            context.unregisterComponentCallbacks(callbacks)
        }
    }

    // Sync search bar state with ScrollStateManager
    LaunchedEffect(showSearchBar) {
        scrollStateManager.setSearchBarState(showSearchBar)
    }

    if (showHomePage) {
        val isPrivate = currentTab?.isPrivate == true
        val bottomPadding = if (isCustomViewVisible) 0.dp else bottomBarHeight
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = bottomPadding)
        ) {
            if (isPrivate && !showSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    IncognitoInfoScreen(
                        onStartBrowsing = onStartBrowsing
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AlasHomePage(
                        onNavigate = onNavigate,
                        preferences = preferences,
                        openSearchBar = showSearchBar,
                        onSearchBarTap = onOpenSearchPage,
                        onVoiceSearch = onVoiceSearch,
                        isListening = isListening,
                        onScanQr = onScanQr,
                        onShowSiteSettings = onShowSiteSettings,
                        onShowAdBlocker = onShowAdBlocker,
                        onShowIncognito = onShowIncognito,
                        isPrivate = isPrivate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else if (currentTab != null) {
        // Nested scroll connection for handling scroll on non-scrollable pages (like modals)
        // This ensures scroll gestures are captured even when WebView content doesn't scroll
        val nestedScrollConnection = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                ): androidx.compose.ui.geometry.Offset {
                    // Forward vertical scroll delta to scroll state manager
                    if (available.y != 0f) {
                        scrollStateManager.onNestedScroll(-available.y)
                    }
                    // Don't consume - let WebView handle it if it can
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                ): androidx.compose.ui.geometry.Offset {
                    // Gesture lifecycle is driven directly from WebView touch callbacks.
                    // Do not emit touch-end from nested scroll; it causes jittery snap races.
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }

        Box(
            modifier = if (!isCustomViewVisible) {
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .nestedScroll(nestedScrollConnection)
            } else {
                Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)
            }
        ) {
            val scrollProgressState = scrollStateManager.scrollProgressState
            val dynamicBottomPad = when (preferences.scrollingBehaviour) {
                ScrollingBehaviour.NEVER_HIDE -> 0.dp
                ScrollingBehaviour.HIDE_BOTH -> 24.dp * (1f - scrollProgressState.value.coerceIn(0f, 1f))
                else -> 24.dp
            }

            // Layer 1: Static background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
            )

            // Layer 2: Content (WebView)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .padding(bottom = dynamicBottomPad)
                    .clipToBounds() // Must be before .layout to clip the outer bounds correctly
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .layout { measurable, constraints ->
                        val progressVal = scrollProgressState.value
                        val hideTopBar = preferences.hideTopBarOnScroll
                        val maxTopPaddingPx = topBarHeight.roundToPx()
                        
                        // Convert bottomBarHeight to pixels for layout calculation
                        val bottomBarHeightPx = bottomBarHeight.roundToPx()
                        // When HIDE_BOTH is active, reserve only the currently visible portion
                        // of the bottom bar so WebView expands into the freed area while scrolling.
                        val visibleBottomBarPx = if (preferences.scrollingBehaviour == ScrollingBehaviour.HIDE_BOTH) {
                            (bottomBarHeightPx * (1f - progressVal.coerceIn(0f, 1f))).toInt()
                        } else {
                            bottomBarHeightPx
                        }
                        
                        // Calculate available height - note we don't subtract IME (keyboard) height
                        // to keep the 24dp padding fixed even when keyboard opens
                        val availableHeight = constraints.maxHeight - visibleBottomBarPx
                        
                        // Determine the height of the WebView content.
                        // If we are hiding the top bar, we need the WebView to be full 'availableHeight' 
                        // so it can expand to fill the top gap when shifted up.
                        // If we are NOT hiding it, we measure it to exactly fit the gap.
                        val contentHeight = if (hideTopBar) {
                            availableHeight
                        } else {
                            availableHeight - maxTopPaddingPx
                        }.coerceAtLeast(0)

                        val placeable = measurable.measure(
                            constraints.copy(
                                maxHeight = contentHeight,
                                minHeight = contentHeight
                            )
                        )
                        
                        // Reduce top bar padding to web content by 24dp
                        val topPaddingReduction = with(density) { 24.dp.roundToPx() }
                        val currentY = if (hideTopBar) {
                            ((maxTopPaddingPx - topPaddingReduction) * (1f - progressVal)).toInt()
                        } else {
                            maxTopPaddingPx - topPaddingReduction
                        }

                        // The layout boundary is set to exactly availableHeight.
                        // This ensures that even if the WebView (contentHeight) is pushed down by currentY,
                        // it is clipped at the top of the bottom bar area.
                        layout(constraints.maxWidth, availableHeight) {
                            placeable.place(x = 0, y = currentY)
                        }
                    }
            ) {
                val hasNavigated = remember(currentTab.id) {
                    mutableStateOf(
                        currentTab.url.isNotBlank() && 
                        currentTab.url != "about:blank" && 
                        !currentTab.url.startsWith("about:")
                    )
                }
            
                LaunchedEffect(currentTab.id) {
                    scrollStateManager.reset()
                    hasNavigated.value = currentTab.url.isNotBlank() && 
                        currentTab.url != "about:blank" && 
                        !currentTab.url.startsWith("about:")
                }
                
                val hasValidUrl = currentTab.url.isNotBlank() && 
                    currentTab.url != "about:blank" && 
                    !currentTab.url.startsWith("about:")
        
                if (hasValidUrl) {
                    val thisTabId = currentTab.id
                    val isThisTabPrivate = currentTab.isPrivate
                    val isOvershrollingTop = scrollProgressState.value == 0f && scrollMetrics.scrollY <= 0 && scrollStateManager.topBarHeightPx > 0
                    val scaleFactor = if (isOvershrollingTop) {
                        1f + (kotlin.math.abs(scrollMetrics.scrollY) / 3000f).coerceIn(0f, 0.02f)
                    } else 1f

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        }
                    ) {
                        key(thisTabId) {
                            WebViewContainer(
                                url = currentTab.url,
                                tabId = thisTabId,
                                isPrivate = isThisTabPrivate,
                                desktopMode = preferences.desktopMode,
                                preferences = preferences,
                                autofillManager = autofillManager,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding()
                                    .background(MaterialTheme.colorScheme.background),
                                onPageStarted = { startedUrl ->
                                    updateTabById(thisTabId) { 
                                        it.copy(isLoading = true, url = startedUrl) 
                                    }
                                    // Show progress bar immediately when page starts loading
                                    onProgressChange(1)
                                },
                                onPageFinished = { finishedUrl, _ ->
                                    updateTabById(thisTabId) { 
                                        it.copy(isLoading = false, url = finishedUrl) 
                                    }
                                    
                                    handlePageFinished(
                                        context = context,
                                        coroutineScope = coroutineScope,
                                        database = database,
                                        tabId = thisTabId,
                                        finishedUrl = finishedUrl,
                                        isPrivate = isThisTabPrivate,
                                        tabs = tabs,
                                        onThumbnailCaptured = { bmp ->
                                            updateTabById(thisTabId) { 
                                                it.copy(thumbnail = bmp) 
                                            }
                                        }
                                    )
                                },
                                onProgressChanged = { newProgress ->
                                    onProgressChange(newProgress)
                                },
                                onUrlChange = { newUrl ->
                                    updateTabById(thisTabId) { it.copy(url = newUrl) }
                                    onUrlChange(newUrl)
                                },
                                onTitleChange = { title ->
                                    updateTabById(thisTabId) { it.copy(title = title) }
                                },
                                onFaviconReceived = { favicon ->
                                    // Update tab's favicon when received from the WebView
                                    updateTabById(thisTabId) { it.copy(favicon = favicon) }
                                },
                                onNavigationStateChanged = { canBack, canForward ->
                                    onNavigationStateChanged(canBack, canForward)
                                },
                                onDownloadRequested = { downloadUrl, filename, contentType, 
                                    contentLength, userAgent, contentDisposition, cookie, 
                                    pageTitle, pageUrl ->
                                    onDownloadRequested(
                                        downloadUrl,
                                        userAgent ?: "Mozilla/5.0",
                                        contentDisposition ?: "attachment; filename=\"${filename ?: "download"}\"",
                                        contentType,
                                        contentLength,
                                        cookie,
                                        pageUrl ?: currentTab.url,
                                        pageTitle,
                                        pageUrl
                                    )
                                },
                                onOpenInNewTab = { urlStr ->
                                    onCreateWindow(urlStr)
                                },
                                onScrollChanged = { scrollY, scrollRange, scrollExtent ->
                                    scrollStateManager.updateScrollState(scrollY, scrollRange, scrollExtent)
                                },
                                onTouchStateChanged = { touching ->
                                    scrollStateManager.onTouchStateChanged(touching)
                                },
                                onTouchDrag = { dragDy ->
                                    if (preferences.scrollingBehaviour != ScrollingBehaviour.NEVER_HIDE) {
                                        scrollStateManager.onEdgeGestureDrag(dragDy)
                                    }
                                }
                            )
                        }

                        NightModeOverlay(preferences = preferences)
                    }
                }
            }

            // Incognito info screen overlay
            val shouldShowIncognitoInfo = currentTab.isPrivate &&
                (currentTab.url.isBlank() || currentTab.url == "about:blank")
            if (shouldShowIncognitoInfo && !showSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    IncognitoInfoScreen(
                        onStartBrowsing = onStartBrowsing
                    )
                }
            }

            // Top Bar
            if (!isCustomViewVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clipToBounds()
                        .offset { 
                            IntOffset(0, topBarAnimatedOffset.roundToPx()) 
                        }
                        .shadow(
                            elevation = (8f * (1f - scrollProgressState.value)).dp,
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                ) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val heightPx = coordinates.size.height
                            updateTopBarHeight(with(density) { heightPx.toDp() })
                            scrollStateManager.topBarHeightPx = heightPx.toFloat()
                        }
                    ) {
                        MinimalTopBar(
                            currentTab = currentTab,
                            url = url,
                            progress = progress,
                            isEditing = showSearchBar,
                            onUrlChange = onUrlChange,
                            onSearchClick = onSearchClick,
                            onMenuClick = onMenuClick,
                            onSearch = onSearch,
                            onEditClose = onEditClose,
                            onVoiceSearch = onVoiceSearch,
                            isListening = isListening,
                            onHomeClick = onHomeClick,
                            onSecurityClick = onSecurityClick
                        )
                    }
                }
            }
        }
    }
}

private fun handlePageFinished(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    database: BrowserDatabase,
    tabId: String,
    finishedUrl: String,
    isPrivate: Boolean,
    tabs: SnapshotStateList<BrowserTab>,
    onThumbnailCaptured: (Bitmap) -> Unit
) {
    if (!isPrivate && finishedUrl.isNotBlank() && finishedUrl != "about:blank") {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val tab = tabs.firstOrNull { it.id == tabId }
                val title = tab?.title ?: ""
                val currentTime = System.currentTimeMillis()
                
                if (database.historyDao().urlExists(finishedUrl)) {
                    database.historyDao().updateVisitCount(finishedUrl, currentTime)
                } else {
                    database.historyDao().insertHistory(
                        History(
                            title = title,
                            url = finishedUrl,
                            visitTime = currentTime,
                            visitCount = 1
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving history", e)
            }
        }
    }

    if (finishedUrl.isNotBlank() && finishedUrl != "about:blank") {
        coroutineScope.launch(Dispatchers.Main) {
            delay(1500)
            try {
                val wv = WebViewCache.getWebView(tabId)
                if (wv != null && wv.width > 0 && wv.height > 0) {
                    val bitmap = captureWebViewThumbnail(wv)
                    bitmap?.let { bmp ->
                        onThumbnailCaptured(bmp)
                        coroutineScope.launch(Dispatchers.IO) {
                            ThumbnailCache.saveThumbnail(context, finishedUrl, bmp)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing WebView thumbnail", e)
            }
        }
    }
}

private fun captureWebViewThumbnail(webView: WebView): Bitmap? {
    return try {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) return null
        
        val scale = 300f / width
        val targetHeight = (height * scale).toInt().coerceAtMost(450)
        
        val bitmap = Bitmap.createBitmap(300, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale, scale)
        webView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "Error capturing WebView thumbnail", e)
        null
    }
}

// Memory trim constants
private const val TRIM_MEMORY_RUNNING_CRITICAL = 15
