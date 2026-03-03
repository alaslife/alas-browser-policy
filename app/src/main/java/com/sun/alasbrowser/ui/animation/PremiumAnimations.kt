package com.sun.alasbrowser.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════════════════
// PREMIUM EASING CURVES - Comet Browser Style
// ═══════════════════════════════════════════════════════════════════════════════

object PremiumEasing {
    // Ultra-smooth ease-out for UI elements
    val smoothOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    
    // Snappy spring-like feel
    val snappy = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    
    // Elegant deceleration
    val elegant = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    
    // Bounce effect
    val bounce = CubicBezierEasing(0.68f, -0.6f, 0.32f, 1.6f)
    
    // Material3 emphasized
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    // iOS-like spring
    val iosSpring = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
}

// ═══════════════════════════════════════════════════════════════════════════════
// PREMIUM SPRING SPECS
// ═══════════════════════════════════════════════════════════════════════════════

object PremiumSpring {
    val bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    
    val gentle = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )
    
    val quick = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOW EFFECTS - Premium UI Enhancement
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.glowEffect(
    color: Color = Color.Cyan,
    radius: Dp = 20.dp,
    alpha: Float = 0.6f
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = alpha * 0.5f,
        targetValue = alpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    return this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = glowAlpha),
                    color.copy(alpha = glowAlpha * 0.5f),
                    Color.Transparent
                )
            ),
            radius = radius.toPx()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RIPPLE BURST EFFECT - Premium Touch Feedback
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun RippleBurstEffect(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onRipple: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var rippleCenter by remember { mutableStateOf(Offset.Zero) }
    var showRipple by remember { mutableStateOf(false) }
    val rippleProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        rippleCenter = offset
                        showRipple = true
                        scope.launch {
                            rippleProgress.snapTo(0f)
                            rippleProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(600, easing = PremiumEasing.elegant)
                            )
                            showRipple = false
                        }
                        onRipple()
                    }
                )
            }
            .drawWithContent {
                drawContent()
                if (showRipple) {
                    val maxRadius = hypot(size.width, size.height)
                    drawCircle(
                        color = color.copy(alpha = 0.3f * (1f - rippleProgress.value)),
                        radius = maxRadius * rippleProgress.value,
                        center = rippleCenter
                    )
                }
            }
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MORPHING BUTTON - State Machine Animation
// ═══════════════════════════════════════════════════════════════════════════════

enum class ButtonState { Idle, Pressed, Loading, Success, Error }

@Composable
fun rememberMorphingButtonState(): MutableState<ButtonState> {
    return remember { mutableStateOf(ButtonState.Idle) }
}

