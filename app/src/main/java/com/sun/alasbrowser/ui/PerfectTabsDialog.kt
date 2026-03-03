
package com.sun.alasbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.sun.alasbrowser.R
import com.sun.alasbrowser.data.BrowserTab
import com.sun.alasbrowser.utils.ThumbnailCache
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// Theme colors are now MaterialTheme-aware
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PerfectTabsDialog(
    tabs: List<BrowserTab>,
    currentTabId: String,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit = {},
    onCloseAllTabs: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    // Current page: 0 = normal tabs, 1 = private tabs
    val showPrivateOnly = pagerState.currentPage == 1

    // Background Animation - Theme-aware with enhanced glassmorphism
    val targetBgColor = if (showPrivateOnly)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    else
        MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    val animatedBgColor by animateColorAsState(targetBgColor, animationSpec = tween(400), label = "BgAnim")
    BackHandler(onBack = {
        if (showSearchOverlay) showSearchOverlay = false
        else onDismiss()
    })
    // Main Container - Full Screen Overlay with enhanced blur
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        // Main Content
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OperaTabHeader(
                    currentPage = pagerState.currentPage,
                    onTabSwitch = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onPrivateSwitch = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            },
            bottomBar = {
                OperaBottomBar(
                    isPrivate = showPrivateOnly,
                    isGridView = isGridView,
                    onNewTabClick = {
                        if (showPrivateOnly) onNewIncognitoTab() else onNewTab()
                    },
                    onMenuClick = { isGridView = !isGridView },
                    onCloseAllTabs = onCloseAllTabs,
                    onSettings = onSettings,
                    onHistory = onHistory
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                // Filter tabs based on current page
                val pageTabs by remember(tabs, searchQuery, page) {
                    derivedStateOf {
                        // Create snapshot to track changes
                        val currentTabs = tabs.toList()

                        var result = if (page == 1) {
                            currentTabs.filter { it.isPrivate }
                        } else {
                            currentTabs.filter { !it.isPrivate }
                        }

                        // Search filter
                        if (searchQuery.isNotEmpty()) {
                            result = result.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.url.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        result
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = pageTabs.isEmpty(),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f)).togetherWith(
                                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f)
                            )
                        }, label = "EmptyStateAnim"
                    ) { isEmpty ->
                        if (isEmpty) {
                            EmptyTabsState(
                                onNewTab = onNewTab,
                                onNewIncognitoTab = onNewIncognitoTab,
                                isPrivateMode = page == 1
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (isGridView) 2 else 1),
                                verticalArrangement = Arrangement.spacedBy(if (isGridView) 12.dp else 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 100.dp
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(pageTabs, key = { it.id }) { tab ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = {
                                            if (it != SwipeToDismissBoxValue.Settled) {
                                                onTabClose(tab.id)
                                                true
                                            } else false
                                        },
                                        positionalThreshold = { distance -> distance * 0.3f }
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn() + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut() + scaleOut(spring(stiffness = Spring.StiffnessMediumLow)),
                                        modifier = Modifier.animateItem()
                                    ) {
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(vertical = 4.dp)
                                                        .clip(RoundedCornerShape(24.dp))
                                                        .background(
                                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                        )
                                                )
                                            },
                                            enableDismissFromStartToEnd = false,
                                            enableDismissFromEndToStart = true
                                        ) {
                                            PerfectTabCard(
                                                tab = tab,
                                                isSelected = tab.id == currentTabId,
                                                isGridView = isGridView,
                                                onClick = {
                                                    onTabClick(tab.id)
                                                },
                                                onClose = { onTabClose(tab.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Overlay
        if (showSearchOverlay) {
            TabSearchOverlay(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onClose = { showSearchOverlay = false }
            )
        }
    }
}

@Composable
fun OperaTabHeader(
    currentPage: Int,
    onTabSwitch: () -> Unit,
    onPrivateSwitch: () -> Unit
) {
    val isPrivate = currentPage == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))

        // Tabs Header with animation
        val tabFontSize by animateDpAsState(if (!isPrivate) 32.dp else 24.dp, label = "TabFontAnim")
        Text(
            text = "Tabs",
            fontSize = tabFontSize.value.sp,
            fontWeight = if (!isPrivate) FontWeight.Bold else FontWeight.Normal,
            color = if (!isPrivate) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTabSwitch() }
        )

        Spacer(modifier = Modifier.width(28.dp))

        // Private Header with animation
        val privateFontSize by animateDpAsState(if (isPrivate) 32.dp else 24.dp, label = "PrivateFontAnim")
        Text(
            text = "Private",
            fontSize = privateFontSize.value.sp,
            fontWeight = if (isPrivate) FontWeight.Bold else FontWeight.Normal,
            color = if (isPrivate) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onPrivateSwitch() }
        )
    }
}

