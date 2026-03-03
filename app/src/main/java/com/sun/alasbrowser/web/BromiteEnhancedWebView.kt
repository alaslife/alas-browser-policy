package com.sun.alasbrowser.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sun.alasbrowser.utils.WebViewLifecycleManager

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
class BromiteEnhancedWebView(context: Context) : WebView(context) {

    private var isAttachedToLifecycle = false
    
    init {
        configurePrivacySettings()
        configurePerformanceSettings()
        configureMediaSettings()
        
        // Register with lifecycle manager
        WebViewLifecycleManager.registerWebView(this)
        isAttachedToLifecycle = true
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun configurePrivacySettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            
            // Bromite-style privacy enhancements
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Disable geolocation by default
            setGeolocationEnabled(false)
            
            // Block mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // Disable safe browsing data collection
            safeBrowsingEnabled = false
            
            // Enhanced privacy settings
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Security settings
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // Clear referrer headers
            @Suppress("DEPRECATION")
            setSaveFormData(false)
        }
    }
    
    private fun configurePerformanceSettings() {
        settings.apply {
            // Hardware acceleration & speed optimizations
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            
            // Enable wide viewport for better rendering
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Improve loading performance
            loadsImagesAutomatically = true
            blockNetworkImage = false

            offscreenPreRaster = true
            
            // Enhanced caching for better performance
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            domStorageEnabled = true

            // Use AndroidX WebKit for algorithmic darkening if supported
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, true)
            }
        }
        
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    private fun configureMediaSettings() {
        settings.apply {
            // Background playback support - allow media to play without user gesture
            mediaPlaybackRequiresUserGesture = false
            
            // Enable all media features
            javaScriptCanOpenWindowsAutomatically = false
            
            // Support for picture-in-picture and background audio
            // Enable media controls in notifications
            mediaPlaybackRequiresUserGesture = false
        }
        
        // Keep screen on for media playback
        keepScreenOn = false // Don't keep screen on, but keep media playing
    }

    override fun onPause() {
        // Don't pause media playback when app goes to background
        // Override default WebView behavior that pauses everything
    }
    
    override fun onResume() {
        // Keep playback state when resuming - don't call super to avoid pausing media
    }

    override fun destroy() {
        if (isAttachedToLifecycle) {
            WebViewLifecycleManager.unregisterWebView(this)
            isAttachedToLifecycle = false
        }
        super.destroy()
    }
}
