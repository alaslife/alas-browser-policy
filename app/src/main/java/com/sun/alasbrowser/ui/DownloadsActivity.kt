package com.sun.alasbrowser.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.DownloadEntity
import com.sun.alasbrowser.downloads.AlasDownloadManager
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { com.sun.alasbrowser.data.BrowserPreferences(this) }
            AlasBrowserTheme(appTheme = preferences.appTheme) {
                DownloadsScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val database = remember { BrowserDatabase.getDatabase(context) }
    val downloadManager = remember { AlasDownloadManager.getInstance(context) }
    
    // Collecting flow from Room
    val allDownloads by database.downloadDao().getAllDownloads()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Filter logic
    val filteredDownloads = remember(allDownloads, searchQuery, selectedCategory) {
        allDownloads.filter { download ->
            val matchesSearch = download.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Images" -> download.mimeType.startsWith("image/")
                "Video" -> download.mimeType.startsWith("video/")
                "Audio" -> download.mimeType.startsWith("audio/")
                "Documents" -> download.mimeType.let { it.contains("pdf") || it.contains("text") || it.contains("document") }
                "APK" -> download.mimeType.equals("application/vnd.android.package-archive", true) || download.fileName.endsWith(".apk", true)
                else -> true
            }
            matchesSearch && matchesCategory
        }
    }

    val inProgress = filteredDownloads.filter { 
        it.status == DownloadEntity.STATUS_RUNNING || 
        it.status == DownloadEntity.STATUS_PENDING || 
        it.status == DownloadEntity.STATUS_PAUSED 
    }
    
    val completed = filteredDownloads.filter { 
        it.status == DownloadEntity.STATUS_COMPLETED || 
        it.status == DownloadEntity.STATUS_FAILED 
    }.sortedByDescending { it.timestamp }

    val availableCategories = remember(allDownloads) {
        val cats = mutableListOf("All")
        if (allDownloads.any { it.mimeType.startsWith("image/") }) cats.add("Images")
        if (allDownloads.any { it.mimeType.startsWith("video/") }) cats.add("Video")
        if (allDownloads.any { it.mimeType.startsWith("audio/") }) cats.add("Audio")
        if (allDownloads.any { it.mimeType.contains("pdf") || it.mimeType.contains("text") || it.mimeType.contains("document") }) cats.add("Documents")
        if (allDownloads.any { it.mimeType.equals("application/vnd.android.package-archive", true) || it.fileName.endsWith(".apk", true) }) cats.add("APK")
         cats
    }

    // Grouping
    val groupedDownloads = remember(filteredDownloads) {
        filteredDownloads.groupBy { 
            if (it.pageTitle.isNotBlank()) it.pageTitle else "Other Downloads" 
        }
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} Selected" else "Downloads", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Close Selection")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                         // Select All
                         IconButton(onClick = { 
                             selectedIds = filteredDownloads.map { it.id }.toSet()
                         }) {
                             Icon(Icons.Default.SelectAll, "Select All")
                         }
                        // Delete
                        IconButton(onClick = { 
                            selectedIds.forEach { downloadManager.cancel(it) } 
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    } else {
                        IconButton(onClick = { /* Search */ }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        
                        // Overflow Menu for Clear All
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear All Completed") },
                                onClick = { 
                                    completed.forEach { downloadManager.cancel(it.id) }
                                    showMenu = false 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select Multiple") },
                                onClick = { 
                                    selectionMode = true
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // Categories
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                items(availableCategories) { category ->
                    val icon = when (category) {
                        "Images" -> Icons.Default.Image
                        "Video" -> Icons.Default.Videocam
                        "Audio" -> Icons.Default.Audiotrack
                        "Documents" -> Icons.Default.Description
                        "APK" -> Icons.Default.Android
                        else -> null 
                    }
                    
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        leadingIcon = if (icon != null) {
                            { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = Color.Transparent, 
                            labelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            iconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                             enabled = true,
                             selected = selectedCategory == category,
                             borderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(50) 
                    )
                }
            }

            if (filteredDownloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloads found", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Grouped List
                    groupedDownloads.forEach { (pageTitle, downloads) ->
                        item(key = "header_$pageTitle") {
                            Text(
                                pageTitle,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        items(downloads, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            
                            if (item.status == DownloadEntity.STATUS_RUNNING || 
                                item.status == DownloadEntity.STATUS_PENDING || 
                                item.status == DownloadEntity.STATUS_PAUSED) {
                                ActiveDownloadCard(
                                    download = item,
                                    onPause = { downloadManager.pauseDownload(item.id) },
                                    onResume = { downloadManager.resumeDownload(item.id) },
                                    onCancel = { downloadManager.cancelDownload(item.id) }
                                )
                            } else {
                                CompletedDownloadItem(
                                    download = item,
                                    selected = isSelected,
                                    selectionMode = selectionMode,
                                    onClick = { 
                                        if (selectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        } else if (item.status == DownloadEntity.STATUS_COMPLETED) {
                                             openFile(context, item)
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedIds = selectedIds + item.id
                                    },
                                    onDelete = { downloadManager.cancel(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ActiveDownloadCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val progress = if (download.totalSize > 0) download.downloadedSize.toFloat() / download.totalSize else 0f
    val isPaused = download.status == DownloadEntity.STATUS_PAUSED
    val isFailed = download.status == DownloadEntity.STATUS_FAILED
    val isQueued = download.status == DownloadEntity.STATUS_PENDING
    
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Progress with Icon
            Box(contentAlignment = Alignment.Center) {
                if (isQueued) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                        trackColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 5.dp,
                    )
                } else if (download.totalSize <= 0 && download.downloadedSize > 0) {
                     // Determinate but unknown total (or just indeterminate spinner?)
                     // If we are downloading but don't know total, we can't show progress circle accurately.
                     // Just show a spinner.
                     CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        strokeWidth = 5.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { if (isPaused || isFailed) 0f else progress },
                        modifier = Modifier.size(64.dp),
                        color = when {
                            isFailed -> androidx.compose.material3.MaterialTheme.colorScheme.error
                            isPaused -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
                            else -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                        },
                        trackColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 5.dp,
                    )
                }
                
                 Icon(
                    mapperMimeTypeToIcon(if (download.fileName.endsWith(".apk", true)) "application/vnd.android.package-archive" else download.mimeType),
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(20.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    download.title,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(6.dp))
                
                // Status Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFailed) {
                        Text(
                            "Failed",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isPaused) {
                         Text(
                            if (download.totalSize <= 0) {
                                "Paused • ${formatBytes(download.downloadedSize)}"
                            } else {
                                "Paused • ${formatBytes(download.downloadedSize)} / ${formatBytes(download.totalSize)}"
                            },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else if (isQueued) {
                         Text(
                            "Queued...",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        if (download.totalSize <= 0) {
                             Text(
                                formatBytes(download.downloadedSize),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            val percent = (progress * 100).toInt()
                            Text(
                                "$percent% • ${formatBytes(download.downloadedSize)} / ${formatBytes(download.totalSize)}",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Action Button
            IconButton(
                onClick = {
                    when (download.status) {
                        DownloadEntity.STATUS_RUNNING -> onPause()
                        DownloadEntity.STATUS_PENDING -> onPause() // Pausing a queued item removes it from queue to paused state
                        else -> onResume() // Resume for paused or failed
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = when (download.status) {
                        DownloadEntity.STATUS_RUNNING -> Icons.Default.Pause
                        DownloadEntity.STATUS_PENDING -> Icons.Default.Pause // Or maybe Close/Cancel? Pause is fine, moves to "Paused" list
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (isPaused || isFailed || isQueued) {
                 IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadItem(
    download: DownloadEntity,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {

    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    // Check file existence for UI indication (optional, maybe too expensive to check on every scroll)
    // For now, relies on click to show error.
    
    // List Item Style (Opera-like)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() }
            )
            Spacer(Modifier.width(16.dp))
        }
        
        // Colored Icon Circle
        val iconColor = if (download.status == DownloadEntity.STATUS_FAILED) androidx.compose.material3.MaterialTheme.colorScheme.errorContainer else getCategoryColor(download.mimeType, download.fileName)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(iconColor, CircleShape)
        ) {
            Icon(
                if (download.status == DownloadEntity.STATUS_FAILED) Icons.Default.Close else mapperMimeTypeToIcon(if (download.fileName.endsWith(".apk", true)) "application/vnd.android.package-archive" else download.mimeType),
                contentDescription = null,
                tint = if (download.status == DownloadEntity.STATUS_FAILED) androidx.compose.material3.MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(Modifier.weight(1f)) {
            Text(
                download.title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal, // Opera has regular weight
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                 // Format: "61.57 kB • 5 December"
                 // Fallback to downloadedSize if totalSize is invalid
                if (download.status == DownloadEntity.STATUS_FAILED) "Download Failed" 
                else formatBytes(if (download.totalSize > 0) download.totalSize else download.downloadedSize) + " • " + formatDate(download.timestamp),
                color = if (download.status == DownloadEntity.STATUS_FAILED) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, "More", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        shareFile(context, download)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy Link") },
                    onClick = {
                        copyToClipboard(context, download.url)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDelete() 
                        showMenu = false
                    }
                )
            }
        }
    }
}

// Helpers

fun shareFile(context: android.content.Context, download: DownloadEntity) {
    try {
        val file = File(download.filePath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = download.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } else {
             Toast.makeText(context, "File not found. It may have been deleted.", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Download Link", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

fun mapperMimeTypeToIcon(mimeType: String): ImageVector {
    return when {
         mimeType.contains("android.package-archive") || mimeType.endsWith("apk") -> Icons.Default.Android
        mimeType.contains("image") -> Icons.Default.Image
        mimeType.contains("video") -> Icons.Default.Videocam
        mimeType.contains("audio") -> Icons.Default.Audiotrack
        mimeType.contains("pdf") || mimeType.contains("doc") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

fun getCategoryColor(mimeType: String, fileName: String = ""): Color {
    return when {
         // PDF -> Red
        mimeType.contains("pdf") || fileName.endsWith(".pdf", true) -> Color(0xFFB71C1C) // Red 900
        // APK -> Green
        mimeType.contains("android.package-archive") || fileName.endsWith(".apk", true) -> Color(0xFF558B2F) // Light Green 800
        // Image -> Teal/Cyan
        mimeType.contains("image") -> Color(0xFF00695C) // Teal 800
        // Video -> Purple/Red/Pink? Opera uses maybe Blue/Purple
        mimeType.contains("video") -> Color(0xFF4527A0) // Purple 800
        // Audio -> Orange
        mimeType.contains("audio") -> Color(0xFFEF6C00) // Orange 800
        // Zip -> Grey/Brown
        mimeType.contains("zip") || mimeType.contains("compressed") -> Color(0xFF424242)
        else -> Color(0xFF1565C0) // Blue 800 default
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown size" // Better fallback
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

fun openFile(context: android.content.Context, download: DownloadEntity) {
    try {
        val file = File(download.filePath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            // Special handling for APKs
            val isApk = download.fileName.endsWith(".apk", ignoreCase = true) || 
                        download.mimeType == "application/vnd.android.package-archive"

            if (isApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // Show a message and open settings
                    Toast.makeText(context, "Please enable 'Install unknown apps' permission to install this APK.", Toast.LENGTH_LONG).show()
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val mimeType = if (isApk) "application/vnd.android.package-archive" else download.mimeType

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            // Improved Error Message
            Toast.makeText(context, "File missing: ${file.name}\nIt may have been deleted.", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        // Fallback for no handler
        android.util.Log.e("DownloadsActivity", "Error opening file", e)
        Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show()
    }
}
