package com.sun.alasbrowser.utils

import android.webkit.WebView

object PipVideoHelper {
    
    // JavaScript to detect playing video (YouTube-aware)
    const val VIDEO_DETECTION_SCRIPT = """
        (function() {
            // Check for HTML5 video elements in main page
            const videos = document.querySelectorAll('video');
            for (let video of videos) {
                if (!video.paused && video.currentTime > 0 && !video.ended && video.readyState > 2) {
                    return JSON.stringify({
                        playing: true,
                        currentTime: video.currentTime,
                        duration: video.duration,
                        width: video.videoWidth,
                        height: video.videoHeight
                    });
                }
            }
            
            // Special check for YouTube mobile site
            if (window.location.href.includes('youtube.com') || window.location.href.includes('youtu.be')) {
                // Check if player is playing via YouTube mobile controls
                const playButton = document.querySelector('.ytp-play-button');
                const playerState = playButton?.getAttribute('aria-label');
                
                if (playerState && playerState.toLowerCase().includes('pause')) {
                    // Pause button showing means video is playing
                    return JSON.stringify({playing: true});
                }
                
                // Check for video element directly (YouTube mobile uses native video)
                const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
                if (ytVideo && !ytVideo.paused && ytVideo.currentTime > 0) {
                    return JSON.stringify({playing: true});
                }
            }
            
            return JSON.stringify({playing: false});
        })();
    """
    
    // JavaScript to prepare video for PIP (make it fullscreen in DOM)
    const val PREPARE_VIDEO_FOR_PIP = """
        (function() {
            const videos = document.querySelectorAll('video');
            let targetVideo = null;
            let maxArea = 0;
            
            // Find the largest playing video
            for (let video of videos) {
                if (!video.paused) {
                    const rect = video.getBoundingClientRect();
                    const area = rect.width * rect.height;
                    if (area > maxArea) {
                        maxArea = area;
                        targetVideo = video;
                    }
                }
            }
            
            if (targetVideo) {
                // Mark as PiP mode
                document.body.setAttribute('data-pip-modified', 'true');
                targetVideo.setAttribute('data-pip-active', 'true');
                
                // Store originals
                if (!document.documentElement.hasAttribute('data-original-html-style')) {
                    document.documentElement.setAttribute('data-original-html-style', document.documentElement.getAttribute('style') || '');
                    document.body.setAttribute('data-original-body-style', document.body.getAttribute('style') || '');
                    targetVideo.setAttribute('data-original-style', targetVideo.getAttribute('style') || '');
                    targetVideo.setAttribute('data-original-parent', targetVideo.parentElement?.tagName || 'BODY');
                }
                
                // Black background for everything
                document.documentElement.style.cssText = 'margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:black;';
                document.body.style.cssText = 'margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:black;';
                
                // Hide all body children except video container
                Array.from(document.body.children).forEach(child => {
                    if (!child.contains(targetVideo) && child !== targetVideo) {
                        child.setAttribute('data-pip-hidden', 'true');
                        child.style.setProperty('display', 'none', 'important');
                        child.style.setProperty('visibility', 'hidden', 'important');
                    }
                });
                
                // Move video to body if needed
                if (targetVideo.parentElement !== document.body) {
                    document.body.appendChild(targetVideo);
                }
                
                // Make video fullscreen with aggressive styling
                targetVideo.style.cssText = 'position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; min-width: 100vw !important; min-height: 100vh !important; margin: 0 !important; padding: 0 !important; border: none !important; outline: none !important; z-index: 2147483647 !important; object-fit: contain !important; background: black !important; display: block !important; visibility: visible !important; opacity: 1 !important;';
                
                // Remove controls attribute temporarily and re-add
                const hadControls = targetVideo.hasAttribute('controls');
                targetVideo.setAttribute('controls', 'controls');
                
                // Aggressive playback enforcement
                const enforcePlayback = () => {
                    if (targetVideo.paused && targetVideo.hasAttribute('data-pip-active')) {
                        targetVideo.play().catch(e => console.log('Play error:', e));
                    }
                };
                
                // Override pause method
                const originalPause = targetVideo.pause;
                targetVideo.pause = function() {
                    if (!targetVideo.hasAttribute('data-pip-active')) {
                        originalPause.call(this);
                    }
                };
                
                // Store original for restoration
                targetVideo.setAttribute('data-original-pause', 'stored');
                
                // Multiple play attempts
                targetVideo.play().catch(e => console.log('Play attempt 1:', e));
                
                setTimeout(() => {
                    targetVideo.play().catch(e => console.log('Play attempt 2:', e));
                }, 100);
                
                setTimeout(() => {
                    targetVideo.play().catch(e => console.log('Play attempt 3:', e));
                }, 300);
                
                // Continuous playback monitoring
                const playInterval = setInterval(() => {
                    if (targetVideo.hasAttribute('data-pip-active')) {
                        enforcePlayback();
                    } else {
                        clearInterval(playInterval);
                    }
                }, 500);
                
                // Store interval ID for cleanup
                targetVideo.setAttribute('data-pip-interval', playInterval);
                
                // Pause event prevention
                targetVideo.addEventListener('pause', enforcePlayback);
                
                // Waiting event handling
                targetVideo.addEventListener('waiting', () => {
                    setTimeout(() => targetVideo.play().catch(e => {}), 100);
                });
                
                return 'prepared';
            }
            return 'no_video';
        })();
    """
    
