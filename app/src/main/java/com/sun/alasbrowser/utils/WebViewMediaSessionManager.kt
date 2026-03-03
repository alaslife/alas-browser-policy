package com.sun.alasbrowser.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.sun.alasbrowser.service.MediaPlaybackService
import java.util.Locale

/**
 * Connects WebView media playback to native MediaSession
 */
class WebViewMediaSessionManager(
    private val context: Context,
    private var webView: WebView?
) {

    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var isPaused = false
    private var mediaService: MediaPlaybackService? = null

    private var currentMediaUrl: String? = null
    private var isMediaPlaying = false
    private var isDestroyed = false
    private var lastPlaybackFingerprint: String? = null
    private var lastPlaybackStartMs: Long = 0L

    companion object {
        private const val TAG = "WebViewMediaSession"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaService = (service as? MediaPlaybackService.LocalBinder)?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaService = null
        }
    }

    fun initialize() {
        enableNativeMediaSession()
    }

    fun monitorPlayback() {
        if (isMonitoring) return
        isMonitoring = true

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isWebViewValid()) {
                    isMonitoring = false
                    return
                }
                
                if (!isPaused) {
                    syncMediaMetadata()
                    enforceBackgroundPlayback()
                }
                
                handler.postDelayed(this, 1000)
            }
        }, 500)
    }

    /**
     * Pause aggressive enforcement. 
     * Used when the tab is hidden but not destroyed.
     */
    fun pauseMonitoring() {
        isPaused = true
    }

    /**
     * Resume aggressive enforcement.
     * Used when the tab becomes active.
     */
    fun resumeMonitoring() {
        isPaused = false
    }

    fun destroy() {
        isDestroyed = true
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        stopMediaService()
        webView = null
    }

    private fun isWebViewValid(): Boolean {
        if (isDestroyed || webView == null) return false
        return try {
            webView?.url != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun enableNativeMediaSession() {
        if (!isWebViewValid()) return

        webView?.evaluateJavascript(
            """
            (function () {
                if (!('mediaSession' in navigator)) return;

                const media = () => document.querySelector('video, audio');

                ['play','pause','seekbackward','seekforward','seekto'].forEach(action => {
                    try {
                        navigator.mediaSession.setActionHandler(action, details => {
                            const m = media();
                            if (!m) return;

                            if (action === 'play') {
                                m.dataset.manuallyPaused = 'false';
                                m.play();
                            }
                            if (action === 'pause') {
                                m.dataset.manuallyPaused = 'true';
                                m.pause();
                            }
                            if (action === 'seekbackward') m.currentTime -= (details.seekOffset || 10);
                            if (action === 'seekforward') m.currentTime += (details.seekOffset || 10);
                            if (action === 'seekto') m.currentTime = details.seekTime || 0;
                        });
                    } catch (e) {}
                });
            })();
            """.trimIndent(),
            null
        )
    }

    private fun enforceBackgroundPlayback() {
        webView?.let { BackgroundPlaybackInjector.enforcePlayback(it) }
    }

    private fun syncMediaMetadata() {
        if (!isWebViewValid()) return

        webView?.evaluateJavascript(
            """
            (function() {
                const media = document.querySelector('video, audio');
                if (!media) return JSON.stringify({hasMedia:false});

                // For YouTube/YT Music, prefer location.href to get the video ID
                const isYT = location.hostname.includes('youtube.com');
                const url = isYT ? location.href : (media.currentSrc || media.src || location.href);

                return JSON.stringify({
                    hasMedia: true,
                    url: url,
                    title: document.title,
                    playing: !media.paused
                });
            })();
            """.trimIndent()
        ) { handleMediaUpdate(it) }
    }

    private fun handleMediaUpdate(result: String?) {
        if (result.isNullOrEmpty() || result == "null") return

        try {
            // Fix double escaping if present
            val clean = if (result.startsWith("\"") && result.endsWith("\"")) {
                result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            } else result

            if (!clean.contains("\"hasMedia\":true")) {
                if (isMediaPlaying) stopMediaService()
                isMediaPlaying = false
                currentMediaUrl = null
                return
            }

            val url = "\"url\":\"([^\"]+)\"".toRegex().find(clean)?.groupValues?.get(1)
            val title = "\"title\":\"([^\"]+)\"".toRegex().find(clean)?.groupValues?.get(1) ?: "Media"
            val playing = "\"playing\":true".toRegex().containsMatchIn(clean)

            if (url.isNullOrEmpty()) return
            val canonicalUrl = canonicalizeMediaUrl(url)
            val sameMedia = canonicalizeMediaUrl(currentMediaUrl) == canonicalUrl

            when {
                playing && (!sameMedia || mediaService == null) -> startMediaService(url, title)
                playing && !isMediaPlaying -> mediaService?.resume()
                !playing && isMediaPlaying -> mediaService?.pause()
            }

            isMediaPlaying = playing
            currentMediaUrl = url

        } catch (e: Exception) {
            Log.e(TAG, "Media parse error: ${e.message}")
        }
    }

    private fun startMediaService(url: String, title: String) {
        try {
            val now = System.currentTimeMillis()
            val fingerprint = "${canonicalizeMediaUrl(url)}|${title.trim().lowercase(Locale.US)}"
            if (lastPlaybackFingerprint == fingerprint && (now - lastPlaybackStartMs) < 5000L) {
                return
            }
            lastPlaybackFingerprint = fingerprint
            lastPlaybackStartMs = now

            if (mediaService == null) {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = "LOCAL_BIND"
                }
                context.startService(intent)
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

                handler.postDelayed({
                    mediaService?.playVideo(url, title)
                }, 500)
            } else {
                mediaService?.playVideo(url, title)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media service", e)
        }
    }

    private fun canonicalizeMediaUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val uri = Uri.parse(raw)
            val host = (uri.host ?: "").lowercase(Locale.US)
            val path = uri.path ?: ""

            if (host.contains("google.") && path == "/search") {
                val q = uri.getQueryParameter("q").orEmpty()
                return "google-search:$q"
            }

            if (host.contains("youtube.com")) {
                val v = uri.getQueryParameter("v").orEmpty()
                if (v.isNotEmpty()) return "youtube-watch:$v"
            }

            val base = buildString {
                append(uri.scheme ?: "https")
                append("://")
                append(host)
                append(path)
            }
            val stableKeys = listOf("v", "list", "q", "id")
            val stableQuery = stableKeys
                .mapNotNull { key ->
                    uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let { "$key=$it" }
                }
                .joinToString("&")
            if (stableQuery.isEmpty()) base else "$base?$stableQuery"
        } catch (_: Exception) {
            raw
        }
    }

    private fun stopMediaService() {
        try {
            mediaService?.stop()
        } catch (_: Exception) {}
    }
}
