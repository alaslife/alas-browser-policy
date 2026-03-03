package com.sun.alasbrowser.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.R
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.BrowserTab
import com.sun.alasbrowser.downloads.AlasDownloadManager
import com.sun.alasbrowser.utils.QrCodeGenerator
import com.sun.alasbrowser.utils.ScriptManager
import com.sun.alasbrowser.web.AlasWebView
import com.sun.alasbrowser.web.SimpleAdBlocker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


private val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

@SuppressLint("UnnecessaryComposedModifier")
@Composable
fun Modifier.smoothClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    this.pointerInput(enabled) {
        if (enabled) {
            detectTapGestures(
                onTap = { onClick() }
            )
        }
    }
}

@Composable
fun MinimalTopBar(
    currentTab: BrowserTab,
    url: String,
    progress: Int,
    isEditing: Boolean = false,
    onUrlChange: (String) -> Unit = {},
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onSearch: () -> Unit = {},
    onEditClose: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    isListening: Boolean = false,
    onHomeClick: () -> Unit = {},
    onSecurityClick: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Tablet-adaptive sizing
    val configuration = android.content.res.Configuration()
    val screenWidthDp = LocalContext.current.resources.configuration.screenWidthDp
    val isTablet = screenWidthDp >= 600
    val rowHeight = if (isTablet) 52.dp else 42.dp
    val buttonSize = if (isTablet) 46.dp else 38.dp
    val iconSize = if (isTablet) 22.dp else 18.dp
    val urlBarHeight = if (isTablet) 46.dp else 38.dp
    val urlFontSize = if (isTablet) 15.sp else 13.sp
    val securityIconSize = if (isTablet) 18.dp else 14.dp
    val securityBoxSize = if (isTablet) 36.dp else 30.dp
    val editBarHeight = if (isTablet) 54.dp else 48.dp
    val editFontSize = if (isTablet) 17.sp else 15.sp

    // Ad block count for current site
    val domain = remember(url) {
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split("/").firstOrNull() ?: ""
    }
    var adBlockCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(domain) {
        while (true) {
            if (domain.isNotEmpty()) {
                adBlockCount = SimpleAdBlocker.getAdsBlockedForSite(domain) +
                    SimpleAdBlocker.getTrackersBlockedForSite(domain)
            }
            delay(1000)
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            // Request focus when editing mode is enabled
            delay(150) // Increased delay for better reliability
            try {
                focusRequester.requestFocus()
                // Explicitly show keyboard after focus
                keyboardController?.show()
            } catch (e: Exception) {
         
            }
        } else {
            try {
                keyboardController?.hide()
            } catch (e: Exception) {
            
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 4.dp)
    ) {
        // Search Bar - Modern blue accent style with liquid glass effect
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 2.dp)
                    .height(editBarHeight)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEditClose) {
                    CuteBackIcon(
                        tint = MaterialTheme.colorScheme.primary,
                        size = 20.dp
                    )
                }
               
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier
                            .wrapContentWidth()
                            .widthIn(min = 200.dp)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = editFontSize,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearch()
                                keyboardController?.hide()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.wrapContentWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (url.isEmpty()) {
                                    Text(
                                        "Search or enter URL",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
               
                VoiceSearchButton(
                    onClick = onVoiceSearch,
                    isListening = isListening
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(vertical = 4.dp)
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Home — thin border circle, Figure.ai style
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .smoothClickable { onHomeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home),
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // URL bar — thin border pill, subtle inner glow
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security indicator — minimal dot/icon
                    val isSecure = url.startsWith("https://")
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .smoothClickable { onSecurityClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSecure)
                                Icons.Filled.Lock
                            else
                                Icons.Filled.Shield,
                            contentDescription = "Security",
                            tint = if (adBlockCount > 0)
                                Color(0xFF4CAF50)
                            else if (isSecure)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        // Ad block count badge
                        if (adBlockCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .background(Color(0xFF4CAF50), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (adBlockCount > 99) "99+" else adBlockCount.toString(),
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // URL text
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onSearchClick() }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url.takeIf { it.isNotEmpty() }
                                ?.removePrefix("https://")
                                ?.removePrefix("http://")
                                ?.removePrefix("www.")
                                ?: "Search or enter URL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Menu — gradient border glow, Figure.ai accent
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .smoothClickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.hamburger_menu_dark),
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Progress Bar - smooth animated like Chrome/Safari
        SmoothProgressBar(
            isLoading = currentTab.isLoading,
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
/**
 * Smooth animated progress bar like Chrome/Safari.
 * - Animates progress smoothly with spring physics
 * - Shows a gradient shimmer highlight at the leading edge
 * - Fades out gracefully when loading completes instead of disappearing abruptly
 * - Reserves no layout space when fully hidden (no empty Spacer)
 */
@Composable
fun SmoothProgressBar(
    isLoading: Boolean,
    progress: Int,
    modifier: Modifier = Modifier
) {
    val barHeight = 2.dp
    val primaryColor = MaterialTheme.colorScheme.primary

    // Smooth animated progress value (spring for natural feel)
    val animatedProgress by animateFloatAsState(
        targetValue = if (isLoading) (progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f) else 1f,
        animationSpec = if (isLoading) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
        },
        label = "progress"
    )

    // Visibility: fade out after loading completes
    var showBar by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, progress) {
        if (isLoading && progress < 100) {
            showBar = true
        } else if (!isLoading || progress >= 100) {
            // Brief delay so user sees 100% before fade
            delay(250)
            showBar = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (showBar) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (showBar) 150 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "barAlpha"
    )

    // Shimmer highlight animation at leading edge
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .height(barHeight)
                .graphicsLayer { this.alpha = alpha }
        ) {
            // Track — barely visible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(primaryColor.copy(alpha = 0.06f))
            )
            // Filled progress — Figure.ai glow gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(barHeight)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.3f),
                                primaryColor.copy(alpha = 0.8f),
                                primaryColor,
                                Color.White.copy(alpha = shimmerAlpha * 0.6f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun MinimalBottomBar(
    tabCount: Int,
    onTabSwitcherClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    // Figure.ai-inspired floating pill
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
            .fillMaxWidth()
            .height(44.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back/Forward Group
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = CircleShape
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                enabled = canGoBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onForwardClick,
                enabled = canGoForward,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
       
        // Right Side Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Button
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Tab Counter — Figure.ai thin border style
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTabSwitcherClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(5.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tabCount > 0) tabCount.toString() else "0",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
           
            // Home Button — Figure.ai bordered accent button
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable(onClick = onHomeClick)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Home",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Menu / Settings
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TabStrip(
    tabs: List<com.sun.alasbrowser.data.BrowserTab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            val bgColor = if (isActive) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerLow
            val textColor = if (isActive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

            Row(
                modifier = Modifier
                    .height(38.dp)
                    .widthIn(min = 120.dp, max = 200.dp)
                    .background(bgColor)
                    .clickable { onTabClick(tab.id) }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favicon
                val favicon = tab.favicon
                if (favicon != null) {
                    Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Title
                Text(
                    text = tab.title.ifBlank { "New Tab" },
                    color = textColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Close button
                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Separator between tabs
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                )
            }
        }

        // New tab button
        IconButton(
            onClick = onNewTab,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun WebPageMenuDialog(
    url: String,
    title: String,
    historyLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    bookmarksLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    preferences: BrowserPreferences? = null,
    onOpenSettings: () -> Unit = {},
    onOpenAdBlocker: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onNewPrivateTab: () -> Unit = {},
    onShowTabSwitcher: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onScanQr: () -> Unit = {},
    onShowPageQr: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onShowZoom: () -> Unit = {},
    
    onSummarize: () -> Unit = {},
    onReload: () -> Unit = {},
    onReaderMode: () -> Unit = {},
    onPrint: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    onToggleEngine: () -> Unit = {},
    isUsingWebView: Boolean = false
) {
    val context = LocalContext.current
    val desktopModeEnabled = preferences?.desktopMode == true
    var showFindInPage by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(preferences?.textSize ?: 100) }
    
    BackHandler { onDismiss() }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dismiss overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )
       
        // Advanced Menu surface positioned at top-end - Modern dark with blue accents
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 8.dp)
                .width(280.dp)
                .heightIn(max = 560.dp)
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume tap */ }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(24.dp)
                    clip = true
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Top circular icon buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings icon
                    CircularIconButton(
                        painter = painterResource(id = R.drawable.ic_settings),
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                    
                    // Ad blocker icon
                    CircularIconButton(
                        painter = painterResource(id = R.drawable.ad_block_dark),
                        onClick = {
                            onDismiss()
                            onOpenAdBlocker()
                        }
                    )
                    
                    // Bookmark icon
                    CircularIconButton(
                        imageVector = Icons.Default.BookmarkBorder,
                        onClick = {
                            onDismiss()
                            onAddBookmark()
                        }
                    )
                    
                    // Refresh icon
                    CircularIconButton(
                        imageVector = Icons.Default.Refresh,
                        onClick = {
                            onDismiss()
                            onReload()
                        }
                    )
                }
                
                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // New Tab
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.Add,
                    text = "New Tab",
                    onClick = {
                        onDismiss()
                        onNewTab()
                    }
                )
                
                // New Private Tab
                AdvancedMenuOptionRow(
                    painter = painterResource(id = R.drawable.search_incogonito_dark),
                    text = "New Private Tab",
                    onClick = {
                        onDismiss()
                        onNewPrivateTab()
                    }
                )
                
                // See all Tabs
                AdvancedMenuOptionRow(
                    painter = painterResource(id = R.drawable.ic_tabs),
                    text = "See all Tabs",
                    onClick = {
                        onDismiss()
                        onShowTabSwitcher()
                    }
                )
                
                // Summarize (AI)
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.AutoAwesome,
                    text = "Summarize Page (AI)",
                    onClick = {
                        onDismiss()
                        onSummarize()
                    }
                )
                
                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // View History
                AdvancedMenuOptionRow(
                    painter = painterResource(id = R.drawable.history_dark),
                    text = "View History",
                    onClick = {
                        onDismiss()
                        historyLauncher.launch(Intent(context, HistoryActivity::class.java))
                    }
                )
                
                // View Bookmarks
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.BookmarkBorder,
                    text = "View Bookmarks",
                    onClick = {
                        onDismiss()
                        bookmarksLauncher.launch(Intent(context, BookmarksActivity::class.java))
                    }
                )
                
                // Downloads
                AdvancedMenuOptionRow(
                    painter = painterResource(id = R.drawable.download_dark),
                    text = "Downloads",
                    onClick = {
                        onDismiss()
                        val intent = Intent(context, DownloadsActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                
                // Reader Mode
                AdvancedMenuOptionRow(
                    imageVector = Icons.AutoMirrored.Filled.ChromeReaderMode,
                    text = "Reader View",
                    onClick = {
                        onDismiss()
                        onReaderMode()
                    }
                )
                
                // Translate
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.Translate,
                    text = "Translate Page",
                    onClick = {
                        onDismiss()
                        onTranslate()
                    }
                )

                
                    // QR Scanner
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.QrCodeScanner,
                    text = "Scan QR Code",
                    onClick = {
                        onDismiss()
                        onScanQr()
                    }
                )
                
                // Show QR Code for page
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.QrCode,
                    text = "Show Page QR Code",
                    onClick = {
                        onDismiss()
                        onShowPageQr()
                    }
                )

                // Set as Default Browser
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.Settings,
                    text = "Set as Default Browser",
                    onClick = {
                        onDismiss()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                                !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                                (context as? Activity)?.startActivityForResult(intent, 999)
                            } else {
                                Toast.makeText(context, "Already set as default or not available", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(settingsIntent)
                            }
                        }
                    }
                )

                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Desktop Mode with toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = "Desktop Mode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Desktop Mode",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    
                    Switch(
                        checked = desktopModeEnabled,
                        onCheckedChange = { enabled ->
                            preferences?.setDesktopMode(enabled)
                            // Reload handled by BrowserScreen/GeckoViewContainer observing preferences
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(48.dp, 28.dp)
                    )
                }
                

                
                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Summarize (AI)
                AdvancedMenuOptionRow(
                    imageVector = Icons.Default.AutoAwesome,
                    text = "Summarize Page (AI)",
                    onClick = {
                        onDismiss()
                        onSummarize()
                    }
                )
                
                // Find in Page
                AdvancedMenuTextRow(
                    text = "Find in Page",
                    onClick = { 
                        onDismiss()
                        onFindInPage()
                    }
                )
                
                // Save as PDF
                AdvancedMenuTextRow(
                    text = "Print / Save as PDF",
                    onClick = {
                        onDismiss()
                        onPrint()
                    }
                )
                
                // Zoom
                AdvancedMenuTextRow(
                    text = "Zoom ($zoomLevel%)",
                    onClick = {
                        onDismiss()
                        onShowZoom()
                    }
                )
                
                // Share
                AdvancedMenuTextRow(
                    text = "Share",
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
fun CircularIconButton(
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .smoothClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageVector != null -> Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun AdvancedMenuOptionRow(
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .smoothClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            imageVector != null -> Icon(
                imageVector = imageVector,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            painter != null -> Icon(
                painter = painter,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun AdvancedMenuTextRow(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .smoothClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 34.dp)
        )
    }
}

@Composable
fun QrCodeDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isLoading) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "qrScale"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(300),
        label = "qrAlpha"
    )
    
    LaunchedEffect(url) {
        isLoading = true
        qrBitmap = withContext(Dispatchers.Default) {
            try {
                QrCodeGenerator.generateQrCode(url, 512)
            } catch (e: Exception) {
           
                null
            }
        }
        isLoading = false
    }
    
    fun saveQrCode() {
        val bitmap = qrBitmap ?: return
        isSaving = true
        coroutineScope.launch {
            try {
                val fileName = "QR_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlasBrowser")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                uri?.let {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                    
                    Toast.makeText(context, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(context, "Failed to save QR code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
        
                Toast.makeText(context, "Failed to save QR code", Toast.LENGTH_SHORT).show()
            } finally {
                isSaving = false
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 340.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume tap */ }
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = "Page QR Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                            }
                            qrBitmap != null -> {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = animatedAlpha },
                                    contentScale = ContentScale.Fit
                                )
                            }
                            else -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Failed to generate",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))

                FilledTonalButton(
                    onClick = { saveQrCode() },
                    enabled = qrBitmap != null && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download QR Code",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun FindInPageDialog(
    webView: WebView?,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Find in page", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            webView?.findAllAsync(query)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
            }) {
                Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {
                webView?.findNext(true)
            }) {
                Icon(Icons.Default.KeyboardArrowDown, "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
},
confirmButton = {
TextButton(onClick = {
    webView?.clearMatches()
    onDismiss()
}) {
    Text("Done", color = MaterialTheme.colorScheme.primary)
}
}
    )
}
@Composable
fun GeckoFindInPageDialog(
    tabId: String,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = {
            com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)?.clearMatches()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Find in page", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            val wv = com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)
                            if (query.isNotEmpty()) {
                                wv?.findAllAsync(query)
                            } else {
                                wv?.clearMatches()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)?.findNext(false)
                        }) {
                            Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)?.findNext(true)
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Next", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                com.sun.alasbrowser.engine.WebViewCache.getWebView(tabId)?.clearMatches()
                onDismiss()
            }) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun VoiceSearchButton(
    onClick: () -> Unit,
    isListening: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val primaryColor = MaterialTheme.colorScheme.primary

    // Ring 1 — fast inner pulse
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ring1"
    )
    // Ring 2 — medium outer pulse (offset phase via different timing)
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EmphasizedEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ring2"
    )
    // Ring 3 — slow outermost ripple
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 1.1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ring3"
    )

    // Waveform bar heights — 5 bars with staggered timing
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300), repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400), repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(250), repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(350), repeatMode = RepeatMode.Reverse
        ), label = "bar4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450), repeatMode = RepeatMode.Reverse
        ), label = "bar5"
    )

    // Smooth transition for icon/glow
    val glowAlpha by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(300), label = "glow"
    )

    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            // Ring 3 — outermost, faintest
            Box(
                modifier = Modifier
                    .size(44.dp * ring3)
                    .graphicsLayer { alpha = 0.08f }
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            // Ring 2
            Box(
                modifier = Modifier
                    .size(44.dp * ring2)
                    .graphicsLayer { alpha = 0.15f }
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            // Ring 1 — inner, strongest
            Box(
                modifier = Modifier
                    .size(44.dp * ring1)
                    .graphicsLayer { alpha = 0.25f }
                    .clip(CircleShape)
                    .background(primaryColor)
            )
        }

        // Core button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (isListening) Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.4f)
                            )
                        ),
                        shape = CircleShape
                    ) else Modifier
                )
                .background(
                    if (isListening) primaryColor.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .smoothClickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                // Waveform bars instead of mic icon while listening
                Canvas(modifier = Modifier.size(20.dp)) {
                    val barWidth = size.width * 0.09f
                    val gap = size.width * 0.11f
                    val totalWidth = 5 * barWidth + 4 * gap
                    val startX = (size.width - totalWidth) / 2f
                    val centerY = size.height / 2f
                    val maxH = size.height * 0.7f
                    val bars = listOf(bar1, bar2, bar3, bar4, bar5)

                    bars.forEachIndexed { i, h ->
                        val x = startX + i * (barWidth + gap) + barWidth / 2f
                        val barHeight = maxH * h.coerceIn(0.15f, 1f)
                        drawLine(
                            color = primaryColor,
                            start = Offset(x, centerY - barHeight / 2f),
                            end = Offset(x, centerY + barHeight / 2f),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            } else {
                CuteMicIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp
                )
            }
        }
    }
}

