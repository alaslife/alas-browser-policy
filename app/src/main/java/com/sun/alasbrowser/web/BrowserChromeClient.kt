package com.sun.alasbrowser.web

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sun.alasbrowser.data.BrowserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BrowserChromeClient(
    private val onProgressChanged: (Int) -> Unit = {},
    private val onFaviconReceived: (Bitmap?) -> Unit = {},
    private val onShowCustomView: ((View, CustomViewCallback) -> Unit)? = null,
    private val onHideCustomView: (() -> Unit)? = null,
    private val onFileChooser: ((ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean)? = null,
    private val onVideoPlaying: ((Boolean) -> Unit)? = null,
    private val onCreateWindow: ((String) -> Unit)? = null,
    private val preferences: BrowserPreferences? = null,
    private val activity: Activity? = null,
    private val onDownloadRequested: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long, cookies: String?, referer: String?, pageTitle: String?, pageUrl: String?) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var isVideoPlaying = false

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        onFaviconReceived(icon)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
        view?.let { v ->
            callback?.let { cb ->
                customView = v
                customViewCallback = cb
                isVideoPlaying = true
                onVideoPlaying?.invoke(true)
                onShowCustomView?.invoke(v, cb)
            }
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        isVideoPlaying = false
        onVideoPlaying?.invoke(false)
        onHideCustomView?.invoke()
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback != null && fileChooserParams != null) {
            return onFileChooser?.invoke(filePathCallback, fileChooserParams) == true
        }
        return false
    }
    
    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let { req ->
            val origin = req.origin?.toString() ?: ""
            val domain = preferences?.extractDomain(origin) ?: return@let
            
            // First check if there is an explicit user preference saved for this specific site
            val savedMicPref = preferences.isSiteMicrophoneAllowed(domain)
            val savedCamPref = preferences.isSiteCameraAllowed(domain)

            val wantsMic = req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            val wantsCam = req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            // If preferences exist for what's requested, apply them immediately
            val hasExistingDecision = (wantsMic && savedMicPref != null) || (wantsCam && savedCamPref != null) || (!wantsMic && !wantsCam)

            if (hasExistingDecision) {
                applyPermissionsBasedOnPreferences(req, domain)
                return
            }

            // Otherwise, prompt the user with an AlertDialog
            if (activity == null) {
                req.deny()
                return
            }

            var message = "$domain wants to "
            if (wantsMic && wantsCam) {
                message += "use your camera and microphone."
            } else if (wantsCam) {
                message += "use your camera."
            } else if (wantsMic) {
                message += "use your microphone."
            } else {
                applyPermissionsBasedOnPreferences(req, domain)
                return
            }

            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Permission Request")
                .setMessage(message)
                .setPositiveButton("Allow") { _, _ ->
                    if (wantsMic) preferences.setSiteMicrophonePermission(domain, true)
                    if (wantsCam) preferences.setSiteCameraPermission(domain, true)
                    applyPermissionsBasedOnPreferences(req, domain)
                }
                .setNegativeButton("Block") { _, _ ->
                    if (wantsMic) preferences.setSiteMicrophonePermission(domain, false)
                    if (wantsCam) preferences.setSiteCameraPermission(domain, false)
                    applyPermissionsBasedOnPreferences(req, domain)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun applyPermissionsBasedOnPreferences(req: PermissionRequest, domain: String) {
        val allowedResources = req.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    preferences?.isSiteMicrophoneAllowed(domain) ?: (preferences?.microphone == true)
                }
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    preferences?.isSiteCameraAllowed(domain) ?: (preferences?.camera == true)
                }
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> true
                PermissionRequest.RESOURCE_MIDI_SYSEX -> true
                else -> false
            }
        }
        
        if (allowedResources.isEmpty()) {
            Log.d("BrowserChromeClient", "🚫 Permission denied for $domain - camera/mic blocked")
            req.deny()
            return
        }
        
        Log.d("BrowserChromeClient", "✅ Permission granted for $domain: ${allowedResources.joinToString()}")
        
        // Check Android runtime permissions
        val neededPermissions = mutableListOf<String>()
        allowedResources.forEach { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (activity != null && ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) 
                        != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (activity != null && ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) 
                        != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(android.Manifest.permission.CAMERA)
                    }
                }
            }
        }
        
        // Request permissions if needed
        if (neededPermissions.isNotEmpty() && activity is com.sun.alasbrowser.MainActivity) {
            activity.requestSitePermissions(neededPermissions.toTypedArray()) { allGranted ->
                if (allGranted) {
                    Log.d("BrowserChromeClient", "✅ Android permissions granted, honoring WebView request")
                    req.grant(allowedResources.toTypedArray())
                } else {
                    Log.d("BrowserChromeClient", "🚫 Android permissions denied, denying WebView request")
                    req.deny()
                }
            }
        } else {
            req.grant(allowedResources.toTypedArray())
        }
    }
    
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (preferences?.location != true) {
            callback?.invoke(origin, false, false)
            return
        }
        if (activity == null) {
            callback?.invoke(origin, false, false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            activity, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            callback?.invoke(origin, true, true)
        } else {
            com.sun.alasbrowser.utils.GeolocationPermissionHelper.storePending(origin, callback)
            (activity as? com.sun.alasbrowser.MainActivity)?.requestLocationPermission()
        }
    }
    
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        // Intercept popup windows and open them as new tabs (preventing "fullscreen glitch")
        if (view == null || resultMsg == null || activity == null) return false
        
        // Capture referer from the initiating WebView
        val referer = view.url
        val originalTitle = view.title

        // Block script-initiated popups (no user gesture) — almost always ads
        val parentIsAuth = referer != null && AuthCompatibilityEngine.evaluate(referer).active
        if (!isUserGesture && !parentIsAuth) {
            Log.d("BrowserChromeClient", "🚫 Blocked script-initiated popup (no user gesture) from: $referer")
            return false
        }

        try {
            // Create a temporary invisible WebView to capture the URL
            val newWebView = WebView(activity)
            
            // Track whether this popup is handling an OAuth flow
            var isOAuthPopup = false
            
            // Allow JS to run so it can trigger the load and for Blob extraction
            newWebView.settings.javaScriptEnabled = true
            newWebView.settings.domStorageEnabled = true
            newWebView.settings.javaScriptCanOpenWindowsAutomatically = false
            newWebView.settings.setSupportMultipleWindows(false) // Prevent infinite popup loops
            
            // Match the parent WebView's UA to prevent Google OAuth blocking
            newWebView.settings.userAgentString = view.settings.userAgentString
            
            // Enable third-party cookies for OAuth popups
            if (parentIsAuth) {
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(newWebView, true)
                newWebView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // Inject BlobDownloader interface to the temp WebView
            newWebView.addJavascriptInterface(BlobDownloader(activity) { base64Data, mimeType ->
                 val size = ((base64Data.length * 3) / 4).toLong()
                 onDownloadRequested(base64Data, null, null, mimeType, size, null, null, view.title, referer)
            }, "AlasBlobDownloader")

            // Add download listener for popup downloads
            newWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                 onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength, null, referer, view.title, referer)
            }
            
            // Set a client to capture the URL
            newWebView.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    if (!url.isNullOrEmpty()) {
                        Log.d("BrowserChromeClient", "Intercepted popup URL: $url")
                        
                        // PRIORITY 0: Block popup ads FIRST (but NOT on OAuth popups)
                        if (!isOAuthPopup && SimpleAdBlocker.isPopupAd(url)) {
                            Log.d("BrowserChromeClient", "🚫 Blocked popup ad: $url")
                            view?.stopLoading()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try { newWebView.destroy() } catch (_: Exception) {}
                            }
                            return true
                        }
                        
                        // PRIORITY 0.5: Let OAuth popups navigate freely — do NOT redirect to new tab
                        if (isOAuthPopup || parentIsAuth || isOAuthUrl(url)) {
                            isOAuthPopup = true
                            Log.d("BrowserChromeClient", "🔓 OAuth popup navigation, allowing in-place: $url")
                            return false
                        }
                        
                        // Handle Blob URLs
                        if (url.startsWith("blob:")) {
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
                                                AlasBlobDownloader.onBase64Download(base64data, '${"application/octet-stream"}');
                                            }
                                            reader.readAsDataURL(blob);
                                        }
                                    };
                                    xhr.send();
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                            return true
                        } else if (url.startsWith("data:")) {
                             val size = ((url.length * 3) / 4).toLong()
                             onDownloadRequested(url, null, null, "application/octet-stream", size, null, referer, view?.title, referer)
                             return true
                        }
                        
                        // Check if it's an obvious download URL by extension
                        if (isDownloadUrl(url)) {
                            Log.d("BrowserChromeClient", "Detected download URL in popup via extension: $url")
                            fetchDownloadMetadata(url, referer, view?.settings?.userAgentString) { userAgent, contentDisposition, mimeType, contentLength, finalUrl ->
                                val cookies = android.webkit.CookieManager.getInstance().getCookie(finalUrl ?: url)
                                onDownloadRequested(finalUrl ?: url, userAgent, contentDisposition, mimeType, contentLength, cookies, referer, originalTitle, referer)
                            }
                            view?.stopLoading()
                            return true
                        }
                        
                        // Check if we're on an APK/mod download site - be aggressive with popups
                        val isFromApkSite = listOf("liteapks.com", "modyolo.com", "apkmody.io", "happymod.com",
                            "an1.com", "revdl.com", "apkpure.com", "apkmirror.com", "apkcombo.com", "9mod.cloud")
                            .any { referer?.contains(it) == true }
                        
                        if (isFromApkSite) {
                            val isSameDomain = try {
                                val refererHost = android.net.Uri.parse(referer).host ?: ""
                                val urlHost = android.net.Uri.parse(url).host ?: ""
                                urlHost.contains(refererHost.replace("www.", "")) || 
                                refererHost.contains(urlHost.replace("www.", ""))
                            } catch (e: Exception) { false }
                            
                            val isDownloadCdn = SimpleAdBlocker.isDownloadCdnUrl(url)
                            
                            if (!isSameDomain && !isDownloadCdn) {
                                Log.d("BrowserChromeClient", "🚫 Blocked popup from APK site (not download): $url")
                                view?.stopLoading()
                                return true
                            }
                        }
                        
                        // Block cross-domain popups that aren't downloads (click-hijack ads)
                        val isSameDomain = try {
                            val refererHost = android.net.Uri.parse(referer).host?.lowercase()?.removePrefix("www.") ?: ""
                            val urlHost = android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""
                            refererHost.isNotEmpty() && urlHost.isNotEmpty() && 
                                (urlHost == refererHost || urlHost.endsWith(".$refererHost") || refererHost.endsWith(".$urlHost"))
                        } catch (e: Exception) { false }
                        
                        if (!isSameDomain && !SimpleAdBlocker.isDownloadCdnUrl(url) && !isOAuthUrl(url)) {
                            Log.d("BrowserChromeClient", "🚫 Blocked cross-domain popup ad: $url (from: $referer)")
                            view?.stopLoading()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try { newWebView.destroy() } catch (_: Exception) {}
                            }
                            return true
                        }
                        
                        Log.d("BrowserChromeClient", "Popup URL captured, opening new tab: $url")
                        onCreateWindow?.invoke(url)
                        
                        view?.stopLoading()
                        return true
                    }
                    return false
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                     if (!url.isNullOrEmpty() && url != "about:blank") {
                        // Let OAuth popups load freely
                        if (isOAuthPopup || parentIsAuth || isOAuthUrl(url ?: "")) {
                            isOAuthPopup = true
                            Log.d("BrowserChromeClient", "🔓 OAuth popup page loading: $url")
                            return
                        }
                         
                        // PRIORITY 0: Block popup ads FIRST
                        if (SimpleAdBlocker.isPopupAd(url)) {
                            Log.d("BrowserChromeClient", "🚫 Blocked popup ad in page start: $url")
                            view?.stopLoading()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try { newWebView.destroy() } catch (_: Exception) {}
                            }
                            return
                        }
                        
                        if (url.startsWith("blob:") || url.startsWith("data:")) {
                             // Handled in shouldOverrideUrlLoading or will be handled by Blob extraction logic
                        } else if (isDownloadUrl(url)) {
                            Log.d("BrowserChromeClient", "Detected download URL in popup page start: $url")
                            fetchDownloadMetadata(url, referer, view?.settings?.userAgentString) { userAgent, contentDisposition, mimeType, contentLength, finalUrl ->
                                val cookies = android.webkit.CookieManager.getInstance().getCookie(finalUrl ?: url)
                                onDownloadRequested(finalUrl ?: url, userAgent, contentDisposition, mimeType, contentLength, cookies, referer, originalTitle, referer)
                            }
                            view?.stopLoading()
                        } else {
                            // Block cross-domain popups (click-hijack ads)
                            val isCrossDomain = try {
                                val refererHost = android.net.Uri.parse(referer).host?.lowercase()?.removePrefix("www.") ?: ""
                                val urlHost = android.net.Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""
                                refererHost.isNotEmpty() && urlHost.isNotEmpty() && 
                                    urlHost != refererHost && !urlHost.endsWith(".$refererHost") && !urlHost.endsWith(".$urlHost")
                            } catch (e: Exception) { false }
                            
                            if (isCrossDomain && !SimpleAdBlocker.isDownloadCdnUrl(url) && !isOAuthUrl(url)) {
                                Log.d("BrowserChromeClient", "🚫 Blocked cross-domain popup ad in page start: $url")
                                view?.stopLoading()
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    try { newWebView.destroy() } catch (_: Exception) {}
                                }
                                return
                            }
                            
                            Log.d("BrowserChromeClient", "Intercepted popup page start: $url")
                            onCreateWindow?.invoke(url)
                            view?.stopLoading()
                        }
                    }
                }
            }

            // Transport the new WebView to the message
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = newWebView
            resultMsg.sendToTarget()
            
            // Clean up the temporary WebView after a delay
            // OAuth flows need much longer (user may be typing credentials)
            val cleanupDelay = if (parentIsAuth) 120_000L else 5000L
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    newWebView.destroy()
                    Log.d("BrowserChromeClient", "Temporary popup WebView destroyed safely")
                } catch (e: Exception) {
                    // Ignore crash on cleanup
                }
            }, cleanupDelay)

            return true
        } catch (e: Exception) {
            Log.e("BrowserChromeClient", "Error handling onCreateWindow", e)
            return false
        }
    }
    
    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message?.contains("ad", ignoreCase = true) == true ||
            message?.contains("popup", ignoreCase = true) == true) {
            Log.d("AdBlocker", "Blocked ad alert: $message")
            result?.cancel()
            return true
        }
        return super.onJsAlert(view, url, message, result)
    }
    
    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message?.contains("ad", ignoreCase = true) == true ||
            message?.contains("popup", ignoreCase = true) == true) {
            Log.d("AdBlocker", "Blocked ad confirm: $message")
            result?.cancel()
            return true
        }
        return super.onJsConfirm(view, url, message, result)
    }
    
    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        if (message?.contains("ad", ignoreCase = true) == true ||
            message?.contains("popup", ignoreCase = true) == true) {
            Log.d("AdBlocker", "Blocked ad prompt: $message")
            result?.cancel()
            return true
        }
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }
    
    /**
     * Detects if a URL is likely a download link based on file extensions.
     * Note: We avoid checking path patterns like "/download/" to prevent false positives for intermediate HTML pages.
     */
    private fun isDownloadUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // Common download file extensions
        val downloadExtensions = listOf(
            ".apk", ".xapk", ".apks", ".apkm",  // Android apps
            ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2",  // Archives
            ".exe", ".msi", ".dmg", ".pkg",  // Executables
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",  // Documents
            ".mp3", ".mp4", ".avi", ".mkv", ".mov", ".flv", ".wmv",  // Media
            ".iso", ".img",  // Disk images
            ".deb", ".rpm"  // Linux packages
        )
        
        // Check if URL ends with a download extension
        if (downloadExtensions.any { lowerUrl.endsWith(it) || lowerUrl.contains("$it?") || lowerUrl.contains("$it#") }) {
            return true
        }
        
        // Common mod site download URL patterns - these should match ACTUAL download files, not intermediate pages
        // DO NOT match intermediate HTML pages like "/download/app-name/1" as they need to load in browser
        val modSiteDownloadPatterns = listOf(
            Regex("""/download\.php\?.*file="""),  // PHP download scripts with file param
            Regex("""/get_app\?.*id="""),           // App download APIs
            Regex("""/dl/[^/]+\.apk"""),            // Direct APK downloads
            Regex("""\.apk\?"""),                   // APK with query params
            Regex("""download.*\.apk$""")           // Download ending in .apk
        )
        
        if (modSiteDownloadPatterns.any { it.containsMatchIn(lowerUrl) }) {
            return true
        }
        
        return false
    }
    
    /**
     * Checks if a URL belongs to a known OAuth/authentication provider.
     * These URLs should always open as new tabs without download detection to prevent
     * infinite loading issues with sign-in flows.
     */
    private fun isOAuthUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val oauthDomains = listOf(
            "accounts.google.com",
            "accounts.youtube.com",
            "login.microsoftonline.com",
            "appleid.apple.com",
            "facebook.com/login",
            "facebook.com/dialog",
            "github.com/login",
            "twitter.com/oauth",
            "linkedin.com/oauth",
            "id.realme.com",
            "/oauth/",
            "/signin",
            "/login",
            "/auth/"
        )
        return oauthDomains.any { lowerUrl.contains(it) }
    }
    
    
    /**
     * Fetches download metadata (Content-Disposition, Content-Type, Content-Length) via HTTP HEAD request.
     * This ensures proper filename detection and MIME type identification.
     */
    private fun fetchDownloadMetadata(
        url: String,
        referer: String?,
        customUserAgent: String? = null,
        callback: (userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long, finalUrl: String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get cookies for the URL
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url)
                
                // Use the provided User-Agent or fall back to a realistic one
                val userAgent = customUserAgent ?: com.sun.alasbrowser.engine.ChromiumCompat.MOBILE_UA

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .head()
                
                if (!cookies.isNullOrEmpty()) {
                    requestBuilder.addHeader("Cookie", cookies)
                }
                
                if (!referer.isNullOrEmpty()) {
                    requestBuilder.addHeader("Referer", referer)
                }
                
                var response = client.newCall(requestBuilder.build()).execute()
                
                // Fallback: If HEAD not allowed (405), Forbidden (403), or Not Found (404), try GET with range 0-0
                // Cloudflare often returns 403 for HEAD requests without proper headers or even with them sometimes
                if (!response.isSuccessful && (response.code == 405 || response.code == 403 || response.code == 404)) {
                     response.close()
                     Log.d("BrowserChromeClient", "HEAD failed with ${response.code}, trying GET for metadata")
                     val getRequestBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=0-0") // Try to get just first byte
                        .get()
                     
                     if (!cookies.isNullOrEmpty()) {
                        getRequestBuilder.addHeader("Cookie", cookies)
                     }
                     
                     if (!referer.isNullOrEmpty()) {
                        getRequestBuilder.addHeader("Referer", referer)
                     }
                        
                     response = client.newCall(getRequestBuilder.build()).execute()
                }

                val finalUrl = response.request.url.toString()
                val contentDisposition = response.header("Content-Disposition")
                val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim()
                // Content-Length might be for the range if we used GET range
                var contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                
                // If we used Range, correct the content length if possible via Content-Range
                val contentRange = response.header("Content-Range")
                if (contentRange != null && contentRange.contains("/")) {
                    val totalSize = contentRange.substringAfterLast("/").trim().toLongOrNull()
                    if (totalSize != null) contentLength = totalSize
                }
                
                response.close()
                
                // Call callback on main thread
                withContext(Dispatchers.Main) {
                    callback(userAgent, contentDisposition, mimeType, contentLength, finalUrl)
                }
            } catch (e: Exception) {
                Log.e("BrowserChromeClient", "Failed to fetch download metadata", e)
                withContext(Dispatchers.Main) {
                    callback(null, null, null, 0L, url)
                }
            }
        }
    }
}
