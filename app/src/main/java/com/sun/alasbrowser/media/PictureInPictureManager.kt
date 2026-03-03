package com.sun.alasbrowser.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder

import android.util.Rational
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sun.alasbrowser.service.MediaPlaybackService
import com.sun.alasbrowser.utils.BackgroundPlaybackInjector

/**
 * Manages Picture-in-Picture mode with media playback integration
 * Similar to Flutter's floating + audio_service packages
 */
class PictureInPictureManager(
    private val activity: Activity,
    private val webView: WebView
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PiPManager"
    }
    
    private var mediaService: MediaPlaybackService? = null
    private var isInPipMode = false
    private var isPipAvailable = false
    private var currentVideoTitle: String = ""
    private var currentVideoUrl: String = ""
    
    // Callbacks
    private var onPipEnter: (() -> Unit)? = null
    private var onPipExit: (() -> Unit)? = null
    private var onPlayStateChanged: ((Boolean) -> Unit)? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MediaPlaybackService.LocalBinder
            mediaService = binder?.getService()
         
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            mediaService = null

        }
    }
    
    init {
        checkPipAvailability()
        bindToMediaService()
    }
    
    /**
     * Check if PiP is available on this device
     */
    private fun checkPipAvailability() {
        isPipAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else {
            false
        }
      
    }
    
    /**
     * Bind to media playback service
     */
    private fun bindToMediaService() {
        val serviceIntent = Intent(activity, MediaPlaybackService::class.java)
        val bindIntent = Intent(activity, MediaPlaybackService::class.java).apply {
            action = "LOCAL_BIND"
        }
        activity.startService(serviceIntent)
        activity.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Set callbacks
     */
    fun setOnPipEnterListener(callback: () -> Unit) {
        onPipEnter = callback
    }
    
    fun setOnPipExitListener(callback: () -> Unit) {
        onPipExit = callback
    }
    
    fun setOnPlayStateChangedListener(callback: (Boolean) -> Unit) {
        onPlayStateChanged = callback
    }
    
    /**
     * Enable PiP immediately (for manual trigger)
     */
    fun enablePipImmediately(
        aspectRatio: Rational = Rational(16, 9),
        title: String = currentVideoTitle
    ): Boolean {
        if (!isPipAvailable || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      
            return false
        }
        
        currentVideoTitle = title
        return enterPictureInPictureMode(aspectRatio)
    }
    
    /**
     * Enable PiP on app leave (automatic)
     */
    fun enablePipOnLeave(
        aspectRatio: Rational = Rational(16, 9),
        title: String = currentVideoTitle
    ) {
        currentVideoTitle = title
        // This will be triggered by onUserLeaveHint in Activity
    }
    
    /**
     * Actually enter PiP mode
     */
    private fun enterPictureInPictureMode(aspectRatio: Rational): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setAutoEnterEnabled(true)
                            setSeamlessResizeEnabled(true)
                        }
                    }
                    .build()
                
                val result = activity.enterPictureInPictureMode(params)
                if (result) {
                    isInPipMode = true
                    onPipEnter?.invoke()
                    // Continue playback in background
                    ensurePlaybackContinues()
                }
                return result
            } catch (e: Exception) {
             
                return false
            }
        }
        return false
    }
    
    /**
     * Exit PiP mode
     */
    fun exitPipMode() {
        isInPipMode = false
        onPipExit?.invoke()
    }
    
    /**
     * Handle PiP mode change from Activity
     */
    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        isInPipMode = isInPictureInPictureMode
        
        if (isInPictureInPictureMode) {
 
            onPipEnter?.invoke()
            ensurePlaybackContinues()
        } else {
    
            onPipExit?.invoke()
        }
    }
    
    /**
     * Ensure playback continues in PiP/background
     */
    private fun ensurePlaybackContinues() {
        BackgroundPlaybackInjector.enforcePlayback(webView)
        
        // Also ensure media service continues
        if (mediaService?.isPlaying() == false && currentVideoUrl.isNotEmpty()) {
            mediaService?.resume()
        }
    }
    
    /**
     * Set current video info for PiP metadata
     */
    fun setCurrentVideo(url: String, title: String) {
        currentVideoUrl = url
        currentVideoTitle = title
    }
    
    /**
     * Play video with PiP support
     */
    fun playVideo(url: String, title: String) {
        setCurrentVideo(url, title)
        mediaService?.playVideo(url, title)
        onPlayStateChanged?.invoke(true)
    }
    
    /**
     * Pause video
     */
    fun pause() {
        BackgroundPlaybackInjector.pauseVideo(webView)
        mediaService?.pause()
        onPlayStateChanged?.invoke(false)
    }
    
    /**
     * Resume video
     */
    fun resume() {
        BackgroundPlaybackInjector.playVideo(webView)
        mediaService?.resume()
        onPlayStateChanged?.invoke(true)
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (mediaService?.isPlaying() == true) {
            pause()
        } else {
            resume()
        }
    }
    
    /**
     * Check if currently in PiP mode
     */
    fun isInPipMode(): Boolean = isInPipMode
    
    /**
     * Check if PiP is available
     */
    fun isPipAvailable(): Boolean = isPipAvailable
    
    /**
     * Check if video is playing
     */
    fun isPlaying(): Boolean = mediaService?.isPlaying() == true
    
    /**
     * Handle user leaving the app (for auto PiP)
     */
    fun onUserLeaveHint() {
        if (!isInPipMode && isPlaying()) {

            enablePipImmediately()
        }
    }
    
    /**
     * Lifecycle event: onPause
     */
    override fun onPause(owner: LifecycleOwner) {
        if (isInPipMode) {
            // Keep playing in PiP
            ensurePlaybackContinues()
        }
    }
    
    /**
     * Lifecycle event: onStop
     */
    override fun onStop(owner: LifecycleOwner) {
        if (isInPipMode) {
            // Keep playing in PiP
            ensurePlaybackContinues()
        }
    }
    
    /**
     * Clean up
     */
    fun dispose() {
        try {
            if (mediaService != null) {
                activity.unbindService(serviceConnection)
                mediaService = null
            }
        } catch (e: Exception) {

        }
    }
}

/**
 * PiP status enum (similar to Flutter's PipStatus)
 */
enum class PipStatus {
    ENABLED,
    DISABLED,
    UNAVAILABLE,
    AUTOMATIC
}
