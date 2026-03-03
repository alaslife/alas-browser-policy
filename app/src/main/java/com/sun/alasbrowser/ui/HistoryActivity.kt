package com.sun.alasbrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.History
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme
import kotlinx.coroutines.launch
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = BrowserDatabase.getDatabase(applicationContext)
        
        setContent {
            val preferences = remember { com.sun.alasbrowser.data.BrowserPreferences(this) }
            AlasBrowserTheme(appTheme = preferences.appTheme) {
                HistoryScreen(
                    onBackClick = { finish() },
                    onHistoryClick = { url ->
                        val intent = Intent().apply {
                            putExtra("url", url)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    database = database,
                    lifecycleScope = lifecycleScope
                )
            }
        }
    }
}

// Theme colors — Opera-inspired clean dark
private val BgColor = Color(0xFF101014)
private val CardColor = Color(0xFF1C1C26)
private val AccentColor = Color(0xFFE84040)
private val SecondaryAccent = Color(0xFF3BB5E8)
private val SelectionBlue = Color(0xFF2979FF)
private val TextPrimary = Color(0xFFECECF1)
private val TextSecondary = Color(0xFF9696A8)
private val TextTertiary = Color(0xFF5E5E74)
private val DividerColor = Color(0xFF24243A)
private val ErrorRed = Color(0xFFFF4757)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onHistoryClick: (String) -> Unit,
    database: BrowserDatabase,
    lifecycleScope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf<List<History>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedHistory by remember { mutableStateOf(setOf<Long>()) }
    var selectionMode by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        historyList = database.historyDao().getAllHistorySync()
    }
    
    val filteredHistory = if (searchQuery.isNotEmpty()) {
        historyList.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.url.contains(searchQuery, ignoreCase = true)
        }
    } else {
        historyList
    }
    
    val grouped = filteredHistory.groupBy { history ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = history.visitTime
        }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        
        when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(history.visitTime))
        }
    }
    
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
    ) {
        // ═══ TOP BAR ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isSearchActive) {
                    isSearchActive = false
                    searchQuery = ""
                } else if (selectionMode) {
                    selectionMode = false
                    selectedHistory = setOf()
                } else {
                    onBackClick()
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = TextPrimary
                )
            }
            
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search history…", color = TextTertiary, fontSize = 16.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, "Clear", tint = TextSecondary)
                    }
                }
            } else if (selectionMode) {
                Text(
                    "${selectedHistory.size} selected",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                // Select all / deselect all
                val allIds = filteredHistory.map { it.id }.toSet()
                val allSelected = allIds.isNotEmpty() && selectedHistory.containsAll(allIds)
                IconButton(
                    onClick = {
                        selectedHistory = if (allSelected) setOf() else allIds
                    }
                ) {
                    Icon(
                        Icons.Default.Check,
                        if (allSelected) "Deselect all" else "Select all",
                        tint = if (allSelected) SelectionBlue else TextSecondary
                    )
                }
                IconButton(
                    onClick = {
                        lifecycleScope.launch {
                            selectedHistory.forEach { id ->
                                database.historyDao().deleteHistoryById(id)
                            }
                            historyList = database.historyDao().getAllHistorySync()
                            selectionMode = false
                            selectedHistory = setOf()
                        }
                    }
                ) {
                    Icon(Icons.Default.Delete, "Delete selected", tint = ErrorRed)
                }
            } else {
                Text(
                    "History",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Default.Search, "Search", tint = TextSecondary)
                }
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CardColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Expand all", color = TextPrimary) },
                            onClick = {
                                expandedGroups = grouped.flatMap { (dateGroup, historyItems) ->
                                    historyItems.groupBy { history ->
                                        try { URL(history.url).host } catch (_: Exception) { "unknown" }
                                    }.keys.map { domain -> "$dateGroup-$domain" }
                                }.toSet()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Collapse all", color = TextPrimary) },
                            onClick = {
                                expandedGroups = setOf()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear all history", color = ErrorRed) },
                            onClick = {
                                lifecycleScope.launch {
                                    database.historyDao().clearHistory()
                                    historyList = emptyList()
                                }
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                            }
                        )
                    }
                }
            }
        }
        
        // Subtle divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
        )
        
        // ═══ CONTENT ═══
        if (historyList.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AccentColor.copy(alpha = 0.15f),
                                        AccentColor.copy(alpha = 0.03f)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = AccentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        "No history yet",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Pages you visit will appear here",
                        color = TextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                grouped.forEach { (dateGroup, historyItems) ->
                    // Date header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dateGroup,
                                color = AccentColor.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                AccentColor.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                    
                    val domainGroups = historyItems.groupBy { history ->
                        try { URL(history.url).host } catch (_: Exception) { "unknown" }
                    }
                    
                    domainGroups.forEach { (domain, domainHistory) ->
                        val groupKey = "$dateGroup-$domain"
                        val isExpanded = expandedGroups.contains(groupKey)
                        val sortedDomainHistory = domainHistory.sortedByDescending { it.visitTime }
                        val pageCount = sortedDomainHistory.size
                        val firstTime = sortedDomainHistory.firstOrNull()?.visitTime ?: 0
                        val lastTime = sortedDomainHistory.lastOrNull()?.visitTime ?: 0
                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        
                        val timeRange = if (pageCount == 1) {
                            timeFormat.format(Date(firstTime))
                        } else {
                            "${timeFormat.format(Date(lastTime))} – ${timeFormat.format(Date(firstTime))}"
                        }
                        
                        val groupItemIds = sortedDomainHistory.map { it.id }.toSet()
                        val allGroupSelected = groupItemIds.isNotEmpty() && selectedHistory.containsAll(groupItemIds)

                        item(key = groupKey) {
                            DomainGroupHeader(
                                domain = domain,
                                pageCount = pageCount,
                                timeRange = timeRange,
                                isExpanded = isExpanded,
                                selectionMode = selectionMode,
                                isSelected = allGroupSelected,
                                onClick = {
                                    if (selectionMode) {
                                        selectedHistory = if (allGroupSelected) {
                                            selectedHistory - groupItemIds
                                        } else {
                                            selectedHistory + groupItemIds
                                        }
                                    } else {
                                        expandedGroups = if (isExpanded) {
                                            expandedGroups - groupKey
                                        } else {
                                            expandedGroups + groupKey
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selectedHistory = selectedHistory + groupItemIds
                                },
                                onDelete = {
                                    lifecycleScope.launch {
                                        groupItemIds.forEach { id ->
                                            database.historyDao().deleteHistoryById(id)
                                        }
                                        historyList = database.historyDao().getAllHistorySync()
                                    }
                                }
                            )
                        }
                        
                        if (isExpanded) {
                            items(
                                items = sortedDomainHistory,
                                key = { it.id }
                            ) { history ->
                                HistoryItemWithTimeline(
                                    history = history,
                                    context = context,
                                    selectionMode = selectionMode,
                                    isSelected = selectedHistory.contains(history.id),
                                    onClick = {
                                        if (selectionMode) {
                                            selectedHistory = if (selectedHistory.contains(history.id)) {
                                                selectedHistory - history.id
                                            } else {
                                                selectedHistory + history.id
                                            }
                                        } else {
                                            onHistoryClick(history.url)
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedHistory = setOf(history.id)
                                    },
                                    onDelete = {
                                        lifecycleScope.launch {
                                            database.historyDao().deleteHistoryById(history.id)
                                            historyList = database.historyDao().getAllHistorySync()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DomainGroupHeader(
    domain: String,
    pageCount: Int,
    timeRange: String,
    isExpanded: Boolean,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> SelectionBlue.copy(alpha = 0.12f)
            isExpanded -> CardColor
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "groupBg"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                    dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                if (isSwiping) ErrorRed else Color.Transparent,
                animationSpec = tween(200),
                label = "groupDismissColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSwiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 20.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete group",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Delete",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) bgColor else if (isExpanded) CardColor else BgColor)
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = { onLongClick() }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                // Selection checkbox
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) SelectionBlue else Color.Transparent)
                        .border(1.5.dp, if (isSelected) SelectionBlue else TextTertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            // Domain favicon circle
            val initial = domain.removePrefix("www.")
                .firstOrNull()?.uppercaseChar() ?: 'W'
            val domainColors = listOf(
                Color(0xFFE84040), Color(0xFF3BB5E8), Color(0xFF4CD964),
                Color(0xFFFF9F43), Color(0xFFAF52DE), Color(0xFFFF6B81)
            )
            val colorIndex = (domain.hashCode() and 0x7FFFFFFF) % domainColors.size
            val domainColor = domainColors[colorIndex]

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(domainColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.toString(),
                    color = domainColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    domain.removePrefix("www."),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$pageCount page${if (pageCount > 1) "s" else ""} · $timeRange",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            // Count badge
            if (pageCount > 1) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(SecondaryAccent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        pageCount.toString(),
                        color = SecondaryAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemWithTimeline(
    history: History,
    context: Context,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(history.visitTime))
    
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) SelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(150),
        label = "itemBg"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                    dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                if (isSwiping) ErrorRed else Color.Transparent,
                animationSpec = tween(200),
                label = "dismissColor"
            )
            val scale by androidx.compose.animation.core.animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f,
                animationSpec = tween(200),
                label = "deleteIconScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSwiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Delete",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) bgColor else BgColor)
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = { onLongClick() }
                )
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timeline dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isSelected) SelectionBlue else TextTertiary.copy(alpha = 0.5f),
                            CircleShape
                        )
                )
            }

            Spacer(Modifier.width(14.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    history.title.ifEmpty { "Untitled" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        time,
                        color = SecondaryAccent.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        " · ",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Text(
                        history.url
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .removePrefix("www.")
                            .take(35) + if (history.url.length > 50) "…" else "",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Selection checkbox or menu
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) SelectionBlue else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) SelectionBlue else TextTertiary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            "More",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CardColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy link", color = TextPrimary, fontSize = 14.sp) },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("URL", history.url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = TextPrimary, fontSize = 14.sp) },
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, history.url)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share via"))
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = ErrorRed, fontSize = 14.sp) },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}
