package com.sun.alasbrowser.ui

import android.content.Context
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.R
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.SearchSuggestionService
import com.sun.alasbrowser.ui.theme.AlasColors
import com.sun.alasbrowser.web.SimpleAdBlocker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlasHomePage(
    onNavigate: (String) -> Unit,
    preferences: BrowserPreferences,
    modifier: Modifier = Modifier,
    openSearchBar: Boolean = false,
    onSearchBarTap: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    isListening: Boolean = false,
    onScanQr: () -> Unit = {},
    onShowSiteSettings: () -> Unit = {},
    onShowAdBlocker: () -> Unit = {},
    onShowIncognito: () -> Unit = {},
    isPrivate: Boolean = false
) {
    // Use incognito search engine in private mode
    val currentSearchEngine = if (isPrivate) preferences.incognitoSearchEngine else preferences.searchEngine
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { BrowserDatabase.getDatabase(context) }
    
    // Keep callbacks stable to prevent stale closures
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionJob by remember { mutableStateOf<Job?>(null) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showCustomizationPopup by remember { mutableStateOf(false) }
    
    LaunchedEffect(searchQuery) {
        suggestionJob?.cancel()
        if (searchQuery.isBlank()) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        suggestionJob = scope.launch {
            delay(300)
            val suggestions = SearchSuggestionService.getSuggestions(searchQuery, currentSearchEngine)
            searchSuggestions = suggestions.take(6)
        }
    }
    
    // When requested externally, route to the same search page flow as tap.
    LaunchedEffect(openSearchBar) {
        if (openSearchBar) {
            onSearchBarTap()
        }
    }
    
    // Startup Animation State
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // Ad blocker status - derived from preferences, no polling needed
    val adBlockEnabled by remember { derivedStateOf { preferences.adBlockEnabled } }
    var blockedAdsCount by remember { mutableIntStateOf(SimpleAdBlocker.getBlockedCount()) }
    
    // Only refresh blocked count when screen is visible - less frequent
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000L) // Refresh count every 10 seconds
            blockedAdsCount = SimpleAdBlocker.getBlockedCount()
        }
    }
    
    // Dynamic greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning!"
            hour < 17 -> "Good afternoon!"
            else -> "Good evening!"
        }
    }

    val scrollState = rememberScrollState()

    val customWallpaperUri = preferences.customWallpaperUri
    val selectedWallpaperId = preferences.selectedWallpaperId

    // Map wallpaper IDs to drawable resources - avoid reflection
    val wallpaperResId = when (selectedWallpaperId) {
        "wp_1" -> R.drawable.wp_1
        "wp_2" -> R.drawable.wp_2
        "wp_3" -> R.drawable.wp_3
        "wp_4" -> R.drawable.wp_4
        "wp_5" -> R.drawable.wp_5
        "wp_6" -> R.drawable.wp_6
        "wp_7" -> R.drawable.wp_7
        "wp_8" -> R.drawable.wp_8
        "wp_9" -> R.drawable.wp_9
        "wp_10" -> R.drawable.wp_10
        "wp_11" -> R.drawable.wp_11
        "wp_12" -> R.drawable.wp_12
        "wp_13" -> R.drawable.wp_13
        "wp_14" -> R.drawable.wp_14
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Wallpaper Layer - fills entire screen
        if (customWallpaperUri != null) {
            AsyncImage(
                model = customWallpaperUri,
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) // Scrim for contrast
        } else if (wallpaperResId != null) {
            Image(
                painter = painterResource(id = wallpaperResId),
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) // Scrim for contrast
        } else {
            // Default background
            Box(modifier = Modifier.fillMaxSize().background(AlasColors.PrimaryBackground))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    state = scrollState,
                    flingBehavior = ScrollableDefaults.flingBehavior()
                )
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header with hamburger menu and status icons
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -40 }, animationSpec = tween(300))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Menu/Settings button
                    AnimatedIconButton(
                        onClick = { onShowSiteSettings() },
                        icon = Icons.Default.Lock
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ad Blocker Pill
                        AnimatedPill(
                            onClick = { onShowAdBlocker() },
                            backgroundColor = if (adBlockEnabled) AlasColors.Success.copy(alpha = 0.2f) else AlasColors.UnfocusedIndicator
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (adBlockEnabled) Icons.Default.Block else Icons.Default.Warning,
                                    contentDescription = "AdBlock",
                                    tint = if (adBlockEnabled) AlasColors.Success else AlasColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (blockedAdsCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$blockedAdsCount",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (adBlockEnabled) AlasColors.Success else AlasColors.TextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Incognito Pill
                        AnimatedIconButton(
                            onClick = { onShowIncognito() },
                            icon = Icons.Default.VisibilityOff
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Greeting Section - Proxima AI style - smooth animation
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(400, delayMillis = 100)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = greeting,
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlasColors.TextPrimary,
                            lineHeight = 36.sp
                        )
                    )
                    Text(
                        text = "What would you like to explore?",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlasColors.Accent,
                            lineHeight = 36.sp
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Modern Search Bar with gradient button - smooth animation
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 200)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(400, delayMillis = 200)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(29.dp),
                                spotColor = Color.Black.copy(alpha = 0.08f)
                            )
                            .clip(RoundedCornerShape(29.dp))
                            .background(AlasColors.CardBackground)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Search Engine Icon
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AlasColors.PrimaryBackground.copy(alpha = 0.6f))
                                    .clickable { showSearchEngineDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                SearchEngineIcon(
                                    searchEngine = currentSearchEngine,
                                    onClick = { showSearchEngineDialog = true },
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                                    .clickable { onSearchBarTap() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    "Search or enter URL...",
                                    color = AlasColors.Placeholder,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // QR Code Scanner Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AlasColors.PrimaryBackground.copy(alpha = 0.6f))
                                    .clickable { onScanQr() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR Code",
                                    tint = AlasColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Voice/Go Button with gradient
                            GradientActionButton(
                                isListening = isListening,
                                hasQuery = false,
                                onVoiceClick = onVoiceSearch,
                                onGoClick = {
                                    onSearchBarTap()
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Search Suggestions - shows dynamic suggestions when typing
            if (isVisible) {
                SearchSuggestions(
                    query = searchQuery,
                    suggestions = searchSuggestions,
                    onSuggestionClick = { suggestion ->
                        val url = currentSearchEngine.buildSearchUrl(suggestion)
                        currentOnNavigate(url)
                        searchQuery = ""
                        searchSuggestions = emptyList()
                    },
                    onFillSearchBar = { suggestion ->
                        searchQuery = suggestion
                    }
                )
            }
        }
    }
    
    if (showSearchEngineDialog) {
        SearchEngineDialog(
            currentEngine = currentSearchEngine,
            onEngineSelected = { engine ->
                if (isPrivate) {
                    preferences.setIncognitoSearchEngine(engine)
                } else {
                    preferences.setSearchEngine(engine)
                }
                showSearchEngineDialog = false
            },
            onDismiss = { showSearchEngineDialog = false }
        )
    }
}

// --- Animated Component Helpers ---

@Composable
fun AnimatedPill(
    onClick: () -> Unit,
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pill_scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        content()
    }
}

@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    icon: ImageVector
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "icon_scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(AlasColors.UnfocusedIndicator.copy(alpha = 0.3f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AlasColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GradientActionButton(
    isListening: Boolean,
    hasQuery: Boolean,
    onVoiceClick: () -> Unit,
    onGoClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = when {
            isListening -> 1.1f
            isPressed -> 0.92f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gradient_button_scale"
    )
    
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(44.dp)
            .scale(scale)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                spotColor = AlasColors.Accent.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(
                color = AlasColors.PinkAccent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = if (hasQuery) onGoClick else onVoiceClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hasQuery) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Mic,
            contentDescription = if (hasQuery) "Go" else "Voice Search",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SearchSuggestions(
    query: String,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onFillSearchBar: (String) -> Unit
) {
    AnimatedVisibility(
        visible = query.isNotEmpty() && suggestions.isNotEmpty(),
        enter = fadeIn(tween(200)) + slideInVertically(
            initialOffsetY = { -20 },
            animationSpec = tween(200)
        ),
        exit = fadeOut(tween(150))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Suggestions",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AlasColors.TextSecondary
                ),
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
            
            suggestions.forEach { suggestion ->
                SuggestionCard(
                    text = suggestion,
                    query = query,
                    onClick = { onSuggestionClick(suggestion) },
                    onFillClick = { onFillSearchBar(suggestion) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    text: String,
    query: String,
    onClick: () -> Unit,
    onFillClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "suggestion_scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(AlasColors.CardBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AlasColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AlasColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        HighlightedText(
            text = text,
            query = query,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AlasColors.PrimaryBackground)
                .clickable(onClick = onFillClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Fill search",
                tint = AlasColors.TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = -45f }
            )
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier
) {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val startIndex = lowerText.indexOf(lowerQuery)
    
    if (startIndex >= 0 && query.isNotEmpty()) {
        val endIndex = startIndex + query.length
        val beforeMatch = text.substring(0, startIndex)
        val match = text.substring(startIndex, endIndex)
        val afterMatch = text.substring(endIndex)
        
        Row(modifier = modifier) {
            if (beforeMatch.isNotEmpty()) {
                Text(
                    text = beforeMatch,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AlasColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
            Text(
                text = match,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlasColors.Accent
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            if (afterMatch.isNotEmpty()) {
                Text(
                    text = afterMatch,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AlasColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AlasColors.TextPrimary
            ),
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
