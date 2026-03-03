package com.sun.alasbrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ScrollingBehaviour {
    NEVER_HIDE,
    HIDE_BOTH,
    ONLY_HIDE_TOP;

    companion object {
        fun fromString(name: String): ScrollingBehaviour {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                HIDE_BOTH
            }
        }
    }
}

class BrowserPreferences(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    init {
        // Apply frozen defaults on first launch for low-end devices
        if (!prefs.contains("first_launch_optimized")) {
            if (com.sun.alasbrowser.utils.DeviceUtils.isLowEndDevice(context)) {
                prefs.edit { 
                    putBoolean("memory_saving_enabled", true)
                    putInt("tab_memory_limit", 1) // Strict limit: 1 active WebView
                    putBoolean("autoplay_media", false) // Auto-play off
                    putBoolean("preload_pages", false)
                    putBoolean("first_launch_optimized", true)
                }
            } else {
                prefs.edit { putBoolean("first_launch_optimized", true) }
            }
        }
    }

    private var _searchEngine by mutableStateOf(loadSearchEngine())
    val searchEngine: SearchEngine
        get() = _searchEngine
    
    private var _adBlockEnabled by mutableStateOf(loadAdBlockEnabled())
    val adBlockEnabled: Boolean
        get() = _adBlockEnabled

    private var _javaScriptEnabled by mutableStateOf(loadJavaScriptEnabled())
    val javaScriptEnabled: Boolean
        get() = _javaScriptEnabled

    private var _dataSaverEnabled by mutableStateOf(loadDataSaverEnabled())
    val dataSaverEnabled: Boolean
        get() = _dataSaverEnabled

    private var _cookiesEnabled by mutableStateOf(loadCookiesEnabled())
    val cookiesEnabled: Boolean
        get() = _cookiesEnabled

    private var _popupBlockEnabled by mutableStateOf(loadPopupBlockEnabled())
    val popupBlockEnabled: Boolean
        get() = _popupBlockEnabled

    private var _doNotTrack by mutableStateOf(loadDoNotTrack())
    val doNotTrack: Boolean
        get() = _doNotTrack

    private var _scrollingBehaviour by mutableStateOf(loadScrollingBehaviour())
    val scrollingBehaviour: ScrollingBehaviour
        get() = _scrollingBehaviour

    // Kept for backward compatibility if needed, but derived from scrollingBehaviour
    val hideTopBarOnScroll: Boolean
        get() = _scrollingBehaviour != ScrollingBehaviour.NEVER_HIDE

    private var _savePasswords by mutableStateOf(loadSavePasswords())
    val savePasswords: Boolean
        get() = _savePasswords

    private var _autoFillForms by mutableStateOf(loadAutoFillForms())
    val autoFillForms: Boolean
        get() = _autoFillForms

    private var _notifications by mutableStateOf(loadNotifications())
    val notifications: Boolean
        get() = _notifications

    private var _location by mutableStateOf(loadLocation())
    val location: Boolean
        get() = _location

    private var _camera by mutableStateOf(loadCamera())
    val camera: Boolean
        get() = _camera

    private var _microphone by mutableStateOf(loadMicrophone())
    val microphone: Boolean
        get() = _microphone

    private var _desktopMode by mutableStateOf(loadDesktopMode())
    val desktopMode: Boolean
        get() = _desktopMode

    private var _textSize by mutableIntStateOf(loadTextSize())
    val textSize: Int
        get() = _textSize

    private var _memorySavingEnabled by mutableStateOf(loadMemorySavingEnabled())
    val memorySavingEnabled: Boolean
        get() = _memorySavingEnabled

    private var _pageMemoryLimit by mutableIntStateOf(loadPageMemoryLimit())
    val pageMemoryLimit: Int
        get() = _pageMemoryLimit

    private var _tabMemoryLimit by mutableIntStateOf(loadTabMemoryLimit())
    val tabMemoryLimit: Int
        get() = _tabMemoryLimit

    private var _adBlockerMode by mutableStateOf(loadAdBlockerMode())
    val adBlockerMode: String
        get() = _adBlockerMode

    private var _enabledFilterLists by mutableStateOf(loadEnabledFilterLists())
    val enabledFilterLists: Set<String>
        get() = _enabledFilterLists

    private var _blockCookieDialogs by mutableStateOf(loadBlockCookieDialogs())
    val blockCookieDialogs: Boolean
        get() = _blockCookieDialogs

    private var _autoAcceptCookies by mutableStateOf(loadAutoAcceptCookies())
    val autoAcceptCookies: Boolean
        get() = _autoAcceptCookies

    private var _excludedSites by mutableStateOf(loadExcludedSites())
    val excludedSites: Set<String>
        get() = _excludedSites

    private var _incognitoLockEnabled by mutableStateOf(loadIncognitoLockEnabled())
    val incognitoLockEnabled: Boolean
        get() = _incognitoLockEnabled

    private var _incognitoSearchEngine by mutableStateOf(loadIncognitoSearchEngine())
    val incognitoSearchEngine: SearchEngine
        get() = _incognitoSearchEngine

    private var _downloadLocation by mutableStateOf(loadDownloadLocation())
    val downloadLocation: String
        get() = _downloadLocation

    private var _customWallpaperUri by mutableStateOf<String?>(loadCustomWallpaperUri())
    val customWallpaperUri: String?
        get() = _customWallpaperUri

    private var _selectedWallpaperId by mutableStateOf(loadSelectedWallpaperId())
    val selectedWallpaperId: String
        get() = _selectedWallpaperId

    private var _appTheme by mutableStateOf(loadAppTheme())
    var appTheme: AppTheme
        get() = _appTheme
        internal set(value) {
            _appTheme = value
            prefs.edit(commit = true) { putString("app_theme", value.name) }
        }

    private var _webViewDarkMode by mutableStateOf(loadWebViewDarkMode())
    val webViewDarkMode: WebViewDarkMode
        get() = _webViewDarkMode

    var lastSelectedTabId: String
        get() = prefs.getString("last_selected_tab_id", "") ?: ""
        set(value) = prefs.edit { putString("last_selected_tab_id", value) }

    var wasOnHomepage: Boolean
        get() = prefs.getBoolean("was_on_homepage", true)
        set(value) = prefs.edit { putBoolean("was_on_homepage", value) }

    private fun loadSearchEngine(): SearchEngine {
        val name = prefs.getString("search_engine", SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name
        return try {
            SearchEngine.valueOf(name)
        } catch (e: IllegalArgumentException) {
            SearchEngine.GOOGLE
        }
    }

    private fun loadJavaScriptEnabled(): Boolean {
        return prefs.getBoolean("javascript_enabled", true)
    }

    private fun loadAdBlockEnabled(): Boolean {
        return prefs.getBoolean("adblock_enabled", true)
    }

    private fun loadDataSaverEnabled(): Boolean {
        return prefs.getBoolean("data_saver_enabled", false)
    }

    private fun loadCookiesEnabled(): Boolean {
        return prefs.getBoolean("cookies_enabled", true)
    }

    private fun loadPopupBlockEnabled(): Boolean {
        return prefs.getBoolean("popup_block_enabled", true)
    }

    private fun loadDoNotTrack(): Boolean {
        return prefs.getBoolean("do_not_track", false)
    }

    private fun loadScrollingBehaviour(): ScrollingBehaviour {
        val name = prefs.getString("scrolling_behaviour", null)
        return if (name != null) {
            ScrollingBehaviour.fromString(name)
        } else {
            // Migration from old boolean if it exists
            if (prefs.contains("hide_top_bar")) {
                if (prefs.getBoolean("hide_top_bar", true)) ScrollingBehaviour.HIDE_BOTH else ScrollingBehaviour.NEVER_HIDE
            } else {
                ScrollingBehaviour.HIDE_BOTH
            }
        }
    }

    private fun loadSavePasswords(): Boolean {
        return prefs.getBoolean("save_passwords", true)
    }

    private fun loadAutoFillForms(): Boolean {
        return prefs.getBoolean("autofill_forms", true)
    }

    private fun loadNotifications(): Boolean {
        return prefs.getBoolean("notifications", true)
    }

    private fun loadLocation(): Boolean {
        return prefs.getBoolean("location", false)
    }

    private fun loadCustomWallpaperUri(): String? {
        return prefs.getString("custom_wallpaper_uri", null)
    }

    private fun loadSelectedWallpaperId(): String {
        return prefs.getString("selected_wallpaper_id", "default") ?: "default"
    }

    private fun loadCamera(): Boolean {
        return prefs.getBoolean("camera", false)
    }

    private fun loadMicrophone(): Boolean {
        return prefs.getBoolean("microphone", false)
    }

    private fun loadDesktopMode(): Boolean {
        return prefs.getBoolean("desktop_mode", false)
    }

    private fun loadTextSize(): Int {
        return prefs.getInt("text_size", 100)
    }

    private fun loadMemorySavingEnabled(): Boolean {
        return prefs.getBoolean("memory_saving_enabled", false)
    }

    private fun loadPageMemoryLimit(): Int {
        return prefs.getInt("page_memory_limit", 5)
    }

    private fun loadTabMemoryLimit(): Int {
        return prefs.getInt("tab_memory_limit", 5)
    }

    private fun loadAdBlockerMode(): String {
        return prefs.getString("ad_blocker_mode", "BALANCED") ?: "BALANCED"
    }

    private fun loadEnabledFilterLists(): Set<String> {
        val saved = prefs.getStringSet("enabled_filter_lists", null)
        return saved ?: setOf("easylist")
    }

    private fun loadBlockCookieDialogs(): Boolean {
        return prefs.getBoolean("block_cookie_dialogs", true)
    }

    private fun loadAutoAcceptCookies(): Boolean {
        return prefs.getBoolean("auto_accept_cookies", false)
    }

    private fun loadExcludedSites(): Set<String> {
        val saved = prefs.getStringSet("excluded_sites", null)
        return saved ?: emptySet()
    }

    private fun loadIncognitoLockEnabled(): Boolean {
        return prefs.getBoolean("incognito_lock_enabled", false)
    }

    private fun loadIncognitoSearchEngine(): SearchEngine {
        val name = prefs.getString("incognito_search_engine", SearchEngine.DUCKDUCKGO.name) ?: SearchEngine.DUCKDUCKGO.name
        return try {
            SearchEngine.valueOf(name)
        } catch (e: IllegalArgumentException) {
            SearchEngine.DUCKDUCKGO
        }
    }

    private fun loadDownloadLocation(): String {
        return prefs.getString("download_location", "") ?: ""
    }

    private fun loadAppTheme(): AppTheme {
        val themeName = prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return AppTheme.fromString(themeName)
    }

    private fun loadWebViewDarkMode(): WebViewDarkMode {
        val modeName = prefs.getString("webview_dark_mode", WebViewDarkMode.AUTOMATIC.name) ?: WebViewDarkMode.AUTOMATIC.name
        return WebViewDarkMode.fromString(modeName)
    }

    fun setSearchEngine(engine: SearchEngine) {
        _searchEngine = engine
        prefs.edit { putString("search_engine", engine.name) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        _javaScriptEnabled = enabled
        prefs.edit { putBoolean("javascript_enabled", enabled) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        _adBlockEnabled = enabled
        prefs.edit { putBoolean("adblock_enabled", enabled) }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        _dataSaverEnabled = enabled
        prefs.edit { putBoolean("data_saver_enabled", enabled) }
    }

    fun setCookiesEnabled(enabled: Boolean) {
        _cookiesEnabled = enabled
        prefs.edit { putBoolean("cookies_enabled", enabled) }
    }

    fun setPopupBlockEnabled(enabled: Boolean) {
        _popupBlockEnabled = enabled
        prefs.edit { putBoolean("popup_block_enabled", enabled) }
    }

    fun setDoNotTrack(enabled: Boolean) {
        _doNotTrack = enabled
        prefs.edit { putBoolean("do_not_track", enabled) }
    }

    fun setScrollingBehaviour(behaviour: ScrollingBehaviour) {
        _scrollingBehaviour = behaviour
        prefs.edit { putString("scrolling_behaviour", behaviour.name) }
    }

    fun setHideTopBarOnScroll(enabled: Boolean) {
        // Legacy setter, maps to ScrollingBehaviour
        val behaviour = if (enabled) ScrollingBehaviour.HIDE_BOTH else ScrollingBehaviour.NEVER_HIDE
        setScrollingBehaviour(behaviour)
    }

    fun setSavePasswords(enabled: Boolean) {
        _savePasswords = enabled
        prefs.edit { putBoolean("save_passwords", enabled) }
    }

    fun setAutoFillForms(enabled: Boolean) {
        _autoFillForms = enabled
        prefs.edit { putBoolean("autofill_forms", enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        _notifications = enabled
        prefs.edit { putBoolean("notifications", enabled) }
    }

    fun setLocation(enabled: Boolean) {
        _location = enabled
        prefs.edit { putBoolean("location", enabled) }
    }

    fun setCustomWallpaperUri(uri: String?) {
        _customWallpaperUri = uri
        prefs.edit { putString("custom_wallpaper_uri", uri) }
    }

    fun setSelectedWallpaperId(id: String) {
        _selectedWallpaperId = id
        prefs.edit { putString("selected_wallpaper_id", id) }
    }

    fun setCamera(enabled: Boolean) {
        _camera = enabled
        prefs.edit { putBoolean("camera", enabled) }
    }

    fun setMicrophone(enabled: Boolean) {
        _microphone = enabled
        prefs.edit { putBoolean("microphone", enabled) }
    }

    // ============== Per-site Camera Permissions ==============
    
    /**
     * Get all sites with custom camera permission settings
     * Returns a map of domain -> true (allowed) or false (blocked)
     */
    fun getSiteCameraPermissions(): Map<String, Boolean> {
        val sitesJson = prefs.getString("site_camera_permissions", null) ?: return emptyMap()
        return try {
            org.json.JSONObject(sitesJson).let { json ->
                val map = mutableMapOf<String, Boolean>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    map[key] = json.getBoolean(key)
                }
                map
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Set camera permission for a specific site (domain)
     * @param domain The domain to set permission for (e.g., "example.com")
     * @param allowed true to allow, false to block
     */
    fun setSiteCameraPermission(domain: String, allowed: Boolean) {
        val current = getSiteCameraPermissions().toMutableMap()
        if (allowed) {
            current[domain] = true
        } else {
            current.remove(domain)
        }
        saveSiteCameraPermissions(current)
    }

    /**
     * Check if camera is allowed for a specific domain
     * Returns null if no specific setting exists (uses global default)
     */
    fun isSiteCameraAllowed(domain: String): Boolean? {
        return getSiteCameraPermissions()[domain]
    }

    /**
     * Remove camera permission setting for a site (reverts to global default)
     */
    fun removeSiteCameraPermission(domain: String) {
        val current = getSiteCameraPermissions().toMutableMap()
        current.remove(domain)
        saveSiteCameraPermissions(current)
    }

    private fun saveSiteCameraPermissions(sites: Map<String, Boolean>) {
        val json = org.json.JSONObject()
        sites.forEach { (domain, allowed) ->
            json.put(domain, allowed)
        }
        prefs.edit { putString("site_camera_permissions", json.toString()) }
    }

    // ============== Per-site Microphone Permissions ==============
    
    /**
     * Get all sites with custom microphone permission settings
     * Returns a map of domain -> true (allowed) or false (blocked)
     */
    fun getSiteMicrophonePermissions(): Map<String, Boolean> {
        val sitesJson = prefs.getString("site_microphone_permissions", null) ?: return emptyMap()
        return try {
            org.json.JSONObject(sitesJson).let { json ->
                val map = mutableMapOf<String, Boolean>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    map[key] = json.getBoolean(key)
                }
                map
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Set microphone permission for a specific site (domain)
     * @param domain The domain to set permission for (e.g., "example.com")
     * @param allowed true to allow, false to block
     */
    fun setSiteMicrophonePermission(domain: String, allowed: Boolean) {
        val current = getSiteMicrophonePermissions().toMutableMap()
        if (allowed) {
            current[domain] = true
        } else {
            current.remove(domain)
        }
        saveSiteMicrophonePermissions(current)
    }

    /**
     * Check if microphone is allowed for a specific domain
     * Returns null if no specific setting exists (uses global default)
     */
    fun isSiteMicrophoneAllowed(domain: String): Boolean? {
        return getSiteMicrophonePermissions()[domain]
    }

    /**
     * Remove microphone permission setting for a site (reverts to global default)
     */
    fun removeSiteMicrophonePermission(domain: String) {
        val current = getSiteMicrophonePermissions().toMutableMap()
        current.remove(domain)
        saveSiteMicrophonePermissions(current)
    }

    private fun saveSiteMicrophonePermissions(sites: Map<String, Boolean>) {
        val json = org.json.JSONObject()
        sites.forEach { (domain, allowed) ->
            json.put(domain, allowed)
        }
        prefs.edit { putString("site_microphone_permissions", json.toString()) }
    }

    /**
     * Extract domain from URL for permission checks
     */
    fun extractDomain(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if camera is allowed for a given URL (considering both global and site-specific settings)
     */
    fun isCameraAllowedForUrl(url: String): Boolean {
        val domain = extractDomain(url) ?: return _camera
        val sitePermission = isSiteCameraAllowed(domain)
        return sitePermission ?: _camera
    }

    /**
     * Check if microphone is allowed for a given URL (considering both global and site-specific settings)
     */
    fun isMicrophoneAllowedForUrl(url: String): Boolean {
        val domain = extractDomain(url) ?: return _microphone
        val sitePermission = isSiteMicrophoneAllowed(domain)
        return sitePermission ?: _microphone
    }

    fun setDesktopMode(enabled: Boolean) {
        _desktopMode = enabled
        prefs.edit { putBoolean("desktop_mode", enabled) }
    }

    fun setTextSize(size: Int) {
        _textSize = size
        prefs.edit { putInt("text_size", size) }
    }

    fun setMemorySavingEnabled(enabled: Boolean) {
        _memorySavingEnabled = enabled
        prefs.edit { putBoolean("memory_saving_enabled", enabled) }
    }

    fun setPageMemoryLimit(limit: Int) {
        _pageMemoryLimit = limit
        prefs.edit { putInt("page_memory_limit", limit) }
    }

    fun setTabMemoryLimit(limit: Int) {
        _tabMemoryLimit = limit
        prefs.edit { putInt("tab_memory_limit", limit) }
    }

    fun setAdBlockerMode(mode: String) {
        _adBlockerMode = mode
        prefs.edit { putString("ad_blocker_mode", mode) }
    }

    fun setEnabledFilterLists(lists: Set<String>) {
        _enabledFilterLists = lists
        prefs.edit { putStringSet("enabled_filter_lists", lists) }
    }

    fun setBlockCookieDialogs(enabled: Boolean) {
        _blockCookieDialogs = enabled
        prefs.edit { putBoolean("block_cookie_dialogs", enabled) }
    }

    fun setAutoAcceptCookies(enabled: Boolean) {
        _autoAcceptCookies = enabled
        prefs.edit { putBoolean("auto_accept_cookies", enabled) }
    }

    fun setExcludedSites(sites: Set<String>) {
        _excludedSites = sites
        this.prefs.edit { putStringSet("excluded_sites", sites) }
    }

    fun addExcludedSite(site: String) {
        val newSites = _excludedSites + site
        setExcludedSites(newSites)
    }

    fun removeExcludedSite(site: String) {
        val newSites = _excludedSites - site
        setExcludedSites(newSites)
    }

    fun setIncognitoLockEnabled(enabled: Boolean) {
        _incognitoLockEnabled = enabled
        prefs.edit { putBoolean("incognito_lock_enabled", enabled) }
    }

    fun setIncognitoSearchEngine(engine: SearchEngine) {
        _incognitoSearchEngine = engine
        prefs.edit { putString("incognito_search_engine", engine.name) }
    }

    fun setDownloadLocation(location: String) {
        _downloadLocation = location
        prefs.edit { putString("download_location", location) }
    }


    fun setWebViewDarkMode(mode: WebViewDarkMode) {
        _webViewDarkMode = mode
        prefs.edit { putString("webview_dark_mode", mode.name) }
    }





    private var _isFirstLaunch by mutableStateOf(loadIsFirstLaunch())

    private fun loadIsFirstLaunch(): Boolean {
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setIsFirstLaunch(isFirst: Boolean) {
        _isFirstLaunch = isFirst
        prefs.edit { putBoolean("is_first_launch", isFirst) }
    }

    private var _proxyEnabled by mutableStateOf(loadProxyEnabled())
    val proxyEnabled: Boolean
        get() = _proxyEnabled

    private fun loadProxyEnabled(): Boolean {
        return prefs.getBoolean("proxy_enabled", false)
    }

    fun setProxyEnabled(enabled: Boolean) {
        _proxyEnabled = enabled
        prefs.edit { putBoolean("proxy_enabled", enabled) }
    }




    
    // Night Mode Settings
    private var _nightModeColorTemp by mutableStateOf(loadNightModeColorTemp())
    val nightModeColorTemp: Float
        get() = _nightModeColorTemp
    
    private fun loadNightModeColorTemp(): Float {
        return prefs.getFloat("night_mode_color_temp", 0.3f)
    }
    
    fun setNightModeColorTemp(value: Float) {
        _nightModeColorTemp = value
        prefs.edit { putFloat("night_mode_color_temp", value) }
    }
    
    private var _nightModeDimming by mutableStateOf(loadNightModeDimming())
    val nightModeDimming: Float
        get() = _nightModeDimming
    
    private fun loadNightModeDimming(): Float {
        return prefs.getFloat("night_mode_dimming", 0.1f)
    }
    
    fun setNightModeDimming(value: Float) {
        _nightModeDimming = value
        prefs.edit { putFloat("night_mode_dimming", value) }
    }
    
    private var _dimKeyboard by mutableStateOf(loadDimKeyboard())
    val dimKeyboard: Boolean
        get() = _dimKeyboard
    
    private fun loadDimKeyboard(): Boolean {
        return prefs.getBoolean("dim_keyboard", false)
    }
    
    fun setDimKeyboard(enabled: Boolean) {
        _dimKeyboard = enabled
        prefs.edit { putBoolean("dim_keyboard", enabled) }
    }

    // Night Mode Enabled
    private var _nightModeEnabled by mutableStateOf(loadNightModeEnabled())
    val nightModeEnabled: Boolean
        get() = _nightModeEnabled

    private fun loadNightModeEnabled(): Boolean {
        return prefs.getBoolean("night_mode_enabled", false)
    }

    fun setNightModeEnabled(enabled: Boolean) {
        _nightModeEnabled = enabled
        prefs.edit { putBoolean("night_mode_enabled", enabled) }
    }

    // Night Mode Schedule
    private var _nightModeScheduleEnabled by mutableStateOf(loadNightModeScheduleEnabled())
    val nightModeScheduleEnabled: Boolean
        get() = _nightModeScheduleEnabled

    private fun loadNightModeScheduleEnabled(): Boolean {
        return prefs.getBoolean("night_mode_schedule_enabled", false)
    }

    fun setNightModeScheduleEnabled(enabled: Boolean) {
        _nightModeScheduleEnabled = enabled
        prefs.edit { putBoolean("night_mode_schedule_enabled", enabled) }
    }

    private var _nightModeScheduleStart by mutableIntStateOf(loadNightModeScheduleStart())
    val nightModeScheduleStart: Int
        get() = _nightModeScheduleStart

    private fun loadNightModeScheduleStart(): Int {
        return prefs.getInt("night_mode_schedule_start", 21)
    }

    fun setNightModeScheduleStart(hour: Int) {
        _nightModeScheduleStart = hour
        prefs.edit { putInt("night_mode_schedule_start", hour) }
    }

    private var _nightModeScheduleEnd by mutableIntStateOf(loadNightModeScheduleEnd())
    val nightModeScheduleEnd: Int
        get() = _nightModeScheduleEnd

    private fun loadNightModeScheduleEnd(): Int {
        return prefs.getInt("night_mode_schedule_end", 7)
    }

    fun setNightModeScheduleEnd(hour: Int) {
        _nightModeScheduleEnd = hour
        prefs.edit { putInt("night_mode_schedule_end", hour) }
    }

    private var _nightModeAutoSunset by mutableStateOf(loadNightModeAutoSunset())
    val nightModeAutoSunset: Boolean
        get() = _nightModeAutoSunset

    private fun loadNightModeAutoSunset(): Boolean {
        return prefs.getBoolean("night_mode_auto_sunset", false)
    }

    fun setNightModeAutoSunset(enabled: Boolean) {
        _nightModeAutoSunset = enabled
        prefs.edit { putBoolean("night_mode_auto_sunset", enabled) }
    }

    fun isNightModeActiveNow(): Boolean {
        if (nightModeEnabled) return true
        if (!nightModeScheduleEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val start = nightModeScheduleStart
        val end = nightModeScheduleEnd
        return if (start <= end) {
            currentHour in start..end
        } else {
            currentHour >= start || currentHour <= end
        }
    }

    // Background Playback
    private var _backgroundPlaybackEnabled by mutableStateOf(loadBackgroundPlaybackEnabled())
    val backgroundPlaybackEnabled: Boolean
        get() = _backgroundPlaybackEnabled

    private fun loadBackgroundPlaybackEnabled(): Boolean {
        return prefs.getBoolean("background_playback_enabled", true)
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _backgroundPlaybackEnabled = enabled
        prefs.edit { putBoolean("background_playback_enabled", enabled) }
    }

    // Download settings
    val downloadThreads: Int
        get() = prefs.getInt("download_threads", 8).coerceIn(1, 16)

    fun setDownloadThreads(count: Int) {
        prefs.edit { putInt("download_threads", count.coerceIn(1, 16)) }
    }

    val maxConcurrentDownloads: Int
        get() = prefs.getInt("max_concurrent_downloads", 3).coerceIn(1, 5)

    fun setMaxConcurrentDownloads(count: Int) {
        prefs.edit { putInt("max_concurrent_downloads", count.coerceIn(1, 5)) }
    }

    val downloadRetryLimit: Int
        get() = prefs.getInt("download_retry_limit", 15).coerceIn(1, 30)

    fun setDownloadRetryLimit(count: Int) {
        prefs.edit { putInt("download_retry_limit", count.coerceIn(1, 30)) }
    }
}
