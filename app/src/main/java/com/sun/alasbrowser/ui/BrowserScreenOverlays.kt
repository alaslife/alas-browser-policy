package com.sun.alasbrowser.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.sun.alasbrowser.data.Bookmark
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.downloads.AlasDownloadManager

import com.sun.alasbrowser.ui.dialogs.DownloadConfirmationDialog
import com.sun.alasbrowser.ui.dialogs.SavePasswordDialog
import com.sun.alasbrowser.ui.dialogs.SummaryDialog
import com.sun.alasbrowser.utils.AutofillManager
import com.sun.alasbrowser.utils.IncognitoLockManager
import com.sun.alasbrowser.utils.ScriptManager
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BoxScope.BrowserScreenOverlays(
    state: BrowserScreenState,
    controller: BrowserScreenController,
    preferences: BrowserPreferences,
    database: BrowserDatabase,
    scrollStateManager: ScrollStateManager,
    autofillManager: AutofillManager,
    historyLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    bookmarksLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    voiceSearchLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    bottomBarAnimatedOffset: Dp,
    bottomBarAlpha: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    // Capture density at composable scope so it can be used in onGloballyPositioned
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomBarAnimatedOffsetPx = with(density) { bottomBarAnimatedOffset.toPx() }
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val keyboardOnlyOffsetPx = if (imeBottomPx <= 0f) {
        0f
    } else {
        // OEMs report IME inset in two ways:
        // 1) includes nav bar (need subtract), 2) excludes nav bar (use full ime inset).
        val withoutNav = (imeBottomPx - navBottomPx).coerceAtLeast(0f)
        if (withoutNav > 0f) withoutNav else imeBottomPx
    }

    // ========== BOTTOM NAVIGATION BAR ==========
    // Container always keeps full layout height; only the visual layer slides.
    // This prevents any background box resizing that would show a black gap.
    if (state.customView == null) {
        // animateFloatAsState: 0 = bar visible, 1 = bar slid fully off-screen.
        val tabSwitcherProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (state.showTabSwitcher) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "bottomBarHide"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    // Measure real height and update state so the WebView spacer
                    // always matches the actual bar height exactly WITHOUT nav bar padding.
                    .onGloballyPositioned { coords ->
                        val heightPx = coords.size.height.toFloat()
                        if (heightPx > 0f) {
                            state.bottomBarHeightPx = heightPx
                            state.bottomBarHeight = with(density) { heightPx.toDp() }
                            // Also update ScrollStateManager for HIDE_BOTH snapping
                            scrollStateManager.bottomBarHeightPx = heightPx
                        }
                    }
                    .graphicsLayer {
                        // Visual-only slide: layout size never changes → no black gap
                        translationY = keyboardOnlyOffsetPx + bottomBarAnimatedOffsetPx + (size.height * tabSwitcherProgress)
                        alpha = (bottomBarAlpha * (1f - tabSwitcherProgress)).coerceIn(0f, 1f)
                    }
            ) {
                // Liquid glow edge line at top of bottom bar area
                LiquidGlowEdge()

                // Tab strip for tablets
                if (isTablet && state.tabs.size > 1) {
                    TabStrip(
                        tabs = state.tabs,
                        activeTabId = state.activeTabId,
                        onTabClick = { tabId ->
                            state.activeTabId = tabId
                            val tab = state.tabs.firstOrNull { it.id == tabId }
                            if (tab != null) {
                                // Update URL state when switching tabs to ensure proper page loading
                                state.url = tab.url
                                state.showHomePage = tab.url.isEmpty()
                            }
                        },
                        onTabClose = { tabId -> controller.removeTab(tabId) },
                        onNewTab = {
                            val isPrivate = state.currentTab?.isPrivate == true
                            controller.addNewTab("", isPrivate = isPrivate)
                        }
                    )
                }

                MinimalBottomBar(
                    tabCount = state.tabs.size,
                    onTabSwitcherClick = { 
                        controller.captureCurrentTabThumbnail()
                        state.showTabSwitcher = true 
                    },
                    onHomeClick = { 
                        state.showHomePage = true
                        state.url = "" 
                        state.progress = 0
                        state.showSearchBar = false
                    },
                    onSettingsClick = { state.showSettings = true },
                    onBackClick = { controller.navigateBack() },
                    onForwardClick = { controller.navigateForward() },
                    onSearchClick = { state.showFindInPage = true },
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward
                )
            }
        }
    }
    
    // Opera-Style Menu (Main Menu when three-dot is pressed)
    if (state.showSettings) {
        OperaStyleMenu(
            onDismiss = { state.showSettings = false },
            preferences = preferences,
            onHistoryClick = {
                historyLauncher.launch(Intent(context, HistoryActivity::class.java))
            },
            onBookmarksClick = {
                bookmarksLauncher.launch(Intent(context, BookmarksActivity::class.java))
            },
            onDownloadsClick = {
                context.startActivity(Intent(context, DownloadsActivity::class.java))
            },
            onSettingsClick = {
                state.showFullSettings = true
            },
            onPasswordsClick = {
                // Handled inside OperaStyleMenu
            },
            onAdBlockerSettingsClick = {
                state.showAdBlocker = true
            },
            onPrivacySettingsClick = {
                state.showSiteSettings = true
            },
            onClearDataClick = {
                context.startActivity(Intent(context, StorageManagerActivity::class.java))
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    
    // Full Settings Dialog (when clicking Settings from Opera Menu)
    if (state.showFullSettings) {
        SettingsDialog(
            onDismiss = { state.showFullSettings = false },
            preferences = preferences,
            currentUrl = state.url,
            onHistoryNavigate = { historyUrl ->
                val tab = state.currentTab
                if (tab != null) {
                    val index = state.tabs.indexOfFirst { it.id == tab.id }
                    if (index != -1) {
                        state.tabs[index] = state.tabs[index].copy(url = historyUrl)
                    }
                    state.url = historyUrl
                    state.showHomePage = false
                } else {
                    controller.addNewTab(historyUrl)
                    state.showHomePage = false
                }
            },
            onTranslate = { state.showTranslationDialog = true },
            onReaderModeClick = {
                state.currentTab?.let { tab ->
                    com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(tab.id, ScriptManager.getReaderModeScript())
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
   
    // Site Permissions Screen
    if (state.showSiteSettings) {
        SitePermissionsScreen(
            onNavigateBack = { state.showSiteSettings = false },
            preferences = preferences
        )
    }
   
    // Ad Blocker Screen
    if (state.showAdBlocker) {
        AdBlockerScreen(
            preferences = preferences,
            onBack = { state.showAdBlocker = false }
        )
    }
   
    // Tab Switcher with Animation - Comet Style
    AnimatedVisibility(
        visible = state.showTabSwitcher,
        enter = scaleIn(
            initialScale = 1.1f, // Come from slightly closer (larger)
            animationSpec = androidx.compose.animation.core.spring(
                stiffness = 300f,
                dampingRatio = 0.75f
            )
        ) + fadeIn(
            animationSpec = tween(200)
        ),
        exit = scaleOut(
            targetScale = 1.1f, // Zoom back out towards user
            animationSpec = tween(250)
        ) + fadeOut(
            animationSpec = tween(200)
        )
    ) {
        PerfectTabsDialog(
            tabs = state.tabs,
            currentTabId = state.currentTab?.id ?: "",
            onTabClick = { tabId ->
                state.activeTabId = tabId
                scrollStateManager.reset()
                
                val selectedTab = state.tabs.find { it.id == tabId }
                if (selectedTab != null) {
                    android.util.Log.d("BrowserScreen", "Tab clicked! ID: $tabId, URL: '${selectedTab.url}', title: '${selectedTab.title}'. showHomePage will be ${selectedTab.url.isEmpty()}")
                    // Update URL state when switching tabs to ensure proper page loading
                    state.url = selectedTab.url
                    state.showHomePage = selectedTab.url.isEmpty()
                }
                state.showTabSwitcher = false
            },
            onTabClose = { tabId ->
                controller.removeTab(tabId)
            },
            onNewTab = {
                controller.addNewTab("")
                state.showTabSwitcher = false
            },
            onNewIncognitoTab = {
                // Auth Optimization: Bypass if already has private tabs
                if (state.tabs.any { it.isPrivate }) {
                     controller.addNewTab("", isPrivate = true)
                     state.showTabSwitcher = false
                } else {
                    val fragmentActivity = context as? FragmentActivity
                    if (fragmentActivity != null) {
                        IncognitoLockManager.authenticateForIncognito(
                            activity = fragmentActivity,
                            preferences = preferences,
                            onSuccess = {
                                controller.addNewTab("", isPrivate = true)
                                state.showTabSwitcher = false
                            },
                            onFailure = {
                                // Stay in switcher but don't close
                            }
                        )
                    } else {
                        // Fallback: just create tab without authentication
                        controller.addNewTab("", isPrivate = true)
                        state.showTabSwitcher = false
                    }
                }
            },
            onCloseAllTabs = {
                val allTabIds = state.tabs.map { it.id }
                state.tabs.clear()
                controller.addNewTab("")
                scrollStateManager.reset()
                state.showTabSwitcher = false
            },
            onSettings = {
                state.showSettings = true
                state.showTabSwitcher = false
            },
            onHistory = {
                context.startActivity(Intent(context, HistoryActivity::class.java))
                state.showTabSwitcher = false
            },

            onDismiss = { state.showTabSwitcher = false }
        )
    }

    // Save Password Dialog
    state.credentialToSave?.let { (url, username, password) ->
        SavePasswordDialog(
            url = url,
            username = username,
            onSave = {
                autofillManager.saveCredential(url, username, password)
                state.credentialToSave = null
                Toast.makeText(context, "Password saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                state.credentialToSave = null
            }
        )
    }
   
    // Site Security Dialog
    val tab = state.currentTab
    if (state.showSiteSecurityDialog && tab != null) {
        SiteSecurityDialog(
            url = tab.url,
            isSecure = tab.url.startsWith("https://"),
            onDismiss = { state.showSiteSecurityDialog = false },
            onSiteSettingsClick = { state.showSiteSettings = true },
            onAdBlockToggle = { enabled ->
                if (!enabled) {
                    controller.reloadPage()
                }
            },
            preferences = preferences,
            isUsingWebView = tab.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW,
            onToggleEngine = {
                val innerTab = tab
                val domain = innerTab.url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .split("/").firstOrNull() ?: ""
                
                val newEngine = if (innerTab.engineType == com.sun.alasbrowser.engine.EngineType.GECKO_VIEW) {
                    com.sun.alasbrowser.engine.EngineType.WEB_VIEW
                } else {
                    com.sun.alasbrowser.engine.EngineType.GECKO_VIEW
                }
                
                if (domain.isNotEmpty()) {
                    com.sun.alasbrowser.engine.SiteEnginePolicy.setEngineForSite(domain, newEngine)
                }
                
                // Using updateTabById from controller is better, but here we access state.tabs directly
                // Consistent with original code: tabs[currentTabIndex] = ...
                // But safer to use controller.updateTabById
                controller.updateTabById(innerTab.id) { it.copy(engineType = newEngine) }
            }
        )
    }
    
    // Incognito Info Screen
    if (state.showIncognitoInfo) {
        IncognitoInfoScreen(
            onStartBrowsing = {
                val fragmentActivity = context as? FragmentActivity
                if (fragmentActivity != null) {
                    IncognitoLockManager.authenticateForIncognito(
                        activity = fragmentActivity,
                        preferences = preferences,
                        onSuccess = {
                            controller.addNewTab("", isPrivate = true)
                            state.showIncognitoInfo = false
                        },
                        onFailure = {
                            state.showIncognitoInfo = false
                        }
                    )
                } else {
                    controller.addNewTab("", isPrivate = true)
                    state.showIncognitoInfo = false
                }
            }
        )
    }
   
    // Page Menu
    val currentTabForMenu = state.currentTab
    if (state.showPageMenu && currentTabForMenu != null && state.url.isNotEmpty()) {
        WebPageMenuDialog(
            url = state.url,
            title = currentTabForMenu.title,
            preferences = preferences,
            historyLauncher = historyLauncher,
            bookmarksLauncher = bookmarksLauncher,
            onDismiss = { state.showPageMenu = false },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.url)
                    putExtra(Intent.EXTRA_SUBJECT, currentTabForMenu.title)
                }
                context.startActivity(Intent.createChooser(intent, "Share link"))
                state.showPageMenu = false
            },
            onOpenSettings = {
                state.showSettings = true
            },
            onOpenAdBlocker = {
                state.showAdBlocker = true
            },
            onAddBookmark = {
                coroutineScope.launch {
                    try {
                        database.bookmarkDao().insertBookmark(
                            Bookmark(
                                title = currentTabForMenu.title,
                                url = state.url,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Bookmark already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onNewTab = {
                controller.addNewTab("")
            },
            onNewPrivateTab = {
                controller.addNewTab("", isPrivate = true)
            },
            onShowTabSwitcher = {
                controller.captureCurrentTabThumbnail()
                state.showTabSwitcher = true
            },
            onNavigate = { navigateUrl ->
                controller.updateTabById(currentTabForMenu.id) { it.copy(url = navigateUrl) }
                state.url = navigateUrl
                state.showHomePage = false
            },
            onScanQr = { state.showQrScanner = true },
            onShowPageQr = { state.showQrDialog = true },
            onTranslate = { state.showTranslationDialog = true },
            onShowZoom = { state.showZoomControl = true },
            onSummarize = {
                state.showPageMenu = false
                state.showSummaryDialog = true
                state.summaryIsLoading = true
                state.summaryText = null
                state.summaryError = null
                val script = ScriptManager.getTextExtractionScript()
                currentTabForMenu.let { t ->
                    com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, script)
                }
            },
            onReload = {
                controller.reloadPage()
                state.showPageMenu = false
            },
            onReaderMode = {
                currentTabForMenu.let { t ->
                    com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, ScriptManager.getReaderModeScript())
                }
            },
            onPrint = {
                currentTabForMenu.let { t ->
                    val wv = com.sun.alasbrowser.engine.WebViewCache.getWebView(t.id)
                    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
                    printManager?.print(t.title, wv?.createPrintDocumentAdapter(t.title) ?: return@let, null)
                }
                Toast.makeText(
                    context,
                    "Opening print dialog... Select 'Save as PDF' to export",
                    Toast.LENGTH_LONG
                ).show()
            },
            onFindInPage = {
                state.showActualFindInPage = true
                state.showPageMenu = false
            },
            isUsingWebView = currentTabForMenu.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW,
            onToggleEngine = {
                val t = currentTabForMenu
                val domain = state.url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .split("/").firstOrNull() ?: ""
                
                val newEngine = if (t.engineType == com.sun.alasbrowser.engine.EngineType.GECKO_VIEW) {
                    com.sun.alasbrowser.engine.EngineType.WEB_VIEW
                } else {
                    com.sun.alasbrowser.engine.EngineType.GECKO_VIEW
                }
                
                if (domain.isNotEmpty()) {
                    com.sun.alasbrowser.engine.SiteEnginePolicy.setEngineForSite(domain, newEngine)
                }
                
                controller.updateTabById(t.id) { it.copy(engineType = newEngine) }
                state.showPageMenu = false
            }
        )
    }
   
    // Web Search Overlay
    if (state.showFindInPage) {
        val currentTabForSearch = state.currentTab
        val historyList by database.historyDao().getAllHistory().collectAsState(initial = emptyList())
        val quickAccessList by database.historyDao().getTopVisited(6).collectAsState(initial = emptyList())
        
        val currentEngine = if (currentTabForSearch?.isPrivate == true) preferences.incognitoSearchEngine else preferences.searchEngine
        WebSearchOverlay(
            searchQuery = state.findInPageQuery,
            onSearchQueryChange = { query -> state.findInPageQuery = query },
            searchEngine = currentEngine,
            onSearch = { 
                if (currentTabForSearch != null) {
                    val queryToSearch = state.findInPageQuery.trim()
                    if (queryToSearch.isNotEmpty()) {
                        val engine = currentEngine
                        val searchUrl = if (Patterns.WEB_URL.matcher(queryToSearch).matches()) {
                            if (!queryToSearch.startsWith("http")) "https://$queryToSearch" else queryToSearch
                        } else {
                            engine.buildSearchUrl(queryToSearch)
                        }
                        
                        controller.updateTabById(currentTabForSearch.id) { it.copy(url = searchUrl, isLoading = true) }
                        state.url = searchUrl
                        state.showHomePage = false
                        state.showFindInPage = false
                        state.findInPageQuery = ""
                    }
                }
            },
            onClose = {
                state.showFindInPage = false
                state.findInPageQuery = ""
            },
            onScanQr = {
                state.showFindInPage = false
                state.showQrScanner = true
            },
            onVoiceSearch = {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    val act = context as? Activity
                    if (act != null) {
                        androidx.core.app.ActivityCompat.requestPermissions(
                            act,
                            arrayOf(android.Manifest.permission.RECORD_AUDIO),
                            102
                        )
                    }
                } else {
                    state.voicePartialResult = ""
                    state.voiceError = null
                    state.showVoiceOverlay = true
                }
            },
            onOpenSettings = {
                state.showFindInPage = false
                state.showFullSettings = true
            },
            history = historyList.take(5),
            quickAccessSites = quickAccessList,
            onClearHistory = {
                coroutineScope.launch {
                    database.historyDao().deleteAllHistory()
                }
            },
            onHistoryItemClick = { historyUrl ->
                if (currentTabForSearch != null) {
                    controller.updateTabById(currentTabForSearch.id) { it.copy(url = historyUrl, isLoading = true) }
                    state.url = historyUrl
                    state.showHomePage = false
                    state.showFindInPage = false
                    state.findInPageQuery = ""
                }
            },
            onFillSearchBar = { text ->
                state.findInPageQuery = text
            }
        )
    }
    
    val translationTab = state.currentTab
    if (state.showTranslationDialog && translationTab != null) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 36.dp)
        ) {
            TranslationBottomSheet(
                onDismiss = { state.showTranslationDialog = false },
                onTranslate = { sourceLang, targetLang ->
                    val script = if (sourceLang == "revert" && targetLang == "revert") {
                        ScriptManager.getRevertTranslationScript()
                    } else {
                        ScriptManager.getTranslationScript(targetLang, sourceLang)
                    }
                    
                    if (translationTab.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW) {
                        com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(translationTab.id, script)
                    } else {
                        com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(translationTab.id, script)
                    }
                },
                translateImmediately = true
            )
        }
    }
    
    // Actual Find in Page Dialog (GeckoView)
    val findInPageTab = state.currentTab
    if (state.showActualFindInPage && findInPageTab != null) {
        GeckoFindInPageDialog(
            tabId = findInPageTab.id,
            onDismiss = { state.showActualFindInPage = false }
        )
    }
    
    // Download Confirmation Dialog
    state.pendingDownloadRequest?.let { request ->
        DownloadConfirmationDialog(
            url = request.url,
            fileName = request.fileName,
            totalSize = request.contentLength,
            onConfirm = { newName ->
                AlasDownloadManager.getInstance(context).startDownload(
                    url = request.url,
                    userAgent = request.userAgent,
                    contentDisposition = request.contentDisposition,
                    mimeType = request.mimeType,
                    customFileName = newName,
                    cookie = request.cookies,
                    referer = request.referer,
                    pageTitle = request.pageTitle,
                    pageUrl = request.pageUrl,
                    contentLength = request.contentLength
                )
                state.pendingDownloadRequest = null
            },
            onDismiss = { state.pendingDownloadRequest = null }
        )
    }
    
    // QR Scanner Screen
    if (state.showQrScanner) {
        QrScannerScreen(
            onResult = { scannedResult ->
                state.showQrScanner = false
                val navigateUrl = when {
                    scannedResult.startsWith("http://") || scannedResult.startsWith("https://") -> scannedResult
                    Patterns.WEB_URL.matcher(scannedResult).matches() -> "https://$scannedResult"
                    else -> preferences.searchEngine.buildSearchUrl(scannedResult)
                }
                val t = state.currentTab
                if (t != null) {
                    controller.updateTabById(t.id) { it.copy(url = navigateUrl) }
                    state.url = navigateUrl
                    state.showHomePage = false
                } else {
                    controller.addNewTab(navigateUrl)
                    state.showHomePage = false
                }
            },
            onClose = { state.showQrScanner = false }
        )
    }
    
    // QR Code Dialog (show current page QR)
    if (state.showQrDialog && state.url.isNotEmpty()) {
        QrCodeDialog(
            url = state.url,
            onDismiss = { state.showQrDialog = false }
        )
    }
    
    val uiState by scrollStateManager.uiState.collectAsState()

    // JS-based scroll position fallback for sites with custom scroll containers
    val currentTabId = state.currentTab?.id
    val currentEngine = state.currentTab?.engineType
    LaunchedEffect(currentTabId, state.showHomePage) {
        if (currentTabId == null || state.showHomePage) return@LaunchedEffect
        val scrollCheckJs = "(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0)"
        while (true) {
            delay(1500)
            try {
                if (currentEngine == com.sun.alasbrowser.engine.EngineType.WEB_VIEW) {
                    com.sun.alasbrowser.engine.WebViewCache.getWebView(currentTabId)?.evaluateJavascript(scrollCheckJs) { result ->
                        val scrollY = result?.toIntOrNull() ?: 0
                        scrollStateManager.onScrollChange(scrollY)
                    }
                } else {
                    com.sun.alasbrowser.engine.WebViewCache.getWebView(currentTabId)?.evaluateJavascript(scrollCheckJs, null)
                }
            } catch (_: Exception) {}
        }
    }

    // Scroll to Top Button
    AnimatedVisibility(
        visible = uiState.isScrollToTopVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = state.bottomBarHeight + 12.dp, end = 6.dp)
            .navigationBarsPadding()
    ) {
        androidx.compose.material3.SmallFloatingActionButton(
            onClick = {
                val tabId = state.currentTab?.id ?: ""
                val scrollJs = "window.scrollTo({top: 0, behavior: 'smooth'});"
                coroutineScope.launch {
                    if (state.currentTab?.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW) {
                        com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(tabId, scrollJs)
                    } else {
                        com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(tabId, scrollJs)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Scroll to Top"
            )
        }
    }

    // Zoom Floating Control
    val zoomTab = state.currentTab
    if (state.showZoomControl && zoomTab != null) {
        val tabId = zoomTab.id
        ZoomFloatingControl(
            initialZoom = preferences.textSize,
            onZoomChange = { newZoom ->
                try {
                    preferences.setTextSize(newZoom)
                    com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(tabId, "document.body.style.zoom = '${newZoom}%'")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onDismiss = {
                 state.showZoomControl = false
            }
        )
    }

    // Summary Dialog (AI)
    if (state.showSummaryDialog) {
        SummaryDialog(
            summary = state.summaryText,
            isLoading = state.summaryIsLoading,
            error = state.summaryError,
            onDismiss = { state.showSummaryDialog = false },
            onRetry = {
                 state.summaryIsLoading = true
                 state.summaryError = null
                 val t = state.currentTab
                 if (t != null) {
                     val script = ScriptManager.getTextExtractionScript()
                     if (t.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW) {
                         com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, script)
                     } else {
                         com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, script)
                     }
                 } else {
                     state.summaryError = "No active tab"
                     state.summaryIsLoading = false
                 }
            },
            sourceTitle = state.currentTab?.title ?: "Current Page",
            sourceIcon = state.currentTab?.favicon,
            onVoiceSearch = {},
            onAskQuestion = { question ->
                state.isAskingQuestion = true
                state.currentQuestion = question
                val t = state.currentTab
                if (t != null) {
                     val script = ScriptManager.getTextExtractionScript()
                     if (t.engineType == com.sun.alasbrowser.engine.EngineType.WEB_VIEW) {
                         com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, script)
                     } else {
                         com.sun.alasbrowser.engine.WebViewCache.evaluateJavascript(t.id, script)
                     }
                } else {
                    state.isAskingQuestion = false
                }
            }
        )
    }
}

@Composable
private fun LiquidGlowEdge() {
    val glowColor1 = Color(0xFF6366F1)
    val glowColor2 = Color(0xFF8B5CF6)
    val glowColor3 = Color(0xFFD946EF)

    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPhase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val w = size.width
        val stop1 = glowPhase * 0.4f
        val stop2 = 0.3f + glowPhase * 0.4f
        val stop3 = 0.6f + glowPhase * 0.4f
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                stop1.coerceIn(0.01f, 0.99f) to glowColor1.copy(alpha = 0.8f),
                stop2.coerceIn(0.01f, 0.99f) to glowColor3.copy(alpha = 0.9f),
                stop3.coerceIn(0.01f, 0.99f) to glowColor2.copy(alpha = 0.7f),
                1f to Color.Transparent
            ),
            start = Offset(0f, size.height / 2),
            end = Offset(w, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}
