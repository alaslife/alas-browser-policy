package com.sun.alasbrowser.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════════════════
// COMET BROWSER STYLE - PREMIUM BUTTON INTERACTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Premium browser action button with Comet-style interactions
 * Features: Scale animation, glow effect, haptic feedback, ripple
 */
@Composable
fun PremiumBrowserButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String? = null,
    enabled: Boolean = true,
    isActive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 44.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    
    // Animations
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isActive -> 1.05f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.4f else 0f,
        animationSpec = tween(300, easing = PremiumEasing.elegant),
        label = "glowAlpha"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "iconColor"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (isActive) accentColor.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .drawBehind {
                    if (glowAlpha > 0) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            radius = size.toPx() * 0.8f
                        )
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
        
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor.copy(alpha = if (enabled) 0.8f else 0.4f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FLOATING ACTION BUTTON - MORPHING STYLE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * FAB that morphs between icons with smooth rotation
 */
@Composable
fun MorphingFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    isExpanded: Boolean = false,
    expandedIcon: ImageVector = Icons.Default.Close,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val haptic = LocalHapticFeedback.current
    
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabRotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabScale"
    )
    
    FloatingActionButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .graphicsLayer { rotationZ = rotation },
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Crossfade(
            targetState = isExpanded,
            animationSpec = tween(200),
            label = "fabIcon"
        ) { expanded ->
            Icon(
                imageVector = if (expanded) expandedIcon else icon,
                contentDescription = null
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB CARD - SWIPE & 3D STACK ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Browser tab card with 3D stack effect and swipe-to-close
 */
@Composable
fun PremiumTabCard(
    modifier: Modifier = Modifier,
    title: String,
    url: String,
    favicon: @Composable (() -> Unit)? = null,
    isActive: Boolean = false,
    stackIndex: Int = 0,
    maxStack: Int = 3,
    onClose: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tabOffsetX"
    )
    
    val stackScale = 1f - (stackIndex * 0.05f).coerceIn(0f, 0.2f)
    val stackOffsetY = (stackIndex * 8).dp
    val stackAlpha = 1f - (stackIndex * 0.15f).coerceIn(0f, 0.5f)
    
    val elevation by animateFloatAsState(
        targetValue = if (isActive) 8f else 2f,
        animationSpec = tween(200),
        label = "tabElevation"
    )
    
    AnimatedVisibility(
        visible = !isDismissed,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Card(
            modifier = modifier
                .offset { IntOffset(animatedOffsetX.toInt(), 0) }
                .offset(y = stackOffsetY)
                .scale(stackScale)
                .alpha(stackAlpha)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX) > 150) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isDismissed = true
                                scope.launch {
                                    delay(200)
                                    onClose()
                                }
                            } else {
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                }
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(elevation.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favicon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    favicon?.invoke() ?: Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                // Close button with animation
                var closePressed by remember { mutableStateOf(false) }
                val closeScale by animateFloatAsState(
                    targetValue = if (closePressed) 0.8f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "closeScale"
                )
                
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDismissed = true
                        scope.launch {
                            delay(200)
                            onClose()
                        }
                    },
                    modifier = Modifier.scale(closeScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Swipe progress indicator
            if (abs(offsetX) > 50) {
                val swipeProgress = (abs(offsetX) / 150f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            if (offsetX > 0) Color(0xFF4CAF50).copy(alpha = swipeProgress)
                            else Color(0xFFF44336).copy(alpha = swipeProgress)
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// URL BAR - PREMIUM SEARCH INTERACTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Premium URL bar with focus animations and security indicator
 */
@Composable
fun PremiumUrlBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecure: Boolean = true,
    isLoading: Boolean = false,
    loadingProgress: Float = 0f,
    placeholder: String = "Search or enter URL"
) {
    var isFocused by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val elevation by animateFloatAsState(
        targetValue = if (isFocused) 8f else 2f,
        animationSpec = tween(200, easing = PremiumEasing.elegant),
        label = "urlBarElevation"
    )
    
    val borderWidth by animateFloatAsState(
        targetValue = if (isFocused) 2f else 0f,
        animationSpec = tween(200),
        label = "borderWidth"
    )
    
    val securityIconAlpha by animateFloatAsState(
        targetValue = if (value.isNotEmpty()) 1f else 0f,
        animationSpec = tween(200),
        label = "securityAlpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(elevation.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = borderWidth.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Security/Search icon
            AnimatedContent(
                targetState = if (value.isEmpty()) "search" else if (isSecure) "secure" else "insecure",
                transitionSpec = {
                    scaleIn(animationSpec = spring()) + fadeIn() togetherWith
                            scaleOut(animationSpec = spring()) + fadeOut()
                },
                label = "urlIcon"
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        "secure" -> Icons.Default.Lock
                        "insecure" -> Icons.Default.Warning
                        else -> Icons.Default.Search
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(if (state == "search") 0.6f else securityIconAlpha),
                    tint = when (state) {
                        "secure" -> Color(0xFF4CAF50)
                        "insecure" -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // URL input
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && !isFocused) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            
            // Clear button
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange("")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Loading progress bar at bottom
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(2.dp)
            ) {
                if (loadingProgress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadingProgress)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                    )
                } else {
                    // Indeterminate
                    val infiniteTransition = rememberInfiniteTransition(label = "urlProgress")
                    val progressOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "progressOffset"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .offset(x = (progressOffset * 300).dp)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.primary,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOTTOM NAVIGATION - FLUID INDICATOR
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bottom navigation with fluid indicator animation
 */
@Composable
fun FluidBottomNavigation(
    items: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorOffset"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Box {
            // Fluid indicator
            val itemWidth = 1f / items.size
            Box(
                modifier = Modifier
                    .fillMaxWidth(itemWidth)
                    .height(3.dp)
                    .offset(x = with(density) { (indicatorOffset * (1f / items.size) * 400).dp })
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                indicatorColor,
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                    )
            )
            
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, (icon, label) ->
                    val isSelected = index == selectedIndex
                    
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "navIconScale$index"
                    )
                    
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) indicatorColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "navIconColor$index"
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onItemSelected(index)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(iconScale),
                            tint = iconColor
                        )
                        
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = indicatorColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PULL TO REFRESH - PREMIUM ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PremiumPullToRefreshIndicator(
    pullProgress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val rotation by animateFloatAsState(
        targetValue = pullProgress * 360f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pullRotation"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = pullProgress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f),
        label = "pullScale"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .graphicsLayer {
                rotationZ = if (isRefreshing) refreshRotation else rotation
            }
            .drawBehind {
                val strokeWidth = 3.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                
                // Track
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
                
                // Progress arc
                val sweepAngle = if (isRefreshing) 270f else pullProgress * 270f
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Arrow or check icon
        AnimatedContent(
            targetState = isRefreshing,
            transitionSpec = {
                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
            },
            label = "refreshIcon"
        ) { refreshing ->
            if (refreshing) {
                // Show nothing, just the spinner
            } else {
                Icon(
                    imageVector = if (pullProgress >= 1f) Icons.Default.Check else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = -rotation },
                    tint = color
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PAGE TRANSITION ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════════

enum class PageTransition {
    SLIDE_HORIZONTAL,
    SLIDE_VERTICAL,
    FADE,
    SCALE,
    SHARED_AXIS_X,
    SHARED_AXIS_Y,
    SHARED_AXIS_Z
}

fun getPageTransitionEnter(transition: PageTransition): EnterTransition {
    return when (transition) {
        PageTransition.SLIDE_HORIZONTAL -> slideInHorizontally { it } + fadeIn()
        PageTransition.SLIDE_VERTICAL -> slideInVertically { it } + fadeIn()
        PageTransition.FADE -> fadeIn(animationSpec = tween(300))
        PageTransition.SCALE -> scaleIn(initialScale = 0.9f) + fadeIn()
        PageTransition.SHARED_AXIS_X -> slideInHorizontally { it / 2 } + fadeIn()
        PageTransition.SHARED_AXIS_Y -> slideInVertically { it / 2 } + fadeIn()
        PageTransition.SHARED_AXIS_Z -> scaleIn(initialScale = 0.85f) + fadeIn(tween(200))
    }
}

fun getPageTransitionExit(transition: PageTransition): ExitTransition {
    return when (transition) {
        PageTransition.SLIDE_HORIZONTAL -> slideOutHorizontally { -it } + fadeOut()
        PageTransition.SLIDE_VERTICAL -> slideOutVertically { -it } + fadeOut()
        PageTransition.FADE -> fadeOut(animationSpec = tween(300))
        PageTransition.SCALE -> scaleOut(targetScale = 1.1f) + fadeOut()
        PageTransition.SHARED_AXIS_X -> slideOutHorizontally { -it / 2 } + fadeOut()
        PageTransition.SHARED_AXIS_Y -> slideOutVertically { -it / 2 } + fadeOut()
        PageTransition.SHARED_AXIS_Z -> scaleOut(targetScale = 1.1f) + fadeOut(tween(200))
    }
}