    // JavaScript to restore page after PIP
    const val RESTORE_PAGE_AFTER_PIP = """
        (function() {
            if (!document.body.hasAttribute('data-pip-modified')) {
                return 'not_modified';
            }
            
            // Find PiP video and clean up
            const pipVideos = document.querySelectorAll('video[data-pip-active]');
            pipVideos.forEach(video => {
                // Remove PiP marker
                video.removeAttribute('data-pip-active');
                
                // Clear interval if exists
                const intervalId = video.getAttribute('data-pip-interval');
                if (intervalId) {
                    clearInterval(parseInt(intervalId));
                    video.removeAttribute('data-pip-interval');
                }
                
                // Restore original pause method
                if (video.hasAttribute('data-original-pause')) {
                    delete video.pause;
                    video.removeAttribute('data-original-pause');
                }
            });
            
            // Restore HTML and body styles
            if (document.documentElement.hasAttribute('data-original-html-style')) {
                const originalHtmlStyle = document.documentElement.getAttribute('data-original-html-style');
                document.documentElement.setAttribute('style', originalHtmlStyle);
                document.documentElement.removeAttribute('data-original-html-style');
            }
            
            if (document.body.hasAttribute('data-original-body-style')) {
                const originalBodyStyle = document.body.getAttribute('data-original-body-style');
                document.body.setAttribute('style', originalBodyStyle);
                document.body.removeAttribute('data-original-body-style');
            }
            
            // Restore all hidden children
            const hiddenElements = document.querySelectorAll('[data-pip-hidden]');
            hiddenElements.forEach(el => {
                el.style.removeProperty('display');
                el.style.removeProperty('visibility');
                el.removeAttribute('data-pip-hidden');
            });
            
            // Restore video styles
            const videos = document.querySelectorAll('video[data-original-style]');
            videos.forEach(video => {
                const originalStyle = video.getAttribute('data-original-style');
                video.setAttribute('style', originalStyle);
                video.removeAttribute('data-original-style');
                video.removeAttribute('data-original-parent');
            });
            
            // Remove modification marker
            document.body.removeAttribute('data-pip-modified');
            
            // Force repaint
            void document.body.offsetHeight;
            
            return 'restored';
        })();
    """
    
    fun detectVideoPlayback(webView: WebView, callback: (Boolean) -> Unit) {
        try {
            webView.post {
                webView.evaluateJavascript(VIDEO_DETECTION_SCRIPT) { result ->
                    android.util.Log.d("PipVideoHelper", "Video detection result: $result")
                    // The result comes with escaped quotes like: "{\"playing\":true..."
                    val isPlaying = result?.contains("\\\"playing\\\":true") == true || 
                                   result?.contains("\"playing\":true") == true ||
                                   result?.contains("playing\":true") == true
                    android.util.Log.d("PipVideoHelper", "Parsed isPlaying: $isPlaying")
                    android.util.Log.d("PipVideoHelper", "Invoking callback with: $isPlaying")
                    try {
                        callback(isPlaying)
                        android.util.Log.d("PipVideoHelper", "Callback invoked successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("PipVideoHelper", "Error invoking callback", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PipVideoHelper", "Error in detectVideoPlayback", e)
            callback(false)
        }
    }
    
    fun prepareForPip(webView: WebView, callback: (Boolean) -> Unit = {}) {
        // First restore any previous PiP modifications
        webView.evaluateJavascript(RESTORE_PAGE_AFTER_PIP) { _ ->
            // Then prepare for new PiP entry
            webView.evaluateJavascript(PREPARE_VIDEO_FOR_PIP) { result ->
                val success = result?.contains("prepared") == true
                callback(success)
            }
        }
    }
    
    fun restoreAfterPip(webView: WebView) {
        webView.evaluateJavascript(RESTORE_PAGE_AFTER_PIP, null)
    }
    
    // Enforce video playback from native side
    const val ENFORCE_VIDEO_PLAYBACK = """
        (function() {
            const video = document.querySelector('video[data-pip-active]');
            if (video) {
                if (video.paused) {
                    video.play().catch(e => console.log('Enforce play error:', e));
                }
                return 'playing';
            }
            return 'no_video';
        })();
    """
    
    fun enforceVideoPlayback(webView: WebView) {
        webView.evaluateJavascript(ENFORCE_VIDEO_PLAYBACK, null)
    }
}
