package com.sun.alasbrowser.web

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import java.net.URLDecoder
import java.util.regex.Pattern
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.sun.alasbrowser.utils.BackgroundPlaybackInjector
import java.io.ByteArrayInputStream

open class WebClient(
    private val context: Context,
    private val adBlockEnabled: Boolean = true,
    private val enableBackgroundPlayback: Boolean = false,
    private val onPageStarted: (url: String) -> Unit = {},
    private val onPageFinished: (url: String, thumbnail: Bitmap?) -> Unit = { _, _ -> },
    private val onFaviconReceived: (Bitmap?) -> Unit = {},
    private val onNavigationStateChanged: (String?) -> Unit = {},
    private val onRenderProcessGoneCb: ((android.webkit.RenderProcessGoneDetail?) -> Unit)? = null,
    private val onNavigationInitiated: (url: String) -> Unit = {},
    private val autofillManager: com.sun.alasbrowser.utils.AutofillManager? = null,
    private val preferences: com.sun.alasbrowser.data.BrowserPreferences? = null,
    private val tabId: String? = null
) : WebViewClient() {

    private var currentFavicon: Bitmap? = null
    private var currentPageUrl: String? = null
    
    // Ad blocking enhancements
    private var lastNavigationTime = 0L
    private val RAPID_REDIRECT_THRESHOLD_MS = 1000L // Block redirects faster than 1 second
    private var navigationCount = 0
    private val MAX_RAPID_NAVIGATIONS = 3 // Block if more than 3 navigations in quick succession
    
    // 🛡️ Download click protection - blocks ad redirects after user clicks download buttons
    @Volatile
    private var lastUserGesture = 0L
    private val DOWNLOAD_PROTECTION_WINDOW_MS = 4000L // 4 seconds of protection after gesture
    
    /**
     * Call this when user makes a gesture (touch) on the WebView.
     * This helps distinguish user-initiated vs script-initiated navigations.
     */
    fun onUserGesture() {
        lastUserGesture = System.currentTimeMillis()
        tabId?.let { SmartBackEngine.onUserGesture(it) }
    }

    private fun injectAdBlocker(view: WebView?) {
        if (adBlockEnabled && view != null) {
            try {
                view.evaluateJavascript(OperaAdBlockerPro.getOperaLevelBlockerScript()) { result ->
                    Log.d("AdBlocker", "Opera AdBlocker Script injected")
                }
            } catch (e: Exception) {
                Log.e("AdBlocker", "Failed to inject script", e)
            }
        }
    }
    
    private fun injectImageProtection(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                function initAlasProtection() {
                    try {
                        if (document.getElementById('alas-dark-mode-fix')) return;
                        
                        var parent = document.head || document.documentElement;
                        if (!parent) {
                            setTimeout(initAlasProtection, 50);
                            return;
                        }

                        var style = document.createElement('style');
                        style.id = 'alas-dark-mode-fix';
                        style.textContent = '\
                            img, svg, picture, video, canvas, iframe, embed, object, \
                            [style*="background-image"], [style*="background:url"] { \
                                color-scheme: only light !important; \
                                forced-color-adjust: none !important; \
                                -webkit-print-color-adjust: exact !important; \
                                print-color-adjust: exact !important; \
                            } \
                            @media (prefers-color-scheme: dark) { \
                                img, svg, picture, video, canvas, iframe, embed, object { \
                                    mix-blend-mode: normal !important; \
                                    isolation: isolate !important; \
                                } \
                            }';
                        try { parent.appendChild(style); } catch(e) {}
                        
                        function debounce(func, wait) {
                            var timeout;
                            return function() {
                                var context = this, args = arguments;
                                clearTimeout(timeout);
                                timeout = setTimeout(function() {
                                    func.apply(context, args);
                                }, wait);
                            };
                        }

                        var observer = new MutationObserver(debounce(function(mutations) {
                            mutations.forEach(function(mutation) {
                                mutation.addedNodes.forEach(function(node) {
                                    if (node.nodeType === 1) { 
                                        var tagName = node.tagName;
                                        if (tagName === 'IMG' || tagName === 'SVG' || tagName === 'PICTURE') {
                                            node.style.colorScheme = 'only light';
                                        } else {
                                            var imgs = node.querySelectorAll ? node.querySelectorAll('img, svg, picture') : [];
                                            imgs.forEach(function(img) {
                                                img.style.colorScheme = 'only light';
                                            });
                                        }
                                    }
                                });
                            });
                        }, 100));
                        
                        if (document.body || document.documentElement) {
                            observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
                        }
                    } catch (e) {
                        console.error('Alas image protection error:', e);
                    }
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initAlasProtection);
                } else {
                    initAlasProtection();
                }
            })();
        """.trimIndent(), null)
    }
    
    private fun injectExpandableElementsFix(view: WebView?) {
        if (view == null) return
        
        view.evaluateJavascript("""
            (function() {
                function enableExpandableElements() {
                    try {
                        [].forEach.call(document.querySelectorAll('[data-toggle], [aria-expanded], .expand, .collapse, [class*="expand"]'), function(el) {
                            el.style.cursor = 'pointer';
                            el.style.userSelect = 'none';
                            el.style.webkitUserSelect = 'none';
                            
                            if (el.getAttribute('onclick') === null) {
                                el.addEventListener('click', function(e) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    
                                    var target = el.getAttribute('data-target') || el.getAttribute('aria-controls');
                                    if (target) {
                                        var elem = document.getElementById(target) || document.querySelector(target);
                                        if (elem) {
                                            var isHidden = elem.style.display === 'none' || getComputedStyle(elem).display === 'none';
                                            elem.style.display = isHidden ? 'block' : 'none';
                                            el.setAttribute('aria-expanded', isHidden);
                                        }
                                    }
                                    
                                    return false;
                                }, true);
                            }
                        });
                        
                        [].forEach.call(document.querySelectorAll('button, [role="button"], div[onclick]'), function(btn) {
                            var text = (btn.textContent || '').toLowerCase();
                            var html = (btn.innerHTML || '').toLowerCase();
                            
                            if ((text.includes('+') || html.includes('+') || 
                                 text.includes('expand') || html.includes('expand') ||
                                 btn.className.includes('expand') || btn.className.includes('toggle')) &&
                                !btn.hasAttribute('data-alas-enabled')) {
                                
                                btn.setAttribute('data-alas-enabled', 'true');
                                btn.style.cursor = 'pointer';
                                
                                btn.addEventListener('click', function(e) {
                                    e.stopPropagation();
                                }, false);
                            }
                        });
                    } catch (e) {
                        console.error('[Alas] Expandable elements fix error:', e);
                    }
                }
                
                enableExpandableElements();
                
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.addedNodes.length > 0) {
                            enableExpandableElements();
                        }
                    });
                });
                
                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true
                });
            })();
        """.trimIndent(), null)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (view == null || (view as? AlasWebView)?.isDestroyed() == true) {
            return
        }
        
        super.onPageStarted(view, url, favicon)
        
        view.settings?.loadWithOverviewMode = true
        
        if (url != null) {
            val authDecision = AuthCompatibilityEngine.evaluate(url)
            if (authDecision.active) {
                if (authDecision.allowThirdPartyCookies) {
                    android.webkit.CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(view, true)
                    }
                }
                view.settings?.apply {
                    userAgentString = com.sun.alasbrowser.engine.ChromiumCompat.cleanMobileUA(view.context)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    try {
                        if (androidx.webkit.WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
                            androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
                        }
                    } catch (e: Exception) {
                        Log.e("WebClient", "Error setting RequestedWithHeader allowlist", e)
                    }
                }
                Log.d("WebClient", "🔓 Auth compat: ${authDecision.reason} for $url")

                if (authDecision.allowStoragerelay) {
                    view.evaluateJavascript(AuthCompatibilityEngine.getStoragerelayShimScript(), null)
                }
            }

            AuthCompatibilityEngine.onNavigation(tabId ?: "", url)
        }
        
        currentFavicon = favicon
        currentPageUrl = url
        favicon?.let { onFaviconReceived(it) }
        url?.let { 
            onPageStarted(it)
            SimpleAdBlocker.setCurrentPageDomain(it)
        }
        
        if (adBlockEnabled && view != null && url != null) {
            val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                YouTubeAdBlocker.injectEarlyBlocker(view)
            }
        }
    }
    
    
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        if (view == null || (view as? AlasWebView)?.isDestroyed() == true) {
            return
        }
        
        super.doUpdateVisitedHistory(view, url, isReload)
        url?.let {
            onNavigationStateChanged(it)
            SimpleAdBlocker.setCurrentPageDomain(it)
            
            if (!isReload && tabId != null) {
                val hasGesture = System.currentTimeMillis() - lastUserGesture < 2000
                val timeSinceLastNav = System.currentTimeMillis() - lastNavigationTime
                SmartBackEngine.onNavigationEvent(
                    tabId = tabId,
                    url = it,
                    previousUrl = currentPageUrl,
                    hasUserGesture = hasGesture,
                    timeSinceLastNav = timeSinceLastNav
                )
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (view == null || (view as? AlasWebView)?.isDestroyed() == true) {
            Log.d("WebClient", "onPageFinished: Ignoring callback on null/destroyed WebView")
            return
        }
        
        super.onPageFinished(view, url)
        
        view.settings?.loadWithOverviewMode = false
        
        url?.let {
            onNavigationStateChanged(it)
            
            onPageFinished(url, null)
            
            val injectionPlan = AdBlockingCoordinator.getInjectionPlan(tabId, url, adBlockEnabled)
            if (injectionPlan.injectAdBlocker) {
                injectAdBlocker(view)
            }
            if (injectionPlan.injectStoragerelayShim) {
                try {
                    view.evaluateJavascript(AuthCompatibilityEngine.getStoragerelayShimScript(), null)
                } catch (_: Exception) {}
            }
            if (injectionPlan.injectAntiAdblockBypass) {
                try {
                    view.evaluateJavascript(AdBlockingCoordinator.getAntiAdblockBypassScript(), null)
                } catch (_: Exception) {}
            }
            
            if (adBlockEnabled) {
                try {
                    val cosmeticScript = SimpleAdBlocker.getCosmeticFilterScript()
                    if (cosmeticScript.isNotBlank()) {
                        view.evaluateJavascript(cosmeticScript, null)
                    }
                } catch (e: Exception) {
                    Log.e("WebClient", "Failed to inject cosmetic filters", e)
                }
            }
            
            injectScrollUnlockRepair(view)
            injectFullscreenExitHandler(view)
            injectImageProtection(view)
            injectExpandableElementsFix(view)
            
            if (adBlockEnabled) {
                view.postDelayed({
                    val alasWv = view as? AlasWebView
                    if (alasWv?.isDestroyed() != true && alasWv?.isAlive == true) {
                        YouTubeAdBlocker.injectYouTubeAdBlocker(view)
                    }
                }, 100)
            }
            
            if (enableBackgroundPlayback) {
                view.postDelayed({
                    val alasWv = view as? AlasWebView
                    if (alasWv?.isDestroyed() != true && alasWv?.isAlive == true) {
                        BackgroundPlaybackInjector.injectBackgroundPlaybackScript(view)
                    }
                }, 500)
                
                view.postDelayed({
                    val alasWv = view as? AlasWebView
                    if (alasWv?.isDestroyed() != true && alasWv?.isAlive == true) {
                        com.sun.alasbrowser.utils.WebViewPipSettings.injectPipMetaTags(view)
                    }
                }, 1200)
            }
            
            if (autofillManager != null && preferences != null) {
                if (preferences.savePasswords) {
                    view.evaluateJavascript(com.sun.alasbrowser.utils.ScriptManager.getFormDetectionScript(), null)
                }
                
                if (preferences.autoFillForms) {
                    autofillManager.checkAndAutofill(view, url)
                }
            }
        }
    }
    
    @Deprecated("Deprecated in API level 23", ReplaceWith("onReceivedError(view, request, error)"))
    @Suppress("DEPRECATION")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        if (errorCode == -10 && failingUrl != null) {
            Log.d("WebClient", "Intercepting ERR_UNKNOWN_URL_SCHEME (old API) for: $failingUrl")
            if (handleUrlScheme(view, failingUrl)) {
                return
            }
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
        onNavigationStateChanged(null)
    }
    
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        if (error?.errorCode == -10) {
            val url = request?.url?.toString()
            if (url != null) {
                Log.d("WebClient", "Intercepting ERR_UNKNOWN_URL_SCHEME for: $url")
                if (handleUrlScheme(view, url)) {
                    return
                }
            }
        }
        super.onReceivedError(view, request, error)
        onNavigationStateChanged(null)
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        onNavigationStateChanged(null)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        
        val url = request.url?.toString() ?: return null
        
        try {
            if (request.isForMainFrame) return null
            
            val pageUrl = currentPageUrl
            if (tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId)) {
                return null
            }
            if (pageUrl != null && AuthCompatibilityEngine.evaluate(pageUrl).active) {
                return null
            }
            
            if (adBlockEnabled) {
                val host = request.url.host ?: ""
                
                if (com.sun.alasbrowser.engine.ChromiumCompat.isAllowlistedHost(host)) return null
                
                val oemAllowlist = listOf(
                    "wikipedia.org", "wikimedia.org",
                    "realme.com", "heytap.com", "heytapmobile.com", "realmemobile.com",
                    "oppo.com", "oneplus.com", "samsung.com", "mi.com", "xiaomi.com"
                )
                if (oemAllowlist.any { host.endsWith(it) }) {
                    return null
                }
                
                if (SimpleAdBlocker.YOUTUBE_AD_PATTERNS.any { url.contains(it) }) {
                    SimpleAdBlocker.recordBlockPublic(url)
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                
                if (SimpleAdBlocker.shouldBlock(url)) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                
                if (SimpleAdBlocker.isPopupAd(url)) {
                    SimpleAdBlocker.recordBlockPublic(url)
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WebClient", "Error in shouldInterceptRequest", e)
        }
        
        if (preferences?.proxyEnabled == true) {
            try {
                if (!url.contains("alas-proxy.onrender.com")) {
                    val proxyUrl = "https://alas-proxy.onrender.com/proxy?url=" + java.net.URLEncoder.encode(url, "UTF-8")
                    
                    val client = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    val proxyRequest = okhttp3.Request.Builder()
                        .url(proxyUrl)
                        .addHeader("User-Agent", com.sun.alasbrowser.engine.ChromiumCompat.cleanMobileUA(context))
                        .build()

                    val response = client.newCall(proxyRequest).execute()
                    
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type", "text/plain")
                        val mimeType = contentType?.split(";")?.firstOrNull()?.trim() ?: "text/plain"
                        val encoding = contentType?.split("charset=")?.getOrNull(1)?.split(";")?.firstOrNull()?.trim() ?: "utf-8"
                        
                        return WebResourceResponse(
                            mimeType,
                            encoding,
                            response.body?.byteStream()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("WebClient", "Proxy request failed", e)
            }
        }

        return null
    }
    
    private fun handleUrlScheme(view: WebView?, url: String): Boolean {
        return try {
            when {
                url.startsWith("storagerelay://") -> {
                    Log.d("WebClient", "Suppressing storagerelay scheme (handled): $url")
                    true
                }
                url.contains("www.google.com/sorry") || url.contains("recaptcha") -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        Log.e("WebClient", "Failed to open external URL for CAPTCHA", e)
                        false
                    }
                }
                url.startsWith("intent://") -> {
                    handleIntentUrl(view, url)
                }
                !url.startsWith("http://") && !url.startsWith("https://") -> {
                    handleAppScheme(view, url)
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("WebClient", "Error in handleUrlScheme", e)
            false
        }
    }
    
    private fun handleAppScheme(view: WebView?, url: String): Boolean {
        try {
            Log.d("WebClient", "Handling app scheme: $url")
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            
            val packageManager = context.packageManager
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            
            if (activities.isNotEmpty()) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("WebClient", "Successfully opened app for: $url")
                return true
            } else {
                Log.d("WebClient", "No app found for: $url")
                
                if (url.startsWith("market://")) {
                    val playUrl = url.replace("market://details?id=", "https://play.google.com/store/apps/details?id=")
                        .replace("market://", "https://play.google.com/store/apps/")
                    Log.d("WebClient", "Redirecting to Play Store web: $playUrl")
                    view?.loadUrl(playUrl)
                    return true
                }
                
                return false
            }
        } catch (e: Exception) {
            Log.e("WebClient", "Error handling app scheme: $url", e)
            return false
        }
    }
    
    private fun handleIntentUrl(view: WebView?, url: String): Boolean {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val packageName = intent.getPackage()
            val scheme = intent.scheme
            
            Log.d("WebClient", "Handling intent URL: $url")
            Log.d("WebClient", "Parsed scheme: $scheme, package: $packageName")
            
            if ((scheme == "http" || scheme == "https") && (packageName == null || packageName == context.packageName)) {
                val fallbackUrl = intent.dataString ?: intent.getStringExtra("browser_fallback_url")
                if (fallbackUrl != null) {
                    Log.d("WebClient", "Intercepting HTTP/S intent, loading internally: $fallbackUrl")
                    view?.loadUrl(fallbackUrl)
                    return true
                }
            }
            
            val packageManager = context.packageManager
            
            if (packageName != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d("WebClient", "Successfully launched app from intent (package check)")
                    return true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    Log.d("WebClient", "Package not installed: $packageName")
                }
            }
            
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            
            if (activities.isNotEmpty()) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("WebClient", "Successfully launched app from intent (query check)")
                return true
            }
            
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
            if (fallbackUrl != null) {
                Log.d("WebClient", "Using fallback URL: $fallbackUrl")
                view?.loadUrl(fallbackUrl)
                return true
            }
            
            if (packageName != null) {
                val playUrl = "https://play.google.com/store/apps/details?id=$packageName"
                Log.d("WebClient", "Redirecting to Play Store: $playUrl")
                view?.loadUrl(playUrl)
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e("WebClient", "Error handling intent URL", e)
            return false
        }
    }
    
    @Deprecated("Deprecated in API level 24")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        Log.d("WebClient", "shouldOverrideUrlLoading (deprecated) called with: $url")
        return handleUrlScheme(view, url)
    }
    
    private val searchEnginePatterns = mapOf(
        "google_redirect" to Pattern.compile("https?://(?:www\\.)?google\\.[a-z]{2,3}(?:\\.[a-z]{2})?/url\\?.*"),
        "google_search" to Pattern.compile("https?://(?:www\\.)?google\\.[a-z]{2,3}(?:\\.[a-z]{2})?/search\\?.*"),
        "google_amp" to Pattern.compile("https?://(?:www\\.)?google\\.[a-z]{2,3}(?:\\.[a-z]{2})?/amp/s/.*"),
        
        "bing_redirect" to Pattern.compile("https?://(?:www\\.)?bing\\.[a-z]{2,3}(?:\\.[a-z]{2})?/.*(?:url|r|search)\\?.*"),
        "bing_search" to Pattern.compile("https?://(?:www\\.)?bing\\.[a-z]{2,3}(?:\\.[a-z]{2})?/search\\?.*"),
        
        "duckduckgo_redirect" to Pattern.compile("https?://(?:[a-z]+\\.)?duckduckgo\\.[a-z]{2,3}/l/\\?.*"),
        "duckduckgo_search" to Pattern.compile("https?://(?:[a-z]+\\.)?duckduckgo\\.[a-z]{2,3}/\\?.*"),
        
        "yahoo_redirect" to Pattern.compile("https?://(?:r\\.|search\\.)?yahoo\\.[a-z]{2,3}/.*"),
        "yahoo_search" to Pattern.compile("https?://(?:search\\.)?yahoo\\.[a-z]{2,3}/search\\?.*"),
        
        "generic_redirect" to Pattern.compile(".*/url\\?.*|.*/redirect\\?.*|.*/r\\?.*|.*/link\\?.*"),
        "url_in_query" to Pattern.compile(".*[?&](?:q|query|search|text)=.*https?://.*")
    )

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        Log.d("WebClient", "shouldOverrideUrlLoading called with: $url")
        
        val currentTime = System.currentTimeMillis()
        val currentHost = view?.url?.let { Uri.parse(it).host?.lowercase() } ?: ""
        val targetHost = Uri.parse(url).host?.lowercase() ?: ""
        
        if (targetHost.isNotEmpty() && (targetHost == currentHost || currentHost.endsWith(".$targetHost") || targetHost.endsWith(".$currentHost"))) {
            Log.d("WebClient", "✅ Same-origin navigation: $url")
            return false
        }
        
        if (SimpleAdBlocker.isDownloadCdnUrl(url)) {
            Log.d("WebClient", "✅ Allowing download URL: $url")
            return false 
        }
        
        val isCurrentPageAuth = tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId)
        val isTargetAuth = AuthCompatibilityEngine.evaluate(url).active
        if (isCurrentPageAuth || isTargetAuth) {
            Log.d("WebClient", "🔓 Auth compat: allowing navigation: $url")
            return false
        }
        
        if (adBlockEnabled && SimpleAdBlocker.isPopupAd(url)) {
            Log.d("WebClient", "🚫 Blocked popup ad navigation: $url")
            return true
        }
        
        if (adBlockEnabled && SmartBackEngine.isYouTubeAdRedirect(url, currentPageUrl)) {
            Log.d("WebClient", "🎬 Blocked YouTube ad redirect: $url")
            return true
        }
        
        val timeSinceGesture = currentTime - lastUserGesture
        val isWithinProtectionWindow = timeSinceGesture < DOWNLOAD_PROTECTION_WINDOW_MS && timeSinceGesture > 50 
        
        if (adBlockEnabled && isWithinProtectionWindow && targetHost != currentHost) {
            val lowerUrl = url.lowercase()
            val isKnownAdPattern = SimpleAdBlocker.POPUP_AD_DOMAINS.any { lowerUrl.contains(it) } ||
                lowerUrl.contains("flirt") || lowerUrl.contains("dating") || lowerUrl.contains("casino") ||
                lowerUrl.contains("betting") || lowerUrl.contains("1xbet") || lowerUrl.contains("adult") ||
                lowerUrl.contains("consist.org") || lowerUrl.contains("3ckz.com") || lowerUrl.contains("monetag")
            
            if (isKnownAdPattern) {
                Log.d("WebClient", "🛡️ Blocked ad redirect during download protection: $url")
                return true
            }
        }
        
        val timeSinceLastNav = currentTime - lastNavigationTime
        
        if (timeSinceLastNav < RAPID_REDIRECT_THRESHOLD_MS) {
            navigationCount++
            
            if (navigationCount > MAX_RAPID_NAVIGATIONS) {
                if (adBlockEnabled && SimpleAdBlocker.shouldBlock(url) && !SimpleAdBlocker.isDownloadCdnUrl(url)) {
                    Log.d("WebClient", "🚫 Blocked rapid redirect ad chain: $url")
                    navigationCount = 0 
                    return true
                }
            }
        } else {
            navigationCount = 0
        }
        
        lastNavigationTime = currentTime

        val isAuthUrl = AuthCompatibilityEngine.evaluate(url).active ||
            (tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId))
        if (!isAuthUrl && handleSearchEngineUrls(view, url)) {
            return true
        }

        val handled = handleUrlScheme(view, url)
        if (!handled && url.startsWith("https://")) {
            onNavigationInitiated(url)
        }
        return handled
    }

    fun handleSearchEngineUrls(view: WebView?, url: String): Boolean {
        if (isRedirectUrl(url)) {
            return handleRedirect(view, url)
        }
        
        if (isSearchPageWithUrl(url)) {
            return handleSearchWithUrl(view, url)
        }
        
        if (url.contains("/amp/") || url.contains(".amp") || url.contains("amp/")) {
            return handleAmpLink(view, url)
        }
        
        return false
    }

    private fun isRedirectUrl(url: String): Boolean {
        val redirectPatterns = listOf(
            "google_redirect", "bing_redirect", 
            "duckduckgo_redirect", "yahoo_redirect", "generic_redirect"
        )
        
        return redirectPatterns.any { patternName ->
            searchEnginePatterns[patternName]?.matcher(url)?.find() ?: false
        }
    }

    private fun isSearchPageWithUrl(url: String): Boolean {
        val isSearchPage = listOf(
            "google_search", "bing_search", 
            "duckduckgo_search", "yahoo_search"
        ).any { patternName ->
            searchEnginePatterns[patternName]?.matcher(url)?.find() ?: false
        }
        
        if (!isSearchPage) return false
        
        return searchEnginePatterns["url_in_query"]?.matcher(url)?.find() ?: false
    }

    private fun handleRedirect(view: WebView?, redirectUrl: String): Boolean {
        val targetUrl = extractTargetUrl(redirectUrl)
        if (targetUrl != null && !isSearchEnginePage(targetUrl)) {
            Log.d("NAVIGATION", "Redirecting from ${getDomain(redirectUrl)} to: $targetUrl")
            view?.loadUrl(targetUrl)
            return true
        }
        return false
    }

    private fun extractTargetUrl(redirectUrl: String): String? {
        return try {
            val uri = Uri.parse(redirectUrl)
            
            val paramNames = listOf("url", "q", "u", "r", "link", "dest", "target")
            
            for (param in paramNames) {
                var target = uri.getQueryParameter(param)
                if (target != null) {
                    target = URLDecoder.decode(target, "UTF-8")
                    
                    if (isValidUrl(target)) {
                        if (isSearchEngineRedirect(target)) {
                            return extractTargetUrl(target)
                        }
                        return target
                    }
                }
            }
            
            if (redirectUrl.contains("duckduckgo.com/l/?uddg=")) {
                val start = redirectUrl.indexOf("uddg=") + 5
                if (start < redirectUrl.length) {
                    val encodedUrl = redirectUrl.substring(start)
                    val decoded = URLDecoder.decode(encodedUrl, "UTF-8")
                    if (isValidUrl(decoded)) {
                        return decoded
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e("URL_EXTRACT", "Error extracting URL from $redirectUrl: $e")
            null
        }
    }

    private fun handleSearchWithUrl(view: WebView?, searchUrl: String): Boolean {
        val targetUrl = extractUrlFromSearchQuery(searchUrl)
        if (targetUrl != null) {
            Log.d("NAVIGATION", "Direct navigation for URL found in search query: $targetUrl")
            view?.loadUrl(targetUrl)
            return true
        }
        return false
    }

    private fun extractUrlFromSearchQuery(searchUrl: String): String? {
        return try {
            val uri = Uri.parse(searchUrl)
            
            val queryParams = listOf("q", "query", "search", "text", "p")
            
            for (param in queryParams) {
                val query = uri.getQueryParameter(param) ?: continue
                
                val urlPattern = "(https?://[^\\s&]+|www\\.[^\\s&]+\\.[a-z]{2,})".toRegex()
                val match = urlPattern.find(query)
                
                match?.value?.let { foundUrl ->
                    var finalUrl = foundUrl
                    if (finalUrl.startsWith("www.")) {
                        finalUrl = "https://$finalUrl"
                    }
                    
                    finalUrl = finalUrl.replace("[.,;:!?)]+$".toRegex(), "")
                    
                    if (isValidUrl(finalUrl)) {
                        return finalUrl
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAmpLink(view: WebView?, ampUrl: String): Boolean {
        val canonicalUrl = extractCanonicalFromAmp(ampUrl)
        if (canonicalUrl != null) {
            Log.d("NAVIGATION", "Extracted canonical URL from AMP: $canonicalUrl")
            view?.loadUrl(canonicalUrl)
            return true
        }
        return false
    }

    private fun extractCanonicalFromAmp(ampUrl: String): String? {
        val ampPatterns = listOf(
            "/amp/s/",           
            "/amp/",             
            ".amp/",             
            "/amp/",             
            "amp/"               
        )
        
        for (pattern in ampPatterns) {
            val idx = ampUrl.indexOf(pattern)
            if (idx != -1) {
                val path = ampUrl.substring(idx + pattern.length)
                val end = path.indexOf('?').takeIf { it > 0 } ?: path.length
                val cleanPath = path.substring(0, end)
                
                if (cleanPath.contains(".") && !cleanPath.startsWith("http")) {
                    return "https://$cleanPath"
                } else if (cleanPath.startsWith("http")) {
                    return cleanPath
                }
            }
        }
        
        return null
    }

    private fun isSearchEnginePage(url: String): Boolean {
        val searchEngineDomains = listOf(
            "google.", "bing.", "duckduckgo.", "yahoo.", 
            "baidu.", "yandex.", "ask.", "ecosia."
        )
        
        return searchEngineDomains.any { url.contains(it) && 
            (url.contains("/search") || url.contains("/?")) }
    }

    private fun isSearchEngineRedirect(url: String): Boolean {
        return url.contains("/url?") || url.contains("/r?") || 
               url.contains("/redirect?") || url.contains("/link?") ||
               url.contains("duckduckgo.com/l/?")
    }

    private fun isValidUrl(url: String): Boolean {
        return (url.startsWith("http://") || url.startsWith("https://")) &&
               url.contains(".") && url.length > 10
    }

    private fun getDomain(url: String): String {
        return try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }
    
    private fun injectScrollUnlockRepair(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                if (window.__alasScrollFixApplied) return;
                window.__alasScrollFixApplied = true;
                
                function fixScroll() {
                    var html = document.documentElement;
                    var body = document.body;
                    if (!body) return;
                    
                    var isRealFullscreen = document.fullscreenElement || document.webkitFullscreenElement;
                    if (isRealFullscreen) return;
                    
                    var needsScroll = body.scrollHeight > window.innerHeight + 20;
                    if (!needsScroll) return;
                    
                    var htmlStyle = window.getComputedStyle(html);
                    var bodyStyle = window.getComputedStyle(body);
                    var isLocked = htmlStyle.overflow === 'hidden' || bodyStyle.overflow === 'hidden' ||
                        htmlStyle.overflowY === 'hidden' || bodyStyle.overflowY === 'hidden' ||
                        bodyStyle.position === 'fixed';
                    
                    if (!isLocked) return;
                    
                    html.style.setProperty('overflow', 'auto', 'important');
                    html.style.setProperty('overflow-y', 'auto', 'important');
                    body.style.setProperty('overflow', 'auto', 'important');
                    body.style.setProperty('overflow-y', 'auto', 'important');
                    
                    if (bodyStyle.position === 'fixed') {
                        body.style.setProperty('position', 'static', 'important');
                    }
                    
                    html.style.setProperty('height', 'auto', 'important');
                    body.style.setProperty('height', 'auto', 'important');
                    
                    body.style.setProperty('touch-action', 'auto', 'important');
                    body.style.setProperty('overscroll-behavior', 'auto', 'important');
                }
                
                setTimeout(fixScroll, 300);
                setTimeout(fixScroll, 1000);
                setTimeout(fixScroll, 3000);
                
                var observer = new MutationObserver(function() {
                    fixScroll();
                });
                observer.observe(document.documentElement, {
                    childList: true, subtree: true, attributes: true,
                    attributeFilter: ['style', 'class']
                });
            })();
        """.trimIndent(), null)
    }
    
    private fun injectFullscreenExitHandler(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                if (window.__alasFullscreenHandlerInstalled) return;
                window.__alasFullscreenHandlerInstalled = true;
                
                var exitBtn = null;
                
                function isRealFullscreen() {
                    return !!(document.fullscreenElement || document.webkitFullscreenElement);
                }
                
                function createExitButton() {
                    if (exitBtn || !isRealFullscreen()) return;
                    exitBtn = document.createElement('div');
                    exitBtn.id = 'alas-fullscreen-exit';
                    exitBtn.innerHTML = '✕';
                    exitBtn.style.cssText = 'position:fixed!important;top:12px!important;right:12px!important;z-index:2147483647!important;' +
                        'width:36px!important;height:36px!important;background:rgba(0,0,0,0.6)!important;color:#fff!important;' +
                        'border-radius:50%!important;display:flex!important;align-items:center!important;justify-content:center!important;' +
                        'font-size:18px!important;cursor:pointer!important;border:1px solid rgba(255,255,255,0.3)!important;' +
                        'backdrop-filter:blur(4px)!important;-webkit-backdrop-filter:blur(4px)!important;' +
                        'touch-action:manipulation!important;user-select:none!important;-webkit-user-select:none!important;' +
                        'opacity:0.7!important;transition:opacity 0.2s!important;';
                    exitBtn.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (document.exitFullscreen) document.exitFullscreen();
                        else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                    }, true);
                    (document.body || document.documentElement).appendChild(exitBtn);
                }
                
                function removeExitButton() {
                    if (exitBtn) { exitBtn.remove(); exitBtn = null; }
                }
                
                document.addEventListener('fullscreenchange', function() {
                    if (isRealFullscreen()) createExitButton(); else removeExitButton();
                });
                document.addEventListener('webkitfullscreenchange', function() {
                    if (isRealFullscreen()) createExitButton(); else removeExitButton();
                });
                
                if (isRealFullscreen()) createExitButton();
            })();
        """.trimIndent(), null)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
        if (view == null) return false
        
        Log.e("WebClient", "Renderer process gone! crashed=${detail?.didCrash()}, priority=${detail?.rendererPriorityAtExit()}")
        
        val alasWebView = view as? AlasWebView
        alasWebView?.markDead()
        
        alasWebView?.onRecreationNeeded?.invoke()
        
        onRenderProcessGoneCb?.invoke(detail)
        
        return true
    }
}
