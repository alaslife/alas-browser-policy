package com.sun.alasbrowser.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.R
import com.sun.alasbrowser.data.AccountManager
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.WebViewDarkMode

// Colors are now theme-aware via MaterialTheme.colorScheme
private val MenuSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private val SquircleLarge = RoundedCornerShape(24.dp)
private val SquircleMedium = RoundedCornerShape(20.dp)
private val SquircleSmall = RoundedCornerShape(18.dp)
private val SquircleIcon = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun OperaStyleMenu(
    onDismiss: () -> Unit,
    preferences: BrowserPreferences,
    onHistoryClick: () -> Unit = {},
    onBookmarksClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onPasswordsClick: () -> Unit = {},
    onAdBlockerSettingsClick: () -> Unit = {},
    onPrivacySettingsClick: () -> Unit = {},
    onClearDataClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var dataSaverEnabled by remember { mutableStateOf(preferences.dataSaverEnabled) }
    var adBlockEnabled by remember { mutableStateOf(preferences.adBlockEnabled) }
    var nightModeEnabled by remember { mutableStateOf(preferences.nightModeEnabled || preferences.isNightModeActiveNow()) }
    
    var showCredentialManager by remember { mutableStateOf(false) }
    var showAdBlockerScreen by remember { mutableStateOf(false) }
    var showNightModeSettings by remember { mutableStateOf(false) }
    var showStorageManager by remember { mutableStateOf(false) }
    var showAccountScreen by remember { mutableStateOf(false) }
    
    val accountManager = remember { AccountManager.getInstance(context) }
    
    // Sub-screens
    if (showCredentialManager) {
        CredentialListScreen(onDismiss = { showCredentialManager = false })
        return
    }
    
    if (showAdBlockerScreen) {
        AdBlockerScreen(
            preferences = preferences,
            onBack = { showAdBlockerScreen = false }
        )
        return
    }
    
    if (showNightModeSettings) {
        NightModeSettingsScreen(
            preferences = preferences,
            onBack = { showNightModeSettings = false }
        )
        return
    }
    
    if (showStorageManager) {
        context.startActivity(Intent(context, StorageManagerActivity::class.java))
        showStorageManager = false
    }
    
    if (showAccountScreen) {
        AccountScreen(
            accountManager = accountManager,
            onDismiss = { showAccountScreen = false }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clickable(enabled = false) { },
            shape = MenuSheetShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Profile Section
                ProfileSection(
                    accountManager = accountManager,
                    onProfileClick = { showAccountScreen = true },
                    onSettingsClick = {
                        onDismiss()
                        onSettingsClick()
                    },
                    onExitClick = {
                        onDismiss()
                        (context as? android.app.Activity)?.let { activity ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                activity.finishAndRemoveTask()
                            } else {
                                activity.finishAffinity()
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Quick Toggle Row with long-press
                QuickToggleRow(
                    dataSaverEnabled = dataSaverEnabled,
                    adBlockEnabled = adBlockEnabled,
                    nightModeEnabled = nightModeEnabled,
                    onDataSaverToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dataSaverEnabled = !dataSaverEnabled
                        preferences.setDataSaverEnabled(dataSaverEnabled)
                        Toast.makeText(context, 
                            if (dataSaverEnabled) "Data Saver enabled" else "Data Saver disabled", 
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDataSaverLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Data Saver reduces data usage by compressing images", Toast.LENGTH_LONG).show()
                    },
                    onAdBlockToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        adBlockEnabled = !adBlockEnabled
                        preferences.setAdBlockEnabled(adBlockEnabled)
                        Toast.makeText(context, 
                            if (adBlockEnabled) "Ad Blocker enabled" else "Ad Blocker disabled", 
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onAdBlockLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAdBlockerScreen = true
                    },
                    onNightModeToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        nightModeEnabled = !nightModeEnabled
                        preferences.setNightModeEnabled(nightModeEnabled)
                        val newMode = if (nightModeEnabled) {
                            WebViewDarkMode.DARK_PREFERRED
                        } else {
                            WebViewDarkMode.LIGHT_PREFERRED
                        }
                        preferences.setWebViewDarkMode(newMode)
                        Toast.makeText(context, 
                            if (nightModeEnabled) "Night mode enabled" else "Night mode disabled", 
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onNightModeLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showNightModeSettings = true
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Quick Access Grid
                QuickAccessGrid(
                    onBookmarksClick = {
                        onDismiss()
                        onBookmarksClick()
                    },
                    onDownloadsClick = {
                        onDismiss()
                        onDownloadsClick()
                    },
                    onHistoryClick = {
                        onDismiss()
                        onHistoryClick()
                    },
                    onOfflinePagesClick = {
                        Toast.makeText(context, "Offline pages coming soon", Toast.LENGTH_SHORT).show()
                    }
                )
                
                Spacer(modifier = Modifier.height(20.dp))

                // Menu List Items
                MenuListSection(
                    onAppearanceClick = {
                        onDismiss()
                        onSettingsClick()
                    },
                    onPrivacyClick = {
                        onDismiss()
                        onPrivacySettingsClick()
                    },
                    onPasswordsClick = {
                        showCredentialManager = true
                    },
                    onClearDataClick = {
                        showStorageManager = true
                    },
                    onAboutClick = {
                        val appVersion = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: Exception) { "Unknown" }
                        Toast.makeText(context, "AlasBrowser v$appVersion", Toast.LENGTH_SHORT).show()
                    }
                )
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun ProfileSection(
    accountManager: AccountManager,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val isLoggedIn = accountManager.isLoggedIn
    val user = accountManager.currentUser
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Avatar with gradient ring
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(8.dp, CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF4CAF50),
                            Color(0xFF2196F3),
                            Color(0xFFE91E63),
                            Color(0xFFFFEB3B),
                            Color(0xFF4CAF50)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(2.5.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isLoggedIn && user != null) {
                val initials = user.displayName
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                    .ifEmpty { "U" }
                Text(
                    text = initials,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // Profile Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (isLoggedIn && user != null) user.displayName else "Guest",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isLoggedIn && user != null) user.email
                       else "Tap to sign in or create account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .background(
                        if (isLoggedIn) Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isLoggedIn) "Signed in" else "Guest mode",
                    color = if (isLoggedIn) Color(0xFF4CAF50)
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Grouped actions (M3-style): one pill container with divider
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExitClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Exit",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .height(18.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickToggleRow(
    dataSaverEnabled: Boolean,
    adBlockEnabled: Boolean,
    nightModeEnabled: Boolean,
    onDataSaverToggle: () -> Unit,
    onDataSaverLongPress: () -> Unit,
    onAdBlockToggle: () -> Unit,
    onAdBlockLongPress: () -> Unit,
    onNightModeToggle: () -> Unit,
    onNightModeLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickToggleButton(
            modifier = Modifier.weight(1f),
            painter = painterResource(id = R.drawable.data_saving_dark),
            label = "Data Saver",
            subLabel = if (dataSaverEnabled) "On" else "Off",
            isEnabled = dataSaverEnabled,
            enabledColor = MaterialTheme.colorScheme.tertiary,
            onClick = onDataSaverToggle,
            onLongClick = onDataSaverLongPress
        )
        
        QuickToggleButton(
            modifier = Modifier.weight(1f),
            painter = painterResource(id = R.drawable.ad_block_dark),
            label = "Ad Blocker",
            subLabel = if (adBlockEnabled) "On" else "Off",
            isEnabled = adBlockEnabled,
            enabledColor = MaterialTheme.colorScheme.error,
            onClick = onAdBlockToggle,
            onLongClick = onAdBlockLongPress
        )
        
        QuickToggleButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.DarkMode,
            label = "Night Mode",
            subLabel = if (nightModeEnabled) "On" else "Off",
            isEnabled = nightModeEnabled,
            enabledColor = MaterialTheme.colorScheme.secondary,
            onClick = onNightModeToggle,
            onLongClick = onNightModeLongPress
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickToggleButton(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String,
    subLabel: String,
    isEnabled: Boolean,
    enabledColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.98f
            isEnabled -> 1.02f
            else -> 1f
        },
        animationSpec = spring(stiffness = 400f),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            isEnabled -> enabledColor.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        },
        animationSpec = tween(200),
        label = "bg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) enabledColor.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(200),
        label = "border"
    )
    
    Column(
        modifier = modifier
            .scale(animatedScale)
            .clip(SquircleLarge)
            .background(backgroundColor)
            .border(1.dp, borderColor, SquircleLarge)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isEnabled) enabledColor.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.1f),
                    SquircleIcon
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isEnabled) enabledColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = label,
                    tint = if (isEnabled) enabledColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        
        Text(
            text = subLabel,
            color = if (isEnabled) enabledColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun QuickAccessGrid(
    onBookmarksClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onOfflinePagesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAccessButton(
                icon = Icons.Default.Bookmark,
                label = "Bookmarks",
                iconTint = Color(0xFF64B5F6),
                onClick = onBookmarksClick,
                modifier = Modifier.weight(1f)
            )
            
            QuickAccessButton(
                icon = Icons.Default.Download,
                label = "Downloads",
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onDownloadsClick,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAccessButton(
                icon = Icons.Default.History,
                label = "History",
                iconTint = Color(0xFFFFB74D),
                onClick = onHistoryClick,
                modifier = Modifier.weight(1f)
            )
            
            QuickAccessButton(
                icon = Icons.Default.OfflinePin,
                label = "Offline Pages",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onOfflinePagesClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickAccessButton(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(140),
        label = "quickAccessBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(120),
        label = "quickAccessScale"
    )
    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        shape = SquircleMedium,
        color = bgColor,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconTint.copy(alpha = 0.18f), SquircleIcon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuListSection(
    onAppearanceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onPasswordsClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(SquircleMedium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        MenuListItem(
            icon = Icons.Default.ColorLens,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Appearance",
            subtitle = "Theme, dark mode, text size",
            onClick = onAppearanceClick
        )
        
        MenuDivider()
        
        MenuListItem(
            icon = Icons.Default.Shield,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Privacy & Security",
            subtitle = "Permissions, tracking, cookies",
            onClick = onPrivacyClick
        )
        
        MenuDivider()
        
        MenuListItem(
            icon = Icons.Default.Password,
            iconTint = Color(0xFF64B5F6),
            title = "Passwords",
            subtitle = "Manage saved passwords",
            onClick = onPasswordsClick
        )
        
        MenuDivider()
        
        MenuListItem(
            icon = Icons.Default.CleaningServices,
            iconTint = Color(0xFFFFB74D),
            title = "Clear Browsing Data",
            subtitle = "Cache, cookies, history",
            onClick = onClearDataClick
        )
        
        MenuDivider()
        
        MenuListItem(
            icon = Icons.Default.Info,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            title = "About",
            subtitle = "Version info",
            onClick = onAboutClick
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 52.dp)
    )
}

@Composable
private fun MenuListItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.992f else 1f,
        animationSpec = tween(120),
        label = "menuItemScale"
    )
    val itemBg by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(120),
        label = "menuItemBg"
    )
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(SquircleSmall)
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.14f), SquircleIcon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                modifier = Modifier.size(16.dp)
            )
        }
    )
}
