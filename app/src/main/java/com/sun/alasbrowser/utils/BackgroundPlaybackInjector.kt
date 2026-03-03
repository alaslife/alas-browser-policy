package com.sun.alasbrowser.utils

import android.webkit.WebView

/**
 * Injects JavaScript to enable background playback and PiP for websites
 * that normally block it (like YouTube and YouTube Music).
 */
object BackgroundPlaybackInjector {
    
    /**
     * Checks if WebView is valid and not destroyed
     */
    private fun isWebViewValid(webView: WebView?): Boolean {
        if (webView == null) return false
        return try {
            webView.url != null 
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Syncs tab visibility state to the injected script
     */
    fun setTabVisibility(webView: WebView?, visible: Boolean) {
        if (!isWebViewValid(webView)) return
        webView?.evaluateJavascript("if(window.setAlasTabVisibility) window.setAlasTabVisibility($visible);", null)
    }
    
    /**
     * Prevents websites from pausing media on visibility change or focus loss
     */
    fun injectBackgroundPlaybackScript(webView: WebView?) {
        if (!isWebViewValid(webView)) return
        
        val script = """
            (function() {
                if (window.__alasBackgroundPlaybackInjected) return;
                window.__alasBackgroundPlaybackInjected = true;
                
                let _tabVisible = true;
                window.setAlasTabVisibility = (visible) => {
                    _tabVisible = visible;
                    console.log('Alas: Tab visibility changed to ' + visible);
                };

                // 1. Override document.hidden and visibilityState
                Object.defineProperty(document, 'hidden', {
                    get: () => false,
                    configurable: true
                });
                
                Object.defineProperty(document, 'visibilityState', {
                    get: () => 'visible',
                    configurable: true
                });
                
                Object.defineProperty(document, 'webkitVisibilityState', {
                    get: () => 'visible',
                    configurable: true
                });
                
                // 2. Override document.hasFocus
                document.hasFocus = () => true;
                
                // 3. Prevent visibilitychange/blur events
                const blockEvent = (e) => {
                    e.stopImmediatePropagation();
                    if (e.stopPropagation) e.stopPropagation();
                };
                window.addEventListener('visibilitychange', blockEvent, true);
                window.addEventListener('webkitvisibilitychange', blockEvent, true);
                window.addEventListener('blur', blockEvent, true);
                window.addEventListener('mouseleave', blockEvent, true);
                
                // 4. Mock IntersectionObserver (prevents pausing when video is off-screen)
                const OriginalIntersectionObserver = window.IntersectionObserver;
                if (OriginalIntersectionObserver) {
                    window.IntersectionObserver = function(callback, options) {
                        return new OriginalIntersectionObserver((entries, obs) => {
                            const mockedEntries = entries.map(entry => {
                                return {
                                    time: entry.time,
                                    target: entry.target,
                                    rootBounds: entry.rootBounds,
                                    boundingClientRect: entry.boundingClientRect,
                                    intersectionRect: entry.intersectionRect,
                                    isIntersecting: true,
                                    intersectionRatio: 1
                                };
                            });
                            callback(mockedEntries, obs);
                        }, options);
                    };
                    window.IntersectionObserver.prototype = OriginalIntersectionObserver.prototype;
                }

                // 5. Track real visibility internally
                let _reallyVisible = true;
                window.addEventListener('pagehide', () => { _reallyVisible = false; }, true);
                window.addEventListener('pageshow', () => { _reallyVisible = true; }, true);
                window.addEventListener('focus', () => { _reallyVisible = true; }, true);

                // 6. requestAnimationFrame shim for background
                const originalRAF = window.requestAnimationFrame;
                window.requestAnimationFrame = function(callback) {
                    if (!_reallyVisible || !_tabVisible) {
                        return window.setTimeout(() => callback(performance.now()), 1000 / 30);
                    }
                    return originalRAF(callback);
                };

                // 7. Prevent auto-pause on background (Aggressive)
                const setupMediaListeners = (media) => {
                    if (media.__alasListenersSet) return;
                    media.__alasListenersSet = true;
                    
                    media.addEventListener('pause', () => {
                        // If the tab is visible to the user, assume it's a manual pause
                        if (_tabVisible) {
                            media.dataset.manuallyPaused = 'true';
                        }
                    });
                    
                    media.addEventListener('play', () => {
                        media.dataset.manuallyPaused = 'false';
                    });
                };
                
                document.querySelectorAll('video, audio').forEach(setupMediaListeners);
                
                const observer = new MutationObserver((mutations) => {
                    mutations.forEach((mutation) => {
                        mutation.addedNodes.forEach((node) => {
                            if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') {
                                setupMediaListeners(node);
                            } else if (node.querySelectorAll) {
                                node.querySelectorAll('video, audio').forEach(setupMediaListeners);
                            }
                        });
                    });
                });
                observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

                const originalPause = HTMLMediaElement.prototype.pause;
                HTMLMediaElement.prototype.pause = function() {
                    const isManual = this.dataset.manuallyPaused === 'true';
                    if ((!_reallyVisible || !_tabVisible) && !isManual && (!window.__alasLastClickTime || (Date.now() - window.__alasLastClickTime > 800))) {
                        console.log('Alas: Blocked auto-pause in background');
                        return Promise.resolve();
                    }
                    return originalPause.apply(this, arguments);
                };
                window.addEventListener('click', () => { window.__alasLastClickTime = Date.now(); }, true);

                // 8. YouTube & YouTube Music specific fixes
                if (location.hostname.includes('youtube.com')) {
                    // Prevent YouTube "Continue watching?" popup
                    setInterval(() => {
                        const btn = document.querySelector('yt-confirm-dialog-renderer #confirm-button') || 
                                    document.querySelector('.yt-player-error-message-renderer button');
                        if (btn) btn.click();
                    }, 5000);
                    
                    // Force YouTube player to stay in "playing" state
                    if (window.YT && window.YT.Player) {
                        const originalGetPlayerState = window.YT.Player.prototype.getPlayerState;
                        if (originalGetPlayerState) {
                            window.YT.Player.prototype.getPlayerState = function() {
                                const state = originalGetPlayerState.call(this);
                                return ((!_reallyVisible || !_tabVisible) && state === 2) ? 1 : state;
                            };
                        }
                    }
                }
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script, null)
    }
    
    /**
     * Forces video playback to continue
     */
    fun enforcePlayback(webView: WebView?) {
        if (!isWebViewValid(webView)) return
        val script = "(function(){const m=document.querySelectorAll('video,audio');m.forEach(i=>{if(i.paused&&!i.ended&&i.dataset.manuallyPaused!=='true')i.play().catch(()=>{})})})();"
        try { webView?.evaluateJavascript(script, null) } catch (e: Exception) {}
    }
    
    fun playVideo(webView: WebView) {
        webView.evaluateJavascript("(function(){const m=document.querySelector('video,audio');if(m) { m.dataset.manuallyPaused='false'; m.play().catch(()=>{}); }})();", null)
    }
    
    fun pauseVideo(webView: WebView) {
        webView.evaluateJavascript("(function(){const m=document.querySelector('video,audio');if(m){m.dataset.manuallyPaused='true';m.pause()}})();", null)
    }
}
