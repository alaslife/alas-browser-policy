package com.sun.alasbrowser.utils

import android.content.Context
import android.webkit.WebView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

class Media3SessionManager(
    private val context: Context,
    private val webView: WebView
) {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    
    fun initialize(): MediaSession? {
        enableNativeMediaSession()
        
        return try {
            val player = ExoPlayer.Builder(context)
                .build()
                .apply {
                    playWhenReady = false
                }
            
            exoPlayer = player
            
            val session = MediaSession.Builder(context, player)
                .build()
            
            mediaSession = session
            session
        } catch (e: Exception) {
 
            null
        }
    }
    
    private fun enableNativeMediaSession() {
        webView.evaluateJavascript("""
            (function() {
                if (!('mediaSession' in navigator)) {
                 
                    return;
                }
                
              
                
                const actions = [
                    ['play', () => {
                        const media = document.querySelector('video, audio');
                        if (media) media.play();
                    }],
                    ['pause', () => {
                        const media = document.querySelector('video, audio');
                        if (media) media.pause();
                    }],
                    ['seekbackward', (details) => {
                        const media = document.querySelector('video, audio');
                        if (media) media.currentTime = Math.max(0, media.currentTime - (details.seekOffset || 10));
                    }],
                    ['seekforward', (details) => {
                        const media = document.querySelector('video, audio');
                        if (media) media.currentTime = Math.min(media.duration, media.currentTime + (details.seekOffset || 10));
                    }],
                    ['seekto', (details) => {
                        const media = document.querySelector('video, audio');
                        if (media && details.seekTime !== undefined) media.currentTime = details.seekTime;
                    }],
                    ['previoustrack', () => {
                     
                    }],
                    ['nexttrack', () => {
                
                    }],
                    ['stop', () => {
                        const media = document.querySelector('video, audio');
                        if (media) {
                            media.pause();
                            media.currentTime = 0;
                        }
                    }]
                ];
                
                actions.forEach(([action, handler]) => {
                    try {
                        navigator.mediaSession.setActionHandler(action, handler);
                    } catch (e) {
                    
                    }
                });
                
              
            })();
        """.trimIndent(), null)
    }

}
