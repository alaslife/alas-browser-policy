package com.sun.alasbrowser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.BrowserSession
import com.sun.alasbrowser.data.SessionManager
import com.sun.alasbrowser.ui.BrowserScreen
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme
import com.sun.alasbrowser.web.AlasWebView
import com.sun.alasbrowser.web.SiteCompatibilityManager
import com.sun.alasbrowser.web.WebViewHealth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainActivity : androidx.activity.ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var sessionManager: SessionManager
    private var currentWebView: AlasWebView? = null
    private var currentUrl: String? = null

    private val _intentFlow = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val intentFlow: SharedFlow<Intent> = _intentFlow.asSharedFlow()

    private val _healthFlow = MutableSharedFlow<WebViewHealth>(extraBufferCapacity = 1)
    val healthFlow: SharedFlow<WebViewHealth> = _healthFlow.asSharedFlow()

    private fun initializeServiceWorkers() {
        // Deferred service worker initialization
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual Edge-to-Edge implementation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        actionBar?.hide()
        
        sessionManager = SessionManager(this)
        
        // ✅ Initialize Site Compatibility System (dropdown fixes, rules, auto-learning)
        SiteCompatibilityManager.init(this)
        lifecycleScope.launch {
            SiteCompatibilityManager.syncRules()
        }
        
        // High refresh rate optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.context.display?.let { display ->
                display.supportedModes.maxByOrNull { it.refreshRate }?.let { mode ->
                    if (mode.refreshRate > 60f) {
                        window.attributes = window.attributes.apply {
                            preferredDisplayModeId = mode.modeId
                        }
                    }
                }
            }
        }
        
        // Defer service worker init
        window.decorView.post { initializeServiceWorkers() }
        
        val restoredSession = sessionManager.loadSession()
        intent?.let { _intentFlow.tryEmit(it) }
        
        setContent {
            val preferences = remember { BrowserPreferences(this) }
            
            AlasBrowserTheme(appTheme = preferences.appTheme) {
                BrowserApp(
                    preferences = preferences,
                    intentFlow = intentFlow,
                    healthFlow = healthFlow,
                    restoredSession = restoredSession,
                    onWebViewChanged = { webView, url ->
                        currentWebView = webView as? AlasWebView
                        currentUrl = url
                    }
                )
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        saveCurrentSession()
    }
    
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            sessionManager.clearSession()
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 10) {
            Log.w(TAG, "Memory pressure detected (level=$level)")
            _healthFlow.tryEmit(WebViewHealth.MEMORY_PRESSURE)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentFlow.tryEmit(intent)
    }
    
    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        com.sun.alasbrowser.utils.GeolocationPermissionHelper.onPermissionResult(granted)
    }

    private var pendingSitePermissionCallback: ((Boolean) -> Unit)? = null

    private val sitePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Check if all requested permissions were granted
        val allGranted = results.values.all { it }
        pendingSitePermissionCallback?.invoke(allGranted)
        pendingSitePermissionCallback = null
    }

    fun requestLocationPermission() {
        locationPermissionLauncher.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    fun requestSitePermissions(permissions: Array<String>, callback: (Boolean) -> Unit) {
        pendingSitePermissionCallback = callback
        sitePermissionLauncher.launch(permissions)
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasState", true)
    }
    
    private fun saveCurrentSession() {
        val webView = currentWebView
        val url = currentUrl
        
        if (webView == null || url.isNullOrEmpty()) return
        
        // Only save if WebView is alive
        if (!webView.isAlive) {
            Log.w(TAG, "WebView is dead - saving URL without scroll")
            sessionManager.saveSession(url, 0)
            return
        }
        
        // Safely get scroll position
        webView.safeEvaluateJavascript("(function(){return window.scrollY;})()") { result ->
            try {
                val scrollY = result?.trim('"')?.toIntOrNull() ?: 0
                sessionManager.saveSession(url, scrollY)
            } catch (e: Exception) {
                sessionManager.saveSession(url, 0)
            }
        } ?: sessionManager.saveSession(url, 0)
    }
    
    fun updateSafeState(url: String, scrollY: Int) {
        if (!isFinishing && ::sessionManager.isInitialized) {
            sessionManager.saveSession(url, scrollY)
        }
    }
}

@Composable
fun BrowserApp(
    preferences: BrowserPreferences,
    intentFlow: SharedFlow<Intent>,
    healthFlow: SharedFlow<WebViewHealth>,
    restoredSession: BrowserSession? = null,
    onWebViewChanged: (WebView?, String?) -> Unit = { _, _ -> }
) {
    var initialUrl by remember { mutableStateOf<String?>(null) }
    var openSearchBar by remember { mutableStateOf(false) }
    var openVoiceSearch by remember { mutableStateOf(false) }
    var openCameraSearch by remember { mutableStateOf(false) }
    
    LaunchedEffect(intentFlow) {
        intentFlow.collect { intent ->
            when (intent.action) {
                "com.sun.alasbrowser.WIDGET_SEARCH",
                "com.sun.alasbrowser.SEARCH_CLICK" -> openSearchBar = true
                
                "com.sun.alasbrowser.WIDGET_VOICE_SEARCH",
                "com.sun.alasbrowser.VOICE_CLICK" -> openVoiceSearch = true
                
                "com.sun.alasbrowser.CAMERA_CLICK" -> openCameraSearch = true
                
                Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.isNotEmpty() }?.let {
                    initialUrl = it
                }
                
                Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")?.takeIf { it.isNotEmpty() }?.let {
                    initialUrl = it
                }
            }
        }
    }

    BrowserScreen(
        preferences = preferences,
        openSearchBar = openSearchBar,
        openVoiceSearch = openVoiceSearch,
        openCameraSearch = openCameraSearch,
        initialUrl = initialUrl,
        restoredSession = restoredSession,
        onIntentHandled = {
            openSearchBar = false
            openVoiceSearch = false
            openCameraSearch = false
            initialUrl = null
        },
        onWebViewChanged = onWebViewChanged
    )
}
