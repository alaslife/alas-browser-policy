package com.sun.alasbrowser.web

import android.webkit.WebView

/**
 * Brave/uBlock-style YouTube ad blocker.
 *
 * Strategy:
 * 1. EARLY (onPageStarted): Override JSON.parse globally to strip ad config
 *    from ALL parsed JSON. Override fetch/XHR to block ad endpoints.
 *    Trap ytInitialPlayerResponse setter.
 * 2. LATE (onPageFinished): CSS cosmetic hiding + player class observer
 *    for instant mute+skip of any ads that slip through.
 * 3. NETWORK (WebViewContainer): Block ad domains in shouldInterceptRequest.
 */
object YouTubeAdBlocker {

    fun injectEarlyBlocker(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                'use strict';
                if (window.__alasYtEarly) return;
                window.__alasYtEarly = true;

                // ═══ SAFE AD-KEY DETECTION ═══
                // Keys that start with "ad" + uppercase letter are ad config,
                // EXCEPT known safe prefixes like "adaptive", "address", etc.
                const SAFE = /^(adaptive|address|added|addon|adjust|admin|admit|adopt|adult|advance)/;
                const EXACT = new Set([
                    'adPlacements','adSlots','adBreakParams','adBreakHeartbeatParams',
                    'playerAds','adParams','adDevice','adSignals','adRequestConfig',
                    'adPlaybackContext','instreamAdPlayerOverlayRenderer',
                    'linearAdSequenceRenderer','adModule','adSafetyReason',
                    'advertisingId','instreamVideoAdRenderer','adLayoutMetadata',
                    'adInfoRenderer','adNextParams','adBreakServiceRenderer',
                    'playerLegacyDesktopWatchAdsRenderer','adInfoTextRenderer',
                    'adHoverTextButtonRenderer','adVideoId','adLayoutLoggingData',
                    'adFeedbackRenderer','adDurationRemaining','adReasons',
                    'adActionInterstitials','adContentMetadata'
                ]);

                function isAdKey(k) {
                    if (EXACT.has(k)) return true;
                    if (k.length > 2 && k[0]==='a' && k[1]==='d' && k[2]===k[2].toUpperCase() && !SAFE.test(k)) return true;
                    return false;
                }

                function stripAds(obj) {
                    if (!obj || typeof obj !== 'object') return obj;
                    if (Array.isArray(obj)) { for (let i=0;i<obj.length;i++) stripAds(obj[i]); return obj; }
                    for (const k of Object.keys(obj)) {
                        if (isAdKey(k)) { delete obj[k]; }
                        else if (typeof obj[k] === 'object') { stripAds(obj[k]); }
                    }
                    return obj;
                }

                // ═══ CORE: OVERRIDE JSON.parse (Brave/uBlock technique) ═══
                // This catches ALL ad data regardless of which endpoint delivers it.
                const origParse = JSON.parse;
                JSON.parse = function() {
                    const result = origParse.apply(this, arguments);
                    if (result && typeof result === 'object') {
                        // Only strip on YouTube pages (safety check)
                        if (result.playerAds || result.adPlacements || result.adSlots ||
                            result.adBreakParams || result.adSignals) {
                            stripAds(result);
                        }
                        // Strip adBlocksFound from playability
                        if (result.playabilityStatus) {
                            delete result.playabilityStatus.adBlocksFound;
                        }
                    }
                    return result;
                };

                // ═══ INTERCEPT FETCH ═══
                const origFetch = window.fetch;
                window.fetch = function(input, init) {
                    const url = (typeof input === 'string') ? input : (input?.url || '');
                    // Block ad endpoints
                    if (url.includes('/pagead/') || url.includes('/ptracking') ||
                        url.includes('/api/stats/ads') || url.includes('/get_midroll_') ||
                        url.includes('&adformat=') || url.includes('/log_event?') ||
                        url.includes('/api/stats/atr') || url.includes('/generate_204') ||
                        url.includes('/youtubei/v1/log_event') || url.includes('&ad_type=')) {
                        return Promise.resolve(new Response('{}', { status: 200 }));
                    }
                    return origFetch.apply(this, arguments);
                };

                // ═══ INTERCEPT XMLHttpRequest ═══
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._alasUrl = url || '';
                    return origOpen.apply(this, arguments);
                };
                const origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    const url = this._alasUrl;
                    if (url.includes('/pagead/') || url.includes('/ptracking') ||
                        url.includes('/api/stats/ads') || url.includes('/log_event?') ||
                        url.includes('/get_midroll_')) {
                        return;
                    }
                    return origSend.apply(this, arguments);
                };

                // ═══ TRAP ytInitialPlayerResponse ═══
                let _ytPR = undefined;
                try {
                    Object.defineProperty(window, 'ytInitialPlayerResponse', {
                        configurable: true,
                        get: function() { return _ytPR; },
                        set: function(val) {
                            if (val && typeof val === 'object') stripAds(val);
                            _ytPR = val;
                        }
                    });
                } catch(e) {}

                // ═══ BLOCK AD-BLOCKER DETECTION ═══
                const origDefineProperty = Object.defineProperty;
                Object.defineProperty = function(obj, prop, desc) {
                    if (prop === 'adBlocksFound' || prop === 'hasAdBlocker') return obj;
                    return origDefineProperty.call(this, obj, prop, desc);
                };

                // ═══ BACKUP: strip if ytInitialPlayerResponse already set ═══
                try {
                    if (window.ytInitialPlayerResponse) stripAds(window.ytInitialPlayerResponse);
                } catch(e) {}

            })();
        """.trimIndent(), null)
    }

    fun injectYouTubeAdBlocker(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                const host = window.location.hostname;
                const isMusic = host.includes('music.youtube.com');
                if (!host.includes('youtube.com') && !host.includes('youtu.be')) return;
                if (window.__alasYtLate) return;
                window.__alasYtLate = true;

                let _skipActive = false;
                let _skipTimer = null;

                // ═══ CSS COSMETIC HIDING & JITTER FIXES ═══
                const style = document.createElement('style');
                style.id = 'alas-yt-css';
                style.textContent = `
                    .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                    .ytp-ad-overlay-container, .ytp-ad-image-overlay,
                    .ytp-ad-text-overlay, .ytp-ad-skip-button-slot,
                    .ytp-ad-message-container, .ytp-ad-preview-container,
                    ytd-display-ad-renderer, ytd-promoted-sparkles-web-renderer,
                    ytd-ad-slot-renderer, ytd-banner-promo-renderer,
                    ytd-video-masthead-ad-advertiser-info-renderer,
                    ytd-video-masthead-ad-v3-renderer, ytd-primetime-promo-renderer,
                    #masthead-ad, .ytd-rich-item-renderer[is-ad],
                    ytd-in-feed-ad-layout-renderer, ytd-statement-banner-renderer,
                    ytd-promoted-video-renderer, ytd-compact-promoted-video-renderer,
                    ytd-action-companion-ad-renderer, ytd-ad-inline-playback-renderer,
                    ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],
                    ytmusic-rich-header-renderer[title*="Sponsored"],
                    ytmusic-item-section-renderer[is-ad], .ytmusic-player-bar-ad-renderer,
                    tp-yt-paper-dialog:has(#feedback.ytd-enforcement-message-view-model),
                    ytd-enforcement-message-view-model,
                    tp-yt-paper-dialog:has(yt-playability-error-supported-renderers)
                    { display:none!important; height:0!important; overflow:hidden!important; }
                    .html5-video-player { opacity:1!important; }
                    video { opacity:1!important; }
                    
                    /* YT Music Mini Player Jitter Fix */
                    ytmusic-player-bar, #player-bar-background {
                        transition: transform 0.1s ease-out !important;
                        will-change: transform;
                        transform: translateZ(0);
                        backface-visibility: hidden;
                    }
                    /* Prevent player from jumping when height changes */
                    ytmusic-app-layout[player-visible_] > [slot=player-bar] {
                        position: fixed !important;
                        bottom: 0 !important;
                        left: 0 !important;
                        right: 0 !important;
                        z-index: 1000 !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // ═══ AD STATE: only trust the player class ═══
                const isAd = () => {
                    const p = document.querySelector('.html5-video-player');
                    return !!(p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')));
                };

                // ═══ SKIP AD: mute + seek to end + click skip ═══
                const skipAd = () => {
                    const video = document.querySelector('video');
                    if (!video) return;
                    video.muted = true;
                    video.volume = 0;
                    video.playbackRate = 16;
                    _skipActive = true;
                    if (video.duration && isFinite(video.duration)) video.currentTime = video.duration;
                    document.querySelectorAll(
                        '.ytp-ad-skip-button,.ytp-skip-ad-button,.ytp-ad-skip-button-modern'
                    ).forEach(b => { try{b.click();}catch(e){} });
                    try {
                        const mp = document.getElementById('movie_player');
                        if (mp && typeof mp.skipAd === 'function') mp.skipAd();
                    } catch(e) {}
                    if (_skipTimer) clearTimeout(_skipTimer);
                    _skipTimer = setTimeout(() => { _skipActive = false; recover(); }, 500);
                };

                // ═══ RECOVER: restore normal playback ═══
                const recover = () => {
                    if (_skipActive || isAd()) return;
                    const video = document.querySelector('video');
                    if (!video) return;
                    if (video.muted || video.volume === 0 || video.playbackRate > 2) {
                        video.muted = false;
                        video.volume = 1;
                        video.playbackRate = 1.0;
                    }
                };

                // ═══ PLAYER CLASS OBSERVER — instant ad detection ═══
                const observePlayer = () => {
                    const player = document.querySelector('.html5-video-player');
                    if (!player || player.__alasObs) return;
                    player.__alasObs = true;
                    new MutationObserver(() => {
                        if (isAd()) skipAd();
                        else if (!_skipActive) recover();
                    }).observe(player, { attributes: true, attributeFilter: ['class'] });
                };

                // ═══ VIDEO PLAY LISTENER ═══
                const observeVideo = () => {
                    const video = document.querySelector('video');
                    if (!video || video.__alasObs) return;
                    video.__alasObs = true;
                    video.addEventListener('playing', () => { if (isAd()) skipAd(); });
                };

                // ═══ DISMISS POPUPS ═══
                const dismissPopups = () => {
                    document.querySelectorAll(
                        'tp-yt-paper-dialog, ytd-popup-container, ytd-enforcement-message-view-model'
                    ).forEach(d => {
                        const t = (d.textContent||'').toLowerCase();
                        if (t.includes('ad blocker') || t.includes('adblocker')) {
                            d.remove();
                            const v = document.querySelector('video');
                            if (v && v.paused) v.play().catch(()=>{});
                        }
                    });
                    document.querySelectorAll(
                        'ytd-enforcement-message-view-model,' +
                        'ytd-player-error-message-renderer,' +
                        'yt-playability-error-supported-renderers'
                    ).forEach(e => e.remove());
                };

                // ═══ REMOVE SPONSORED ═══
                const removeSponsored = () => {
                    document.querySelectorAll('ytmusic-rich-header-renderer,.title').forEach(h => {
                        if ((h.textContent||'').trim() === 'Sponsored') {
                            const s = h.closest('ytmusic-rich-section-renderer,ytmusic-shelf-renderer,ytmusic-carousel-shelf-renderer');
                            if (s) s.style.display = 'none';
                        }
                    });
                };

                // ═══ RE-STRIP any missed ytInitialPlayerResponse ═══
                const restrip = () => {
                    try {
                        const r = window.ytInitialPlayerResponse;
                        if (r) ['adPlacements','adSlots','playerAds','adBreakParams','adBreakHeartbeatParams','adParams','adSignals']
                            .forEach(k => delete r[k]);
                    } catch(e) {}
                };

                // ═══ CLEANUP CYCLE ═══
                const cleanup = () => {
                    restrip();
                    if (isAd()) skipAd();
                    else if (!_skipActive) recover();
                    dismissPopups();
                    removeSponsored();
                    observePlayer();
                    observeVideo();
                };

                cleanup();

                // ═══ DOM OBSERVER ═══
                let t = null;
                new MutationObserver(() => {
                    if (t) return;
                    t = setTimeout(() => { cleanup(); t = null; }, 16);
                }).observe(document.body || document.documentElement, {
                    childList: true, subtree: true,
                    attributes: true, attributeFilter: ['class']
                });

                setInterval(cleanup, 2000);
            })();
        """.trimIndent(), null)
    }
}
