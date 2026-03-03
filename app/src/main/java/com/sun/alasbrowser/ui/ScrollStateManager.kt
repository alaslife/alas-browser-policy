package com.sun.alasbrowser.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.ScrollingBehaviour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val SNAP_SPRING = SpringSpec<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

@Immutable
data class BarUiState(
    val isVisible: Boolean = true,
    val isScrollToTopVisible: Boolean = false
) {
    companion object {
        val Default = BarUiState()
    }
}

@Immutable
data class ScrollMetrics(
    val scrollY: Int = 0,
    val scrollRange: Int = 0,
    val scrollExtent: Int = 0
) {
    companion object {
        val Default = ScrollMetrics()
    }
}

/**
 * Opera/Chrome-style scroll hide/show for the top bar.
 *
 * State machine: TOUCH → FLING → SETTLING → IDLE
 *  - TOUCH:    1:1 proportional tracking (finger on screen)
 *  - FLING:    damped tracking from scroll events after finger-up
 *  - SETTLING: spring animation to endpoint; scroll callbacks ignored
 *  - IDLE:     bar at rest, waiting for next touch
 *
 * Content uses `graphicsLayer { translationY }` driven by progress,
 * so the WebView is never re-measured → scrollY never jumps.
 */
@Stable
class ScrollStateManager(
    @Suppress("UNUSED_PARAMETER") density: Float,
    private val scope: CoroutineScope
) {
    private enum class TrackingMode { IDLE, TOUCH, FLING, SETTLING }

    /* 0 = bar fully visible, 1 = bar fully hidden */
    private val _progressState = mutableFloatStateOf(0f)
    private val _debouncedProgressState = mutableFloatStateOf(0f)

    private val _uiState = MutableStateFlow(BarUiState.Default)
    val uiState: StateFlow<BarUiState> = _uiState.asStateFlow()

    private val _scrollMetrics = MutableStateFlow(ScrollMetrics.Default)
    val scrollMetrics: StateFlow<ScrollMetrics> = _scrollMetrics.asStateFlow()

    private var snapJob: Job? = null
    private var flingSettleJob: Job? = null
    private var lastScrollY = 0
    private var isSearchBarOpen = false
    private var mode = TrackingMode.IDLE

    // Velocity tracking
    private var lastScrollTimeNs = 0L
    private var smoothedVelocity = 0f   // px/s, positive = scroll down
    private var lastDirection = 0       // -1 = up, 0 = neutral, 1 = down

    private var smoothedDy = 0f
    private var accumulatedReverseScroll = 0f
    private val reverseThreshold = 8f // Reduced for more immediate response
    private var gestureAccumulatedDy = 0f
    private var lastGestureTimeNs = 0L
    private var gestureVelocityPxPerS = 0f
    private var filteredGestureDy = 0f
    private var lastGestureFrameNs = 0L

    /** Top-bar height in px — set once from onGloballyPositioned. */
    var topBarHeightPx: Float = 0f

    /** Bottom-bar height in px — needed for HIDE_BOTH snapping. */
    var bottomBarHeightPx: Float = 0f

    /**
     * Called when user changes scrolling behavior.
     * Forces toolbars to immediately reset to visible for instant visual feedback.
     */
    fun updateBehavior(newBehavior: ScrollingBehaviour) {
        // Force reset to visible - instant visual feedback
        snapJob?.cancel()
        flingSettleJob?.cancel()

        // Reset progress to 0 (fully visible)
        setProgressSafely(0f)
        _debouncedProgressState.floatValue = 0f
        
        // Reset state machine
        mode = TrackingMode.IDLE
        smoothedVelocity = 0f
        smoothedDy = 0f
        accumulatedReverseScroll = 0f
        lastDirection = 0
        
        // Update UI visibility
        _uiState.update { BarUiState.Default }
    }

    /**
     * Handle scroll from nested scroll connection for non-scrollable pages.
     * This ensures scroll gestures work even when WebView content doesn't scroll (like modals).
     */
    fun onNestedScroll(delta: Float) {
        if (isGestureToolbarMode()) return
        if (mode == TrackingMode.SETTLING || mode == TrackingMode.IDLE) {
            if (delta != 0f) {
                mode = TrackingMode.TOUCH
            }
        }
        
        if (mode == TrackingMode.TOUCH && topBarHeightPx > 0f) {
            val deltaProgress = delta / topBarHeightPx
            val p = _progressState.floatValue
            val dampenedDelta = dampenDelta(p, deltaProgress)
            val newProgress = (p + dampenedDelta).coerceIn(0f, 1f)
            setProgressSafely(newProgress)
            syncVisibility()
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun setProgressSafely(value: Float) {
        val clamped = value.coerceIn(0f, 1f)

        // kill microscopic noise that causes shimmer
        if (kotlin.math.abs(clamped - _progressState.floatValue) < 0.002f) return

        _progressState.floatValue = clamped
        _debouncedProgressState.floatValue = clamped
    }

    private fun dampenDelta(p: Float, delta: Float): Float {
        return when {
            p == 0f && delta < 0 -> 0f
            p == 1f && delta > 0 -> 0f
            p < 0.05f && delta < 0 -> delta * 0.2f
            p > 0.95f && delta > 0 -> delta * 0.2f
            p !in 0.15f..0.85f -> delta * 0.35f
            else -> delta
        }
    }

    // ── public API ──────────────────────────────────────────────────────

    fun setSearchBarState(isOpen: Boolean) {
        if (isSearchBarOpen == isOpen) return
        isSearchBarOpen = isOpen
        if (isOpen) {
            mode = TrackingMode.SETTLING
            animateTo(0f)
        }
    }

    fun onTouchStateChanged(touching: Boolean) {
        if (isGestureToolbarMode()) {
            if (touching) onEdgeGestureStart() else onEdgeGestureEnd()
            return
        }
        if (touching) {
            // Finger down: cancel any running animation and enter TOUCH mode
            snapJob?.cancel()
            snapJob = null
            flingSettleJob?.cancel()
            flingSettleJob = null
            mode = TrackingMode.TOUCH
            lastScrollTimeNs = 0L
            smoothedVelocity = 0f
        } else {
            // Finger up: enter FLING mode and schedule debounced settle
            if (mode == TrackingMode.TOUCH) {
                mode = TrackingMode.FLING
                scheduleFlingSettle()
            }
        }
    }

    fun updateScrollState(scrollY: Int, scrollRange: Int, scrollExtent: Int) {
        val prevExtent = _scrollMetrics.value.scrollExtent
        _scrollMetrics.value = ScrollMetrics(scrollY, scrollRange, scrollExtent)
        // When viewport extent changes (WebView resized from bar animation),
        // reset scroll baseline so the resize-induced scroll shift reads as dy=0.
        // This breaks the feedback loop: resize → scroll adjust → progress change → resize…
        if (prevExtent != 0 && scrollExtent != prevExtent) {
            lastScrollY = scrollY
        }
        onScrollChange(scrollY)
    }

    fun onScrollChange(scrollY: Int) {
        if (scrollY < 0) return
        updateScrollToTop(scrollY)
        if (isGestureToolbarMode()) return

        val safeScrollY = scrollY.coerceAtLeast(0)
        val dy = safeScrollY - lastScrollY
        lastScrollY = safeScrollY

        if (kotlin.math.abs(dy) > 400) return

        // During settle, keep lastScrollY in sync but don't update progress
        // (avoids feedback loop from layout-induced scroll shifts)
        if (mode == TrackingMode.SETTLING || mode == TrackingMode.IDLE) return
        if (isSearchBarOpen) return
        if (dy == 0 || topBarHeightPx <= 0f) return

        // At bottom of page → don't hide further
        val metrics = _scrollMetrics.value
        if (metrics.scrollRange > 0) {
            val maxScroll = (metrics.scrollRange - metrics.scrollExtent).coerceAtLeast(0)
            val atBottom = scrollY >= maxScroll - 2

            if (atBottom && dy > 0) return
        }

        // Track velocity (smoothed EMA)
        val now = System.nanoTime()
        if (lastScrollTimeNs > 0L) {
            val dtS = (now - lastScrollTimeNs) / 1_000_000_000f
            if (dtS > 0.004f) { // ignore sub-4ms bursts
                val instantV = smoothedDy / dtS  // px/s
                val clampedV = instantV.coerceIn(-20_000f, 20_000f)
                smoothedVelocity = smoothedVelocity * 0.7f + clampedV * 0.3f
            }
        }
        lastScrollTimeNs = now

        // 1. Low-Pass Filter the incoming delta
        smoothedDy += (dy - smoothedDy) * 0.35f
        if (kotlin.math.abs(smoothedDy) < 0.5f) return

        // Update direction and handle reversal threshold
        val currentDir = if (smoothedDy > 0) 1 else -1

        if (lastDirection != 0 && currentDir != lastDirection) {
            accumulatedReverseScroll += kotlin.math.abs(smoothedDy)

            if (accumulatedReverseScroll < reverseThreshold) {
                lastScrollTimeNs = now
                return
            }
        } else {
            accumulatedReverseScroll = 0f
        }
        lastDirection = currentDir

        when (mode) {
            TrackingMode.TOUCH -> {
                var deltaProgress = smoothedDy / topBarHeightPx
                val p = _progressState.floatValue

                deltaProgress = dampenDelta(p, deltaProgress)

                val newProgress = p + deltaProgress
                setProgressSafely(newProgress)
                syncVisibility()
            }
            TrackingMode.FLING -> {
                // Ignore tiny oscillations during fling
                if (kotlin.math.abs(smoothedDy) < 3) return

                val absV = kotlin.math.abs(smoothedVelocity)

                // Fast fling → instant snap prediction calculation
                if (absV > 3000f) {
                    val p = _progressState.floatValue

                    val snapTarget = when {
                        smoothedVelocity > 0 && p > 0.1f -> 1f
                        smoothedVelocity < 0 && p < 0.9f -> 0f
                        else -> if (p > 0.5f) 1f else 0f
                    }

                    if (p != snapTarget) {
                        mode = TrackingMode.SETTLING
                        animateTo(snapTarget)
                    }
                    return
                }

                var deltaProgress = smoothedDy / topBarHeightPx
                val p = _progressState.floatValue

                // Velocity-aware bar hiding
                // Fling tracking accelerates with higher velocity
                val velocityMultiplier = (absV / 1000f).coerceIn(0.5f, 2.0f)
                deltaProgress *= velocityMultiplier

                deltaProgress = dampenDelta(p, deltaProgress)

                val limitedDelta = deltaProgress.coerceIn(-0.12f, 0.12f)
                val newProgress = p + limitedDelta
                setProgressSafely(newProgress)
                syncVisibility()

                // Re-arm fling settle debounce
                scheduleFlingSettle()
            }
            else -> { /* IDLE/SETTLING: already returned above */ }
        }
    }

    val scrollProgressState: State<Float>
        @Composable get() {
            return remember {
                derivedStateOf {
                    FastOutSlowInEasing.transform(_progressState.floatValue)
                }
            }
        }

    fun reset() {
        snapJob?.cancel()
        snapJob = null
        flingSettleJob?.cancel()
        flingSettleJob = null
        setProgressSafely(0f)
        _debouncedProgressState.floatValue = 0f
        mode = TrackingMode.IDLE
        lastScrollY = 0
        lastScrollTimeNs = 0L
        smoothedVelocity = 0f
        smoothedDy = 0f
        accumulatedReverseScroll = 0f
        lastDirection = 0
        gestureAccumulatedDy = 0f
        gestureVelocityPxPerS = 0f
        lastGestureTimeNs = 0L
        _uiState.update { BarUiState.Default }
    }

    /**
     * Gesture-driven toolbar control (Opera-style).
     * dragDy > 0 => finger moving down (show)
     * dragDy < 0 => finger moving up (hide)
     */
    fun onEdgeGestureDrag(dragDy: Float) {
        if (topBarHeightPx <= 0f) return

        if (mode != TrackingMode.TOUCH) {
            onEdgeGestureStart()
        }
        if (kotlin.math.abs(dragDy) < 1.1f) return

        val now = System.nanoTime()
        // Cap update frequency to avoid tiny oscillation when WebView emits very dense move events.
        if (lastGestureFrameNs != 0L && now - lastGestureFrameNs < 8_000_000L) return
        lastGestureFrameNs = now
        if (lastGestureTimeNs != 0L) {
            val dtS = ((now - lastGestureTimeNs) / 1_000_000_000f).coerceAtLeast(0.001f)
            val instantVelocity = dragDy / dtS
            gestureVelocityPxPerS = gestureVelocityPxPerS * 0.75f + instantVelocity * 0.25f
        }
        lastGestureTimeNs = now

        filteredGestureDy = filteredGestureDy * 0.72f + dragDy * 0.28f
        gestureAccumulatedDy += filteredGestureDy

        val p = _progressState.floatValue
        val edgeDamping = when {
            p < 0.08f && filteredGestureDy > 0f -> 0.45f
            p > 0.92f && filteredGestureDy < 0f -> 0.45f
            else -> 0.9f
        }
        val deltaProgress = ((-filteredGestureDy / topBarHeightPx) * edgeDamping).coerceIn(-0.045f, 0.045f)
        setProgressSafely((p + deltaProgress).coerceIn(0f, 1f))
        syncVisibility()
    }

    fun onEdgeGestureEnd() {
        if (mode != TrackingMode.TOUCH) return

        val p = _progressState.floatValue
        val thresholdPx = (topBarHeightPx * 0.14f).coerceAtLeast(14f)
        val fastSwipePxPerS = 900f
        val target = when {
            gestureVelocityPxPerS <= -fastSwipePxPerS -> 1f // fast upward flick hides
            gestureVelocityPxPerS >= fastSwipePxPerS -> 0f  // fast downward flick shows
            gestureAccumulatedDy <= -thresholdPx -> 1f // strong upward swipe => hide
            gestureAccumulatedDy >= thresholdPx -> 0f  // strong downward swipe => show
            p > 0.45f -> 1f
            else -> 0f
        }

        gestureAccumulatedDy = 0f
        gestureVelocityPxPerS = 0f
        lastGestureTimeNs = 0L
        filteredGestureDy = 0f
        lastGestureFrameNs = 0L
        mode = TrackingMode.SETTLING
        animateTo(target)
    }

    fun onEdgeGestureStart() {
        snapJob?.cancel()
        snapJob = null
        flingSettleJob?.cancel()
        flingSettleJob = null
        mode = TrackingMode.TOUCH
        gestureAccumulatedDy = 0f
        gestureVelocityPxPerS = 0f
        lastGestureTimeNs = 0L
        filteredGestureDy = 0f
        lastGestureFrameNs = 0L
    }

    // ── internals ───────────────────────────────────────────────────────

    /** Schedule settle after fling scroll events stop. */
    private fun scheduleFlingSettle() {
        flingSettleJob?.cancel()
        flingSettleJob = scope.launch {
         delay(150)

if (mode != TrackingMode.FLING) return@launch
if (kotlin.math.abs(smoothedDy) > 0.5f) return@launch

settle()
            flingSettleJob = null
        }
    }

    /** Spring to the nearest fully-visible / fully-hidden state. */
    private fun settle() {
        val p = _progressState.floatValue
        if (p == 0f || p == 1f) {
            mode = TrackingMode.IDLE
            return
        }

        // Direction-aware thresholds (easier to continue in the current direction)
        val target = when {
            lastDirection > 0 && p > 0.25f -> 1f   // was scrolling down → bias toward hide
            lastDirection < 0 && p < 0.75f -> 0f   // was scrolling up → bias toward show
            p > 0.5f -> 1f                          // neutral: use midpoint
            else -> 0f
        }

        mode = TrackingMode.SETTLING
        animateTo(target)
    }

    private fun animateTo(target: Float) {
        snapJob?.cancel()
        flingSettleJob?.cancel()
        flingSettleJob = null
        snapJob = scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = _progressState.floatValue,
                targetValue = target,
                animationSpec = SNAP_SPRING
            ) { value, _ ->
                setProgressSafely(value)
            }
            snapJob = null
            mode = TrackingMode.IDLE
            syncVisibility()
        }
    }

    private fun syncVisibility() {
        val visible = _progressState.floatValue < 0.5f
        _uiState.update {
            if (it.isVisible == visible) it else it.copy(isVisible = visible)
        }
    }

    private fun updateScrollToTop(scrollY: Int) {
        val show = scrollY > 1500
        _uiState.update { s ->
            if (s.isScrollToTopVisible == show) s else s.copy(isScrollToTopVisible = show)
        }
    }

    private fun isGestureToolbarMode(): Boolean = true
}

