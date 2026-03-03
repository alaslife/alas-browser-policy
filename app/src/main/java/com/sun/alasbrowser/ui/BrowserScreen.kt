package com.sun.alasbrowser.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Log
import android.util.Patterns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sun.alasbrowser.AlasBrowserApp
import com.sun.alasbrowser.data.Bookmark
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.downloads.AlasDownloadManager
import com.sun.alasbrowser.utils.AutofillManager
import com.sun.alasbrowser.utils.GeminiService
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAX_TABS = 10
const val MAX_WEBVIEW_CACHE_SIZE = 5
const val THUMBNAIL_WIDTH = 300
const val THUMBNAIL_HEIGHT = 450

private fun Activity.exitAndRemoveFromRecents() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAndRemoveTask()
    } else {
        finishAffinity()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    preferences: BrowserPreferences,
    modifier: Modifier = Modifier,
    openSearchBar: Boolean = false,
    openVoiceSearch: Boolean = false,
    openCameraSearch: Boolean = false,
    initialUrl: String? = null,
    restoredSession: com.sun.alasbrowser.data.BrowserSession? = null,
    onIntentHandled: () -> Unit = {},
    onWebViewChanged: (android.webkit.WebView?, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    val coroutineScope = rememberCoroutineScope()
    val database = remember { BrowserDatabase.getDatabase(context) }
    val autofillManager = remember { AutofillManager(context) }
    
    // Refactored State
    val state = rememberBrowserScreenState(
        openSearchBar = openSearchBar,
        openCameraSearch = openCameraSearch
    )
    
    val scrollStateManager = rememberScrollStateManager()

    // Reset scroll state when scrolling behavior changes - instant visual feedback
    LaunchedEffect(preferences.scrollingBehaviour) {
        scrollStateManager.updateBehavior(preferences.scrollingBehaviour)
    }

    val controller = remember {
        BrowserScreenController(
            state = state,
            database = database,
            preferences = preferences,
            coroutineScope = coroutineScope,
            context = context,
            scrollStateManager = scrollStateManager
        )
    }

    // Close page menu on navigation
    LaunchedEffect(state.url, state.activeTabId) {
        state.showPageMenu = false
    }
    
    // Autofill Manager Setup
    LaunchedEffect(autofillManager) {
        autofillManager.onPageTextExtracted = { text ->
            if (state.showSummaryDialog) {
                if (state.summaryIsLoading) {
                    coroutineScope.launch {
                        val result = GeminiService.summarizeText(text)
                        if (result.isSuccess) {
                            state.summaryText = result.getOrNull()
                            state.summaryIsLoading = false
                        } else {
                            state.summaryError = result.exceptionOrNull()?.message ?: "Failed to generate summary"
                            state.summaryIsLoading = false
                        }
                    }
                } else if (state.isAskingQuestion) {
                    coroutineScope.launch {
                        val question = state.currentQuestion
                        val previousText = state.summaryText ?: ""
                        state.summaryText = previousText + "\n\nYou: $question\n\nGemini: Thinking..."
                        val result = GeminiService.askAboutPage(text, question)
                        val baseText = previousText + "\n\nYou: $question"
                        if (result.isSuccess) {
                            val answer = result.getOrNull() ?: "No answer generated."
                            state.summaryText = baseText + "\n\nGemini: $answer"
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message ?: "Failed to get answer"
                            state.summaryText = baseText + "\n\nGemini: Error: $errorMsg"
                        }
                        state.isAskingQuestion = false
                    }
                }
            }
        }
        autofillManager.onCredentialsDetected = { url, username, password ->
            state.credentialToSave = Triple(url, username, password)
        }
    }

    // Animation Bar Offsets
    val animatedBarOffsets = rememberAnimatedBarOffsets(
        scrollStateManager,
        state.topBarHeight,
        state.bottomBarHeight,
        preferences
    )
    val topBarAnimatedOffset by animatedBarOffsets.topBarOffset
    val bottomBarAnimatedOffset by animatedBarOffsets.bottomBarOffset
    val bottomBarAlpha by animatedBarOffsets.bottomBarAlpha

    // AdBlocker Initialization
    LaunchedEffect(preferences.adBlockerMode, preferences.enabledFilterLists) {
        withContext(Dispatchers.IO) {
            SimpleAdBlocker.initialize(context, preferences.adBlockerMode, preferences.enabledFilterLists)
            if (!AlasBrowserApp.isAdBlockerPrewarmed) {
                SimpleAdBlocker.initializeRegionalLists()
            }
        }
    }
    LaunchedEffect(preferences.excludedSites) {
        SimpleAdBlocker.setExcludedSites(preferences.excludedSites)
    }

    // Fullscreen Handling (Back press is handled in the main BackHandler below)
    LaunchedEffect(state.customView) {
        val activity = context as? Activity
        window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, view)
            if (state.customView != null) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }


    // Load tabs on startup
    LaunchedEffect(Unit) {
        try {
            val loadedTabs = withContext(Dispatchers.IO) {
                val savedTabs = database.tabDao().getAll()
                savedTabs.map { entity ->
                    com.sun.alasbrowser.data.BrowserTab(
                        id = entity.id,
                        url = entity.url,
                        title = entity.title,
                        isPrivate = false,
                        thumbnail = null
                    )
                }
            }
            if (loadedTabs.isNotEmpty()) {
                state.tabs.clear()
                state.tabs.addAll(loadedTabs)
                val savedTabId = preferences.lastSelectedTabId
                if (state.tabs.any { it.id == savedTabId }) {
                    state.activeTabId = savedTabId
                } else {
                    state.activeTabId = state.tabs.first().id
                }
                if (!initialUrl.isNullOrEmpty()) {
                    controller.addNewTab(initialUrl)
                    state.showHomePage = false
                } else if (restoredSession != null && restoredSession.isValid()) {
                    val activeTab = state.tabs.find { it.id == state.activeTabId }
                    if (activeTab != null && !activeTab.isPrivate) {
                        controller.updateTabById(activeTab.id) { it.copy(url = restoredSession.url) }
                        state.url = restoredSession.url
                        state.showHomePage = false
                    } else {
                        state.showHomePage = true
                        state.url = ""
                    }
                } else {
                    state.showHomePage = true
                    state.url = ""
                }
            } else {
                if (!initialUrl.isNullOrEmpty()) {
                    controller.addNewTab(initialUrl)
                    state.showHomePage = false
                } else if (restoredSession != null && restoredSession.isValid()) {
                    controller.addNewTab(restoredSession.url)
                    state.showHomePage = false
                } else {
                    controller.addNewTab("")
                }
            }
        } catch (e: Exception) {
            if (state.tabs.isEmpty()) {
                if (!initialUrl.isNullOrEmpty()) {
                    controller.addNewTab(initialUrl)
                    state.showHomePage = false
                } else {
                    controller.addNewTab("")
                }
            }
        }
    }

    // Launchers
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        state.isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrEmpty()) {
                val formattedUrl = if (spokenText.startsWith("http")) {
                    spokenText
                } else if (Patterns.WEB_URL.matcher(spokenText).matches()) {
                    "https://$spokenText"
                } else {
                    val engine = if (state.currentTab?.isPrivate == true) preferences.incognitoSearchEngine else preferences.searchEngine
                    engine.buildSearchUrl(spokenText)
                }
                val tab = state.currentTab
                if (tab != null) {
                    controller.updateTabById(tab.id) { it.copy(url = formattedUrl) }
                    state.url = formattedUrl
                    state.showHomePage = false
                } else {
                    controller.addNewTab(formattedUrl)
                    state.showHomePage = false
                }
                state.showSearchBar = false
            }
        }
    }

    // Voice search helper — opens modern in-app voice overlay
    fun launchVoiceSearch() {
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
            state.isListening = false
            return
        }
        state.voicePartialResult = ""
        state.voiceError = null
        state.showVoiceOverlay = true
    }
    
    val historyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val historyUrl = result.data?.getStringExtra("url")
            if (!historyUrl.isNullOrEmpty()) {
                val tab = state.currentTab
                if (tab != null) {
                    controller.updateTabById(tab.id) { it.copy(url = historyUrl) }
                    state.url = historyUrl
                    state.showHomePage = false
                    state.showSearchBar = false
                } else {
                    controller.addNewTab(historyUrl)
                    state.showHomePage = false
                }
            }
        }
    }

    // File Pickers
    val singleFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = state.pendingFilePickerRequest
        state.pendingFilePickerRequest = null
        if (uri != null && request != null) {
            request.onPicked(listOf(uri))
        } else {
            request?.onCancelled()
        }
    }
    val multiFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val request = state.pendingFilePickerRequest
        state.pendingFilePickerRequest = null
        if (uris.isNotEmpty() && request != null) {
            request.onPicked(uris)
        } else {
            request?.onCancelled()
        }
    }
    val cameraPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val request = state.pendingFilePickerRequest
        state.pendingFilePickerRequest = null
        if (success && request != null && android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                val contentResolver = context.contentResolver
                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    System.currentTimeMillis()
                )
                request.onPicked(listOf(uri))
            } catch (e: Exception) {
                request.onCancelled()
            }
        } else {
            request?.onCancelled()
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val request = state.pendingFilePickerRequest
        val videoUri = state.pendingVideoUri
        state.pendingFilePickerRequest = null
        state.pendingVideoUri = null
        if (success && videoUri != null && request != null) {
            request.onPicked(listOf(videoUri))
        } else {
            request?.onCancelled()
        }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success -> // CaptureVideo? Maybe CaptureAudio/Intents? Keeping original logic
         // Original used CaptureVideo for audio?? "val audioPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CaptureVideo())"
         // Yes, line 803 in original. Probably a copy paste error in original or intent.
         val request = state.pendingFilePickerRequest
         val audioUri = state.pendingAudioUri
         state.pendingFilePickerRequest = null
         state.pendingAudioUri = null
         if (success && audioUri != null && request != null) {
             request.onPicked(listOf(audioUri))
         } else {
             request?.onCancelled()
         }
    }
    val bookmarksLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bookmarkUrl = result.data?.getStringExtra("url")
            if (!bookmarkUrl.isNullOrEmpty()) {
                val tab = state.currentTab
                if (tab != null) {
                    controller.updateTabById(tab.id) { it.copy(url = bookmarkUrl) }
                    state.url = bookmarkUrl
                    state.showHomePage = false
                    state.showSearchBar = false
                } else {
                    controller.addNewTab(bookmarkUrl)
                }
            }
        }
    }

    // Effect: Open Voice Search if requested
    LaunchedEffect(openVoiceSearch) {
        if (openVoiceSearch && !state.isListening) {
            state.isListening = true
            launchVoiceSearch()
            onIntentHandled()
        }
    }
    
    // Effect: Handle external URL
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrEmpty()) {
            val currentUrl = state.currentTab?.url
            if (currentUrl != initialUrl) {
                val tab = state.currentTab
                if (tab != null && (tab.url.isEmpty() || tab.url == "about:blank")) {
                   controller.updateTabById(tab.id) { it.copy(url = initialUrl) }
                   state.url = initialUrl
                } else {
                   controller.addNewTab(initialUrl)
                }
                state.showHomePage = false
                state.showSearchBar = false
                onIntentHandled()
            }
        }
    }
    
    LaunchedEffect(openSearchBar) {
        if (openSearchBar) {
            state.showSearchBar = true
            state.showHomePage = false
            onIntentHandled()
        }
    }
    
    LaunchedEffect(openCameraSearch) {
        if (openCameraSearch) {
            state.showQrScanner = true
            onIntentHandled()
        }
    }

    LaunchedEffect(state.currentTab?.id, state.showHomePage) {
        val tab = state.currentTab
        if (tab != null && !state.showSearchBar && !state.showHomePage && tab.url.isNotEmpty()) {
            state.url = tab.url
        }
    }

    // Lifecycle Observer
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    AlasBrowserApp.isInBackground = true
                    AlasBrowserApp.lastBackgroundTime = android.os.SystemClock.elapsedRealtime()
                    controller.saveTabs(immediate = true)
                    preferences.wasOnHomepage = state.showHomePage
                }
                Lifecycle.Event.ON_RESUME -> {
                    AlasBrowserApp.isInBackground = false
                }
                Lifecycle.Event.ON_STOP -> {
                    controller.saveTabs(immediate = true)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    state.saveTabsJob?.cancel()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            state.saveTabsJob?.cancel()
            state.scrollbarHideJob?.cancel()
            controller.saveTabs(immediate = true)
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (state.customView != null) {
                try {
                    state.customViewCallback?.onCustomViewHidden()
                    state.customView = null
                    state.customViewCallback = null
                } catch (e: Exception) {}
            }
        }
    }

    // Memory Monitor — only checks when lifecycle is at least STARTED
    LaunchedEffect(preferences.memorySavingEnabled) {
        if (!preferences.memorySavingEnabled) return@LaunchedEffect
        while (true) {
            delay(60000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
                if (memoryUsagePercent > 80) {
                    System.gc()
                }
            }
        }
    }

    // Back Button Handling logic
    BackHandler(enabled = true) {
        when {
            state.customView != null -> {
                state.customViewCallback?.onCustomViewHidden()
                state.customView = null
                state.customViewCallback = null
            }
            state.showSettings -> state.showSettings = false
            state.showFullSettings -> state.showFullSettings = false
            state.showSiteSettings -> state.showSiteSettings = false
            state.showAdBlocker -> state.showAdBlocker = false
            state.showIncognitoInfo -> state.showIncognitoInfo = false
            state.showTabSwitcher -> state.showTabSwitcher = false
            state.showSearchBar -> {
                state.showSearchBar = false
                val tab = state.currentTab
                if (tab?.isPrivate == true && tab.url.trim().isEmpty()) {
                    state.showHomePage = true
                }
            }
            state.showPageMenu -> state.showPageMenu = false
            !state.showHomePage -> {
                val tab = state.currentTab
                if (tab?.parentTabId != null && state.tabs.any { it.id == tab.parentTabId }) {
                    // Popup tab — close it and return to the parent tab
                    state.progress = 10
                    controller.removeTab(tab.id)
                    scrollStateManager.reset()
                } else {
                    val wentBack = controller.navigateBack()
                    if (wentBack) {
                        state.progress = 10
                        scrollStateManager.reset()
                    } else if (state.tabs.size > 1 && tab != null) {
                        state.progress = 10
                        controller.removeTab(tab.id)
                        scrollStateManager.reset()
                    } else {
                        state.showHomePage = true
                        scrollStateManager.reset()
                    }
                }
            }
            state.showHomePage && state.currentTab?.isPrivate == true -> {
                val tab = state.currentTab
                if (state.tabs.size > 1 && tab != null) {
                    controller.removeTab(tab.id)
                } else {
                    (context as? Activity)?.exitAndRemoveFromRecents()
                }
            }
            state.showHomePage -> (context as? Activity)?.exitAndRemoveFromRecents()
        }
    }
    
    // Depth Animation
    val contentScale by animateFloatAsState(
        targetValue = if (state.showTabSwitcher) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 250f, dampingRatio = 0.75f),
        label = "contentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (state.showTabSwitcher) 0.5f else 1f,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "contentAlpha"
    )
    val contentCornerRadius by animateDpAsState(
        targetValue = if (state.showTabSwitcher) 24.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 250f, dampingRatio = 0.75f),
        label = "contentRound"
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = contentAlpha
                    clip = true
                    shape = RoundedCornerShape(contentCornerRadius.coerceAtLeast(0.dp))
                }
        ) {
            BrowserContent(
                showHomePage = state.showHomePage,
                currentTab = state.currentTab,
                showSearchBar = state.showSearchBar,
                tabs = state.tabs,
                preferences = preferences,
                autofillManager = autofillManager,
                scrollStateManager = scrollStateManager,
                isListening = state.isListening,
                database = database,
                topBarHeight = state.topBarHeight,
                topBarAnimatedOffset = topBarAnimatedOffset,
                bottomBarHeight = state.bottomBarHeight,
                url = state.url,
                progress = state.progress,
                onNavigate = { navigateUrl ->
                    val tab = state.currentTab
                    if (tab != null) {
                        controller.updateTabById(tab.id) { it.copy(url = navigateUrl) }
                        state.url = navigateUrl
                        state.showHomePage = false
                    } else {
                        controller.addNewTab(navigateUrl)
                    }
                },
                onScanQr = { state.showQrScanner = true },
                onShowSiteSettings = { state.showSiteSettings = true },
                onShowAdBlocker = { state.showAdBlocker = true },
                onShowIncognito = {
                     val activeTab = state.tabs.find { it.id == state.activeTabId }
                     if (activeTab != null && !activeTab.isPrivate) {
                         val url = activeTab.url
                         controller.removeTab(activeTab.id)
                         controller.addNewTab(url, isPrivate = true)
                     } else {
                         controller.addNewTab("", isPrivate = true)
                     }
                },
                onStartBrowsing = {
                    state.showSearchBar = true
                    state.showHomePage = false
                },
                onOpenSearchPage = {
                    state.showFindInPage = true
                },
                onUrlChange = { newUrl ->
                     state.url = newUrl
                     if (state.currentTab != null) {
                          controller.saveTabs()
                     }
                },
                onSearchClick = { 
                     state.showSearchBar = true
                     state.showHomePage = false
                },
                onMenuClick = { state.showPageMenu = true },
                onSearch = { 
                    val query = state.url.trim()
                    if (query.isNotEmpty()) {
                        val tab = state.currentTab
                        val engine = if (tab?.isPrivate == true) preferences.incognitoSearchEngine else preferences.searchEngine
                        val navigateUrl = when {
                            query.startsWith("http://") || query.startsWith("https://") -> query
                            Patterns.WEB_URL.matcher(query).matches() -> "https://$query"
                            else -> engine.buildSearchUrl(query)
                        }
                        if (tab != null) {
                            controller.updateTabById(tab.id) { it.copy(url = navigateUrl) }
                            state.url = navigateUrl
                        } else {
                            controller.addNewTab(navigateUrl)
                        }
                        state.showHomePage = false
                        state.showSearchBar = false
                    }
                },
                onEditClose = { 
                     state.showSearchBar = false
                     if (state.currentTab?.url?.isNotEmpty() == true) {
                         state.showHomePage = false
                     }
                },
                onVoiceSearch = {
                    launchVoiceSearch()
                },
                onHomeClick = {
                    val currentTab = state.currentTab
                    if (currentTab?.isPrivate == true) {
                        val normalTab = state.tabs.firstOrNull { !it.isPrivate }
                        if (normalTab != null) {
                            state.activeTabId = normalTab.id
                            controller.updateTabById(normalTab.id) { it.copy(url = "", title = "New Tab", thumbnail = null, favicon = null, canGoBack = false, canGoForward = false) }
                        } else {
                            controller.addNewTab("", isPrivate = false)
                        }
                    } else if (currentTab != null) {
                        controller.updateTabById(currentTab.id) { it.copy(url = "", title = "New Tab", thumbnail = null, favicon = null, canGoBack = false, canGoForward = false) }
                    }
                    state.showHomePage = true
                    state.url = "" 
                    state.progress = 0
                    state.showSearchBar = false
                },
                onSecurityClick = { state.showSiteSecurityDialog = true },
                onProgressChange = { newProgress -> state.progress = newProgress },
                onDownloadRequested = { url, userAgent, contentDisposition, mimetype, contentLength, cookies, referer, pageTitle, pageUrl ->
                    var suggestedName = AlasDownloadManager.getInstance(context).extractFileName(url, contentDisposition, mimetype)
                    // If extracted name is generic (no extension or just "download"), use page title
                    val nameWithoutExt = suggestedName.substringBeforeLast(".")
                    val ext = if (suggestedName.contains(".")) suggestedName.substringAfterLast(".") else ""
                    val genericNames = setOf("download", "file", "index", "content", "blob")
                    val isHashLike = nameWithoutExt.length > 20 && nameWithoutExt.all { it.isLetterOrDigit() || it == '-' || it == '_' }
                    if ((nameWithoutExt.lowercase() in genericNames || isHashLike) && !pageTitle.isNullOrBlank()) {
                        val sanitized = pageTitle.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120)
                        suggestedName = if (ext.isNotEmpty()) "$sanitized.$ext" else sanitized
                    }
                    state.pendingDownloadRequest = DownloadRequest(
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimetype,
                        contentLength = contentLength,
                        cookies = cookies,
                        referer = referer,
                        fileName = suggestedName,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl
                    )
                },
                onCreateWindow = { url -> controller.addNewTab(url, parentTabId = state.activeTabId) },
                updateTabById = controller::updateTabById,
                updateTopBarHeight = { state.topBarHeight = it },
                isCustomViewVisible = state.customView != null,
                onSessionStateUpdated = { currentUrl, scrollY ->
                    (context as? com.sun.alasbrowser.MainActivity)?.updateSafeState(currentUrl, scrollY)
                },
                onNavigationStateChanged = { canBack, canForward ->
                    state.canGoBack = canBack
                    state.canGoForward = canForward
                },
            )
        }

        // Overlays outside animated container — prevents flicker from graphicsLayer recomposition
        BrowserScreenOverlays(
            state = state,
            controller = controller,
            preferences = preferences,
            database = database,
            scrollStateManager = scrollStateManager,
            autofillManager = autofillManager,
            historyLauncher = historyLauncher,
            bookmarksLauncher = bookmarksLauncher,
            voiceSearchLauncher = voiceSearchLauncher,
            bottomBarAnimatedOffset = bottomBarAnimatedOffset,
            bottomBarAlpha = bottomBarAlpha
        )

        // Modern voice search overlay
        rememberVoiceRecognizer(state = state) { spokenText ->
            // Process the result after a brief delay to show the text
            kotlinx.coroutines.MainScope().launch {
                kotlinx.coroutines.delay(600)
                state.showVoiceOverlay = false
                state.isListening = false
                val formattedUrl = if (spokenText.startsWith("http")) {
                    spokenText
                } else if (android.util.Patterns.WEB_URL.matcher(spokenText).matches()) {
                    "https://$spokenText"
                } else {
                    val engine = if (state.currentTab?.isPrivate == true) preferences.incognitoSearchEngine else preferences.searchEngine
                    engine.buildSearchUrl(spokenText)
                }
                val tab = state.currentTab
                if (tab != null) {
                    controller.updateTabById(tab.id) { it.copy(url = formattedUrl) }
                    state.url = formattedUrl
                    state.showHomePage = false
                } else {
                    controller.addNewTab(formattedUrl)
                    state.showHomePage = false
                }
                state.showSearchBar = false
            }
        }

        ModernVoiceSearchOverlay(
            visible = state.showVoiceOverlay,
            partialResult = state.voicePartialResult,
            error = state.voiceError,
            rms = state.voiceRms,
            onResult = { /* handled by rememberVoiceRecognizer */ },
            onDismiss = {
                state.showVoiceOverlay = false
                state.isListening = false
                state.voicePartialResult = ""
                state.voiceError = null
            },
            onStartListening = {
                state.voicePartialResult = ""
                state.voiceError = null
                // Re-trigger by toggling overlay
                state.showVoiceOverlay = false
                state.showVoiceOverlay = true
            }
        )
    }
}
