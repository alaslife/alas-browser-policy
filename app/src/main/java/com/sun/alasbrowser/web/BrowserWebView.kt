@file:Suppress("DEPRECATION")

package com.sun.alasbrowser.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sun.alasbrowser.data.WebViewDarkMode
import com.sun.alasbrowser.downloads.AlasDownloadManager
import com.sun.alasbrowser.utils.WebViewMediaSessionManager

@SuppressLint("UseKtx", "ObsoleteSdkInt")
@Composable
fun BrowserWebView(
    url: String,
    tabId: String = "",
    adBlockEnabled: Boolean,
    isPrivate: Boolean = false,
    desktopMode: Boolean = false,
    zoomLevel: Float = 100f,
    enableMediaSession: Boolean = false,
    enableBackgroundPlayback: Boolean = false,
    bottomPaddingPx: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String, Bitmap?) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    onFaviconReceived: (Bitmap?) -> Unit = {},
    onNavigationStateChanged: (String?) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {},
    onWebViewCrashed: (String) -> Unit = {},
    onShowCustomView: ((android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onHideCustomView: (() -> Unit)? = null,
    onFileChooser: ((android.webkit.ValueCallback<Array<Uri>>, android.webkit.WebChromeClient.FileChooserParams) -> Boolean)? = null,
    onVideoPlaying: ((Boolean) -> Unit)? = null,
    onMediaSessionCreated: ((androidx.media3.session.MediaSession?) -> Unit)? = null,
    onCreateWindow: ((String) -> Unit)? = null,
    onScrollStart: (() -> Unit)? = null,
    onScrollEnd: (() -> Unit)? = null,
    onNavigationInitiated: (String) -> Unit = {},  // Called when user taps a link before page starts
    autofillManager: com.sun.alasbrowser.utils.AutofillManager? = null,
    preferences: com.sun.alasbrowser.data.BrowserPreferences? = null,
    cachedWebView: WebView? = null,
    onDownloadRequested: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long, cookies: String?, referer: String?, pageTitle: String?, pageUrl: String?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    restoredState: android.os.Bundle? = null,
    onHealthEvent: (WebViewHealth) -> Unit = {},
    recreationKey: Int = 0  // Add recreation key to force WebView recreation
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    var lastLoadedUrl by remember(tabId) { mutableStateOf("") }
    var isLoadingInternal by remember(tabId) { mutableStateOf(false) }
    var lastAppliedPadding by remember(tabId) { mutableIntStateOf(-1) }

    // Track URLs loaded internally by WebView (user clicking links)
    var internalNavigationUrl by remember(tabId) { mutableStateOf("") }
    
    val wrappedOnPageStarted: (String) -> Unit = { startedUrl ->
        isLoadingInternal = true
        lastLoadedUrl = startedUrl  // Prevent update block from reloading old URL
        internalNavigationUrl = startedUrl  // Track internal navigation
        lastAppliedPadding = -1  // Reset so padding CSS gets reinjected on new page
        onPageStarted(startedUrl)
    }

    val wrappedOnPageFinished: (String, Bitmap?) -> Unit = { finishedUrl, thumbnail ->
        isLoadingInternal = false
        lastLoadedUrl = finishedUrl
        internalNavigationUrl = ""  // Clear internal navigation tracking
        onPageFinished(finishedUrl, thumbnail)
    }

    val throttledOnProgressChanged: (Int) -> Unit = remember(tabId) {
        var lastProgress = -1
        { progress: Int ->
            // Show progress for:
            // 1. progress == 100 (completed)
            // 2. progress went backwards
            // 3. difference >= 5 (throttled updates)
            // 4. progress > 0 and lastProgress < 0 (initial progress - shows loading started)
            // 5. progress > lastProgress and progress < 100 (any positive progress change for slow networks)
            if (progress == 100 || progress < lastProgress || progress - lastProgress >= 5 || 
                (progress > 0 && lastProgress < 0) || (progress > lastProgress && progress < 100 && progress > 0)) {
                lastProgress = progress
                onProgressChanged(progress)
            }
        }
    }

    // Use key() to force WebView recreation when dark mode changes OR when recreationKey changes
    key(tabId, preferences?.webViewDarkMode, recreationKey) {
        // Capture WebView instance for lifecycle management
        var webViewInstance by remember { mutableStateOf<AlasWebView?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current

        // Simple, clean lifecycle observer
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val webView = webViewInstance ?: return@LifecycleEventObserver
                
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (!webView.safeResume()) {
                            // WebView is dead - trigger recreation
                            onHealthEvent(WebViewHealth.RENDERER_GONE)
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        webView.safePause()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { factoryContext: Context ->
            // Determine if we need dark themed context for WebView
            val darkMode = preferences?.webViewDarkMode ?: WebViewDarkMode.AUTOMATIC
            val isSystemInDarkMode = (factoryContext.resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            // Use dark theme wrapper when needed to set isLightTheme=false
            val webViewContext = when (darkMode) {
                WebViewDarkMode.DARK_PREFERRED -> {
                    // Always use dark theme context
                    ContextThemeWrapper(factoryContext, com.sun.alasbrowser.R.style.Theme_AlasBrowser_WebViewDark)
                }
                WebViewDarkMode.AUTOMATIC -> {
                    // Use dark theme context only if system is in dark mode
                    if (isSystemInDarkMode) {
                        ContextThemeWrapper(factoryContext, com.sun.alasbrowser.R.style.Theme_AlasBrowser_WebViewDark)
                    } else {
                        factoryContext
                    }
                }
                else -> factoryContext
            }
            
                // Reuse cached WebView ONLY if it's valid (alive and not destroyed)
                val cachedAlasWebView = cachedWebView as? AlasWebView
                val canReuse = preferences?.memorySavingEnabled == true && 
                               cachedAlasWebView?.isAlive == true
                
                val webView = if (canReuse) {
                    android.util.Log.d("BrowserWebView", "Reusing cached WebView for tab $tabId")
                    (cachedWebView!!.parent as? ViewGroup)?.removeView(cachedWebView)
                    cachedAlasWebView!!
                } else {
                    android.util.Log.d("BrowserWebView", "Creating new WebView for tab $tabId")
                    AlasWebView(webViewContext)
                }
                
                webView.apply {
                    webViewInstance = this
                    
                    // Wire up recreation callback
                    onRecreationNeeded = { onHealthEvent(WebViewHealth.RENDERER_GONE) }
                    onWebViewCreated(this)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = preferences?.javaScriptEnabled != false
                        domStorageEnabled = true
                        databaseEnabled = !isPrivate
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mediaPlaybackRequiresUserGesture = preferences?.microphone == false
                        allowFileAccess = !isPrivate
                        allowContentAccess = !isPrivate
                        allowUniversalAccessFromFileURLs = true
                        allowFileAccessFromFileURLs = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true) // Enable multiple windows for popups (Pling, OAuth, etc.)

                        // Enhanced media settings for better audio/video support
                        @Suppress("DEPRECATION")
                        mediaPlaybackRequiresUserGesture = preferences?.microphone == false

                        // Set geolocation based on preferences
                        setGeolocationEnabled(preferences?.location != false)

                        // Allow mixed content for better media compatibility
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = if (desktopMode) {
                            com.sun.alasbrowser.engine.ChromiumCompat.DESKTOP_UA
                        } else {
                            com.sun.alasbrowser.engine.ChromiumCompat.cleanMobileUA(context)
                        }
                        cacheMode = if (isPrivate) android.webkit.WebSettings.LOAD_NO_CACHE else android.webkit.WebSettings.LOAD_DEFAULT
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        blockNetworkLoads = false

                        textZoom = zoomLevel.toInt()

                        // === MODERN SCROLL PERFORMANCE SETTINGS ===
                        
                        // Disable offscreen pre-raster - prevents "box glitch" artifacts during scroll
                        offscreenPreRaster = false
                        
                        // High priority rendering for responsive scrolling
                        @Suppress("DEPRECATION")
                        setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                        
                        // Text autosizing layout for optimal mobile rendering
                        layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                            safeBrowsingEnabled = true
                        }
                        
                        // Remove X-Requested-With header to prevent Google OAuth from detecting embedded WebView
                        // Empty allow list = don't send X-Requested-With to any origin
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
                        }
                        
                        // Apply WebView dark mode based on preferences
                        applyWebViewDarkMode(this, preferences, webViewContext, isSystemInDarkMode)

                        // Always accept cookies (incognito clears them on exit)
                        // Google's "One Moment" challenge sets cookies to track completion.
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(webView, true)
                        
                        if (isPrivate) {
                            @Suppress("DEPRECATION")
                            saveFormData = false
                            setGeolocationEnabled(false)
                            cookieManager.removeSessionCookies(null)
                            cookieManager.flush()
                        }
                        
                        // Add Autofill Interface
                        if (!isPrivate && autofillManager != null) {
                            addJavascriptInterface(autofillManager, "AlasAutofill")
                        }
                        
                
                        // Add Blob Downloader Interface
                        addJavascriptInterface(BlobDownloader(context) { base64Data, mimeType ->
                            // Calculate approximate size
                            val size = ((base64Data.length * 3) / 4).toLong()
                            onDownloadRequested(base64Data, null, null, mimeType, size, null, null, webView.title, webView.url)
                        }, "AlasBlobDownloader")
                    }

                    keepScreenOn = true
                    
                    // === SCROLL GLITCH PREVENTION ===
                    // Hide scrollbars - WebView handles scroll internally
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isScrollbarFadingEnabled = true
                    scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                    
                    // Solid background avoids per-frame alpha compositing during scroll
                    setBackgroundColor(android.graphics.Color.WHITE)
                    
                    // Disable overscroll glow for cleaner edge behavior
                    overScrollMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    } else {
                        android.view.View.OVER_SCROLL_NEVER
                    }

                    // Allow third-party cookies to ensure features like Google Translate and OAuth work correctly
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    val webClient = WebClient(
                        context = context,
                        adBlockEnabled = adBlockEnabled,
                        enableBackgroundPlayback = enableBackgroundPlayback,
                        onPageStarted = wrappedOnPageStarted,
                        onPageFinished = wrappedOnPageFinished,
                        onFaviconReceived = onFaviconReceived,
                        onNavigationStateChanged = onNavigationStateChanged,
                        onRenderProcessGoneCb = { onWebViewCrashed(tabId) },
                        onNavigationInitiated = onNavigationInitiated,
                        autofillManager = autofillManager,
                        preferences = preferences,
                        tabId = tabId
                    )
                    webViewClient = webClient
                    
                    // 🛡️ Track user gestures for ad redirect protection
                    // When user touches the WebView, we mark the gesture time to help
                    // distinguish legitimate navigations from ad-triggered redirects
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN ||
                            event.action == android.view.MotionEvent.ACTION_UP) {
                            webClient.onUserGesture()
                        }
                        false // Don't consume the event
                    }

                    webChromeClient = BrowserChromeClient(
                        onProgressChanged = throttledOnProgressChanged,
                        onFaviconReceived = onFaviconReceived,
                        onShowCustomView = onShowCustomView,
                        onHideCustomView = onHideCustomView,
                        onFileChooser = onFileChooser,
                        onVideoPlaying = onVideoPlaying,
                        onCreateWindow = onCreateWindow,
                        preferences = preferences,
                        activity = context.findActivity(),
                        onDownloadRequested = { url, userAgent, contentDisposition, mimeType, contentLength, cookies, referer, pageTitle, pageUrl -> 
                           // Pass through cookies from ChromeClient
                           // Note: ChromeClient doesn't easily give us page title/url directly here easily without context, 
                           // but typically downloads come via setDownloadListener. 
                           // For ChromeClient initiated downloads (rare in this context?), we'll use current webView props.
                           onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength, cookies, referer, webView.title, webView.url)
                        }
                    )

                    // Add download listener
                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                        try {
                            android.util.Log.d("BrowserWebView", "=== DOWNLOAD LISTENER TRIGGERED ===")
                            android.util.Log.d("BrowserWebView", "Download URL: $url")
                            android.util.Log.d("BrowserWebView", "User Agent: $userAgent")
                            android.util.Log.d("BrowserWebView", "Content-Disposition: $contentDisposition")
                            android.util.Log.d("BrowserWebView", "MIME Type: $mimetype")
                            android.util.Log.d("BrowserWebView", "Content Length: $contentLength")
                            
                            if (url.startsWith("blob:")) {
                                // Handle blob URL by extracting data via JS and passing to Java interface
                                val js = """
                                    (function() {
                                        var xhr = new XMLHttpRequest();
                                        xhr.open('GET', '$url', true);
                                        xhr.responseType = 'blob';
                                        xhr.onload = function() {
                                            if (xhr.status === 200) {
                                                var blob = xhr.response;
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    var base64data = reader.result;
                                                    AlasBlobDownloader.onBase64Download(base64data, '$mimetype');
                                                }
                                                reader.readAsDataURL(blob);
                                            }
                                        };
                                        xhr.send();
                                    })();
                                """.trimIndent()
                                evaluateJavascript(js, null)
                                Toast.makeText(context, "Processing download...", Toast.LENGTH_SHORT).show()
                            } else if (url.startsWith("data:")) {
                                // Handle Data URIs directly
                                val size = ((url.length * 3) / 4).toLong()
                                onDownloadRequested(url, userAgent, contentDisposition, mimetype, size, null, webView.url, webView.title, webView.url)
                            } else {
                                // Standard download
                                android.util.Log.d("BrowserWebView", "Processing standard download")
                                val cookies = CookieManager.getInstance().getCookie(url)
                                android.util.Log.d("BrowserWebView", "Cookies: $cookies")
                                android.util.Log.d("BrowserWebView", "Referer (current page): ${webView.url}")
                                android.util.Log.d("BrowserWebView", "Calling onDownloadRequested callback")
                                onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength, cookies, webView.url, webView.title, webView.url)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Add long-press context menu for images and links
                    setOnLongClickListener { view ->
                        val hitTestResult = (view as WebView).hitTestResult
                        when (hitTestResult.type) {
                            WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                val imageUrl = hitTestResult.extra
                                if (!imageUrl.isNullOrEmpty()) {
                                    try {
                                        val builder = android.app.AlertDialog.Builder(context)
                                        builder.setTitle("Image")
                                        builder.setItems(arrayOf("Save Image", "Copy Image URL")) { dialog, which ->
                                            when (which) {
                                                0 -> {
                                                    try {
                                                        // Download image with powerful downloader
                                                        AlasDownloadManager.getInstance(context).startDownload(
                                                            url = imageUrl,
                                                            userAgent = settings.userAgentString,
                                                            contentDisposition = null,
                                                            mimeType = "image/*"
                                                        )
                                                        Toast.makeText(context, "Downloading image...", Toast.LENGTH_SHORT).show()
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                1 -> {
                                                    // Copy URL
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Image URL", imageUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Image URL copied", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        builder.show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            // Handle regular links (anchor tags)
                            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                val linkUrl = hitTestResult.extra
                                if (!linkUrl.isNullOrEmpty()) {
                                    try {
                                        val builder = android.app.AlertDialog.Builder(context)
                                        builder.setTitle("Link")
                                        val options = arrayOf("Open in New Tab", "Copy Link", "Download Link", "Share Link")
                                        builder.setItems(options) { dialog, which ->
                                            when (which) {
                                                0 -> {
                                                    // Open in new tab
                                                    onCreateWindow?.invoke(linkUrl)
                                                }
                                                1 -> {
                                                    // Copy URL
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Link URL", linkUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                                }
                                                2 -> {
                                                    // Download link
                                                    try {
                                                        val cookies = CookieManager.getInstance().getCookie(linkUrl)
                                                        onDownloadRequested(
                                                            linkUrl,
                                                            settings.userAgentString,
                                                            null,
                                                            null,
                                                            0L,
                                                            cookies,
                                                            webView.url,
                                                            webView.title,
                                                            webView.url
                                                        )
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Failed to download", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                3 -> {
                                                    // Share link
                                                    try {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, linkUrl)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                        builder.show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }

                    // Initialize MediaSession if enabled
                    if (enableMediaSession) {
                        try {
                            val mediaManager = WebViewMediaSessionManager(context, this)
                            mediaManager.initialize()
                            mediaManager.monitorPlayback()
                            setTag(com.sun.alasbrowser.R.id.media_session_manager, mediaManager)
                            onMediaSessionCreated?.invoke(null) // Chromium handles MediaSession internally
                        } catch (e: Exception) {
                      
                        }
                    }
                    
                    // RESTORE STATE OR LOAD URL
                    // 1. Check in-memory cache (From recent ON_PAUSE/Recreation)


                    // 2. Check DB restored state (From cold start)
                    else if (restoredState != null) {
                        // Restore state from Bundle (handles history, scroll, form data)
                        restoreState(restoredState)
                        isLoadingInternal = false
                    } 
                    // 3. Initial Load
                    else if (url.isNotEmpty() && this.url.isNullOrEmpty()) {
                        isLoadingInternal = true
                        lastLoadedUrl = url
                        loadUrl(url)
                    }
                }.apply {
                    // Reattach to parent if it was detached
                    (parent as? ViewGroup)?.removeView(this)
                }
            },
        update = { view: WebView ->
            try {
                // PRIORITY: Handle URL loading FIRST for responsive navigation
                val currentUrl = view.url ?: ""
                val originalUrl = view.originalUrl ?: ""
                
                // Check if the prop URL is outdated (WebView navigated internally via user click)
                // If WebView is at a different URL than the prop, and we're loading, don't override
                val isInternalNavigation = internalNavigationUrl.isNotEmpty() && 
                    internalNavigationUrl != url && 
                    (currentUrl == internalNavigationUrl || originalUrl == internalNavigationUrl)
                
                val shouldLoad = if (isInternalNavigation) {
                    // WebView navigated internally - don't reload old prop URL
        
                    false
                } else if (currentUrl.isEmpty() && url.isNotEmpty()) {
                    !isLoadingInternal
                } else {
                    url.isNotEmpty()
                            && url != currentUrl
                            && url != originalUrl
                            && url != lastLoadedUrl
                            && !isLoadingInternal
                }

                if (shouldLoad) {
                    // Check for special schemes before loading
                    if (url.startsWith("market://") || 
                        url.startsWith("intent://") || 
                        url.startsWith("tel:") || 
                        url.startsWith("mailto:") || 
                        url.startsWith("sms:")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                               
                            }
                            // Do NOT load in WebView
                            isLoadingInternal = false
                    } else {
                     
                        isLoadingInternal = true
                        lastLoadedUrl = url
                        view.loadUrl(url)
                    }
                }
                
                
                view.webViewClient = WebClient(
                    context = context,
                    adBlockEnabled = adBlockEnabled,
                    enableBackgroundPlayback = enableBackgroundPlayback,
                    onPageStarted = wrappedOnPageStarted,
                    onPageFinished = wrappedOnPageFinished,
                    onFaviconReceived = onFaviconReceived,
                    onNavigationStateChanged = onNavigationStateChanged,
                    onRenderProcessGoneCb = { detail -> 
                    },
                    onNavigationInitiated = onNavigationInitiated,
                    autofillManager = autofillManager,
                    preferences = preferences,
                    tabId = tabId
                )

                view.webChromeClient = BrowserChromeClient(
                    onProgressChanged = throttledOnProgressChanged,
                    onFaviconReceived = onFaviconReceived,
                    onShowCustomView = onShowCustomView,
                    onHideCustomView = onHideCustomView,
                    onFileChooser = onFileChooser, // Correct parameter name from BrowserWebView signature
                    onVideoPlaying = onVideoPlaying,
                    onCreateWindow = onCreateWindow,
                    preferences = preferences,
                    activity = context.findActivity(),
                    onDownloadRequested = onDownloadRequested
                )

                // Update settings that can change
                view.settings.apply {
                    // Safe defaults
                    javaScriptEnabled = preferences?.javaScriptEnabled != false
                    domStorageEnabled = true // Mandatory for modern web
                    databaseEnabled = !isPrivate
                    
                    // Mixed content (Safe default: COMPATIBILITY_MODE or ALWAYS_ALLOW if user wants)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    // Cache mode
                    cacheMode = if (isPrivate) android.webkit.WebSettings.LOAD_NO_CACHE else android.webkit.WebSettings.LOAD_DEFAULT
                }

                // Update settings that can change
                val desktopUA = com.sun.alasbrowser.engine.ChromiumCompat.DESKTOP_UA
                val mobileUA = com.sun.alasbrowser.engine.ChromiumCompat.cleanMobileUA(context)
                // Force Desktop UA for translation services to avoid mobile-specific blocks/captchas
                val isTranslateUrl = url.contains("translate.google.com") || url.contains("translatetheweb.com")
                val expectedUA = if (desktopMode || isTranslateUrl) desktopUA else mobileUA
                
                if (view.settings.userAgentString != expectedUA) {
                    view.settings.userAgentString = expectedUA
                    
                    // Configure viewport settings for desktop/mobile mode
                    view.settings.loadWithOverviewMode = true
                    view.settings.useWideViewPort = true
                    view.setInitialScale(0) // Let WebView auto-scale
                    
                    // Reload the page to apply the new user agent (desktop/mobile mode switch)
                    view.reload()
                }

                val expectedZoom = zoomLevel.toInt()
                if (view.settings.textZoom != expectedZoom) {
                    view.settings.textZoom = expectedZoom
                }

                // Update permission-based settings
                preferences?.let { prefs ->
                    if (view.settings.javaScriptEnabled != prefs.javaScriptEnabled) {
                        view.settings.javaScriptEnabled = prefs.javaScriptEnabled
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        @Suppress("DEPRECATION")
                        view.settings.mediaPlaybackRequiresUserGesture = !prefs.microphone
                    }
                    view.settings.setGeolocationEnabled(prefs.location)
                    
                    // Reapply dark mode when preferences change
                    applyWebViewDarkMode(view.settings, preferences, view.context, isSystemDark)
                }

                // Inject bottom padding CSS to prevent webpage content from being hidden by browser bottom bar
                // Only inject when padding value actually changes to avoid repeated JS execution
                if (bottomPaddingPx != lastAppliedPadding) {
                    lastAppliedPadding = bottomPaddingPx
                    val paddingJs = """
                        (function() {
                            var styleId = 'alas-browser-bottom-padding';
                            var existingStyle = document.getElementById(styleId);
                            if (existingStyle) existingStyle.remove();
                            if (${bottomPaddingPx} > 0) {
                                var style = document.createElement('style');
                                style.id = styleId;
                                style.textContent = 'body { padding-bottom: ${bottomPaddingPx}px !important; }';
                                if (document.head) document.head.appendChild(style);
                            }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(paddingJs, null)
                }
            } catch (e: Exception) {
          
                isLoadingInternal = false
            }
        },
        onRelease = { view: WebView ->
            // DO NOT detach or destroy the WebView here!
            // onRelease is called when the composable leaves composition (app backgrounded),
            // not when the tab is closed. Detaching causes black screen on resume.
            // WebView cleanup is handled by WebViewCache.remove() when tabs are actually closed.
        }
        )
    }
}

/**
 * Applies WebView dark mode settings based on user preferences.
 * Uses androidx.webkit API for algorithmic darkening support.
 */
private fun applyWebViewDarkMode(
    settings: android.webkit.WebSettings,
    preferences: com.sun.alasbrowser.data.BrowserPreferences?,
    context: Context? = null,
    isSystemDark: Boolean? = null
) {
    val darkMode = preferences?.webViewDarkMode ?: WebViewDarkMode.AUTOMATIC
    
    // Detect if system is in dark mode
    val isSystemInDarkMode = isSystemDark ?: (context?.let {
        (it.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    } == true)
    
    // Check if algorithmic darkening is supported (Android 13+)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) when (darkMode) {
        WebViewDarkMode.LIGHT_PREFERRED -> {
            // Disable algorithmic darkening - websites display in light mode
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            // Also disable FORCE_DARK to ensure light mode
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && 
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            }
        }
        WebViewDarkMode.AUTOMATIC -> {
            // Enable algorithmic darkening to match system state
            // This ensures sites like Wikipedia get darkened even if they don't support it natively
            if (isSystemInDarkMode) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            } else {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            }
            
            // Legacy Force Dark support
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && 
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings,
                    if (isSystemInDarkMode) WebSettingsCompat.FORCE_DARK_ON
                    else WebSettingsCompat.FORCE_DARK_OFF
                )
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    @Suppress("DEPRECATION")
                    // Use PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING to respect native dark mode if available
                    WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING)
                }
            }
        }
        WebViewDarkMode.DARK_PREFERRED -> {
            // Enable algorithmic darkening
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            
            // Legacy Force Dark support
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && 
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY)
                }
            }
        }
    } else {
        // Fallback for Android 10-12 using deprecated FORCE_DARK
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            when (darkMode) {
                WebViewDarkMode.LIGHT_PREFERRED -> {
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                }
                WebViewDarkMode.AUTOMATIC -> {
                    // Force dark mode only if system is in dark mode
                    WebSettingsCompat.setForceDark(settings, 
                        if (isSystemInDarkMode) WebSettingsCompat.FORCE_DARK_ON 
                        else WebSettingsCompat.FORCE_DARK_OFF
                    )
                    // Use web theme only to preserve badges/icons
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        @Suppress("DEPRECATION")
                        WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                    }
                }
                WebViewDarkMode.DARK_PREFERRED -> {
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        @Suppress("DEPRECATION")
                        WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                    }
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Last resort fallback for Android 10-12 - no strategy control available
            @Suppress("DEPRECATION")
            when (darkMode) {
                WebViewDarkMode.LIGHT_PREFERRED -> {
                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                }
                WebViewDarkMode.AUTOMATIC -> {
                    // Force dark mode only if system is in dark mode
                    settings.forceDark = if (isSystemInDarkMode) 
                        android.webkit.WebSettings.FORCE_DARK_ON 
                    else 
                        android.webkit.WebSettings.FORCE_DARK_OFF
                }
                WebViewDarkMode.DARK_PREFERRED -> {
                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_ON
                }
            }
        }
    }
}

/**
 * Helper method to find the Activity from a Context
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Extracts filename from Content-Disposition header, URL, or MIME type.
 * This provides better filename detection than URLUtil.guessFileName
 */
private fun extractFileName(url: String, contentDisposition: String?, mimeType: String?): String {
    var fileName: String? = null
    
    // 1. Try to extract from Content-Disposition header (most reliable)
    if (!contentDisposition.isNullOrBlank()) {
        // Try filename*= (RFC 5987 - encoded filename)
        val encodedPattern = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\s]+)""", RegexOption.IGNORE_CASE)
        fileName = encodedPattern.find(contentDisposition)?.groupValues?.get(1)?.let {
            try {
                java.net.URLDecoder.decode(it, "UTF-8")
            } catch (e: Exception) {
                it
            }
        }
        
        // Try filename= with quotes
        if (fileName.isNullOrBlank()) {
            val quotedPattern = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            fileName = quotedPattern.find(contentDisposition)?.groupValues?.get(1)
        }
        
        // Try filename= without quotes
        if (fileName.isNullOrBlank()) {
            val unquotedPattern = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
            fileName = unquotedPattern.find(contentDisposition)?.groupValues?.get(1)
        }
    }
    
    // 2. Try to extract from URL path
    if (fileName.isNullOrBlank()) {
        try {
            val uri = Uri.parse(url)
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val lastSegment = path.substringAfterLast('/')
                // URL decode the segment
                val decodedSegment = try {
                    java.net.URLDecoder.decode(lastSegment, "UTF-8")
                } catch (e: Exception) {
                    lastSegment
                }
                // Only use if it looks like a filename (has extension)
                if (decodedSegment.contains('.') && !decodedSegment.startsWith("?")) {
                    // Remove query parameters if any
                    fileName = decodedSegment.substringBefore('?').substringBefore('#')
                }
            }
        } catch (e: Exception) {
            // Ignore URL parsing errors
        }
    }
    
    // 3. Fallback to URLUtil.guessFileName but validate the result
    if (fileName.isNullOrBlank()) {
        fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    }
    
    // 4. Clean and validate the filename
    fileName = fileName?.trim()?.replace(Regex("[\\\\/:*?\"<>|]"), "_") ?: "download"
    
    // 5. Ensure proper extension based on MIME type if missing or wrong
    val extension = getExtensionFromMimeType(mimeType)
    if (!fileName.contains('.') && extension != null) {
        fileName = "$fileName.$extension"
    } else if (fileName.endsWith(".bin") && extension != null && extension != "bin") {
        // Replace .bin with correct extension
        fileName = fileName.removeSuffix(".bin") + ".$extension"
    }
    
    return fileName
}

/**
 * Maps MIME types to file extensions
 */
private fun getExtensionFromMimeType(mimeType: String?): String? {
    if (mimeType.isNullOrBlank()) return null
    
    return when {
        // APK / XAPK
        mimeType.contains("vnd.android.package-archive") -> "apk"
        mimeType.contains("android") && mimeType.contains("apk") -> "apk"
        mimeType.equals("application/x-xapk", ignoreCase = true) -> "xapk"
        mimeType.contains("xapk") -> "xapk"
        
        // Documents
        mimeType.contains("pdf") -> "pdf"
        mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> "docx"
        mimeType.contains("spreadsheet") || mimeType.contains("excel") -> "xlsx"
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "pptx"
        mimeType.contains("text/plain") -> "txt"
        mimeType.contains("text/html") -> "html"
        mimeType.contains("text/css") -> "css"
        mimeType.contains("javascript") -> "js"
        mimeType.contains("json") -> "json"
        mimeType.contains("xml") -> "xml"
        
        // Images
        mimeType.contains("image/jpeg") || mimeType.contains("image/jpg") -> "jpg"
        mimeType.contains("image/png") -> "png"
        mimeType.contains("image/gif") -> "gif"
        mimeType.contains("image/webp") -> "webp"
        mimeType.contains("image/svg") -> "svg"
        mimeType.contains("image/bmp") -> "bmp"
        mimeType.contains("image/ico") || mimeType.contains("image/x-icon") -> "ico"
        
        // Audio
        mimeType.contains("audio/mpeg") || mimeType.contains("audio/mp3") -> "mp3"
        mimeType.contains("audio/wav") -> "wav"
        mimeType.contains("audio/ogg") -> "ogg"
        mimeType.contains("audio/flac") -> "flac"
        mimeType.contains("audio/aac") -> "aac"
        mimeType.contains("audio/m4a") -> "m4a"
        
        // Video
        mimeType.contains("video/mp4") -> "mp4"
        mimeType.contains("video/webm") -> "webm"
        mimeType.contains("video/x-matroska") -> "mkv"
        mimeType.contains("video/avi") || mimeType.contains("video/x-msvideo") -> "avi"
        mimeType.contains("video/quicktime") -> "mov"
        mimeType.contains("video/x-flv") -> "flv"
        mimeType.contains("video/3gpp") -> "3gp"
        
        // Archives
        mimeType.contains("zip") -> "zip"
        mimeType.contains("rar") || mimeType.contains("x-rar") -> "rar"
        mimeType.contains("x-7z") -> "7z"
        mimeType.contains("x-tar") -> "tar"
        mimeType.contains("gzip") -> "gz"
        
        // Other
        mimeType.contains("octet-stream") -> null // Don't override, let filename extension win
        
        else -> null
    }
    }

/**
 * Javascript Interface to handle Blob downloads
 */
/**
 * Javascript Interface to handle Blob downloads
 */
class BlobDownloader(
    private val context: Context,
    private val onDownload: (base64Data: String, mimeType: String) -> Unit = { data, mime -> 
        // Default fallback if no callback provided (e.g. from ChromeClient without UI for now)
        AlasDownloadManager.getInstance(context).downloadFromBase64(data, mime, null)
    }
) {
    @JavascriptInterface
    fun onBase64Download(base64Data: String, mimeType: String) {
        onDownload(base64Data, mimeType)
    }
}