@Composable
fun rememberScrollStateManager(): ScrollStateManager {
    val density = LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(density, scope) { ScrollStateManager(density.density, scope) }
}

@Immutable
data class AnimatedBarOffsets(
    val topBarOffset: State<Dp>,
    val bottomBarOffset: State<Dp>,
    val bottomBarAlpha: State<Float>,
    val shouldShowBars: State<Boolean>,
    val scrollProgress: State<Float>
)

@Composable
fun rememberAnimatedBarOffsets(
    scrollStateManager: ScrollStateManager,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    preferences: BrowserPreferences
): AnimatedBarOffsets {

    val progressState = scrollStateManager.scrollProgressState
    val topBarOffset = remember(topBarHeight, preferences) {
        derivedStateOf {
            if (preferences.scrollingBehaviour != ScrollingBehaviour.NEVER_HIDE) {
                -topBarHeight * progressState.value.coerceIn(0f, 1f)
            } else {
                0.dp
            }
        }
    }

    val bottomBarOffset = remember(bottomBarHeight, preferences) {
        derivedStateOf {
            if (preferences.scrollingBehaviour == ScrollingBehaviour.HIDE_BOTH) {
                bottomBarHeight * progressState.value.coerceIn(0f, 1f)
            } else {
                0.dp
            }
        }
    }

    val bottomBarAlpha = remember(preferences) {
        derivedStateOf {
            if (preferences.scrollingBehaviour == ScrollingBehaviour.HIDE_BOTH) {
                1f - progressState.value.coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    val shouldShowBars = remember(preferences) {
        derivedStateOf { 
            if (preferences.scrollingBehaviour != ScrollingBehaviour.NEVER_HIDE) progressState.value < 0.95f else true
        }
    }

    return AnimatedBarOffsets(
        topBarOffset = topBarOffset,
        bottomBarOffset = bottomBarOffset,
        bottomBarAlpha = bottomBarAlpha,
        shouldShowBars = shouldShowBars,
        scrollProgress = progressState
    )
}