@Composable
fun Modifier.morphingButton(
    state: ButtonState,
    idleColor: Color = MaterialTheme.colorScheme.primary,
    pressedColor: Color = MaterialTheme.colorScheme.primaryContainer,
    successColor: Color = Color(0xFF4CAF50),
    errorColor: Color = Color(0xFFF44336)
): Modifier {
    val scale by animateFloatAsState(
        targetValue = when (state) {
            ButtonState.Idle -> 1f
            ButtonState.Pressed -> 0.95f
            ButtonState.Loading -> 0.98f
            ButtonState.Success -> 1.05f
            ButtonState.Error -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "morphScale"
    )
    
    val color by animateColorAsState(
        targetValue = when (state) {
            ButtonState.Idle -> idleColor
            ButtonState.Pressed -> pressedColor
            ButtonState.Loading -> idleColor.copy(alpha = 0.7f)
            ButtonState.Success -> successColor
            ButtonState.Error -> errorColor
        },
        animationSpec = tween(300, easing = PremiumEasing.elegant),
        label = "morphColor"
    )
    
    return this
        .scale(scale)
        .background(color, RoundedCornerShape(12.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHIMMER LOADING EFFECT - Skeleton UI
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.shimmerEffect(
    baseColor: Color = Color.LightGray.copy(alpha = 0.3f),
    highlightColor: Color = Color.White.copy(alpha = 0.6f)
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    return this.drawWithContent {
        drawContent()
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(size.width * shimmerOffset, 0f),
            end = Offset(size.width * (shimmerOffset + 1f), size.height)
        )
        drawRect(brush = brush)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ORBIT LOADING ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OrbitLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 3,
    size: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        repeat(dotCount) { index ->
            val delay = index * (1000 / dotCount)
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation$index"
            )
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = PremiumEasing.smoothOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale$index"
            )
            
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .size(6.dp * scale)
                        .background(
                            color.copy(alpha = 0.7f + (0.3f * scale)),
                            CircleShape
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROGRESS RING - Premium Loading Indicator
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PremiumProgressRing(
    modifier: Modifier = Modifier,
    progress: Float = -1f, // -1 for indeterminate
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 4.dp,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progressRing")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = PremiumEasing.elegant),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepAngle"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
                
                // Progress arc
                if (progress < 0) {
                    // Indeterminate mode
                    rotate(rotation) {
                        drawArc(
                            color = color,
                            startAngle = 0f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                        )
                    }
                } else {
                    // Determinate mode
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ELASTIC BOUNCE MODIFIER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.elasticBounce(
    isActive: Boolean = false,
    bounceScale: Float = 1.1f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isActive) bounceScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elasticBounce"
    )
    return this.scale(scale)
}

// ═══════════════════════════════════════════════════════════════════════════════
// PARALLAX TILT EFFECT - 3D Card Feel
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.parallaxTilt(
    maxTilt: Float = 10f
): Modifier {
    var rotationX by remember { mutableFloatStateOf(0f) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    
    val animatedRotationX by animateFloatAsState(
        targetValue = rotationX,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tiltX"
    )
    val animatedRotationY by animateFloatAsState(
        targetValue = rotationY,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tiltY"
    )
    
    return this
        .pointerInput(Unit) {
            detectDragGestures(
                onDrag = { change, _ ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    rotationY = ((change.position.x - centerX) / centerX) * maxTilt
                    rotationX = -((change.position.y - centerY) / centerY) * maxTilt
                },
                onDragEnd = {
                    rotationX = 0f
                    rotationY = 0f
                }
            )
        }
        .graphicsLayer {
            this.rotationX = animatedRotationX
            this.rotationY = animatedRotationY
            cameraDistance = 12f * density
        }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAGNETIC SNAP EFFECT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.magneticSnap(
    snapPoints: List<Float> = listOf(0f, 0.5f, 1f),
    onSnap: (Float) -> Unit = {}
): Modifier {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) offsetX else {
            snapPoints.minByOrNull { abs(it - offsetX) } ?: offsetX
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "magneticSnap",
        finishedListener = { finalValue ->
            if (!isDragging) {
                onSnap(finalValue)
            }
        }
    )
    
    return this
        .offset { IntOffset(animatedOffset.toInt(), 0) }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { isDragging = true },
                onDrag = { _, dragAmount ->
                    offsetX += dragAmount.x
                },
                onDragEnd = { isDragging = false },
                onDragCancel = { isDragging = false }
            )
        }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GRADIENT BORDER ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.animatedGradientBorder(
    colors: List<Color> = listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6),
        Color(0xFFD946EF),
        Color(0xFFF43F5E),
        Color(0xFF6366F1)
    ),
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 12.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "gradientBorder")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )
    
    return this.drawBehind {
        rotate(rotation) {
            drawRoundRect(
                brush = Brush.sweepGradient(colors),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = borderWidth.toPx())
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIQUID GLASS GRADIENT BORDER (Modern 2026 Version)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.liquidGlassBorder(
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 18.dp
): Modifier {

    // 🌈 Refined Premium Glass Colors
    val glassColors = listOf(
        Color(0xFF22D3EE),   // cyan glow
        Color(0xFFA78BFA),   // soft purple
        Color(0xFF60A5FA),   // cool blue
        Color(0xFF22D3EE)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")

    // 🔄 Slow premium rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 7000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )

    // ✨ Moving light reflection
    val shimmerShift by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerShift"
    )

    return this.drawBehind {

        val stroke = borderWidth.toPx()
        val radius = cornerRadius.toPx()

        // 🌟 Outer Soft Glow (depth effect)
        drawRoundRect(
            brush = Brush.sweepGradient(glassColors),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke * 2),
            alpha = 0.25f
        )

        // 🔮 Rotating Liquid Border
        rotate(rotation) {
            drawRoundRect(
                brush = Brush.sweepGradient(glassColors),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = stroke)
            )
        }

        // 💡 Glass Reflection Overlay
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * shimmerShift, size.height)
            ),
            cornerRadius = CornerRadius(radius)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PULSE INDICATOR - Notification Badge
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF43F5E),
    size: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Pulse ring
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha)
                .background(color, CircleShape)
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BREATHING EFFECT - Subtle UI Hint
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.breathingEffect(
    minAlpha: Float = 0.6f,
    maxAlpha: Float = 1f,
    duration: Int = 2000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )
    return this.alpha(alpha)
}

