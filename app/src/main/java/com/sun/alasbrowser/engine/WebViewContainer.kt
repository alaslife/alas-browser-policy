package com.sun.alasbrowser.engine

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.utils.AutofillManager
import com.sun.alasbrowser.utils.BackgroundPlaybackInjector
import com.sun.alasbrowser.utils.WebViewMediaSessionManager
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "WebViewContainer"

private val webViewCache = mutableMapOf<String, WebView>()

private fun setWebViewAudioMutedSafely(webView: WebView?, muted: Boolean) {
    if (webView == null) return
    val script = if (muted) {
        """
        (function(){
          try {
            document.querySelectorAll('video,audio').forEach(function(m){
              m.muted = true;
              m.dataset.alasMutedByApp = '1';
            });
          } catch(e) {}
        })();
        """.trimIndent()
    } else {
        """
        (function(){
          try {
            document.querySelectorAll('video,audio').forEach(function(m){
              if (m.dataset.alasMutedByApp === '1') {
                m.muted = false;
                delete m.dataset.alasMutedByApp;
              }
            });
          } catch(e) {}
        })();
        """.trimIndent()
    }
    try {
        webView.post { webView.evaluateJavascript(script, null) }
    } catch (_: Exception) {
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    url: String,
    tabId: String,
    isPrivate: Boolean,
    desktopMode: Boolean,
    preferences: BrowserPreferences,
    autofillManager: AutofillManager,
    modifier: Modifier = Modifier,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String, String?) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    onUrlChange: (String) -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onFaviconReceived: (android.graphics.Bitmap?) -> Unit = {},
    onNavigationStateChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
    onDownloadRequested: (url: String, filename: String?, contentType: String?, contentLength: Long, userAgent: String?, contentDisposition: String?, cookie: String?, pageTitle: String?, pageUrl: String?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onOpenInNewTab: (String) -> Unit = {},
    onScrollChanged: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onTouchStateChanged: (Boolean) -> Unit = {},
    onTouchDrag: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val webView = remember(tabId) {
        webViewCache.getOrPut(tabId) {
            createWebView(context, tabId, isPrivate, desktopMode, preferences, autofillManager,
                onPageStarted, onPageFinished, onProgressChanged, onUrlChange, onTitleChange,
                onFaviconReceived, onNavigationStateChanged, onDownloadRequested, onOpenInNewTab, onScrollChanged,
                coroutineScope)
        }
    }

    DisposableEffect(tabId) {
        // When this tab becomes active, resume media monitoring and set visible
        val manager = webView.getTag(com.sun.alasbrowser.R.id.media_session_manager) as? WebViewMediaSessionManager
        manager?.resumeMonitoring()
        setWebViewAudioMutedSafely(webView, false)
        BackgroundPlaybackInjector.setTabVisibility(webView, true)
        
        onDispose {
            // When tab is hidden (e.g. user goes to homepage or switches tab)
            // Pause monitoring to stop aggressive enforcePlayback script.
            // This prevents auto-resume loops when the user wants it paused.
            val mgr = webView.getTag(com.sun.alasbrowser.R.id.media_session_manager) as? WebViewMediaSessionManager
            mgr?.pauseMonitoring()
            setWebViewAudioMutedSafely(webView, true)
            BackgroundPlaybackInjector.setTabVisibility(webView, false)
        }
    }

    // Update desktop mode UA when toggled
    LaunchedEffect(desktopMode) {
        val expectedUA = if (desktopMode) {
            ChromiumCompat.DESKTOP_UA
        } else {
            ChromiumCompat.cleanMobileUA(context)
        }
        if (webView.settings.userAgentString != expectedUA) {
            webView.settings.userAgentString = expectedUA
            webView.settings.loadWithOverviewMode = true
            webView.settings.useWideViewPort = true
            webView.reload()
        }
    }

    // Forward touch state for Opera-style toolbar hide/show
    DisposableEffect(webView, onTouchStateChanged, onTouchDrag) {
        var lastY = 0f
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    onTouchStateChanged(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastY
                    lastY = event.y
                    onTouchDrag(dy)
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    onTouchStateChanged(false)
                }
            }
            false
        }
        onDispose {
            webView.setOnTouchListener(null)
        }
    }

    // Pull-to-refresh state
    val density = LocalDensity.current
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshThresholdPx = with(density) { 100.dp.toPx() }
    val maxPullPx = with(density) { 180.dp.toPx() }
    val topEdgeSlopPx = with(density) { 56.dp.toPx() }

    val pullProgress = (pullDistance / refreshThresholdPx).coerceIn(0f, 1.5f)
    val isActive = pullProgress > 0.02f || isRefreshing

    // Indicator Y position — slides down from top, no page offset
    val indicatorY by animateFloatAsState(
        targetValue = when {
            isRefreshing -> with(density) { 48.dp.toPx() }
            isActive -> (pullDistance * 0.45f).coerceAtMost(with(density) { 64.dp.toPx() })
            else -> with(density) { -40.dp.toPx() }
        },
        animationSpec = spring(
            dampingRatio = if (isActive) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "indicatorY"
    )

    // Scale — pops in, shrinks out
    val indicatorScale by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            pullProgress > 0.15f -> (0.5f + pullProgress * 0.5f).coerceAtMost(1f)
            isActive -> 0.3f
            else -> 0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "indicatorScale"
    )

    // Spinner rotation for refreshing state
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surface

    Box(modifier = modifier) {
        AndroidView(
            factory = { 
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView 
            },
            update = { wv ->
                val currentUrl = wv.url
                if (currentUrl == null || (currentUrl != url && url.isNotEmpty() && url != "about:blank")) {
                    wv.loadUrl(url)
                    Log.d(TAG, "Loading URL in WebView: $url")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabId, webView) {
                    handlePullToRefresh(webView, coroutineScope,
                        { isRefreshing = it },
                        { pullDistance = it },
                        { pullDistance },
                        { isRefreshing },
                        refreshThresholdPx,
                        maxPullPx,
                        topEdgeSlopPx)
                }
        )

        // Opera-style circular refresh indicator
        if (isActive) {
            val triggered = pullProgress >= 1f

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, indicatorY.toInt()) }
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                        alpha = (indicatorScale * 2f).coerceAtMost(1f)
                    }
                    .size(36.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(bgColor)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.5.dp.toPx()
                    val padding = 8.dp.toPx()
                    val arcSize = Size(
                        size.width - padding * 2,
                        size.height - padding * 2
                    )
                    val topLeft = Offset(padding, padding)

                    if (isRefreshing) {
                        // Spinning arc while refreshing
                        rotate(spinAngle) {
                            drawArc(
                                color = accentColor,
                                startAngle = 0f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    } else {
                        // Progress arc while pulling — rotates with pull
                        val sweepAngle = (pullProgress * 300f).coerceAtMost(330f)
                        val rotation = pullProgress * 360f

                        rotate(rotation) {
                            drawArc(
                                color = if (triggered) accentColor
                                    else accentColor.copy(alpha = 0.6f + pullProgress * 0.4f),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Arrow tip when triggered
                        if (triggered) {
                            rotate(rotation) {
                                val arrowTipAngle = -90f + sweepAngle
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val r = arcSize.width / 2f
                                val rad = Math.toRadians(arrowTipAngle.toDouble())
                                val tipX = cx + r * kotlin.math.cos(rad).toFloat()
                                val tipY = cy + r * kotlin.math.sin(rad).toFloat()

                                drawCircle(
                                    color = accentColor,
                                    radius = strokeWidth * 1.2f,
                                    center = Offset(tipX, tipY)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    tabId: String,
    isPrivate: Boolean,
    desktopMode: Boolean,
    preferences: BrowserPreferences,
    autofillManager: AutofillManager,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onFaviconReceived: (android.graphics.Bitmap?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
    onDownloadRequested: (String, String?, String?, Long, String?, String?, String?, String?, String?) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onScrollChanged: (Int, Int, Int) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Hardware acceleration for smooth rendering
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        settings.apply {
            javaScriptEnabled = preferences.javaScriptEnabled
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

            // Performance: render priority & offscreen pre-raster
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                WebSettingsCompat.setOffscreenPreRaster(this, true)
            }
            
            // UA from centralized ChromiumCompat — no WebView markers
            userAgentString = if (desktopMode) {
                ChromiumCompat.DESKTOP_UA
            } else {
                ChromiumCompat.cleanMobileUA(context)
            }

            textZoom = preferences.textSize
            
            allowUniversalAccessFromFileURLs = false
            allowFileAccessFromFileURLs = false
            
            // Remove X-Requested-With header
            try {
                if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting RequestedWithHeader allowlist", e)
            }
        }

        // Disable debugging in release builds
        WebView.setWebContentsDebuggingEnabled(false)

        // Chrome compatibility: anti-detection + stripped headers + cookies
        ChromiumCompat.applyFullChromeCompat(this, isPrivate)

        // Incognito: clear session cookies (ChromiumCompat already enabled cookie acceptance)
        if (isPrivate) {
            CookieManager.getInstance().apply {
                removeSessionCookies(null)
                flush()
            }
        }

        // WebViewClient
        webViewClient = object : WebViewClient() {
            private var isInAuthMode = false
            private var authSessionActive = false
            private var lastPageLoadTime = 0L
            private var lastRealUrl: String? = null
            private var lastNavigationTime = 0L
            private var pendingRedirectReset = false
            private var rapidLoadCount = 0
            private var lastNavigationHadGesture = false
            private var lastBlockedRedirect: String? = null
            private val blankRecoveryHosts = mutableSetOf<String>()
            private val browserUserAgent = settings.userAgentString

            private fun enableAuthMode(view: WebView?) {
                if (isInAuthMode || view == null) return
                isInAuthMode = true
                // Keep the clean Chrome-like UA (no "wv" marker).
                // Google blocks OAuth from embedded WebViews when it detects "; wv" in the UA.
                view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                Log.d(TAG, "Auth mode enabled")
            }

            private fun forceBackToLastRealPage(view: WebView) {
                val safeUrl = lastRealUrl ?: return
                if (pendingRedirectReset) return
                pendingRedirectReset = true
                Log.d(TAG, "Redirect hijack detected - resetting to: $safeUrl")
                view.stopLoading()
                view.loadUrl(safeUrl)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastNavigationHadGesture = false
                
                url?.let { pageUrl ->
                    if (pageUrl == "about:blank") return@let

                    val now = System.currentTimeMillis()
                    val timeSince = now - lastPageLoadTime

                    // Detect navigation loops
                    if (timeSince in 50..400) {
                        rapidLoadCount++
                    } else {
                        rapidLoadCount = 0
                    }
                    
                    if (rapidLoadCount > 8) {
                        Log.d(TAG, "Navigation loop detected - still capturing URL: $pageUrl")
                        // Still capture the URL even if we stop loading
                        onPageStarted(pageUrl)
                        onUrlChange(pageUrl)
                        view?.stopLoading()
                        rapidLoadCount = 0
                        return@let
                    }

                    val authDecision = com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(pageUrl)
                    if (authDecision.active) {
                        authSessionActive = true
                        enableAuthMode(view)
                        lastPageLoadTime = now
                        lastRealUrl = pageUrl
                        lastNavigationTime = now
                        onPageStarted(pageUrl)
                        onUrlChange(pageUrl)
                        return@let
                    }
                    authSessionActive = isOemAccountHost(extractHost(pageUrl))
                    if (isInAuthMode) {
                        isInAuthMode = false
                    }

                    // Handle redirect blocking
                    val allowAggressiveRedirectsForSite =
                        com.sun.alasbrowser.web.SiteCompatibilityRegistry.shouldAllowAggressiveRedirects(pageUrl) ||
                        (lastRealUrl?.let {
                            com.sun.alasbrowser.web.SiteCompatibilityRegistry.shouldAllowAggressiveRedirects(it)
                        } == true)

                    if (preferences.adBlockEnabled && lastRealUrl != null && timeSince < 800 &&
                        !allowAggressiveRedirectsForSite &&
                        !lastNavigationHadGesture && !pendingRedirectReset) {
                        
                        val prevHost = extractHost(lastRealUrl!!)
                        val newHost = extractHost(pageUrl)
                        val isAuthOrOemFlow =
                            com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(lastRealUrl!!).active ||
                            com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(pageUrl).active ||
                            isOemAccountHost(prevHost) ||
                            isOemAccountHost(newHost)
                        
                        if (prevHost.isNotEmpty() && newHost.isNotEmpty() && prevHost != newHost && 
                            !pageUrl.startsWith(lastRealUrl!!) && !isAuthOrOemFlow) {
                            
                            val classification = com.sun.alasbrowser.web.SmartBackEngine.onNavigationEvent(
                                tabId, pageUrl, lastRealUrl!!, false, timeSince
                            )
                            
                            if (classification in listOf(
                                    com.sun.alasbrowser.web.NavigationClassification.AD_REDIRECT,
                                    com.sun.alasbrowser.web.NavigationClassification.YOUTUBE_AD_REDIRECT,
                                    com.sun.alasbrowser.web.NavigationClassification.SUSPICIOUS_REDIRECT
                                ) && view != null && !SimpleAdBlocker.isEssential(pageUrl)) {
                                
                                if (pageUrl == lastBlockedRedirect) {
                                    Log.d(TAG, "Repeated redirect ignored: $pageUrl")
                                    // Still capture the URL even if redirect is ignored
                                    onPageStarted(pageUrl)
                                    onUrlChange(pageUrl)
                                    return@let
                                }
                                lastBlockedRedirect = pageUrl
                                // Capture the URL before blocking redirect
                                onPageStarted(pageUrl)
                                onUrlChange(pageUrl)
                                forceBackToLastRealPage(view)
                                return@let
                            }
                        }
                    }

                    com.sun.alasbrowser.web.SmartBackEngine.onNavigationEvent(
                        tabId, pageUrl, lastRealUrl, true, timeSince
                    )
                    
                    lastPageLoadTime = now
                    lastRealUrl = pageUrl
                    lastNavigationTime = now
                    onPageStarted(pageUrl)
                    onUrlChange(pageUrl)

                    // Brave-style: inject EARLY ad blocker before YouTube JS loads
                    if (preferences.adBlockEnabled && view != null) {
                        val h = extractHost(pageUrl)
                        if (h.contains("youtube.com") || h.contains("youtu.be") ||
                            h.contains("music.youtube.com")) {
                            com.sun.alasbrowser.web.YouTubeAdBlocker.injectEarlyBlocker(view)
                        }
                    }
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                // Re-inject early blocker at commit time in case onPageStarted was too early
                if (preferences.adBlockEnabled && view != null && url != null) {
                    val h = extractHost(url)
                    if (h.contains("youtube.com") || h.contains("youtu.be") ||
                        h.contains("music.youtube.com")) {
                        com.sun.alasbrowser.web.YouTubeAdBlocker.injectEarlyBlocker(view)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pendingRedirectReset = false
                lastBlockedRedirect = null
                view?.postInvalidate()
                
                url?.let { pageUrl ->
                    if (pageUrl == "about:blank") return@let

                    val authDecision = com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(pageUrl)
                    val pageHost = extractHost(pageUrl)
                    val isOemPage = isOemAccountHost(pageHost)
                    authSessionActive = authDecision.active || isOemPage
                    if (authDecision.active && view != null) {
                        enableAuthMode(view)
                        if (authDecision.allowStoragerelay) {
                            try {
                                view.evaluateJavascript(
                                    com.sun.alasbrowser.web.AuthCompatibilityEngine.getStoragerelayShimScript(), 
                                    null
                                )
                                view.evaluateJavascript("""
                                    (function() {
                                        if (window.__alasGsiPatch) return;
                                        window.__alasGsiPatch = true;
                                        window.addEventListener('message', function(e) {
                                            try {
                                                if (e.data && typeof e.data === 'string' && e.data.indexOf('gsi') !== -1) {
                                                    document.querySelectorAll('iframe[src*="accounts.google"]').forEach(function(f) {
                                                        try { f.contentWindow.postMessage(e.data, '*'); } catch(ex) {}
                                                    });
                                                }
                                            } catch(ex) {}
                                        });
                                    })();
                                """.trimIndent(), null)
                            } catch (_: Exception) {}
                        }
                    }

                    if (!authDecision.active && !isOemPage && !authSessionActive) {
                        view?.evaluateJavascript("""
                            (function() {
                                if (window.__alasHistoryProtected) return;
                                window.__alasHistoryProtected = true;
                                var pushCount = 0;
                                var origPush = history.pushState;
                                var origReplace = history.replaceState;
                                history.pushState = function() {
                                    pushCount++;
                                    if (pushCount > 3) { return; }
                                    return origPush.apply(this, arguments);
                                };
                                history.replaceState = function() {
                                    return origReplace.apply(this, arguments);
                                };
                            })();
                        """.trimIndent(), null)
                    }

                    // Background playback support
                    if (preferences.backgroundPlaybackEnabled && view != null && !authSessionActive) {
                        BackgroundPlaybackInjector.injectBackgroundPlaybackScript(view)
                    }

                    // Do not inject adblock compatibility JS on auth/account pages or compatibility-sensitive sites.
                    val skipCompatInjection = authSessionActive ||
                        authDecision.active ||
                        isOemAccountHost(pageHost) ||
                        com.sun.alasbrowser.web.SiteCompatibilityRegistry.shouldDisableJsInjection(pageUrl)

                    // Inject YouTube ad blocker on YouTube pages
                    if (preferences.adBlockEnabled && view != null && !skipCompatInjection) {
                        if (pageHost.contains("youtube.com") || pageHost.contains("youtu.be") || 
                            pageHost.contains("music.youtube.com")) {
                            view.postDelayed({
                                com.sun.alasbrowser.web.YouTubeAdBlocker.injectYouTubeAdBlocker(view)
                            }, 100)
                        }
                        // Inject cosmetic ad hiding for all pages
                        view.postDelayed({
                            view.evaluateJavascript(SimpleAdBlocker.getBlockerScript(), null)
                        }, 800)
                    }

                    // Universal self-healing: if page appears blank, relax compatibility for host and reload once.
                    if (preferences.adBlockEnabled && view != null && !skipCompatInjection && pageHost.isNotEmpty()) {
                        view.postDelayed({
                            view.evaluateJavascript(
                                """
                                (function() {
                                    try {
                                        var b = document.body;
                                        if (!b) return false;
                                        var txt = (b.innerText || '').trim();
                                        var hasVisual = !!document.querySelector('img,video,canvas,svg,iframe,object,embed');
                                        var nodeCount = b.querySelectorAll('*').length;
                                        var docEl = document.documentElement;
                                        var h = Math.max(
                                            (docEl && docEl.scrollHeight) || 0,
                                            b.scrollHeight || 0,
                                            b.clientHeight || 0
                                        );
                                        // Heuristic: no readable text, no visual content, tiny DOM tree and low content height.
                                        return txt.length == 0 && !hasVisual && nodeCount < 40 && h < 1000;
                                    } catch (e) {
                                        return false;
                                    }
                                })();
                                """.trimIndent()
                            ) { raw ->
                                val isBlank = raw?.trim()?.removePrefix("\"")?.removeSuffix("\"") == "true"
                                if (isBlank && blankRecoveryHosts.add(pageHost)) {
                                    Log.w(TAG, "Blank-page detected on $pageHost, applying runtime compatibility bypass")
                                    com.sun.alasbrowser.web.SiteCompatibilityRegistry.addRuntimeBypassForHost(
                                        pageHost,
                                        "blank-page-auto-recovery"
                                    )
                                    com.sun.alasbrowser.web.SimpleAdBlocker.addExcludedSite(pageHost)
                                    view.post {
                                        try {
                                            view.stopLoading()
                                            view.reload()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }, 900)
                    }

                    onPageFinished(pageUrl, view?.title)
                    onTitleChange(view?.title ?: "")
                    onNavigationStateChanged(
                        com.sun.alasbrowser.web.SmartBackEngine.canGoBack(tabId),
                        view?.canGoForward() ?: false
                    )
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val requestUrl = request?.url?.toString() ?: return false
                val hasGesture = request.isForMainFrame && request.hasGesture()
                lastNavigationHadGesture = hasGesture
                val now = System.currentTimeMillis()
                val timeSince = now - lastNavigationTime
                
                val authDecision = com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(requestUrl)
                if (authDecision.active) {
                    authSessionActive = true
                    enableAuthMode(view)
                    lastNavigationTime = now
                    // Let WebView dispatch onPageStarted for the final committed navigation.
                    onProgressChanged(1)
                    return false
                }

                val requestHost = extractHost(requestUrl)
                val currentUrl = view?.url ?: ""
                val currentHost = extractHost(currentUrl)
                val isAuthOrOemRequest =
                    authSessionActive ||
                    isOemAccountHost(requestHost) ||
                    isOemAccountHost(currentHost) ||
                    com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(currentUrl).active
                if (isAuthOrOemRequest) {
                    // Keep auth/account redirects native. Replaying callback endpoints with
                    // loadUrl() can invalidate one-time auth codes and cause 403/Forbidden.
                    lastNavigationTime = now
                    return false
                }
                
                // Handle storagerelay scheme
                if (requestUrl.startsWith("storagerelay://")) {
                    Log.d(TAG, "Handling storagerelay scheme: $requestUrl")
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                var uri = '$requestUrl';
                                var parts = uri.split('#');
                                if (parts.length > 1) {
                                    var params = new URLSearchParams(parts[1]);
                                    var message = params.get('message');
                                    if (message && window.opener) {
                                        window.opener.postMessage(message, '*');
                                    }
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                    return true
                }
                
                // Handle intent:// scheme
                if (requestUrl.startsWith("intent://")) {
                    if (handleExternalSchemeUrl(context, view, requestUrl)) return true
                    return true
                }
                
                // Handle download URLs
                if (isDownloadUrl(requestUrl)) {
                    Log.d(TAG, "Download URL detected: $requestUrl")
                    val cookie = CookieManager.getInstance().getCookie(requestUrl)
                    onDownloadRequested(requestUrl, null, null, -1L, 
                        view?.settings?.userAgentString, null, cookie, view?.title, view?.url)
                    return true
                }

                // Block ad redirects
                if (preferences.adBlockEnabled && !hasGesture && timeSince < 800) {
                    val classification = com.sun.alasbrowser.web.SmartBackEngine.onNavigationEvent(
                        tabId, requestUrl, currentUrl, false, timeSince
                    )
                    
                    if (classification in listOf(
                            com.sun.alasbrowser.web.NavigationClassification.AD_REDIRECT,
                            com.sun.alasbrowser.web.NavigationClassification.YOUTUBE_AD_REDIRECT
                        ) && !SimpleAdBlocker.isEssential(requestUrl)) {
                        Log.d(TAG, "Blocked redirect ad: $requestUrl")
                        return true
                    }
                }
                
                lastNavigationTime = now
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request == null || request.url == null) return null

                val url = request.url.toString()
                val lowerUrl = url.lowercase()
                val host = request.url.host?.lowercase() ?: ""
                val pageUrl = lastRealUrl ?: ""
                val pageHost = extractHost(pageUrl)

                // Realme OBUS analytics/telemetry - non-essential tracking requests.
                // Return fake success to prevent infinite retry spam from their SDK.
                if (host == "obus-in.dc.heytapmobile.com") {
                    val corsHeaders = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Credentials" to "true",
                        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                        "Access-Control-Allow-Headers" to "Origin, X-Requested-With, Content-Type, Accept, Authorization",
                        "Access-Control-Max-Age" to "86400"
                    )
                    if (request.method.equals("OPTIONS", ignoreCase = true)) {
                        return WebResourceResponse(
                            "text/plain", "utf-8", 204, "No Content",
                            corsHeaders, ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return WebResourceResponse(
                        "application/json", "utf-8", 200, "OK",
                        corsHeaders, ByteArrayInputStream("{\"code\":200}".toByteArray())
                    )
                }

                // Intercept Google image requests
                val isGoogleImage = host.contains("googleusercontent.com") ||
                    host.contains("ggpht.com") ||
                    host.contains("ytimg.com")

                val acceptHeader = request.requestHeaders?.get("Accept") ?: ""
                val looksLikeImage = lowerUrl.endsWith(".png") ||
                    lowerUrl.endsWith(".jpg") ||
                    lowerUrl.endsWith(".jpeg") ||
                    lowerUrl.endsWith(".webp") ||
                    lowerUrl.endsWith(".gif") ||
                    lowerUrl.endsWith(".svg") ||
                    lowerUrl.endsWith(".avif") ||
                    lowerUrl.endsWith(".ico")
                
                val isImageRequest = acceptHeader.contains("image") || looksLikeImage

                if (isGoogleImage && isImageRequest && request.method.equals("GET", ignoreCase = true) && !authSessionActive) {
                    return interceptGoogleImageRequest(request, url, browserUserAgent)
                }

                // Never block Google/CAPTCHA domains (breaks "One Moment" challenge)
                if (ChromiumCompat.isAllowlistedHost(host)) return null

                // Note: Do NOT intercept realme auth callbacks here.
                // The proxy + followRedirects causes infinite redirect loops.

                // Skip blocking for auth and OEM domains
                val authDecision = com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(url)
                if (authDecision.active) return null
                if (authSessionActive) return null
                val parentUrl = lastRealUrl ?: ""
                val parentHost = extractHost(parentUrl)
                if (parentUrl.isNotEmpty() &&
                    (com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(parentUrl).active ||
                        isOemAccountHost(parentHost))
                ) {
                    return null
                }

                if (lowerUrl.contains("heytap") || lowerUrl.contains("heytapmobile") ||
                    lowerUrl.contains("realme") || lowerUrl.contains("realmemobile") ||
                    lowerUrl.contains("oppo") || lowerUrl.contains("oneplus") ||
                    lowerUrl.contains("obus")) {
                    return null
                }

                if (request.isForMainFrame) return null
                if (!preferences.adBlockEnabled) return null

                // Compatibility-sensitive sites (APK/Download portals) can blank out with aggressive interception.
                val compatibilitySensitive = com.sun.alasbrowser.web.SiteCompatibilityRegistry.shouldDisableJsInjection(url) ||
                    (parentUrl.isNotEmpty() &&
                        com.sun.alasbrowser.web.SiteCompatibilityRegistry.shouldDisableJsInjection(parentUrl))
                if (compatibilitySensitive) return null

                // Brave-style: block YouTube ad/tracking requests at network level
                if (!SimpleAdBlocker.isEssential(url)) {
                    // Block known ad domains
                    if (host == "googleads.g.doubleclick.net" ||
                        host == "pubads.g.doubleclick.net" ||
                        host == "pagead2.googlesyndication.com" ||
                        host == "securepubads.g.doubleclick.net" ||
                        host.endsWith(".doubleclick.net") ||
                        host.endsWith(".googlesyndication.com") ||
                        host.endsWith(".googleadservices.com") ||
                        host == "www.google-analytics.com" ||
                        host == "ad.doubleclick.net" ||
                        host == "static.doubleclick.net") {
                        return WebResourceResponse(
                            "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    // Block YouTube ad-specific URL paths
                    if (SimpleAdBlocker.YOUTUBE_AD_PATTERNS.any { lowerUrl.contains(it) }) {
                        return WebResourceResponse(
                            "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                // Block ads - use both SimpleAdBlocker and AdBlockEngine
                if (SimpleAdBlocker.shouldBlock(url) || SimpleAdBlocker.isPopupAd(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                // Advanced engine blocking (domain + pattern matching)
                val adBlockResponse = com.sun.alasbrowser.web.adblock.WebViewAdBlockAdapter
                    .shouldInterceptRequest(request, lastRealUrl)
                if (adBlockResponse != null) return adBlockResponse

                return null
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (error?.errorCode == -10) {
                    val schemeUrl = request?.url?.toString()
                    if (schemeUrl != null) {
                        Log.d(TAG, "Intercepting ERR_UNKNOWN_URL_SCHEME for: $schemeUrl")
                        if (handleExternalSchemeUrl(context, view, schemeUrl)) return
                    }
                }
                
                super.onReceivedError(view, request, error)
                
                if (request?.isForMainFrame == true && view != null) {
                    val errorCode = error?.errorCode ?: -1
                    val description = error?.description?.toString() ?: "Unknown error"
                    val failedUrl = request.url?.toString() ?: ""
                    Log.e(TAG, "Network error ($errorCode): $description for $failedUrl")
                    
                    view.loadDataWithBaseURL(
                        null,
                        buildErrorPage(errorCode, description, failedUrl),
                        "text/html",
                        "utf-8",
                        failedUrl
                    )
                    onNavigationStateChanged(
                        com.sun.alasbrowser.web.SmartBackEngine.canGoBack(tabId),
                        view.canGoForward()
                    )
                }
            }

            @Deprecated("Deprecated in API level 23")
            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (errorCode == -10 && failingUrl != null) {
                    if (handleExternalSchemeUrl(context, view, failingUrl)) return
                }
                
                super.onReceivedError(view, errorCode, description, failingUrl)
                
                if (view != null && failingUrl != null) {
                    Log.e(TAG, "Network error ($errorCode): $description for $failingUrl")
                    view.loadDataWithBaseURL(
                        null,
                        buildErrorPage(errorCode, description ?: "Unknown error", failingUrl),
                        "text/html",
                        "utf-8",
                        failingUrl
                    )
                    onNavigationStateChanged(
                        com.sun.alasbrowser.web.SmartBackEngine.canGoBack(tabId),
                        view.canGoForward()
                    )
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "HTTP error ${errorResponse?.statusCode} for ${request.url}")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Log.e(TAG, "SSL error: ${error?.primaryError} for ${error?.url}")
                handler?.cancel()
                val failedUrl = error?.url ?: ""
                view?.loadDataWithBaseURL(
                    null,
                    buildErrorPage(-1, "SSL certificate error - connection is not secure", failedUrl),
                    "text/html",
                    "utf-8",
                    failedUrl
                )
            }
        }

        // WebChromeClient
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onTitleChange(it) }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                // Pass the favicon to the callback so it can be stored with the tab
                onFaviconReceived(icon)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (view == null || resultMsg == null) return false
                
                val parentUrl = view.url ?: ""
                val parentIsAuth = com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(parentUrl).active
                
                // Block script-initiated popups (no user gesture) — almost always ads
                if (!isUserGesture && !parentIsAuth) {
                    Log.d(TAG, "🚫 Blocked script-initiated popup (no user gesture) from: $parentUrl")
                    return false
                }
                
                return handleCreateWindow(view, parentIsAuth, resultMsg, context,
                    onDownloadRequested, onOpenInNewTab)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val activity = context as? android.app.Activity ?: run {
                    req.deny()
                    return
                }

                val origin = req.origin?.toString().orEmpty()
                val domain = preferences.extractDomain(origin) ?: run {
                    req.deny()
                    return
                }

                val wantsMic = req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                val wantsCam = req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                val savedMicPref = preferences.isSiteMicrophoneAllowed(domain)
                val savedCamPref = preferences.isSiteCameraAllowed(domain)
                val hasExistingDecision =
                    (wantsMic && savedMicPref != null) ||
                        (wantsCam && savedCamPref != null) ||
                        (!wantsMic && !wantsCam)

                if (hasExistingDecision) {
                    applySitePermissionsForRequest(req, domain, preferences, activity)
                    return
                }

                val message = when {
                    wantsMic && wantsCam -> "$domain wants to use your camera and microphone."
                    wantsCam -> "$domain wants to use your camera."
                    wantsMic -> "$domain wants to use your microphone."
                    else -> {
                        applySitePermissionsForRequest(req, domain, preferences, activity)
                        return
                    }
                }

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) {
                        try { req.deny() } catch (_: Exception) {}
                        return@runOnUiThread
                    }
                    try {
                        android.app.AlertDialog.Builder(activity)
                            .setTitle("Permission Request")
                            .setMessage(message)
                            .setPositiveButton("Allow") { _, _ ->
                                if (wantsMic) preferences.setSiteMicrophonePermission(domain, true)
                                if (wantsCam) preferences.setSiteCameraPermission(domain, true)
                                applySitePermissionsForRequest(req, domain, preferences, activity)
                            }
                            .setNegativeButton("Block") { _, _ ->
                                if (wantsMic) preferences.setSiteMicrophonePermission(domain, false)
                                if (wantsCam) preferences.setSiteCameraPermission(domain, false)
                                try { req.deny() } catch (_: Exception) {}
                            }
                            .setOnCancelListener {
                                try { req.deny() } catch (_: Exception) {}
                            }
                            .setCancelable(true)
                            .show()
                    } catch (_: Exception) {
                        try { req.deny() } catch (_: Exception) {}
                    }
                }
            }
        }

        // Initialize MediaSession if background playback is enabled
        if (preferences.backgroundPlaybackEnabled) {
            try {
                val mediaManager = WebViewMediaSessionManager(context, this)
                mediaManager.initialize()
                mediaManager.monitorPlayback()
                setTag(com.sun.alasbrowser.R.id.media_session_manager, mediaManager)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaSession", e)
            }
        }

        // Download listener
        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
            val cookie = CookieManager.getInstance().getCookie(downloadUrl)
            onDownloadRequested(
                downloadUrl,
                null,
                mimetype,
                contentLength,
                userAgent,
                contentDisposition,
                cookie,
                this.title,
                this.url
            )
        }

        // Scroll listener
        setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentHeight = (this.contentHeight * this.scale).toInt()
            val viewHeight = this.height
            onScrollChanged(scrollY, contentHeight - viewHeight, viewHeight)
        }

        // Native stretch overscroll on API 31+ (Opera-style rubber band), disabled on older (ugly glow)
        overScrollMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            android.view.View.OVER_SCROLL_NEVER
        }
        isScrollbarFadingEnabled = true
        scrollBarFadeDuration = 250
        scrollBarDefaultDelayBeforeFade = 400
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        // Autofill interface
        addJavascriptInterface(autofillManager, "AlasAutofill")

        Log.d(TAG, "WebView created for tab $tabId")
    }
}

private fun interceptGoogleImageRequest(
    request: WebResourceRequest,
    url: String,
    userAgent: String
): WebResourceResponse? {
    var connection: HttpURLConnection? = null
    return try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection!!.run {
            requestMethod = request.method ?: "GET"
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 15000
            
            // Copy headers but strip X-Requested-With and Referer
            request.requestHeaders?.forEach { (key, value) ->
                if (value != null &&
                    !key.equals("X-Requested-With", ignoreCase = true) &&
                    !key.equals("Referer", ignoreCase = true)
                ) {
                    setRequestProperty(key, value)
                }
            }
            
            setRequestProperty("User-Agent", userAgent)
            
            // Sync cookies
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                setRequestProperty("Cookie", cookies)
            }

            val responseCode = responseCode

            // Sync Set-Cookie headers
            headerFields?.forEach { (key, values) ->
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    values.forEach { cookie ->
                        CookieManager.getInstance().setCookie(url, cookie)
                    }
                }
            }
            CookieManager.getInstance().flush()

            val contentType = contentType ?: "text/html"
            val mimeType = contentType.split(";").firstOrNull()?.trim() ?: "text/html"
            val encoding = if (contentType.contains("charset=")) {
                contentType.substringAfter("charset=").trim()
            } else "utf-8"

            val inputStream = if (responseCode in 200..299) {
                inputStream
            } else {
                errorStream ?: ByteArrayInputStream(ByteArray(0))
            }

            val responseHeaders = mutableMapOf<String, String>()
            headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.last()
                }
            }
            
            WebResourceResponse(mimeType, encoding, responseCode,
                responseMessage ?: "OK", responseHeaders, inputStream)
        }
    } catch (e: Exception) {
        Log.d(TAG, "Google image intercept failed: ${e.message}")
        null
    } finally {
        // Don't close connection - stream will be consumed by WebView
        // connection?.disconnect()
    }
}

private fun interceptAuthCallback(
    request: WebResourceRequest,
    url: String,
    userAgent: String
): WebResourceResponse? {
    return try {
        Log.d(TAG, "Intercepting auth callback: $url")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.run {
            requestMethod = request.method ?: "GET"
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 15000

            request.requestHeaders?.forEach { (key, value) ->
                if (value != null &&
                    !key.equals("X-Requested-With", ignoreCase = true)
                ) {
                    setRequestProperty(key, value)
                }
            }

            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Sec-Fetch-Dest", "document")
            setRequestProperty("Sec-Fetch-Mode", "navigate")
            setRequestProperty("Sec-Fetch-Site", "same-origin")
            setRequestProperty("Sec-Fetch-User", "?1")

            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                setRequestProperty("Cookie", cookies)
            }

            val responseCode = responseCode

            headerFields?.forEach { (key, values) ->
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    values.forEach { cookie ->
                        CookieManager.getInstance().setCookie(url, cookie)
                    }
                }
            }
            CookieManager.getInstance().flush()

            val contentType = contentType ?: "text/html"
            val mimeType = contentType.split(";").firstOrNull()?.trim() ?: "text/html"
            val encoding = if (contentType.contains("charset=")) {
                contentType.substringAfter("charset=").trim()
            } else "utf-8"

            val inputStream = if (responseCode in 200..299) {
                inputStream
            } else {
                errorStream ?: ByteArrayInputStream(ByteArray(0))
            }

            val responseHeaders = mutableMapOf<String, String>()
            headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.last()
                }
            }

            Log.d(TAG, "Auth callback response: $responseCode")
            WebResourceResponse(mimeType, encoding, responseCode,
                responseMessage ?: "OK", responseHeaders, inputStream)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Auth callback intercept failed: ${e.message}")
        null
    }
}

private fun handleCreateWindow(
    parentView: WebView,
    parentIsAuth: Boolean,
    resultMsg: Message,
    context: android.content.Context,
    onDownloadRequested: (String, String?, String?, Long, String?, String?, String?, String?, String?) -> Unit,
    onOpenInNewTab: (String) -> Unit
): Boolean {
    val tempWebView = object : WebView(parentView.context) {
        override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = parentView.settings.userAgentString
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = true
        setWebViewAudioMutedSafely(this, true)
        
        if (parentIsAuth) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, """
                if (!window.opener) {
                    window.opener = {
                        postMessage: function(message, targetOrigin) {
                            try {
                                _oauthBridge.relayMessage(JSON.stringify(message), targetOrigin || '*');
                            } catch(e) {}
                        },
                        closed: false
                    };
                }
            """.trimIndent(), setOf("*"))
        }
        
        // Remove X-Requested-With header
        try {
            if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting RequestedWithHeader allowlist in popup", e)
        }
    }
    
    val activity = context as? android.app.Activity
    val rootContent = activity?.findViewById<android.widget.FrameLayout>(android.R.id.content)
    var popupAttached = false
    var popupContainer: android.widget.LinearLayout? = null
    
    fun removePopupFromParent() {
        CookieManager.getInstance().flush()
        if (popupAttached) {
            popupAttached = false
            try { 
                if (popupContainer != null) {
                    rootContent?.removeView(popupContainer) 
                } else {
                    rootContent?.removeView(tempWebView)
                }
            } catch (_: Exception) {}
        }
        try { tempWebView.destroy() } catch (_: Exception) {}
    }
    
    // Download listener
    tempWebView.setDownloadListener { dlUrl, dlUserAgent, dlContentDisposition, dlMimetype, dlContentLength ->
        val cookie = CookieManager.getInstance().getCookie(dlUrl)
        onDownloadRequested(dlUrl, null, dlMimetype, dlContentLength, dlUserAgent, 
            dlContentDisposition, cookie, parentView.title, parentView.url)
        removePopupFromParent()
    }
    
    var isOAuthPopup = parentIsAuth
    
    // OAuth message bridge (unchanged logic, just context)
    class OAuthMessageBridge {
        @android.webkit.JavascriptInterface
        fun relayMessage(message: String, targetOrigin: String) {
            Log.d(TAG, "OAuth bridge - relaying postMessage to parent")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                parentView.evaluateJavascript("""
                    (function() {
                        try {
                            var msg = $message;
                            window.postMessage(msg, '*');
                        } catch(e) {
                            try {
                                window.postMessage($message, '*');
                            } catch(e2) {}
                        }
                    })();
                """.trimIndent(), null)
            }
        }
    }
    tempWebView.addJavascriptInterface(OAuthMessageBridge(), "_oauthBridge")
    
    tempWebView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val targetUrl = request?.url?.toString() ?: return false
            
            if (targetUrl.startsWith("storagerelay://")) {
                Log.d(TAG, "Handling storagerelay in popup: $targetUrl")
                CookieManager.getInstance().flush()
                parentView.evaluateJavascript("""
                    (function() {
                        try {
                            var uri = '$targetUrl';
                            var parts = uri.split('#');
                            if (parts.length > 1) {
                                var params = new URLSearchParams(parts[1]);
                                var message = params.get('message');
                                if (message) {
                                    window.postMessage(message, '*');
                                }
                            }
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
                removePopupFromParent()
                return true
            }
            
            // OAuth/Auth URLs: keep in popup WebView for window.opener support
            if (isOAuthPopup || com.sun.alasbrowser.web.AuthCompatibilityEngine.evaluate(targetUrl).active) {
                if (!isOAuthPopup) {
                    isOAuthPopup = true
                }
                
                if (!popupAttached && rootContent != null) {
                    popupAttached = true
                    
                    val density = context.resources.displayMetrics.density
                    
                    // Get system bar insets from the root view
                    val rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets(rootContent)
                    val systemBars = rootInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    val statusBarHeight = systemBars?.top ?: run {
                        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
                        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
                    }
                    val navBarHeight = systemBars?.bottom ?: run {
                        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
                        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
                    }
                    
                    val container = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(0xFF000000.toInt())
                        isClickable = true
                        isFocusable = true
                        setPadding(0, statusBarHeight, 0, navBarHeight)
                    }
                    popupContainer = container

                    val header = android.widget.RelativeLayout(context).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (52 * density).toInt()
                        )
                        setBackgroundColor(0xFF1a1a1a.toInt())
                    }
                    
                    val closeBtn = android.widget.TextView(context).apply {
                        id = android.view.View.generateViewId()
                        text = "✕"
                        textSize = 18f
                        setTextColor(0xFFaaaaaa.toInt())
                        gravity = android.view.Gravity.CENTER
                        layoutParams = android.widget.RelativeLayout.LayoutParams(
                            (52 * density).toInt(),
                            (52 * density).toInt()
                        ).apply {
                            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                        }
                        setOnClickListener { removePopupFromParent() }
                    }
                    
                    val titleView = android.widget.TextView(context).apply {
                        text = "Sign In"
                        textSize = 15f
                        setTextColor(0xFFe0e0e0.toInt())
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = android.widget.RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            addRule(android.widget.RelativeLayout.START_OF, closeBtn.id)
                            marginStart = (16 * density).toInt()
                        }
                    }

                    header.addView(titleView)
                    header.addView(closeBtn)
                    container.addView(header)
                    
                    tempWebView.layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                    container.addView(tempWebView)
                    
                    rootContent.addView(container)
                    Log.d(TAG, "OAuth popup attached")
                }
                Log.d(TAG, "OAuth popup keeping in WebView: $targetUrl")
                
                // After auth callback completes, the popup navigates to the final page
                // Detect this and close popup, load final URL in the parent WebView
                val authCallbackUrl = targetUrl
                if (!authCallbackUrl.contains("oauth") && 
                    !authCallbackUrl.contains("signin") && 
                    !authCallbackUrl.contains("login") && 
                    !authCallbackUrl.contains("auth") && 
                    !authCallbackUrl.contains("accounts.google.com") &&
                    !authCallbackUrl.contains("consent")) {
                    Log.d(TAG, "OAuth flow completed, closing popup and loading in parent: $authCallbackUrl")
                    parentView.loadUrl(authCallbackUrl)
                    view?.stopLoading()
                    removePopupFromParent()
                    return true
                }
                return false
            }
            
            if (isDownloadUrl(targetUrl)) {
                Log.d(TAG, "Download URL detected in popup: $targetUrl")
                val cookie = CookieManager.getInstance().getCookie(targetUrl)
                onDownloadRequested(targetUrl, null, null, -1L, view?.settings?.userAgentString, 
                    null, cookie, parentView.title, parentView.url)
                view?.stopLoading()
                removePopupFromParent()
                return true
            }
            
            onOpenInNewTab(targetUrl)
            view?.stopLoading()
            removePopupFromParent()
            return true
        }
    }
    
    tempWebView.webChromeClient = object : WebChromeClient() {
        override fun onCloseWindow(window: WebView?) {
            Log.d(TAG, "OAuth popup closed")
            CookieManager.getInstance().flush()
            CookieManager.getInstance().setAcceptThirdPartyCookies(parentView, true)
            removePopupFromParent()
        }
    }
    
    val transport = resultMsg.obj as WebView.WebViewTransport
    transport.webView = tempWebView
    resultMsg.sendToTarget()
    
    val cleanupDelay = if (parentIsAuth) 120_000L else 5000L
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        removePopupFromParent()
    }, cleanupDelay)
    
    return true
}

private suspend fun PointerInputScope.handlePullToRefresh(
    webView: WebView,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setIsRefreshing: (Boolean) -> Unit,
    setPullDistance: (Float) -> Unit,
    getPullDistance: () -> Float,
    getIsRefreshing: () -> Boolean,
    refreshThresholdPx: Float,
    maxPullPx: Float,
    topEdgeSlopPx: Float
) {
    // Track whether the current touch gesture started while page was at top.
    // Only allow pull-to-refresh when the finger went DOWN while already at scrollY=0,
    // like Opera — scrolling up to the top mid-gesture must NOT trigger it.
    var touchStartedAtTop = false
    var cumulativeDownPx = 0f  // consecutive downward movement accumulator
    
    // Use a more robust method to check if at top of page
    fun isReallyAtTop(): Boolean {
        // Method 1: Check scrollY position
        val scrollY = webView.scrollY
        if (scrollY > 0) return false
        
        // Method 2: Check canScrollVertically as backup
        return !webView.canScrollVertically(-1)
    }

    awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                
                if (getIsRefreshing()) {
                    setPullDistance(0f)
                    touchStartedAtTop = false
                    cumulativeDownPx = 0f
                    continue
                }
                
                if (event.changes.size != 1) continue
                
                val change = event.changes[0]
                
                // Detect new touch (finger just went down) → lock whether we're at top
                if (change.previousPressed != change.pressed && change.pressed) {
                    val startedInTopEdge = change.position.y <= topEdgeSlopPx
                    touchStartedAtTop = startedInTopEdge && isReallyAtTop()
                    cumulativeDownPx = 0f
                    setPullDistance(0f)
                    continue
                }

                if (!change.pressed) {
                    if (touchStartedAtTop && getPullDistance() > 0f) {
                        if (getPullDistance() >= refreshThresholdPx) {
                            setIsRefreshing(true)
                            coroutineScope.launch {
                                webView.post { webView.reload() }
                                delay(600)
                                setIsRefreshing(false)
                                delay(100)
                                setPullDistance(0f)
                            }
                        } else {
                            setPullDistance(0f)
                        }
                        event.changes.forEach { it.consume() }
                    }
                    touchStartedAtTop = false
                    cumulativeDownPx = 0f
                    continue
                }
                
                // Only allow pull-to-refresh if the touch started at top AND still at top
                // AND user is pulling DOWN (positive dy)
                val isAtTop = isReallyAtTop()
                if (!touchStartedAtTop || !isAtTop) {
                    if (getPullDistance() > 0f) setPullDistance(0f)
                    cumulativeDownPx = 0f
                    continue
                }
                
                val rawDy = change.position.y - change.previousPosition.y

                // Cancel pull if user reverses direction (scrolling up)
                if (rawDy < -3f && getPullDistance() > 0f) {
                    setPullDistance(0f)
                    cumulativeDownPx = 0f
                    continue
                }

                // Only consider downward movement (pulling down to refresh)
                // Ignore upward movement (scrolling up the page)
                if (rawDy <= 0f) {
                    continue
                }
                
                val dy = rawDy
                
                val dx = kotlin.math.abs(change.position.x - change.previousPosition.x)
                val isHorizontalDrag = dx > dy * 1.5f && dx > 2f

                when {
                    isHorizontalDrag -> {
                        setPullDistance(0f)
                        cumulativeDownPx = 0f
                    }
                    change.pressed && getPullDistance() > 0f -> {
                        val normalized = (getPullDistance() / maxPullPx).coerceIn(0f, 1f)
                        val resistance = 1f - (normalized * normalized * 0.7f)
                        setPullDistance((getPullDistance() + dy * resistance).coerceIn(0f, maxPullPx))
                        event.changes.forEach { it.consume() }
                    }
                    change.pressed && isAtTop -> {
                        // Accumulate downward pixels — require a deliberate pull (20px)
                        // before starting the refresh gesture
                        cumulativeDownPx += dy
                        if (cumulativeDownPx > 20f) {
                            setPullDistance(((cumulativeDownPx - 20f) * 0.3f).coerceIn(0f, maxPullPx))
                        }
                    }
                }
            }
        }
}

object WebViewCache {
    fun remove(tabId: String) {
        val wv = webViewCache[tabId]
        if (wv != null) {
            // ✅ CRITICAL: Cleanup Media Session before destroying WebView
            val manager = wv.getTag(com.sun.alasbrowser.R.id.media_session_manager) as? com.sun.alasbrowser.utils.WebViewMediaSessionManager
            manager?.destroy()
            
            wv.destroy()
        }
        webViewCache.remove(tabId)
        com.sun.alasbrowser.web.SmartBackEngine.clearTab(tabId)
        Log.d(TAG, "Removed WebView for tab $tabId")
    }

    fun clear() {
        webViewCache.values.forEach { 
            val manager = it.getTag(com.sun.alasbrowser.R.id.media_session_manager) as? com.sun.alasbrowser.utils.WebViewMediaSessionManager
            manager?.destroy()
            it.destroy() 
        }
        webViewCache.clear()
        Log.d(TAG, "Cleared all cached WebViews")
    }

    fun goBack(tabId: String): Boolean {
        val wv = webViewCache[tabId] ?: return false
        val currentUrl = wv.url ?: ""

        val safeBackUrl = com.sun.alasbrowser.web.SmartBackEngine.getSafeBackUrl(tabId, currentUrl)
            ?: return false

        if (safeBackUrl == currentUrl) return false

        Log.d(TAG, "Opera-style back: $currentUrl → $safeBackUrl")
        com.sun.alasbrowser.web.SmartBackEngine.popCurrentUrl(tabId)
        wv.stopLoading()

        // Try native history jump first (preserves scroll position & page state)
        val list = wv.copyBackForwardList()
        val currentIndex = list.currentIndex
        val normSafe = safeBackUrl.trimEnd('/').lowercase()
        var nativeIndex = -1
        for (i in currentIndex - 1 downTo 0) {
            if (list.getItemAtIndex(i).url.trimEnd('/').equals(normSafe, ignoreCase = true)) {
                nativeIndex = i
                break
            }
        }

        if (nativeIndex >= 0) {
            wv.goBackOrForward(nativeIndex - currentIndex)
        } else {
            wv.loadUrl(safeBackUrl)
        }
        return true
    }

    fun goForward(tabId: String): Boolean {
        val wv = webViewCache[tabId] ?: return false
        return if (wv.canGoForward()) {
            wv.goForward()
            true
        } else false
    }

    fun evaluateJavascript(tabId: String, script: String) {
        webViewCache[tabId]?.evaluateJavascript(script, null)
    }

    fun loadUrl(tabId: String, url: String) {
        webViewCache[tabId]?.loadUrl(url)
    }

    fun getWebView(tabId: String): WebView? = webViewCache[tabId]
}

private fun applySitePermissionsForRequest(
    req: PermissionRequest,
    domain: String,
    preferences: BrowserPreferences,
    activity: android.app.Activity
) {
    fun safeDeny() {
        try { req.deny() } catch (_: Exception) {}
    }
    fun safeGrant(resources: Array<String>) {
        try { req.grant(resources) } catch (_: Exception) {}
    }

    val allowedResources = req.resources.filter { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                preferences.isSiteMicrophoneAllowed(domain) ?: preferences.microphone
            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                preferences.isSiteCameraAllowed(domain) ?: preferences.camera
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            PermissionRequest.RESOURCE_MIDI_SYSEX -> true
            else -> false
        }
    }

    if (allowedResources.isEmpty()) {
        safeDeny()
        return
    }

    val neededPermissions = mutableListOf<String>()
    allowedResources.forEach { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        activity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    neededPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                }
            }

            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        activity,
                        android.Manifest.permission.CAMERA
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    neededPermissions.add(android.Manifest.permission.CAMERA)
                }
            }
        }
    }

    if (neededPermissions.isNotEmpty() && activity is com.sun.alasbrowser.MainActivity) {
        activity.requestSitePermissions(neededPermissions.distinct().toTypedArray()) { allGranted ->
            if (allGranted) {
                safeGrant(allowedResources.toTypedArray())
            } else {
                safeDeny()
            }
        }
    } else {
        safeGrant(allowedResources.toTypedArray())
    }
}

private fun handleExternalSchemeUrl(
    context: android.content.Context,
    view: WebView?,
    url: String
): Boolean {
    return try {
        if (url.startsWith("intent://", ignoreCase = true)) {
            val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
            intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    ?: intent.dataString
                if (!fallbackUrl.isNullOrBlank()) {
                    if (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://")) {
                        view?.loadUrl(fallbackUrl)
                    } else {
                        val fallbackIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(fallbackUrl)
                        )
                        if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(fallbackIntent)
                        }
                    }
                }
                true
            }
        } else {
            val externalIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )
            if (externalIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(externalIntent)
                true
            } else {
                false
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to handle external scheme URL: $url", e)
        false
    }
}

private fun extractHost(url: String): String {
    return try {
        android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""
    } catch (_: Exception) {
        ""
    }
}

private fun isOemAccountHost(host: String): Boolean {
    val h = host.lowercase()
    return h.contains("realme.com") ||
        h.contains("heytap.com") ||
        h.contains("heytapmobile.com") ||
        h.contains("realmemobile.com") ||
        h.contains("oppo.com") ||
        h.contains("oneplus.com")
}

private fun isGoogleHost(host: String): Boolean {
    val h = host.lowercase()
    return h == "google.com" ||
        h.endsWith(".google.com") ||
        h == "google.co.in" ||
        h.endsWith(".google.co.in") ||
        h.endsWith(".google.") ||
        h == "g.co" ||
        h.endsWith(".g.co")
}

private fun proxyObusWithCors(
    targetUrl: String,
    method: String,
    userAgent: String,
    requestHeaders: Map<String, String>?
): WebResourceResponse? {
    return try {
        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", requestHeaders?.get("Accept") ?: "*/*")
            setRequestProperty("Referer", "https://id.realme.com/")
            setRequestProperty("Origin", "https://id.realme.com")
            requestHeaders?.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) &&
                    !k.equals("Connection", ignoreCase = true) &&
                    !k.equals("Accept-Encoding", ignoreCase = true)
                ) {
                    setRequestProperty(k, v)
                }
            }
            connect()
        }

        val statusCode = conn.responseCode
        val input = if (statusCode in 200..399) conn.inputStream else conn.errorStream
        if (input == null) {
            conn.disconnect()
            return null
        }

        val bytes = ByteArrayOutputStream().use { out ->
            input.use { it.copyTo(out) }
            out.toByteArray()
        }

        val mimeType = conn.contentType?.substringBefore(";")?.ifBlank { null }
            ?: "application/javascript"
        val encoding = conn.contentEncoding?.ifBlank { null } ?: "utf-8"
        val reason = conn.responseMessage ?: "OK"

        val responseHeaders = mutableMapOf<String, String>()
        responseHeaders["Access-Control-Allow-Origin"] = "https://id.realme.com"
        responseHeaders["Access-Control-Allow-Credentials"] = "true"
        responseHeaders["Access-Control-Allow-Methods"] = "GET, OPTIONS"
        responseHeaders["Access-Control-Allow-Headers"] = "Origin, X-Requested-With, Content-Type, Accept, Authorization"
        responseHeaders["Vary"] = "Origin"
        responseHeaders["Cache-Control"] = conn.getHeaderField("Cache-Control") ?: "no-cache"
        conn.getHeaderField("Content-Type")?.let { responseHeaders["Content-Type"] = it }

        conn.disconnect()
        WebResourceResponse(
            mimeType,
            encoding,
            statusCode,
            reason,
            responseHeaders,
            ByteArrayInputStream(bytes)
        )
    } catch (e: Exception) {
        Log.w(TAG, "OBUS proxy failed for $targetUrl", e)
        null
    }
}

private fun buildErrorPage(errorCode: Int, description: String, failedUrl: String): String {
    val title = when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP -> "Server not found"
        WebViewClient.ERROR_CONNECT -> "Connection failed"
        WebViewClient.ERROR_TIMEOUT -> "Connection timed out"
        WebViewClient.ERROR_IO -> "Network error"
        WebViewClient.ERROR_UNKNOWN -> "Something went wrong"
        else -> "Can't reach this page"
    }
    val subtitle = when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP -> "Check your internet connection or the web address"
        WebViewClient.ERROR_CONNECT -> "The site refused the connection"
        WebViewClient.ERROR_TIMEOUT -> "The server took too long to respond"
        WebViewClient.ERROR_IO -> "A network error occurred"
        else -> description
    }
    val icon = when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP -> "🔍"
        WebViewClient.ERROR_CONNECT -> "🔌"
        WebViewClient.ERROR_TIMEOUT -> "⏱"
        WebViewClient.ERROR_IO -> "📡"
        else -> "⚠️"
    }
    val safeUrl = failedUrl.replace("\"", "&quot;").replace("<", "&lt;")
    return """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <style>
    *{margin:0;padding:0;box-sizing:border-box}
    body{font-family:-apple-system,system-ui,'Segoe UI',Roboto,sans-serif;background:#0a0a0a;color:#e0e0e0;
    display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px}
    .c{text-align:center;max-width:400px;animation:fadeUp .5s ease-out}
    @keyframes fadeUp{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}
    .icon-wrap{width:80px;height:80px;margin:0 auto 24px;border-radius:20px;
    background:rgba(255,255,255,.06);display:flex;align-items:center;justify-content:center;
    border:1px solid rgba(255,255,255,.08)}
    .icon{font-size:36px;line-height:1}
    h1{font-size:20px;font-weight:600;margin-bottom:8px;color:#f5f5f5;letter-spacing:-.01em}
    .sub{font-size:14px;color:#888;margin-bottom:20px;line-height:1.6}
    .url{font-size:11px;color:#555;word-break:break-all;margin-bottom:28px;
    padding:8px 14px;border-radius:8px;background:rgba(255,255,255,.04);
    border:1px solid rgba(255,255,255,.06);font-family:monospace}
    button{background:#fff;color:#0a0a0a;border:none;border-radius:10px;padding:12px 36px;
    font-size:14px;font-weight:600;cursor:pointer;transition:all .2s ease;letter-spacing:.01em}
    button:active{transform:scale(.96);opacity:.85}
    </style>
    </head>
    <body>
    <div class="c">
    <div class="icon-wrap"><span class="icon">$icon</span></div>
    <h1>$title</h1>
    <p class="sub">$subtitle</p>
    <p class="url">$safeUrl</p>
    <button onclick="location.href='$safeUrl'">Try again</button>
    </div>
    </body>
    </html>
    """.trimIndent()
}

private fun isDownloadUrl(url: String): Boolean {
    val lower = url.lowercase()
    val extensions = listOf(
        ".apk", ".xapk", ".apks", ".apkm",
        ".zip", ".rar", ".7z", ".tar", ".gz",
        ".exe", ".msi", ".dmg", ".pkg",
        ".pdf", ".mp3", ".mp4", ".avi", ".mkv",
        ".iso", ".deb"
    )
    return extensions.any { lower.endsWith(it) || 
        lower.contains("$it?") || 
        lower.contains("$it#") }
}

