package com.sun.alasbrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.AdBlockerMode
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme
import com.sun.alasbrowser.web.FilterListManager
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.launch

// Custom Theme Colors for this screen

class AdBlockerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { BrowserPreferences(this) }
            val appTheme = preferences.appTheme
            
            AlasBrowserTheme(appTheme = appTheme) {
                AdBlockerScreen(
                    preferences = preferences,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockerScreen(
    preferences: BrowserPreferences,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var adBlockEnabled by remember { mutableStateOf(preferences.adBlockEnabled) }
    var adBlockerMode by remember { 
        mutableStateOf(
            when(preferences.adBlockerMode) {
                "ENHANCED" -> AdBlockerMode.ENHANCED
                "CUSTOM" -> AdBlockerMode.CUSTOM
                else -> AdBlockerMode.BALANCED
            }
        )
    }
    var blockCookieDialogs by remember { mutableStateOf(preferences.blockCookieDialogs) }
    var autoAcceptCookies by remember { mutableStateOf(preferences.autoAcceptCookies) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showExcludedSitesDialog by remember { mutableStateOf(false) }
    
    val blockedCount = SimpleAdBlocker.getBlockedCount()
    val blockedToday = SimpleAdBlocker.getBlockedToday()
    val dataSaved = SimpleAdBlocker.getDataSaved()
    val timeSaved = SimpleAdBlocker.getTimeSaved()
    
    LaunchedEffect(adBlockerMode) {
        SimpleAdBlocker.initialize(
            context,
            adBlockerMode.name,
            preferences.enabledFilterLists
        )
    }
    
    if (showModeDialog) {
        AdBlockerModeDialog(
            currentMode = adBlockerMode,
            preferences = preferences,
            onModeSelected = { mode ->
                adBlockerMode = mode
                preferences.setAdBlockerMode(mode.name)
            },
            onDismiss = { showModeDialog = false }
        )
    }
    
    if (showExcludedSitesDialog) {
        ExcludedSitesDialog(
            preferences = preferences,
            onDismiss = { showExcludedSitesDialog = false }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Ad Blocker",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    scrolledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
            // Futuristic Hero section
            FuturisticAdBlockerHero(
                isEnabled = adBlockEnabled,
                blockedToday = blockedToday,
                totalBlocked = blockedCount
            )

            // Switch Row integrated cleanly
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable Ad Blocker",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (adBlockEnabled) "Blocking ads and trackers" else "Ads are not being blocked",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                Switch(
                    checked = adBlockEnabled,
                    onCheckedChange = { 
                        adBlockEnabled = it
                        preferences.setAdBlockEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            
            Spacer(Modifier.height(24.dp))

            // Stats Card
            AdvancedStatsCard(
                dataSavedMB = dataSaved / (1024f * 1024f),
                timeSavedMin = timeSaved / 60000f,
                protectionLevel = if (adBlockEnabled) when(adBlockerMode) {
                    AdBlockerMode.BALANCED -> 0.7f
                    AdBlockerMode.ENHANCED -> 0.95f
                    AdBlockerMode.CUSTOM -> 0.85f
                } else 0f,
                bandwidthSaved = dataSaved / (1024f * 1024f) // Simplified simulation
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Settings Items with custom dark styling
            ModernSettingsItem(
                title = "Ad Blocker mode",
                subtitle = when(adBlockerMode) {
                    AdBlockerMode.BALANCED -> "Balanced"
                    AdBlockerMode.ENHANCED -> "Enhanced"
                    AdBlockerMode.CUSTOM -> "Custom"
                },
                onClick = { showModeDialog = true }
            )
            
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            
            ModernSettingsToggle(
                title = "Block cookie dialogs",
                checked = blockCookieDialogs,
                onCheckedChange = {
                    blockCookieDialogs = it
                    preferences.setBlockCookieDialogs(it)
                }
            )
            
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            
            if (blockCookieDialogs) {
                ModernSettingsToggle(
                    title = "Automatically accept cookie dialogs",
                    checked = autoAcceptCookies,
                    onCheckedChange = {
                        autoAcceptCookies = it
                        preferences.setAutoAcceptCookies(it)
                    },
                    isCheckbox = true
                )
                
                HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            }
            
            ModernSettingsItem(
                title = "Excluded sites",
                subtitle = if (preferences.excludedSites.isNotEmpty()) {
                    "${preferences.excludedSites.size} site${if (preferences.excludedSites.size > 1) "s" else ""}"
                } else null,
                onClick = { showExcludedSitesDialog = true }
            )
            
            HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            
            Spacer(Modifier.height(32.dp))
            
            // Info text
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Why should I use Ad Blocker?",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "When you block ads, webpages load faster and you transfer less data. Pages also look cleaner.",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun ModernSettingsItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ModernSettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isCheckbox: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (isCheckbox) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    uncheckedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    checkmarkColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
fun AdBlockerModeDialog(
    currentMode: AdBlockerMode,
    preferences: BrowserPreferences,
    onModeSelected: (AdBlockerMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedFilters by remember { mutableStateOf(preferences.enabledFilterLists) }
    var isDownloadingFilter by remember { mutableStateOf(false) }
    
    val recommendedLists = FilterListManager.availableFilterLists.filter { it.category == "recommended" }
    val otherLists = FilterListManager.availableFilterLists.filter { it.category == "other" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        textContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ad Blocker mode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isDownloadingFilter) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AdBlockerModeOption(
                    title = "Balanced (recommended)",
                    description = "Blocks common ads while maintaining site compatibility",
                    selected = selectedMode == AdBlockerMode.BALANCED,
                    onClick = { 
                        selectedMode = AdBlockerMode.BALANCED
                        onModeSelected(AdBlockerMode.BALANCED)
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                AdBlockerModeOption(
                    title = "Enhanced",
                    description = "Powered by uBlock lists for a stronger Ad Blocker, though some sites may be affected",
                    selected = selectedMode == AdBlockerMode.ENHANCED,
                    onClick = { 
                        selectedMode = AdBlockerMode.ENHANCED
                        onModeSelected(AdBlockerMode.ENHANCED)
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                AdBlockerModeOption(
                    title = "Custom",
                    description = "Configure Ad Blocker by selecting specific filter lists.",
                    selected = selectedMode == AdBlockerMode.CUSTOM,
                    onClick = { 
                        selectedMode = AdBlockerMode.CUSTOM
                        onModeSelected(AdBlockerMode.CUSTOM)
                    }
                )
                
                if (selectedMode == AdBlockerMode.CUSTOM) {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(Modifier.height(20.dp))
                    
                    Text(
                        "Recommended lists",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    recommendedLists.forEach { filter ->
                        FilterListItemCompact(
                            name = filter.name,
                            description = filter.description,
                            checked = selectedFilters.contains(filter.id),
                            onCheckedChange = { checked ->
                                selectedFilters = if (checked) {
                                    selectedFilters + filter.id
                                } else {
                                    selectedFilters - filter.id
                                }
                                preferences.setEnabledFilterLists(selectedFilters)
                                
                                scope.launch {
                                    if (checked) {
                                        isDownloadingFilter = true
                                        val result = FilterListManager.downloadFilter(context, filter)
                                        isDownloadingFilter = false
                                        if (result.isSuccess) {
                                            SimpleAdBlocker.initialize(context, "CUSTOM", selectedFilters)
                                        }
                                    } else {
                                        SimpleAdBlocker.initialize(context, "CUSTOM", selectedFilters)
                                    }
                                }
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Text(
                        "Other lists",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    otherLists.forEach { filter ->
                        FilterListItemCompact(
                            name = filter.name,
                            description = filter.description,
                            checked = selectedFilters.contains(filter.id),
                            onCheckedChange = { checked ->
                                selectedFilters = if (checked) {
                                    selectedFilters + filter.id
                                } else {
                                    selectedFilters - filter.id
                                }
                                preferences.setEnabledFilterLists(selectedFilters)
                                
                                scope.launch {
                                    if (checked) {
                                        isDownloadingFilter = true
                                        val result = FilterListManager.downloadFilter(context, filter)
                                        isDownloadingFilter = false
                                        if (result.isSuccess) {
                                            SimpleAdBlocker.initialize(context, "CUSTOM", selectedFilters)
                                        }
                                    } else {
                                        SimpleAdBlocker.initialize(context, "CUSTOM", selectedFilters)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun FilterListItemCompact(
    name: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                uncheckedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun AdBlockerModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unselectedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