/**
 * Opera-style Search Overlay with modern UI
 * Features: Google logo, QR/Mic icons, Clipboard link, Quick access tiles,
 * History with fill-to-search, Trending searches, Settings
 */
@Composable
fun WebSearchOverlay(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    searchEngine: com.sun.alasbrowser.data.SearchEngine = com.sun.alasbrowser.data.SearchEngine.GOOGLE,
    onScanQr: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    history: List<com.sun.alasbrowser.data.History> = emptyList(),
    quickAccessSites: List<com.sun.alasbrowser.data.History> = emptyList(),
    onClearHistory: () -> Unit = {},
    onHistoryItemClick: (String) -> Unit = {},
    onFillSearchBar: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Search suggestions
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250) // debounce
        val result = com.sun.alasbrowser.data.SearchSuggestionService.getSuggestions(searchQuery, searchEngine)
        suggestions = result.take(8)
    }
    
    // Clipboard content
    var clipboardText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        clipboardText = clipboardManager.getText()?.text?.takeIf { 
            it.isNotBlank() && (it.startsWith("http") || it.contains("."))
        }
    }
    
    // Animation states
    var isClosing by remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isClosing) 0f else 1f,
        animationSpec = tween(250),
        finishedListener = { if (isClosing) onClose() },
        label = "alpha"
    )
    
    fun handleClose() {
        if (!isClosing) {
            keyboardController?.hide()
            isClosing = true
        }
    }
    
    BackHandler(enabled = !isClosing) { handleClose() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    
    // Modern dark theme — soft violet accent
    val bgColor = Color(0xFF0A0A0F)
    val cardColor = Color(0xFF151520)
    val cardHighlight = Color(0xFF1C1C2A)
    val accentColor = Color(0xFF7C6AFF)
    val secondaryAccent = Color(0xFF4ECDC4)
    val textColor = Color(0xFFF2F2F7)
    val subtleColor = Color(0xFF6E6E80)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = animatedAlpha }
            .background(bgColor)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══ SEARCH BAR ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(cardColor, cardHighlight)
                        ),
                        shape = RoundedCornerShape(27.dp)
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.4f),
                                secondaryAccent.copy(alpha = 0.2f),
                                accentColor.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(27.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search engine logo
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.95f), CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    searchEngine.iconUrl?.let { iconUrl ->
                        coil.compose.AsyncImage(
                            model = iconUrl,
                            contentDescription = searchEngine.displayName,
                            modifier = Modifier.size(20.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } ?: Text(
                        searchEngine.displayName.take(1),
                        color = Color(0xFF4285F4),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = textColor, fontSize = 15.sp),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSearch()
                    }),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Search or ask anything…", color = subtleColor, fontSize = 15.sp)
                            }
                            inner()
                        }
                    }
                )
                
                IconButton(onClick = onScanQr, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = "Scan QR",
                        tint = subtleColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(onClick = onVoiceSearch, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = subtleColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // ═══ SEARCH SUGGESTIONS ═══
            if (suggestions.isNotEmpty() && searchQuery.length >= 2) {
                // Suggestion chips (first 3, compact row)
                val chipSuggestions = suggestions.take(3)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chipSuggestions.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .background(cardColor, RoundedCornerShape(20.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                                .clickable {
                                    onSearchQueryChange(chip)
                                    keyboardController?.hide()
                                    onSearch()
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(chip, color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Suggestion list items (remaining, with fill arrow)
                val listSuggestions = suggestions.drop(3).take(5)
                if (listSuggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardColor, RoundedCornerShape(14.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                    ) {
                        listSuggestions.forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSearchQueryChange(suggestion)
                                        keyboardController?.hide()
                                        onSearch()
                                    }
                                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = suggestion,
                                    color = textColor,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onFillSearchBar(suggestion) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Fill",
                                        tint = subtleColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (index < listSuggestions.size - 1) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 56.dp)
                                        .height(0.5.dp)
                                        .background(Color.White.copy(alpha = 0.04f))
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
            }
            
            // ═══ CLIPBOARD LINK ═══
            clipboardText?.let { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, RoundedCornerShape(14.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .clickable { 
                            onSearchQueryChange(link)
                            onSearch()
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(secondaryAccent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = secondaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Link you copied",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            link.take(40) + if (link.length > 40) "…" else "",
                            color = subtleColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = secondaryAccent.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            
            // ═══ QUICK ACCESS TILES ═══
            if (quickAccessSites.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    quickAccessSites.take(6).forEach { site ->
                        val tileColors = listOf(
                            Color(0xFF7C6AFF), Color(0xFF4ECDC4), 
                            Color(0xFFFF6B6B), Color(0xFFFFA726),
                            Color(0xFF42A5F5), Color(0xFFAB47BC)
                        )
                        val colorIndex = (site.title.hashCode() and 0x7FFFFFFF) % tileColors.size
                        
                        Column(
                            modifier = Modifier
                                .width(68.dp)
                                .clickable { onHistoryItemClick(site.url) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        tileColors[colorIndex].copy(alpha = 0.25f), 
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(0.5.dp, tileColors[colorIndex].copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = site.title.firstOrNull()?.uppercaseChar() ?: 'W'
                                Text(
                                    text = initial.toString(),
                                    color = tileColors[colorIndex].copy(alpha = 0.9f),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = site.title.take(10) + if (site.title.length > 10) "…" else "",
                                color = subtleColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
            
            // ═══ HISTORY SECTION ═══
            if (history.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, RoundedCornerShape(14.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                ) {
                    history.take(5).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHistoryItemClick(item.url) }
                                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(subtleColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = subtleColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title.ifEmpty { item.url },
                                    color = textColor,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.title.isNotEmpty()) {
                                    Text(
                                        text = item.url.removePrefix("https://").removePrefix("http://").take(30),
                                        color = subtleColor.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onFillSearchBar(item.url) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NorthWest,
                                    contentDescription = "Fill",
                                    tint = subtleColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (index < minOf(4, history.size - 1)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 56.dp)
                                    .height(0.5.dp)
                                    .background(Color.White.copy(alpha = 0.04f))
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClearHistory() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Clear all", color = accentColor.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
            
            // ═══ TRENDING SEARCHES ═══
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = secondaryAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trending", color = subtleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            val trendingSearches = listOf("weather today", "news", "sports scores", "recipes", "movies")
            
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trendingSearches.forEach { term ->
                    Box(
                        modifier = Modifier
                            .background(cardColor, RoundedCornerShape(20.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                            .clickable { 
                                onSearchQueryChange(term)
                                onSearch()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(term, color = textColor.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ═══ SETTINGS BUTTON ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, RoundedCornerShape(14.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                    .clickable { onOpenSettings() }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = subtleColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings", color = subtleColor, fontSize = 13.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CustomScrollbar(
    webView: AlasWebView?,
    scrollMetrics: StateFlow<ScrollMetrics>,
    modifier: Modifier = Modifier
) {
    val metrics by scrollMetrics.collectAsState()
    val scrollOffset = metrics.scrollY
    val scrollRange = metrics.scrollRange
    val scrollExtent = metrics.scrollExtent

    if (webView == null || scrollRange <= scrollExtent || scrollExtent == 0) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 4.dp)
    ) {
        val density = LocalDensity.current
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightDp = 90.dp
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        
        val maxScrollOffset = scrollRange - scrollExtent
        val scrollFraction = if (maxScrollOffset > 0) scrollOffset.toFloat() / maxScrollOffset.toFloat() else 0f
        
        val movableHeight = trackHeightPx - thumbHeightPx
        val thumbOffsetPx = (scrollFraction * movableHeight).coerceIn(0f, movableHeight)

        var isDragging by remember { mutableStateOf(false) }
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .size(width = 40.dp, height = thumbHeightDp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isDragging) Color(0xFF3D3E40) else Color(0xFF2D2E30).copy(alpha = 0.9f))
                .pointerInput(maxScrollOffset, movableHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { 
                            isDragging = true
                            accumulatedDrag = 0f
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            
                            val dragFraction = accumulatedDrag / movableHeight
                            val targetScrollY = ((scrollFraction + dragFraction) * maxScrollOffset).roundToInt()
                                .coerceIn(0, maxScrollOffset)
                            
                            webView.scrollTo(0, targetScrollY)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
            ) {
                if (scrollOffset > 10) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to Top",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp).smoothClickable {
                            webView.scrollTo(0, 0)
                        }
                    )
                } else {
                    Spacer(Modifier.size(22.dp))
                }

                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Drag to scroll",
                    tint = if (isDragging) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                if (scrollOffset < maxScrollOffset - 10) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to Bottom",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp).smoothClickable {
                            webView.scrollTo(0, maxScrollOffset)
                        }
                    )
                } else {
                    Spacer(Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun ZoomFloatingControl(
    initialZoom: Int,
    onZoomChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(initialZoom.toFloat().coerceIn(50f, 200f)) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 16.dp, end = 16.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onZoomChange(sliderPosition.toInt()) },
                valueRange = 50f..200f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            Text(
                text = "${sliderPosition.toInt()}%",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            
            IconButton(
                onClick = {
                    sliderPosition = 100f
                    onZoomChange(100)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

data class PendingDownload(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
    val cookies: String? = null,
    val referer: String? = null
)

fun formatFileSize(size: Long): String {
    if (size <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadConfirmationSheet(
    download: PendingDownload,
    onConfirm: (String) -> Unit, // filename
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Extract initial filename
    val context = LocalContext.current
    // Extract initial filename
    val initialName = remember(download) {
        // Try content disposition first
        download.contentDisposition?.let {
            val regex = """filename[*]?=['"]?([^'";]+)['"]?""".toRegex()
            regex.find(it)?.groupValues?.getOrNull(1)
        } ?: run {
            // Try URL
            val urlFileName = download.url.substringAfterLast("/").substringBefore("?")
            if (urlFileName.isNotEmpty() && urlFileName.contains(".")) {
                urlFileName
            } else {
                // Generate filename with extension from MIME type
                val extension = when (download.mimeType) {
                    "application/vnd.android.package-archive" -> "apk"
                    "application/zip" -> "zip"
                    "application/pdf" -> "pdf"
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "video/mp4" -> "mp4"
                    else -> "bin"
                }
                "download_${System.currentTimeMillis()}.$extension"
            }
        }
    }
    var fileName by remember { mutableStateOf(initialName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Box(
                     modifier = Modifier
                         .size(48.dp)
                         .background(Color(0xFF8BC34A), CircleShape), // Android Greenish
                     contentAlignment = Alignment.Center
                 ) {
                     Icon(
                         Icons.Default.Android,
                         contentDescription = null,
                         tint = Color.White,
                         modifier = Modifier.size(28.dp)
                     )
                 }
                 Spacer(modifier = Modifier.width(16.dp))
                 Column(modifier = Modifier.weight(1f)) {
                     BasicTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                     )
                     HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                 }
                 
                 IconButton(onClick = { /* TODO: edit logic if needed */ }) {
                     Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                     )
                 }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Folder Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder, // Need Folder icon
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Download",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 1. Download Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfirm(fileName) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = "Download",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = formatFileSize(download.contentLength),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
            
            // 2. Cancel Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            }
        }
    }
}

