package com.sun.alasbrowser.web

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.ViewCompat

/**
 * Custom WebView with lifecycle-aware safety checks.
 * 
 * STATE MACHINE:
 * - CREATED -> ALIVE -> PAUSED -> ALIVE -> ... -> DEAD
 * - Once DEAD, the WebView cannot be used and must be recreated
 */
@Suppress("DEPRECATION")
class AlasWebView(context: Context) : WebView(context) {
    
    companion object {
        private const val TAG = "AlasWebView"
    }
    
    enum class State {
        ALIVE,      // WebView is active and can be used
        PAUSED,     // WebView is paused but can be resumed
        DEAD        // WebView is destroyed or renderer crashed - MUST recreate
    }
    
    @Volatile
    private var state: State = State.ALIVE
    
    /** Check if WebView is usable */
    val isAlive: Boolean get() = state != State.DEAD
    
    /** Check if WebView has been destroyed */
    fun isDestroyed(): Boolean = state == State.DEAD
    
    /** Check if WebView is currently paused */
    fun isPaused(): Boolean = state == State.PAUSED
    
    // Callbacks
    var onScrollVelocity: ((Float) -> Unit)? = null
    var onScrollStart: (() -> Unit)? = null
    var onScrollEnd: (() -> Unit)? = null
    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    var onRecreationNeeded: (() -> Unit)? = null
    
    // Internal state
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScrolling = false
    private var isRestoringScroll = false
    private var scrollEndRunnable: Runnable? = null
    
    private val gestureDetector = android.view.GestureDetector(context, 
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                onScrollVelocity?.invoke(velocityY)
                return false
            }
        }
    )
    
    init {
        configureScrolling()
        configureRendering()
    }
    
    private fun configureScrolling() {
        isNestedScrollingEnabled = false
        ViewCompat.setNestedScrollingEnabled(this, false)
        overScrollMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            OVER_SCROLL_NEVER
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        setScrollbarFadingEnabled(true)
        isVerticalFadingEdgeEnabled = false
        isHorizontalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        isFocusable = true
        isFocusableInTouchMode = true
    }
    
    private fun configureRendering() {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true)
        }
        
        setBackgroundColor(android.graphics.Color.WHITE)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SAFE OPERATIONS - Always check state before proceeding
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Mark this WebView as dead. Called when renderer crashes.
     */
    fun markDead() {
        if (state != State.DEAD) {
            state = State.DEAD
            Log.w(TAG, "WebView marked as DEAD")
            clearCallbacks()
        }
    }
    
    /**
     * Safely pause the WebView.
     */
    fun safePause() {
        if (state == State.DEAD) return
        
        try {
            state = State.PAUSED
            stopLoading()
            pauseTimers()
            onPause()
            Log.d(TAG, "WebView paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error during pause - marking dead", e)
            markDead()
        }
    }
    
    /**
     * Safely resume the WebView.
     * @return true if resume succeeded, false if WebView is dead
     */
    fun safeResume(): Boolean {
        if (state == State.DEAD) {
            Log.w(TAG, "Cannot resume - WebView is DEAD")
            return false
        }
        
        try {
            onResume()
            resumeTimers()
            state = State.ALIVE
            
            // Force redraw to recover surface
            forceRedraw()
            
            Log.d(TAG, "WebView resumed")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during resume - marking dead", e)
            markDead()
            onRecreationNeeded?.invoke()
            return false
        }
    }
    
    /**
     * Force the WebView to redraw its content.
     * Helps recover from stale GPU surfaces.
     */
    fun forceRedraw() {
        if (state == State.DEAD) return
        
        try {
            visibility = INVISIBLE
            visibility = VISIBLE
            requestLayout()
            invalidate()
            
            // Delayed invalidation for stubborn surfaces
            mainHandler.postDelayed({
                if (state != State.DEAD) {
                    invalidate()
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error during forceRedraw", e)
        }
    }
    
    /**
     * Safely load a URL.
     */
    fun safeLoadUrl(url: String): Boolean {
        if (state == State.DEAD) {
            Log.w(TAG, "Cannot loadUrl - WebView is DEAD")
            return false
        }
        
        try {
            loadUrl(url)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading URL - marking dead", e)
            markDead()
            return false
        }
    }
    
    /**
     * Safely reload the page.
     */
    fun safeReload(): Boolean {
        if (state == State.DEAD) {
            Log.w(TAG, "Cannot reload - WebView is DEAD")
            return false
        }
        
        try {
            reload()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading - marking dead", e)
            markDead()
            return false
        }
    }
    
    /**
     * Safely evaluate JavaScript.
     */
    fun safeEvaluateJavascript(script: String, callback: ((String) -> Unit)? = null): Boolean {
        if (state == State.DEAD) {
            Log.w(TAG, "Cannot evaluateJavascript - WebView is DEAD")
            return false
        }
        
        try {
            evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating JS - marking dead", e)
            markDead()
            return false
        }
    }

    /**
     * Restore scroll position without triggering scroll callbacks.
     * Prevents race conditions during state restoration.
     */
    fun restoreScrollPosition(scrollY: Int) {
        if (state == State.DEAD) return
        
        isRestoringScroll = true
        scrollTo(0, scrollY)
        
        // Brief delay to ensure layout settles before re-enabling callbacks
        postDelayed({
            isRestoringScroll = false
        }, 150)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SCROLL HANDLING
    // ═══════════════════════════════════════════════════════════════
    
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        // Don't report scroll events during restoration
        if (isRestoringScroll) return

        if (!isScrolling) {
            isScrolling = true
            onScrollStart?.invoke()
        }

        scrollEndRunnable?.let { removeCallbacks(it) }
        scrollEndRunnable = Runnable {
            isScrolling = false
            onScrollEnd?.invoke()
        }
        postDelayed(scrollEndRunnable!!, 120)
    }
    
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (state == State.DEAD) return false
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouchStateChanged?.invoke(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onTouchStateChanged?.invoke(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == State.DEAD) return false
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
    
    fun getVerticalScrollRange(): Int = computeVerticalScrollRange()
    fun getVerticalScrollExtent(): Int = computeVerticalScrollExtent()
    
    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "Detached from window")
    }
    
    private fun clearCallbacks() {
        onScrollStart = null
        onScrollEnd = null
        onScrollVelocity = null
        onTouchStateChanged = null
        onRecreationNeeded = null
        scrollEndRunnable?.let { removeCallbacks(it) }
        scrollEndRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
    }
    
    override fun destroy() {
        Log.d(TAG, "destroy() called")
        state = State.DEAD
        
        try {
            stopLoading()
            clearCallbacks()
            
            webViewClient = android.webkit.WebViewClient()
            webChromeClient = null
            
            (parent as? ViewGroup)?.removeView(this)
            
            super.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error during destroy", e)
        }
    }
}
