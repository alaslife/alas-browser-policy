package com.sun.alasbrowser.media

import android.content.Context
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView

class Media3PlayerManager(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playerView: PlayerView? = null
    
    @Suppress("unused")
    fun initializePlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(context)
                )
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        
        exoPlayer = player
        
        mediaSession = MediaSession.Builder(context, player)
            .build()
        
        return player
    }
    
    @Suppress("unused")
    fun createPlayerView(): PlayerView {
        val view = PlayerView(context).apply {
            player = exoPlayer
            useController = true
            controllerAutoShow = true
            controllerHideOnTouch = true
            controllerShowTimeoutMs = 3000
            keepScreenOn = true
            
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        playerView = view
        return view
    }
    
    @Suppress("unused")
    fun playMedia(url: String) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(url.toUri())
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun play() {
        exoPlayer?.play()
    }
    
    @Suppress("unused")
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }
    
    @Suppress("unused")
    fun setFullscreen(fullscreen: Boolean) {
        playerView?.apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = if (fullscreen) {
                @Suppress("DEPRECATION")
                (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
            } else {
                @Suppress("DEPRECATION")
                android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
    
    @Suppress("unused")
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    
    @Suppress("unused")
    fun getDuration(): Long = exoPlayer?.duration ?: 0L
    
    @Suppress("unused")
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true
    
    @Suppress("unused")
    fun addListener(listener: Player.Listener) {
        exoPlayer?.addListener(listener)
    }
    
    @Suppress("unused")
    fun removeListener(listener: Player.Listener) {
        exoPlayer?.removeListener(listener)
    }
    
    @Suppress("unused")
    fun getMediaSession(): MediaSession? = mediaSession
    
    @Suppress("unused")
    fun release() {
        mediaSession?.release()
        mediaSession = null
        
        exoPlayer?.release()
        exoPlayer = null
        
        playerView = null
    }
}
