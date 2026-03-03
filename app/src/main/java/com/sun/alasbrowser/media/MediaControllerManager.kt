package com.sun.alasbrowser.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sun.alasbrowser.service.MediaPlaybackService

class MediaControllerManager(private val context: Context) {
    
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    fun initialize(onReady: (MediaController) -> Unit = {}) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.let { onReady(it) }
   
            } catch (e: Exception) {
       
            }
        }, MoreExecutors.directExecutor())
    }
    
    fun playMedia(
        uri: String,
        title: String,
        artist: String = "Alas life",
        artworkUri: String? = null
    ) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                artworkUri?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build()
        
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
        
        mediaController?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }
    
    fun play() {
        mediaController?.play()
    }
    
    fun pause() {
        mediaController?.pause()
    }
    
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }
    
    fun seekForward(offsetMs: Long = 10000) {
        mediaController?.let {
            val newPosition = it.currentPosition + offsetMs
            it.seekTo(newPosition)
        }
    }
    
    fun seekBackward(offsetMs: Long = 10000) {
        mediaController?.let {
            val newPosition = (it.currentPosition - offsetMs).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }
    
    fun addListener(listener: Player.Listener) {
        mediaController?.addListener(listener)
    }
    
    fun removeListener(listener: Player.Listener) {
        mediaController?.removeListener(listener)
    }
    
    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    
    fun getDuration(): Long = mediaController?.duration ?: 0L
    
    fun isPlaying(): Boolean = mediaController?.isPlaying ?: false
    
    fun getPlaybackState(): Int = mediaController?.playbackState ?: Player.STATE_IDLE
    
    fun release() {
        mediaController?.release()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        controllerFuture = null
    }
}
