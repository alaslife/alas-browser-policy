package com.sun.alasbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences

enum class PermissionStatus {
    ALLOWED, BLOCKED, ASK_FIRST, OFF
}

data class SitePermission(
    val icon: ImageVector,
    val title: String,
    val status: PermissionStatus,
    val onStatusChange: ((PermissionStatus) -> Unit)? = null,
    val showChevron: Boolean = true
)

/**
 * Data class for a site with specific permission settings
 */
data class SitePermissionInfo(
    val domain: String,
    val cameraAllowed: Boolean? = null,  // null = use global default
    val microphoneAllowed: Boolean? = null  // null = use global default
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePermissionsScreen(
    onNavigateBack: () -> Unit,
    preferences: BrowserPreferences,
    modifier: Modifier = Modifier
) {
    // Read directly from preferences for reactive updates across all screens
    val cookiesStatus = if (preferences.cookiesEnabled) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED
    val locationStatus = if (preferences.location) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED
    val cameraStatus = if (preferences.camera) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED
    val microphoneStatus = if (preferences.microphone) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED
    val javascriptStatus = if (preferences.javaScriptEnabled) PermissionStatus.ALLOWED else PermissionStatus.BLOCKED
    val popupsStatus = if (preferences.popupBlockEnabled) PermissionStatus.BLOCKED else PermissionStatus.ALLOWED
    val desktopSiteStatus = if (preferences.desktopMode) PermissionStatus.ALLOWED else PermissionStatus.OFF
    
    // Local-only permissions (not stored in preferences yet)
    var motionSensorsStatus by remember { mutableStateOf(PermissionStatus.ALLOWED) }
    var downloadsStatus by remember { mutableStateOf(PermissionStatus.ASK_FIRST) }
    var protectedContentStatus by remember { mutableStateOf(PermissionStatus.ALLOWED) }
    var nfcStatus by remember { mutableStateOf(PermissionStatus.ASK_FIRST) }
    var usbStatus by remember { mutableStateOf(PermissionStatus.ASK_FIRST) }
    var clipboardStatus by remember { mutableStateOf(PermissionStatus.ASK_FIRST) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var selectedPermissionTitle by remember { mutableStateOf<String?>(null) }
    var showSiteCameraDialog by remember { mutableStateOf(false) }
    var showSiteMicrophoneDialog by remember { mutableStateOf(false) }
    
    val permissions = listOf(
        SitePermission(
            icon = Icons.Default.Cookie,
            title = "Cookies",
            status = cookiesStatus,
            onStatusChange = { preferences.setCookiesEnabled(it == PermissionStatus.ALLOWED) }
        ),
        SitePermission(
            icon = Icons.Default.LocationOn,
            title = "Location",
            status = locationStatus,
            onStatusChange = { preferences.setLocation(it == PermissionStatus.ALLOWED) }
        ),
        SitePermission(
            icon = Icons.Default.Videocam,
            title = "Camera",
            status = cameraStatus,
            onStatusChange = { preferences.setCamera(it == PermissionStatus.ALLOWED) }
        ),
        SitePermission(
            icon = Icons.Default.Mic,
            title = "Microphone",
            status = microphoneStatus,
            onStatusChange = { preferences.setMicrophone(it == PermissionStatus.ALLOWED) }
        ),
        SitePermission(
            icon = Icons.Default.Sensors,
            title = "Motion sensors",
            status = motionSensorsStatus,
            onStatusChange = { motionSensorsStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.Code,
            title = "JavaScript",
            status = javascriptStatus,
            onStatusChange = { preferences.setJavaScriptEnabled(it == PermissionStatus.ALLOWED) }
        ),
        SitePermission(
            icon = Icons.Default.Web,
            title = "Pop-ups and redirects",
            status = popupsStatus,
            onStatusChange = { preferences.setPopupBlockEnabled(it == PermissionStatus.BLOCKED) }
        ),
        SitePermission(
            icon = Icons.Default.Download,
            title = "Automatic downloads",
            status = downloadsStatus,
            onStatusChange = { downloadsStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.VerifiedUser,
            title = "Protected content",
            status = protectedContentStatus,
            onStatusChange = { protectedContentStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.Storage,
            title = "Data stored",
            status = PermissionStatus.ALLOWED,
            onStatusChange = null,
            showChevron = true
        ),
        SitePermission(
            icon = Icons.Default.Nfc,
            title = "NFC devices",
            status = nfcStatus,
            onStatusChange = { nfcStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.Usb,
            title = "USB",
            status = usbStatus,
            onStatusChange = { usbStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.ContentPaste,
            title = "Clipboard",
            status = clipboardStatus,
            onStatusChange = { clipboardStatus = it }
        ),
        SitePermission(
            icon = Icons.Default.DesktopWindows,
            title = "Desktop site",
            status = desktopSiteStatus,
            onStatusChange = { preferences.setDesktopMode(it == PermissionStatus.ALLOWED) }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Site settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // "All sites" Section Header
            Text(
                text = "All sites",
                color = Color(0xFF4DB6AC),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            
            // Permissions List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                permissions.forEach { permission ->
                    SitePermissionRow(
                        permission = permission,
                        onClick = {
                            // Handle camera/microphone specially to show site-specific settings
                            when (permission.title) {
                                "Camera" -> showSiteCameraDialog = true
                                "Microphone" -> showSiteMicrophoneDialog = true
                                else -> {
                                    if (permission.onStatusChange != null) {
                                        selectedPermissionTitle = permission.title
                                        showPermissionDialog = true
                                    }
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        thickness = 1.dp,
                        color = Color(0xFF2A2A2A)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Permission Status Selector Dialog
    val selectedPermission = permissions.find { it.title == selectedPermissionTitle }
    if (showPermissionDialog && selectedPermission != null) {
        PermissionStatusSelector(
            currentStatus = selectedPermission.status,
            onStatusSelected = { newStatus ->
                selectedPermission.onStatusChange?.invoke(newStatus)
                showPermissionDialog = false
                selectedPermissionTitle = null
            },
            onDismiss = {
                showPermissionDialog = false
                selectedPermissionTitle = null
            }
        )
    }
}

@Composable
fun SitePermissionRow(
    permission: SitePermission,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = permission.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Text(
            text = permission.title,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = when (permission.status) {
                PermissionStatus.ALLOWED -> "Allowed"
                PermissionStatus.BLOCKED -> "Blocked"
                PermissionStatus.ASK_FIRST -> "Ask first"
                PermissionStatus.OFF -> "Off"
            },
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )
        
        if (permission.showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PermissionStatusSelector(
    currentStatus: PermissionStatus,
    onStatusSelected: (PermissionStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Select Permission",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                PermissionStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStatusSelected(status)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentStatus == status,
                            onClick = {
                                onStatusSelected(status)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF4DB6AC),
                                unselectedColor = Color(0xFF808080)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (status) {
                                PermissionStatus.ALLOWED -> "Allowed"
                                PermissionStatus.BLOCKED -> "Blocked"
                                PermissionStatus.ASK_FIRST -> "Ask first"
                                PermissionStatus.OFF -> "Off"
                            },
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF808080))
            }
        }
    )
}

/**
 * Dialog showing list of allowed/blocked sites for camera or microphone
 */
@Composable
fun SitePermissionListDialog(
    title: String,
    allowedSites: List<String>,
    blockedSites: List<String>,
    onAddSite: (String) -> Unit,
    onRemoveSite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (allowedSites.isEmpty() && blockedSites.isEmpty()) {
                    Text(
                        text = "No sites have requested this permission yet.",
                        color = Color(0xFF808080),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                
                // Blocked sites section
                if (blockedSites.isNotEmpty()) {
                    Text(
                        text = "Blocked (${blockedSites.size})",
                        color = Color(0xFFF44336),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    blockedSites.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = site,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveSite(site) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF808080),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color(0xFF3A3A3A))
                }
                
                // Allowed sites section
                if (allowedSites.isNotEmpty()) {
                    Text(
                        text = "Allowed (${allowedSites.size})",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    allowedSites.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = site,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveSite(site) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF808080),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF4DB6AC))
            }
        }
    )
}
