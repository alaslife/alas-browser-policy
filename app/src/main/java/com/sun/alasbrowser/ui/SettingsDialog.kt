package com.sun.alasbrowser.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sun.alasbrowser.data.AppTheme
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.ScrollingBehaviour
import com.sun.alasbrowser.data.SearchEngine
import com.sun.alasbrowser.utils.ScriptManager

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    preferences: BrowserPreferences,
    currentUrl: String = "",
    onHistoryNavigate: (String) -> Unit = {},
    onReaderModeClick: () -> Unit = {},
    onTranslate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val historyLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("url")?.let { url ->
                onHistoryNavigate(url)
                onDismiss()
            }
        }
    }
    
    // Launcher for requesting role (default browser)
    val roleRequestLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { 
        // No specific action needed on result
    }
    
    var showCredentialManager by remember { mutableStateOf(false) }
    
    if (showCredentialManager) {
        CredentialListScreen(
            onDismiss = { showCredentialManager = false }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = false) { }
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                var showSearchEngineDialog by remember { mutableStateOf(false) }
                var showIncognitoSearchDialog by remember { mutableStateOf(false) }
                var showScrollBehaviorDialog by remember { mutableStateOf(false) }
                var showDownloadLocationPicker by remember { mutableStateOf(false) }
                var showThemeDialog by remember { mutableStateOf(false) }
                var showDarkWebPagesDialog by remember { mutableStateOf(false) }
                var showWallpaperDialog by remember { mutableStateOf(false) }
                var adBlockEnabled by remember { mutableStateOf(preferences.adBlockEnabled) }
                var incognitoLockEnabled by remember { mutableStateOf(preferences.incognitoLockEnabled) }
                var incognitoSearchEngine by remember { mutableStateOf(preferences.incognitoSearchEngine) }
                var downloadLocation by remember { mutableStateOf(preferences.downloadLocation) }
                var currentTheme by remember { mutableStateOf(preferences.appTheme) }
         
                if (showSearchEngineDialog) {
                    SearchEngineSelectionDialog(
                        currentEngine = preferences.searchEngine,
                        onEngineSelected = { engine ->
                            preferences.setSearchEngine(engine)
                            showSearchEngineDialog = false
                        },
                        onDismiss = { showSearchEngineDialog = false }
                    )
                }

                if (showScrollBehaviorDialog) {
                    ScrollBehaviorDialog(
                        currentBehavior = preferences.scrollingBehaviour,
                        onBehaviorSelected = { behavior ->
                            preferences.setScrollingBehaviour(behavior)
                            showScrollBehaviorDialog = false
                        },
                        onDismiss = { showScrollBehaviorDialog = false }
                    )
                }
                
                if (showIncognitoSearchDialog) {
                    SearchEngineSelectionDialog(
                        currentEngine = incognitoSearchEngine,
                        onEngineSelected = { engine ->
                            preferences.setIncognitoSearchEngine(engine)
                            incognitoSearchEngine = engine
                            showIncognitoSearchDialog = false
                        },
                        onDismiss = { showIncognitoSearchDialog = false }
                    )
                }
                
                if (showDownloadLocationPicker) {
                    DownloadLocationDialog(
                        currentLocation = downloadLocation,
                        onDismiss = { showDownloadLocationPicker = false },
                        onSelect = { location ->
                            preferences.setDownloadLocation(location)
                            downloadLocation = location
                            showDownloadLocationPicker = false
                        }
                    )
                }
                
                if (showThemeDialog) {
                    ThemeSelectionDialog(
                        currentTheme = currentTheme,
                        onThemeSelected = { theme ->
                            preferences.appTheme = theme
                            currentTheme = theme
                            showThemeDialog = false
                            
                            // Force widget update
                            val intent = Intent(context, com.sun.alasbrowser.widget.SearchWidgetModernProvider::class.java).apply {
                                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
                                    android.content.ComponentName(context, com.sun.alasbrowser.widget.SearchWidgetModernProvider::class.java)
                                )
                                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            }
                            context.sendBroadcast(intent)
                        },
                        onDismiss = { showThemeDialog = false }
                    )
                }
                
                if (showDarkWebPagesDialog) {
                    DarkWebPagesDialog(
                        currentMode = preferences.webViewDarkMode,
                        onModeSelected = { mode ->
                            preferences.setWebViewDarkMode(mode)
                            showDarkWebPagesDialog = false
                        },
                        onDismiss = { showDarkWebPagesDialog = false }
                    )
                }

                if (showWallpaperDialog) {
                    WallpaperSettingsDialog(
                        preferences = preferences,
                        onDismiss = { showWallpaperDialog = false }
                    )
                }

                // General
                SettingsGroup(title = "General") {
                     SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.ic_home),
                        title = "Set as Default Browser",
                        subtitle = "Open links with AlasBrowser",
                        onClick = {
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(RoleManager::class.java)
                                if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                                    if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                                        roleRequestLauncher.launch(intent)
                                    } else {
                                        android.widget.Toast.makeText(context, "AlasBrowser is already default", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.search_dark),
                        title = "Search Engine",
                        subtitle = preferences.searchEngine.displayName,
                        onClick = { showSearchEngineDialog = true }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.search_incogonito_dark),
                        title = "Incognito Search Engine",
                        subtitle = incognitoSearchEngine.displayName,
                        onClick = { showIncognitoSearchDialog = true }
                    )
                }

                 // Appearance
                SettingsGroup(title = "Appearance") {
                    SettingsItem(
                        icon = Icons.Default.ColorLens,
                        title = "App Theme",
                        subtitle = currentTheme.displayName,
                        onClick = { showThemeDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.Wallpaper,
                        title = "Wallpaper",
                        subtitle = "Customise background",
                        onClick = { showWallpaperDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = "Webpage Dark Mode",
                        subtitle = preferences.webViewDarkMode.displayName,
                        onClick = { showDarkWebPagesDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    if (currentUrl.isNotEmpty()) {
                         SettingsItem(
                            icon = Icons.Default.Info,
                            title = "Translate",
                            subtitle = "Translate page to English",
                            onClick = {
                                onTranslate()
                                onDismiss()
                            }
                        )
                         HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    }
                    
                    SettingsItem(
                        icon = Icons.Default.MenuBook,
                        title = "Reader View",
                        subtitle = "Distraction-free reading",
                        onClick = {
                            onReaderModeClick()
                            onDismiss()
                        }
                    )
                    
                     HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.desktop_mode_dark),
                        title = "Desktop Site",
                        subtitle = "Request desktop version",
                        checked = preferences.desktopMode,
                        onCheckedChange = { checked ->
                            preferences.setDesktopMode(checked)
                            // Note: Reload is handled by GeckoViewContainer observing the preference
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.bar_position_change),
                        title = "Scrolling behaviour",
                        subtitle = when (preferences.scrollingBehaviour) {
                            ScrollingBehaviour.NEVER_HIDE -> "Never hide toolbars when scrolling"
                            ScrollingBehaviour.HIDE_BOTH -> "Hide toolbars when scrolling"
                            ScrollingBehaviour.ONLY_HIDE_TOP -> "Only hide address bar when scrolling"
                        },
                        onClick = { showScrollBehaviorDialog = true }
                    )
                }

                // Privacy
                SettingsGroup(title = "Privacy & Security") {
                    SettingsToggleItem(
                        icon = Icons.Default.Shield,
                        title = "Enable Proxy / VPN",
                        subtitle = "Route traffic through alas-proxy.onrender.com",
                        checked = preferences.proxyEnabled,
                        onCheckedChange = { preferences.setProxyEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.ad_block_dark),
                        title = "Ad Blocker",
                        subtitle = "Block ads, trackers & malware",
                        checked = adBlockEnabled,
                        onCheckedChange = { 
                            adBlockEnabled = it
                            preferences.setAdBlockEnabled(it)
                        }
                    )

                     HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.incognito_lock_dark),
                        title = "Incognito Lock",
                        subtitle = if (incognitoLockEnabled) "On" else "Off",
                        onClick = {
                            if (!incognitoLockEnabled) {
                                try {
                                    val act = context as? ComponentActivity
                                    if (act != null) {
                                        authenticateForIncognitoLock(act) { success ->
                                            if (success) {
                                                preferences.setIncognitoLockEnabled(true)
                                                incognitoLockEnabled = true
                                            }
                                        }
                                    } else {
                                        preferences.setIncognitoLockEnabled(true)
                                        incognitoLockEnabled = true
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                preferences.setIncognitoLockEnabled(false)
                                incognitoLockEnabled = false
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.do_not_track_dark),
                        title = "Do Not Track",
                        subtitle = "Request websites not to track you",
                        checked = preferences.doNotTrack,
                        onCheckedChange = { preferences.setDoNotTrack(it) }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.cookies_dark),
                        title = "Accept Cookies",
                        subtitle = "Allow sites to save cookies",
                        checked = preferences.cookiesEnabled,
                        onCheckedChange = { preferences.setCookiesEnabled(it) }
                    )
                    
                     HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.block_pop_ups_dark),
                        title = "Block Pop-ups",
                        subtitle = "Prevent pop-up windows",
                        checked = preferences.popupBlockEnabled,
                        onCheckedChange = { preferences.setPopupBlockEnabled(it) }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "Allow sites to show notifications",
                        checked = preferences.notifications,
                        onCheckedChange = { preferences.setNotifications(it) }
                    )
                }

                // Tools & Data
                SettingsGroup(title = "Tools & Data") {
                     SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.history_dark),
                        title = "History",
                        subtitle = "View browsing history",
                        onClick = { 
                            try {
                                val intent = Intent(context, HistoryActivity::class.java)
                                historyLauncher.launch(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.download_dark),
                        title = "Downloads",
                        subtitle = "View downloaded files",
                        onClick = { 
                            try {
                                val intent = Intent(context, DownloadsActivity::class.java)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                     SettingsItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Storage & Cache",
                        subtitle = "Clear cache and free up space",
                        onClick = {
                            try {
                                val intent = Intent(context, StorageManagerActivity::class.java)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                     val downloadPath = if (downloadLocation.isEmpty()) {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .absolutePath + "/Alasbrowser"
                    } else {
                        downloadLocation
                    }
                    
                    SettingsItem(
                        icon = Icons.Default.KeyboardArrowDown,
                        title = "Download Location",
                        subtitle = downloadPath,
                        onClick = { showDownloadLocationPicker = true }
                    )
                }

                // Advanced
                SettingsGroup(title = "Advanced") {
                     SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.data_saving_dark),
                        title = "Data Saver",
                        subtitle = "Reduce data usage",
                        checked = preferences.dataSaverEnabled,
                        onCheckedChange = { preferences.setDataSaverEnabled(it) }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                     SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.javascript_dark),
                        title = "Enable JavaScript",
                        subtitle = "Required for most modern websites",
                        checked = preferences.javaScriptEnabled,
                        onCheckedChange = { enabled ->
                            preferences.setJavaScriptEnabled(enabled)
                            // JavasScript toggle requires reload
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                     SettingsToggleItemWithPainter(
                        painter = painterResource(id = com.sun.alasbrowser.R.drawable.save_passwords_dark),
                        title = "Save Passwords",
                        subtitle = "Remember login credentials",
                        checked = preferences.savePasswords,
                        onCheckedChange = { preferences.setSavePasswords(it) }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.Password,
                        title = "Manage Passwords",
                        subtitle = "View and edit saved passwords",
                        onClick = { showCredentialManager = true }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsToggleItem(
                        icon = Icons.Default.Edit,
                        title = "Autofill Forms",
                        subtitle = "Automatically fill forms",
                        checked = preferences.autoFillForms,
                        onCheckedChange = { preferences.setAutoFillForms(it) }
                    )
                }

                // About
                SettingsGroup(title = "About") {
                     val webViewVersion = remember {
                        try {
                            val packageInfo = android.webkit.WebView.getCurrentWebViewPackage()
                            "${packageInfo?.versionName ?: "Unknown"}"
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    }
                    
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "WebView Version",
                        subtitle = webViewVersion,
                        onClick = {
                            // Copy version to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("WebView Version", webViewVersion)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "WebView version copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                     val appVersion = remember {
                        try {
                            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                            packageInfo.versionName ?: "Unknown"
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = appVersion,
                        onClick = {
                            // Copy version to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("App Version", appVersion)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "App version copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.MenuBook,
                        title = "Privacy Policy & Terms",
                        subtitle = "Read legal and security disclosures",
                        onClick = {
                            context.startActivity(Intent(context, TermsActivity::class.java))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Policy Website",
                        subtitle = "alaslife.github.io/alas-browser-policy",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://alaslife.github.io/alas-browser-policy/")
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    SettingsItem(
                        icon = Icons.Default.Shield,
                        title = "Google Privacy Policy",
                        subtitle = "Firebase/Google data handling",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://policies.google.com/privacy")
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
             modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                 content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingsItemWithPainter(
    painter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                 Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
             if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
             colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun SettingsToggleItemWithPainter(
    painter: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
             colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun ZoomSettingsItem(
    zoomLevel: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Zoom",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Zoom",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${zoomLevel}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onZoomOut,
                enabled = zoomLevel > 50
            ) {
                Icon(
                    painter = painterResource(id = com.sun.alasbrowser.R.drawable.zoom_out),
                    contentDescription = "Zoom out",
                    tint = if (zoomLevel > 50) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            IconButton(
                onClick = onZoomIn,
                enabled = zoomLevel < 200
            ) {
                Icon(
                    painter = painterResource(id = com.sun.alasbrowser.R.drawable.zoom_in),
                    contentDescription = "Zoom in",
                    tint = if (zoomLevel < 200) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SearchEngineSelectionDialog(
    currentEngine: SearchEngine,
    onEngineSelected: (SearchEngine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Search Engine", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchEngine.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEngineSelected(engine) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engine == currentEngine,
                            onClick = { onEngineSelected(engine) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = engine.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ScrollBehaviorDialog(
    currentBehavior: ScrollingBehaviour,
    onBehaviorSelected: (ScrollingBehaviour) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Scrolling behaviour", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ScrollingBehaviour.entries.forEach { behavior ->
                    val displayText = when (behavior) {
                        ScrollingBehaviour.NEVER_HIDE -> "Never hide toolbars when scrolling"
                        ScrollingBehaviour.HIDE_BOTH -> "Hide toolbars when scrolling"
                        ScrollingBehaviour.ONLY_HIDE_TOP -> "Only hide address bar when scrolling"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBehaviorSelected(behavior) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = behavior == currentBehavior,
                            onClick = { onBehaviorSelected(behavior) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    "App Theme",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Choose a style that suits you",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(AppTheme.values()) { theme ->
                        val isSelected = theme == currentTheme
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1f,
                            animationSpec = tween(300),
                            label = "scale"
                        )
                        val themeColor = com.sun.alasbrowser.ui.getThemePreviewColor(theme)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onThemeSelected(theme) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSelected) 0.6f else 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(themeColor)
                                        .align(Alignment.End),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (theme == AppTheme.LIGHT || theme == AppTheme.BEIGE || theme == AppTheme.MINT) Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = theme.displayName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = theme.description,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        lineHeight = 14.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun getThemePreviewColor(theme: AppTheme): Color {
    return when (theme) {
        AppTheme.SYSTEM -> Color(0xFF808080)
        AppTheme.LIGHT -> Color(0xFFF3F4F6)
        AppTheme.DARK -> Color(0xFF121212)
        AppTheme.DARK_WHITE -> Color(0xFF1E1E2E)
        AppTheme.MINT -> Color(0xFF98FF98)
        AppTheme.PURPLE -> Color(0xFF6B5B95)
        AppTheme.MIDNIGHT_AZURE -> Color(0xFF0B3D91)
        AppTheme.REDBULL_WINTER -> Color(0xFFFF3366)
        AppTheme.DARK_CRIMSON -> Color(0xFF8B0000)
        AppTheme.BEIGE -> Color(0xFFF5F5DC)
    }
}

@Composable
fun DarkWebPagesDialog(
    currentMode: com.sun.alasbrowser.data.WebViewDarkMode,
    onModeSelected: (com.sun.alasbrowser.data.WebViewDarkMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Dark Mode",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                com.sun.alasbrowser.data.WebViewDarkMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = mode.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                            Text(
                                text = mode.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun DownloadLocationDialog(
    currentLocation: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val defaultLocation = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .absolutePath + "/Alasbrowser"

    val locations = remember {
        listOf(
            defaultLocation,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = { 
            Text(
                "Download Location", 
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column {
                locations.forEach { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(location) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = location == currentLocation || (currentLocation.isEmpty() && location == defaultLocation),
                            onClick = { onSelect(location) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = location,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Close",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

fun authenticateForIncognitoLock(
    activity: ComponentActivity,
    onResult: (Boolean) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    
    when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity as FragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onResult(true)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onResult(false)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onResult(false)
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Incognito Lock")
                .setSubtitle("Authenticate to enable incognito lock")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
        else -> {
            onResult(true)
        }
    }
}
