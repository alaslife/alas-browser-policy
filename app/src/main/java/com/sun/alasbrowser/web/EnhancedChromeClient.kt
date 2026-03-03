package com.sun.alasbrowser.web

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.media3.ui.PlayerView

class EnhancedChromeClient(
    private val activity: Activity,
    private val onProgressChanged: (Int) -> Unit = {},
    private val onFaviconReceived: (Bitmap?) -> Unit = {},
    private val onShowCustomView: ((View, CustomViewCallback) -> Unit)? = null,
    private val onHideCustomView: (() -> Unit)? = null,
    private val onFileChooser: ((ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean)? = null,
    private val onVideoPlaying: ((Boolean) -> Unit)? = null,
    private val onFullscreenChanged: ((Boolean) -> Unit)? = null
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var originalOrientation: Int? = null
    private var isVideoPlaying = false
    private var isFullscreen = false

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        onFaviconReceived(icon)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        Log.d("EnhancedChromeClient", "onShowCustomView called - entering fullscreen")
        
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        
        view?.let { v ->
            callback?.let { cb ->
                customView = v
                customViewCallback = cb
                isVideoPlaying = true
                isFullscreen = true
                
                originalOrientation = activity.requestedOrientation
                
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                
                val window = activity.window
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                v.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                if (v is FrameLayout) {
                    val videoView = findVideoView(v)
                    videoView?.let {
                        it.keepScreenOn = true
                        Log.d("EnhancedChromeClient", "Found video element, keeping screen on")
                    }
                }
                
                onVideoPlaying?.invoke(true)
                onFullscreenChanged?.invoke(true)
                onShowCustomView?.invoke(v, cb)
            }
        }
    }

    override fun onHideCustomView() {
        Log.d("EnhancedChromeClient", "onHideCustomView called - exiting fullscreen")
        
        customView?.let { view ->
            if (view is FrameLayout) {
                val videoView = findVideoView(view)
                videoView?.keepScreenOn = false
            }
        }
        
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        isVideoPlaying = false
        isFullscreen = false
        
        originalOrientation?.let {
            activity.requestedOrientation = it
        }
        
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        
        onVideoPlaying?.invoke(false)
        onFullscreenChanged?.invoke(false)
        onHideCustomView?.invoke()
        
        super.onHideCustomView()
    }
    
    private fun findVideoView(viewGroup: ViewGroup): View? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.widget.VideoView || child is PlayerView) {
                return child
            }
            if (child is ViewGroup) {
                findVideoView(child)?.let { return it }
            }
        }
        return null
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
            val preferences = com.sun.alasbrowser.data.BrowserPreferences(activity)
            val domain = preferences.extractDomain(origin) ?: return@let

            // First check if there is an explicit user preference saved for this specific site
            val savedMicPref = preferences.isSiteMicrophoneAllowed(domain)
            val savedCamPref = preferences.isSiteCameraAllowed(domain)

            val wantsMic = req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            val wantsCam = req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            // If preferences exist for what's requested, apply them immediately
            val hasExistingDecision = (wantsMic && savedMicPref != null) || (wantsCam && savedCamPref != null) || (!wantsMic && !wantsCam)

            if (hasExistingDecision) {
                applyPermissionsBasedOnPreferences(req, domain, preferences)
                return
            }

            // Otherwise, prompt the user with an AlertDialog
            var message = "$domain wants to "
            if (wantsMic && wantsCam) {
                message += "use your camera and microphone."
            } else if (wantsCam) {
                message += "use your camera."
            } else if (wantsMic) {
                message += "use your microphone."
            } else {
                applyPermissionsBasedOnPreferences(req, domain, preferences)
                return
            }

            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Permission Request")
                .setMessage(message)
                .setPositiveButton("Allow") { _, _ ->
                    if (wantsMic) preferences.setSiteMicrophonePermission(domain, true)
                    if (wantsCam) preferences.setSiteCameraPermission(domain, true)
                    applyPermissionsBasedOnPreferences(req, domain, preferences)
                }
                .setNegativeButton("Block") { _, _ ->
                    if (wantsMic) preferences.setSiteMicrophonePermission(domain, false)
                    if (wantsCam) preferences.setSiteCameraPermission(domain, false)
                    applyPermissionsBasedOnPreferences(req, domain, preferences)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun applyPermissionsBasedOnPreferences(req: PermissionRequest, domain: String, preferences: com.sun.alasbrowser.data.BrowserPreferences) {
        val allowedResources = req.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    preferences.isSiteMicrophoneAllowed(domain) ?: (preferences.microphone)
                }
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    preferences.isSiteCameraAllowed(domain) ?: (preferences.camera)
                }
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> true
                PermissionRequest.RESOURCE_MIDI_SYSEX -> true
                else -> false
            }
        }

        if (allowedResources.isEmpty()) {
            Log.d("EnhancedChromeClient", "🚫 Permission denied for $domain - camera/mic blocked")
            req.deny()
            return
        }

        Log.d("EnhancedChromeClient", "✅ Permission granted for $domain: ${allowedResources.joinToString()}")

        // Check Android runtime permissions
        val neededPermissions = mutableListOf<String>()
        allowedResources.forEach { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(android.Manifest.permission.CAMERA)
                    }
                }
            }
        }

        // Request permissions if needed
        if (neededPermissions.isNotEmpty() && activity is com.sun.alasbrowser.MainActivity) {
            activity.requestSitePermissions(neededPermissions.toTypedArray()) { allGranted ->
                if (allGranted) {
                    Log.d("EnhancedChromeClient", "✅ Android permissions granted, honoring WebView request")
                    req.grant(allowedResources.toTypedArray())
                } else {
                    Log.d("EnhancedChromeClient", "🚫 Android permissions denied, denying WebView request")
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
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            activity, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
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
        Log.d("EnhancedChromeClient", "Blocked popup window creation")
        return false
    }
    
    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message?.contains("ad", ignoreCase = true) == true ||
            message?.contains("popup", ignoreCase = true) == true) {
            Log.d("EnhancedChromeClient", "Blocked ad alert: $message")
            result?.cancel()
            return true
        }
        return super.onJsAlert(view, url, message, result)
    }
    
    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message?.contains("ad", ignoreCase = true) == true ||
            message?.contains("popup", ignoreCase = true) == true) {
            Log.d("EnhancedChromeClient", "Blocked ad confirm: $message")
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
            Log.d("EnhancedChromeClient", "Blocked ad prompt: $message")
            result?.cancel()
            return true
        }
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }
    
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            Log.d("WebConsole", "${it.sourceId()}:${it.lineNumber()} - ${it.message()}")
        }
        return super.onConsoleMessage(consoleMessage)
    }

}
