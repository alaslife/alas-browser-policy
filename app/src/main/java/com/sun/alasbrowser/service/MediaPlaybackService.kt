package com.sun.alasbrowser.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.sun.alasbrowser.utils.YoutubeStreamExtractor

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var currentVideoUrl: String? = null
    private val binder = LocalBinder()

    companion object {
        private const val TAG = "MediaPlaybackService"
        private const val CHANNEL_ID = "media_playback_channel"
        private const val SEEK_OFFSET_MS = 10000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        YoutubeStreamExtractor.initialize()
    }

    private fun initializePlayerAndSession() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setPauseAtEndOfMediaItems(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_ENDED -> stop()
                            Player.STATE_READY -> Log.d(TAG, "Player ready")
                            Player.STATE_BUFFERING -> Log.d(TAG, "Player buffering")
                            Player.STATE_IDLE -> Log.d(TAG, "Player idle")
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Playback state changed: isPlaying=$isPlaying")
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        "seek_backward" -> {
                            val newPosition = (player!!.currentPosition - SEEK_OFFSET_MS).coerceAtLeast(0L)
                            player!!.seekTo(newPosition)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        "seek_forward" -> {
                            val duration = player!!.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                            val newPosition = (player!!.currentPosition + SEEK_OFFSET_MS).coerceAtMost(duration)
                            player!!.seekTo(newPosition)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) initializePlayerAndSession()
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == "LOCAL_BIND") {
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaSession == null) initializePlayerAndSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.pause()
        super.onTaskRemoved(rootIntent)
    }

    fun playVideo(videoUrl: String, title: String = "Video", thumbnail: String? = null) {
        if (mediaSession == null) initializePlayerAndSession()
        
        // If same URL and playing, just resume
        if (videoUrl == currentVideoUrl && player?.isPlaying == true) {
            Log.d(TAG, "Already playing same video")
            return
        }
        
        // If same URL but paused, resume instead of reloading
        if (videoUrl == currentVideoUrl && player?.isPlaying == false) {
            Log.d(TAG, "Resuming paused video")
            resume()
            return
        }

        currentVideoUrl = videoUrl
        
        if (YoutubeStreamExtractor.isYoutubeUrl(videoUrl)) {
            YoutubeStreamExtractor.extractStreamUrl(videoUrl) { streamUrl, videoTitle, videoThumbnail, author ->
                if (streamUrl != null) {
                    playStream(streamUrl, videoTitle ?: title, videoThumbnail ?: thumbnail, author ?: "YouTube")
                } else {
                    Log.e(TAG, "Failed to extract YouTube stream")
                }
            }
        } else {
            playStream(videoUrl, title, thumbnail, "Alas Browser")
        }
    }

    private fun playStream(streamUrl: String, title: String, thumbnail: String?, author: String) {
        Log.d(TAG, "Playing stream: $title by $author")
        
        player?.apply {
            // Stop current playback if any
            if (isPlaying) {
                stop()
            }
            
            // Clear old items
            clearMediaItems()
            
            // Build new media item with metadata
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(author)
                        .setArtworkUri(thumbnail?.toUri())
                        .setDisplayTitle(title)
                        .setSubtitle(author)
                        .build()
                )
                .build()
            
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    fun pause() = player?.pause()
    fun resume() = player?.play()
    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        currentVideoUrl = null
    }
    fun isPlaying(): Boolean = player?.isPlaying == true

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            // Release player first to stop decoding
            player?.let { p ->
                p.stop()
                p.clearMediaItems()
                p.release()
            }
            
            // Then release media session
            mediaSession?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media resources", e)
        } finally {
            player = null
            mediaSession = null
            currentVideoUrl = null
        }
        super.onDestroy()
    }
}