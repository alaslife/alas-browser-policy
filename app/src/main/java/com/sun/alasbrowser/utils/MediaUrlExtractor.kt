package com.sun.alasbrowser.utils

import android.webkit.WebView
import android.util.Log

object MediaUrlExtractor {
    
    fun extractYouTubeVideoUrl(webView: WebView, callback: (String?) -> Unit) {
        val script = """
            (function() {
                try {
                    // Method 1: Try to get from video element
                    var video = document.querySelector('video');
                    if (video && video.src) {
                        return video.src;
                    }
                    
                    // Method 2: Try to get from YouTube player
                    if (window.ytplayer && window.ytplayer.config) {
                        var streamingData = window.ytplayer.config.args.adaptive_fmts;
                        if (streamingData) {
                            var formats = streamingData.split(',');
                            if (formats.length > 0) {
                                var urlMatch = formats[0].match(/url=([^&]+)/);
                                if (urlMatch) {
                                    return decodeURIComponent(urlMatch[1]);
                                }
                            }
                        }
                    }
                    
                    // Method 3: Get current video URL from page
                    var player = document.getElementById('movie_player');
                    if (player && player.getVideoUrl) {
                        return player.getVideoUrl();
                    }
                    
                    // Method 4: Extract from video source
                    var sources = document.querySelectorAll('video source');
                    if (sources.length > 0) {
                        return sources[0].src;
                    }
                    
                    return null;
                } catch (e) {
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            val cleanResult = result?.trim()?.removeSurrounding("\"")
            if (!cleanResult.isNullOrEmpty() && cleanResult != "null" && !cleanResult.startsWith("ERROR")) {
                Log.d("MediaUrlExtractor", "Extracted URL: $cleanResult")
                callback(cleanResult)
            } else {
                Log.d("MediaUrlExtractor", "Could not extract URL: $result")
                callback(null)
            }
        }
    }
    
    fun detectVideoPlaying(webView: WebView, callback: (Boolean, String?) -> Unit) {
        val script = """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    var video = videos[i];
                    if (!video.paused && video.currentTime > 0 && !video.ended && video.readyState > 2) {
                        return JSON.stringify({
                            playing: true,
                            src: video.src || video.currentSrc || '',
                            duration: video.duration,
                            currentTime: video.currentTime
                        });
                    }
                }
                return JSON.stringify({playing: false});
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            try {
                val playing = result?.contains("\"playing\":true") == true
                val srcMatch = result?.let { Regex("\"src\":\"([^\"]+)\"").find(it) }
                val src = srcMatch?.groupValues?.get(1)
                callback(playing, src)
            } catch (e: Exception) {
                Log.e("MediaUrlExtractor", "Error detecting video", e)
                callback(false, null)
            }
        }
    }
    
    fun injectVideoDetector(webView: WebView, onVideoDetected: (String) -> Unit) {
        val script = """
            (function() {
                console.log('[Video Detector] Installing...');
                
                // Monitor video play events
                document.addEventListener('play', function(e) {
                    if (e.target.tagName === 'VIDEO') {
                        var video = e.target;
                        var src = video.src || video.currentSrc;
                        console.log('[Video Detector] Video playing:', src);
                        
                        // Send to native
                        if (window.BrowserBridge && window.BrowserBridge.onVideoPlaying) {
                            window.BrowserBridge.onVideoPlaying(src);
                        }
                    }
                }, true);
                
                // Monitor for dynamically added videos
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.tagName === 'VIDEO') {
                                console.log('[Video Detector] New video element detected');
                            }
                        });
                    });
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                
                console.log('[Video Detector] Active');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
}