@Composable
fun OperaBottomBar(
    isPrivate: Boolean,
    isGridView: Boolean,
    onNewTabClick: () -> Unit,
    onMenuClick: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(80.dp)
            .background(Color.Transparent)
    ) {
        // Bottom bar strip
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grid/List toggle
                IconButton(onClick = onMenuClick) {
                    Icon(
                        if (isGridView) Icons.Outlined.GridView else Icons.Default.ViewList,
                        contentDescription = "Toggle View",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // History
                IconButton(onClick = onHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center FAB
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPrivate) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable { onNewTabClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Empty slot for balance
                Spacer(modifier = Modifier.size(48.dp))

                // Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Close all tabs", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp) },
                            onClick = {
                                showMenu = false
                                onCloseAllTabs()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp) },
                            onClick = {
                                showMenu = false
                                onSettings()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSearchOverlay(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
             // Enhanced blur for overlay
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            // Search Bar - Larger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable { onClose() }
                        .padding(14.dp)
                )

                // Search TextField
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF8AB4F8)),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                // Close button
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable { onSearchQueryChange("") }
                            .padding(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeableTabCard(
    tab: BrowserTab,
    isSelected: Boolean,
    isGridView: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) {
                onClose()
                true
            } else false
        },
        positionalThreshold = { distance -> distance * 0.3f }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
            )
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = modifier
    ) {
        PerfectTabCard(
            tab = tab,
            isSelected = isSelected,
            isGridView = isGridView,
            onClick = onClick,
            onClose = onClose
        )
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PerfectTabCard(
    tab: BrowserTab,
    isSelected: Boolean,
    isGridView: Boolean = false,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderWidth by animateDpAsState(if (isSelected) 2.dp else 1.dp, label = "BorderAnim")

    // Shared favicon logic
    val faviconBitmap = tab.favicon
    val faviconImageBitmap = remember(faviconBitmap) {
        try {
            if (faviconBitmap != null && !faviconBitmap.isRecycled) faviconBitmap.asImageBitmap() else null
        } catch (_: Exception) { null }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isGridView) Modifier.height(200.dp) else Modifier.height(80.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(if (isGridView) 18.dp else 14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(
            width = borderWidth,
            brush = if (isSelected) Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
            ) else SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(if (isGridView) 18.dp else 14.dp)
                )
        ) {
            if (isGridView) {
                // ── GRID VIEW: Header bar + full thumbnail ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header row: favicon + title + close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favicon
                        if (faviconImageBitmap != null) {
                            Image(
                                bitmap = faviconImageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                        } else if (tab.isPrivate) {
                            Icon(
                                painter = painterResource(R.drawable.fox),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Title
                        Text(
                            text = tab.title.ifEmpty { "New Tab" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Close
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClose
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Thumbnail fills remaining space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        AsyncTabThumbnail(
                            tab = tab,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // ── LIST VIEW: Compact horizontal row ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        AsyncTabThumbnail(
                            tab = tab,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Title + URL
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (faviconImageBitmap != null) {
                                Image(
                                    bitmap = faviconImageBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Text(
                                text = tab.title.ifEmpty { "New Tab" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.url,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClose
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTabsState(
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit = {},
    isPrivateMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                if (isPrivateMode) Icons.Default.Lock else Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = if (isPrivateMode) "No Private Tabs" else "No Tabs",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isPrivateMode)
                    "Tap (+) to start browsing privately"
                else
                    "Tap (+) to start browsing",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AsyncTabThumbnail(
    tab: BrowserTab,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Use Coil for better image loading and caching
    if (tab.url.isEmpty() || tab.url == "about:blank") {
        // Show Homepage placeholder for new tabs
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (tab.isPrivate) {
                Icon(
                    painter = painterResource(R.drawable.fox),
                    contentDescription = "Private Tab",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "New Tab",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    } else {
        // Try to load from memory first, then from disk cache
        val bitmapToShow = tab.thumbnail
        val imageBitmap = remember(bitmapToShow) {
            try {
                if (bitmapToShow != null && !bitmapToShow.isRecycled) bitmapToShow.asImageBitmap() else null
            } catch (_: Exception) { null }
        }
        
        if (imageBitmap != null) {
            // Show in-memory thumbnail immediately
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        } else {
            // Load from disk cache directly (avoid Coil caching stale error results)
            val diskBitmap by produceState<android.graphics.Bitmap?>(null, tab.url) {
                value = withContext(Dispatchers.IO) {
                    ThumbnailCache.loadThumbnail(context, tab.url)
                }
            }
            
            val diskImageBitmap = remember(diskBitmap) {
                try {
                    diskBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
                } catch (_: Exception) { null }
            }
            
            if (diskImageBitmap != null) {
                Image(
                    bitmap = diskImageBitmap!!,
                    contentDescription = null,
                    modifier = modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            } else {
                // No thumbnail available - show placeholder
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "No Thumbnail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}
