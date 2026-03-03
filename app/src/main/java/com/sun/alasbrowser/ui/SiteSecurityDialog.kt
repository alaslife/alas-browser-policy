package com.sun.alasbrowser.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.delay

enum class SecurityDialogScreen {
    MAIN,
    ADS_DETAIL,
    TRACKERS_DETAIL
}

@Composable
fun SiteSecurityDialog(
    url: String,
    isSecure: Boolean,
    onDismiss: () -> Unit,
    onSiteSettingsClick: () -> Unit,
    onAdBlockToggle: (Boolean) -> Unit,
    preferences: BrowserPreferences? = null,
    isUsingWebView: Boolean = false,
    onToggleEngine: () -> Unit = {}
) {
    val context = LocalContext.current
    val domain = remember(url) {
        url.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: url
    }
    
    var currentScreen by remember { mutableStateOf(SecurityDialogScreen.MAIN) }
    var adsBlocked by remember { mutableIntStateOf(0) }
    var trackersBlocked by remember { mutableIntStateOf(0) }
    var totalBlocked by remember { mutableIntStateOf(SimpleAdBlocker.getBlockedCount()) }
    var adBlockEnabled by remember { mutableStateOf(preferences?.adBlockEnabled ?: true) }
    var siteExcluded by remember { mutableStateOf(SimpleAdBlocker.isSiteExcluded(domain)) }
    var blockedAdDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var blockedTrackerDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    
    LaunchedEffect(domain) {
        while (true) {
            adsBlocked = SimpleAdBlocker.getAdsBlockedForSite(domain)
            trackersBlocked = SimpleAdBlocker.getTrackersBlockedForSite(domain)
            totalBlocked = SimpleAdBlocker.getBlockedCount()
            adBlockEnabled = preferences?.adBlockEnabled ?: true
            siteExcluded = SimpleAdBlocker.isSiteExcluded(domain)
            blockedAdDomains = SimpleAdBlocker.getBlockedAdDomainsForSite(domain)
            blockedTrackerDomains = SimpleAdBlocker.getBlockedTrackerDomainsForSite(domain)
            delay(500)
        }
    }
    
    val isProtectionActive = adBlockEnabled && !siteExcluded
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            when (currentScreen) {
                SecurityDialogScreen.MAIN -> {
                    MainSecurityContent(
                        domain = domain,
                        isSecure = isSecure,
                        isProtectionActive = isProtectionActive,
                        adsBlocked = adsBlocked,
                        trackersBlocked = trackersBlocked,
                        totalBlocked = totalBlocked,
                        siteExcluded = siteExcluded,
                        isUsingWebView = isUsingWebView,
                        onToggleEngine = {
                            onToggleEngine()
                            onDismiss()
                        },
                        onAdsClick = { currentScreen = SecurityDialogScreen.ADS_DETAIL },
                        onTrackersClick = { currentScreen = SecurityDialogScreen.TRACKERS_DETAIL },
                        onProtectionToggle = { enabled ->
                            if (enabled) {
                                SimpleAdBlocker.removeExcludedSite(domain)
                                preferences?.removeExcludedSite(domain)
                            } else {
                                SimpleAdBlocker.addExcludedSite(domain)
                                preferences?.addExcludedSite(domain)
                            }
                            siteExcluded = !enabled
                            onAdBlockToggle(enabled)
                        },
                        onSiteSettingsClick = {
                            onDismiss()
                            onSiteSettingsClick()
                        },
                        onAdBlockerSettingsClick = {
                            onDismiss()
                            context.startActivity(Intent(context, com.sun.alasbrowser.ui.AdBlockerActivity::class.java))
                        }
                    )
                }
                SecurityDialogScreen.ADS_DETAIL -> {
                    BlockedDomainsDetail(
                        title = "Ads Blocked",
                        count = adsBlocked,
                        domains = blockedAdDomains,
                        icon = Icons.Default.Block,
                        accentColor = MaterialTheme.colorScheme.error,
                        onBack = { currentScreen = SecurityDialogScreen.MAIN }
                    )
                }
                SecurityDialogScreen.TRACKERS_DETAIL -> {
                    BlockedDomainsDetail(
                        title = "Trackers Blocked",
                        count = trackersBlocked,
                        domains = blockedTrackerDomains,
                        icon = Icons.Default.VisibilityOff,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onBack = { currentScreen = SecurityDialogScreen.MAIN }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainSecurityContent(
    domain: String,
    isSecure: Boolean,
    isProtectionActive: Boolean,
    adsBlocked: Int,
    trackersBlocked: Int,
    totalBlocked: Int,
    siteExcluded: Boolean,
    isUsingWebView: Boolean = false,
    onToggleEngine: () -> Unit = {},
    onAdsClick: () -> Unit,
    onTrackersClick: () -> Unit,
    onProtectionToggle: (Boolean) -> Unit,
    onSiteSettingsClick: () -> Unit,
    onAdBlockerSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Compact header: shield + domain + badge in one row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Small shield icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isProtectionActive && isSecure) MaterialTheme.colorScheme.primary
                                else if (isProtectionActive) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline).copy(alpha = 0.2f),
                                (if (isProtectionActive && isSecure) MaterialTheme.colorScheme.primary
                                else if (isProtectionActive) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline).copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isProtectionActive) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = "Protection Status",
                    tint = if (isProtectionActive && isSecure) MaterialTheme.colorScheme.primary
                           else if (isProtectionActive) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isSecure) "Secure" else "Not Secure",
                        color = if (isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        BlockingStatsRow(
            adsBlocked = adsBlocked,
            trackersBlocked = trackersBlocked,
            isActive = isProtectionActive,
            onAdsClick = onAdsClick,
            onTrackersClick = onTrackersClick
        )
        
        if (totalBlocked > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            TotalBlockedInfo(totalBlocked = totalBlocked)
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        ProtectionToggleCard(
            isEnabled = isProtectionActive,
            onToggle = onProtectionToggle
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Ad Blocker",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAdBlockerSettingsClick() }
                    .padding(vertical = 8.dp, horizontal = 10.dp)
            )
            
            Text(
                text = "Site settings",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSiteSettingsClick() }
                    .padding(vertical = 8.dp, horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun BlockedDomainsDetail(
    title: String,
    count: Int,
    domains: List<String>,
    icon: ImageVector,
    accentColor: Color,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Icon and count header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = count.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "blocked on this site",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Domain list
        if (domains.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No domains blocked yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            Text(
                text = "Blocked domains",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(domains.sorted()) { domain ->
                    DomainListItem(
                        domain = domain,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainListItem(
    domain: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            
            Text(
                text = domain,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BlockingStatsRow(
    adsBlocked: Int,
    trackersBlocked: Int,
    isActive: Boolean,
    onAdsClick: () -> Unit,
    onTrackersClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = "Ads",
            count = adsBlocked,
            icon = Icons.Default.Block,
            accentColor = MaterialTheme.colorScheme.error,
            isActive = isActive,
            onClick = onAdsClick,
            modifier = Modifier.weight(1f)
        )
        
        StatCard(
            label = "Trackers",
            count = trackersBlocked,
            icon = Icons.Default.VisibilityOff,
            accentColor = MaterialTheme.colorScheme.secondary,
            isActive = isActive,
            onClick = onTrackersClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayColor = if (isActive) accentColor else MaterialTheme.colorScheme.outline
    
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) displayColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(displayColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = displayColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Column {
                Text(
                    text = count.toString(),
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TotalBlockedInfo(totalBlocked: Int) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Total blocked across all sites: $totalBlocked",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProtectionToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "toggleBg"
    )
    
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "Protection for this site",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isEnabled) "Blocking ads & trackers" else "Protection disabled",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun EngineToggleCard(
    isUsingWebView: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isUsingWebView) 
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "engineBg"
    )
    
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUsingWebView) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isUsingWebView) "W" else "G",
                        color = if (isUsingWebView) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column {
                    Text(
                        text = if (isUsingWebView) "WebView Engine" else "GeckoView Engine",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isUsingWebView) "Compatibility mode active" else "Default engine (tap to switch)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