// ═══════════════════════════════════════════════════════════════════════════════
// STAGGERED REVEAL - List Animation
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StaggeredRevealItem(
    index: Int,
    visible: Boolean,
    staggerDelay: Long = 50L,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        if (visible) {
            delay(index * staggerDelay)
            isVisible = true
        } else {
            isVisible = false
        }
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = PremiumEasing.elegant),
        label = "staggerAlpha"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "staggerOffset"
    )
    
    Box(
        modifier = Modifier
            .alpha(alpha)
            .offset { IntOffset(0, offsetY.toInt()) }
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIQUID BLOB BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun LiquidBlobBackground(
    modifier: Modifier = Modifier,
    color1: Color = Color(0xFF6366F1),
    color2: Color = Color(0xFF8B5CF6),
    color3: Color = Color(0xFFD946EF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = PremiumEasing.smoothOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2"
    )
    
    Box(
        modifier = modifier
            .blur(60.dp)
            .drawBehind {
                drawCircle(
                    color = color1.copy(alpha = 0.6f),
                    radius = size.minDimension * 0.4f,
                    center = Offset(offset1, size.height * 0.3f)
                )
                drawCircle(
                    color = color2.copy(alpha = 0.5f),
                    radius = size.minDimension * 0.35f,
                    center = Offset(size.width - offset2, size.height * 0.5f)
                )
                drawCircle(
                    color = color3.copy(alpha = 0.4f),
                    radius = size.minDimension * 0.3f,
                    center = Offset(size.width * 0.5f, size.height - offset1)
                )
            }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// TYPEWRITER TEXT EFFECT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun rememberTypewriterState(
    text: String,
    charDelay: Long = 50L
): State<String> {
    val displayedText = remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText.value = ""
        text.forEachIndexed { index, _ ->
            delay(charDelay)
            displayedText.value = text.take(index + 1)
        }
    }
    
    return displayedText
}

// ═══════════════════════════════════════════════════════════════════════════════
// SWIPE ACTION ANIMATION HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

data class SwipeActionState(
    val offsetX: Float = 0f,
    val isRevealed: Boolean = false
)

@Composable
fun rememberSwipeActionState(): MutableState<SwipeActionState> {
    return remember { mutableStateOf(SwipeActionState()) }
}

@Composable
fun Modifier.swipeToReveal(
    state: MutableState<SwipeActionState>,
    revealThreshold: Float = 100f,
    maxSwipe: Float = 150f
): Modifier {
    val animatedOffset by animateFloatAsState(
        targetValue = state.value.offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swipeOffset"
    )
    
    return this
        .offset { IntOffset(animatedOffset.toInt(), 0) }
        .pointerInput(Unit) {
            detectDragGestures(
                onDrag = { _, dragAmount ->
                    val newOffset = (state.value.offsetX + dragAmount.x)
                        .coerceIn(-maxSwipe, 0f)
                    state.value = state.value.copy(offsetX = newOffset)
                },
                onDragEnd = {
                    val isRevealed = state.value.offsetX < -revealThreshold
                    state.value = state.value.copy(
                        offsetX = if (isRevealed) -maxSwipe else 0f,
                        isRevealed = isRevealed
                    )
                }
            )
        }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONFETTI EXPLOSION EFFECT
// ═══════════════════════════════════════════════════════════════════════════════

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val color: Color,
    val size: Float
)

@Composable
fun ConfettiExplosion(
    modifier: Modifier = Modifier,
    trigger: Boolean,
    particleCount: Int = 30,
    colors: List<Color> = listOf(
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFE66D),
        Color(0xFF95E1D3),
        Color(0xFFF38181)
    )
) {
    var particles by remember { mutableStateOf(emptyList<ConfettiParticle>()) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            particles = List(particleCount) {
                ConfettiParticle(
                    x = 0.5f,
                    y = 0.5f,
                    velocityX = (kotlin.random.Random.nextFloat() - 0.5f) * 2f,
                    velocityY = kotlin.random.Random.nextFloat() * -2f,
                    rotation = kotlin.random.Random.nextFloat() * 360f,
                    color = colors.random(),
                    size = kotlin.random.Random.nextFloat() * 10f + 5f
                )
            }
            
            repeat(60) {
                delay(16)
                particles = particles.map { p ->
                    p.copy(
                        x = p.x + p.velocityX * 0.02f,
                        y = p.y + p.velocityY * 0.02f + 0.01f,
                        rotation = p.rotation + 5f
                    )
                }.filter { it.y < 2f }
            }
            particles = emptyList()
        }
    }
    
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            particles.forEach { p ->
                rotate(p.rotation, Offset(size.width * p.x, size.height * p.y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(
                            size.width * p.x - p.size / 2,
                            size.height * p.y - p.size / 2
                        ),
                        size = Size(p.size, p.size)
                    )
                }
            }
        }
    )
}
