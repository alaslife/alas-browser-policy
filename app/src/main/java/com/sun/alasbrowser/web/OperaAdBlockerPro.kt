package com.sun.alasbrowser.web

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * OPERA-LEVEL AD BLOCKER PRO
 * 
 * Advanced ad blocking system with:
 * - Aggressive filtering (Opera browser quality)
 * - Tracking prevention
 * - Cookie banner removal
 * - Video ad blocking (YouTube, Twitch, etc.)
 * - Performance optimized
 * - Stats & analytics
 */
object OperaAdBlockerPro {
    
    private const val TAG = "OperaAdBlockerPro"
    private const val BLOCKER_VERSION = "4.5.0"
    
    private val blockedCount = AtomicInteger(0)
    private val unblockedCount = AtomicInteger(0)
    private val trackersBlocked = AtomicInteger(0)
    private val cookieBannersRemoved = AtomicInteger(0)
    
    // Statistics per domain
    private val statsPerDomain = ConcurrentHashMap<String, AdBlockStats>()
    
    data class AdBlockStats(
        var adsBlocked: Int = 0,
        var trackersBlocked: Int = 0,
        var cookieBannersRemoved: Int = 0,
        var dataBlockedKB: Int = 0
    )
    
    /**
     * Get the complete Opera-level ad blocker script
     */
    fun getOperaLevelBlockerScript(): String {
        return """
(function() {
    'use strict';
    
    // ═══════════════════════════════════════════════════════════════════
    // OPERA ADBLOCKER PRO v$BLOCKER_VERSION
    // World-class ad blocking with tracking prevention & performance
    // ═══════════════════════════════════════════════════════════════════
    
    if (window.__operaAdBlocker) return; // Prevent double-loading
    window.__operaAdBlocker = {
        version: '$BLOCKER_VERSION',
        adsBlocked: 0,
        trackersBlocked: 0,
        cookieBannersRemoved: 0,
        performanceMode: true
    };
    
    const config = {
        aggressiveMode: true,
        preventTracking: true,
        removeCookieBanners: true,
        blockVideoAds: true,
        // New features
        mangaMode: true,
        historyProtection: true,
        clickProtection: true,
        networkProtection: true
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 1: AGGRESSIVE CSS FILTERING - Instant ad removal
    // ═══════════════════════════════════════════════════════════════════
    
    const injectCss = () => {
        if (document.getElementById('opera-adblocker-css')) return;
        
        const style = document.createElement('style');
        style.id = 'opera-adblocker-css';
        style.textContent = `
            /* Block everything AD-related */
            [class*="ad"][class*="container"],
            [class*="ad"][class*="box"],
            [id*="ad-"][class*="container"],
            [id*="ad-wrapper"],
            [role="complementary"][aria-label*="ad"],
            [data-ad-unit],
            [data-ad-slot],
            [data-ad-client],
            [data-ad-format],
            
            /* Google Ads */
            .adsbygoogle,
            [id*="google_ads"],
            [id*="googleAds"],
            [id*="div-gpt"],
            iframe[src*="googlesyndication"],
            
            /* Video Ads (YouTube, Dailymotion, etc.) */
            .ytp-ad-overlay,
            .ytp-ad-overlay-container,
            .ytp-ad-image-overlay,
            .ytp-ad-text-overlay,
            ytd-display-ad-renderer,
            ytd-promoted-sparkles-web-renderer,
            .dm-player-context-menu-ad,
            
            /* Facebook Ads & Pixel */
            [data-testid="fbAdvert"],
            [data-testid="inline_feed_ad"],
            [class*="ecxinline_feed_ad"],
            [class*="ecxinstreamadthumbnail"],
            .fb_ad,
            
            /* Major Ad Networks */
            [src*="doubleclick"],
            [src*="googlesyndication"],
            [src*="pagead"],
            [src*="ads.google"],
            [src*="adserver"],
            [src*="adservice"],
            [src*="analytics"],
            [data-src*="advertisement"],
            
            /* Popup & Overlay Ads */
            [class*="popup"][class*="ad"],
            [class*="modal"][class*="ad"],
            [class*="overlay"][class*="ad"],
            [class*="interstitial"],
            [class*="popunder"],
            [class*="pop-up"],
            [class*="notification-ads"],
            
            /* Cookie Banners */
            [class*="cookie"],
            [id*="cookie"],
            [class*="gdpr"],
            [id*="gdpr"],
            [class*="consent"],
            [id*="consent"],
            [class*="privacy-banner"],
            [class*="privacy-notice"],
            [role="dialog"][aria-label*="cookie"],
            [role="dialog"][aria-label*="privacy"],
            
            /* Sponsored Content */
            [class*="sponsored"],
            [class*="promotional"],
            [class*="promo"][class*="box"],
            [data-is-ad],
            [data-is-ad="true"],
            [data-is-promotion="true"],
            
            /* Banner Ads */
            [class*="banner"],
            [class*="top-banner"],
            [class*="bottom-banner"],
            [class*="side-banner"],
            
            /* Native Ads */
            [class*="native-ad"],
            [class*="taboola"],
            [class*="outbrain"],
            [class*="mgid"],
            [class*="revcontent"],
            [class*="disqus"],
            
            /* Tracking Pixels (invisible) */
            [src*="pixel"],
            [src*="track"],
            [src*="analytics"],
            [src*="beacon"],
            [src*="collect"],
            [src*="monitor"],
            
            /* Malicious/Scam Ads */
            [class*="get-lucky"],
            [class*="lucky-box"],
            [class*="lottery"],
            [class*="sweepstakes"],
            [class*="scam"],
            [class*="clickbait"],
            
            /* Gambling & Crypto Scams */
            a[href*="bc.game"],
            a[href*="1xbet"],
            a[href*="yolo247"],
            a[href*="stake.com"],
            a[href*="crypto-casino"],
            [class*="bet-now"],
            [class*="casino"],
            
            /* Manga Site Specific Ad Elements */
            .manga-ad,
            .reader-ad,
            [class*="chapter-ad"],
            [id*="chapter-ad"],
            .ad-container,
            .ads-container,
            #ad-container,
            #ads-container,
            
            /* Fake UI Elements often used on Manga sites */
            [class*="fake-button"],
            [class*="fake-download"],
            [class*="fake-player"],
            .download-button-ad,
            
            /* Fixed Position Ads (Bottom/Top bars) */
            [style*="position: fixed"][style*="bottom: 0"],
            [style*="position: fixed"][style*="top: 0"][style*="z-index: 9999"],
            [style*="position:fixed"][style*="bottom:0"],
            
            /* Shopping Ads (when too aggressive) */
            [aria-label*="Promoted"],
            [aria-label*="Sponsored"],
            [data-sponsored="true"],
            
            /* Chat Widgets & Pop-ups */
            [class*="chatbot"],
            [class*="live-chat"],
            [class*="tawk"],
            [class*="zendesk"],
            [class*="intercom"],
            
            /* Third-party Widgets */
            [src*="reddit.com/r/"],
            [src*="twitter.com/intent"],
            [src*="platform.twitter"],
            [src*="facebook.com/plugins"],
            [src*="tiktok.com/embed"],
            
            /* Newsletter Popups */
            [class*="newsletter"],
            [class*="subscribe"],
            [class*="email-signup"],
            [class*="mailing-list"],
            [role="dialog"][aria-label*="subscribe"],
            [role="dialog"][aria-label*="newsletter"]
            {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
                height: 0 !important;
                width: 0 !important;
                margin: 0 !important;
                padding: 0 !important;
            }
            
            /* Remove space left by hidden ads */
            [class*="ad-space"],
            [class*="ad-placeholder"],
            [class*="ad-reserved"],
            [id*="ad-space"]
            {
                display: none !important;
                height: 0 !important;
                min-height: 0 !important;
                margin: 0 !important;
            }
            
            /* Fix layout after ad removal */
            body { overflow-y: auto !important; }
            html { height: auto !important; }
        `;
        document.head.appendChild(style);
        return true;
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 2: HISTORY HIJACK PROTECTION - Fixes Back Button
    // ═══════════════════════════════════════════════════════════════════
    
    const injectHistoryProtection = () => {
        // Backup original methods
        const originalPushState = history.pushState;
        const originalReplaceState = history.replaceState;
        
        const adHistoryPatterns = [
            'doubleclick', 'googlesyndication', 'adservice',
            'popunder', 'popads', 'popcash', 'adsterra',
            'monetag', 'exosrv', 'trafficjunky', 'juicyads',
            'propellerads', 'clickadu', 'hilltopads'
        ];
        
        // Override pushState to prevents ads from polluting history
        history.pushState = function(state, title, url) {
            if (url) {
                const urlStr = url.toString().toLowerCase();
                if (adHistoryPatterns.some(p => urlStr.includes(p))) {
                    console.log('[OperaBlocker] Blocked history.pushState:', url);
                    return; 
                }
            }
            return originalPushState.apply(this, arguments);
        };
        
        // Override replaceState to prevent ads from modifying current entry to trap user
        history.replaceState = function(state, title, url) {
             if (url) {
                const urlStr = url.toString().toLowerCase();
                if (adHistoryPatterns.some(p => urlStr.includes(p))) {
                    console.log('[OperaBlocker] Blocked history.replaceState:', url);
                    return; 
                }
            }
            return originalReplaceState.apply(this, arguments);
        };
    };

    // ═══════════════════════════════════════════════════════════════════
    // PART 3: CLICK HIJACKING PROTECTION - Prevent first-tap redirects
    // ═══════════════════════════════════════════════════════════════════
    
    const injectClickProtection = () => {
        // Intercept all clicks
        document.addEventListener('click', function(e) {
            // If it's a link
            let target = e.target;
            while (target && target.tagName !== 'A') {
                target = target.parentElement;
            }
            
            if (target && target.tagName === 'A') {
                const href = target.getAttribute('href');
                if (!href) return;
                
                const lowerHref = href.toLowerCase();
                
                // Block javascript: links (often used for popups)
                if (lowerHref.startsWith('javascript:')) {
                    // Check if it's safe (e.g. void(0))
                    if (!lowerHref.includes('void(0)') && !lowerHref.includes(';')) {
                        console.log('[OperaBlocker] Blocked javascript: link');
                        e.preventDefault();
                        e.stopPropagation();
                    }
                }
                
                // Block known ad redirect patterns
                const adRedirectPatterns = [
                    'doubleclick', 'googlesyndication', 'popads', 'popcash',
                    'monetag', 'exosrv', 'adsterra', 'propellerads', 'clickadu'
                ];
                if ((lowerHref.includes('go.php') || lowerHref.includes('out.php')) &&
                    adRedirectPatterns.some(p => lowerHref.includes(p))) {
                    console.log('[OperaBlocker] Blocked redirect link');
                    e.preventDefault();
                    e.stopPropagation();
                }
            }
            
            // Allow legitimate clicks to proceed
            // Note: We don't stop propagation unless we block it
        }, true); // Capture phase to run before other handlers
        
        // Override window.open to block popups from known ad networks
        const originalOpen = window.open;
        const popupBlockPatterns = [
            'doubleclick', 'googlesyndication', 'popunder', 'popads', 'popcash',
            'adsterra', 'monetag', 'exosrv', 'trafficjunky', 'juicyads',
            'propellerads', 'clickadu', 'hilltopads', 'highcpmgate',
            'profitabledisplay', 'onclicka', '1xbet', 'bc.game', 'bcgame'
        ];
        window.open = function(url, target, features) {
            if (!url || url === 'about:blank') return null;
            
            const lowerUrl = url.toString().toLowerCase();
            if (popupBlockPatterns.some(p => lowerUrl.includes(p))) {
                console.log('[OperaBlocker] Blocked window.open:', url);
                return null;
            }
            
            return originalOpen.apply(this, arguments);
        };
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 4: SETTIMEOUT OVERRIDE - Prevent delayed redirects
    // ═══════════════════════════════════════════════════════════════════
    
    const injectTimeoutsProtection = () => {
        const originalSetTimeout = window.setTimeout;
        window.setTimeout = function(func, delay) {
            if (typeof func === 'string') {
                const funcStr = func.toLowerCase();
                if (funcStr.includes('location.href') || funcStr.includes('window.open') ||
                    funcStr.includes('location.replace')) {
                    if (funcStr.includes('doubleclick') || funcStr.includes('popads') || 
                        funcStr.includes('monetag') || funcStr.includes('adsterra') ||
                        funcStr.includes('propellerads') || funcStr.includes('exosrv')) {
                        console.log('[OperaBlocker] Blocked setTimeout redirect');
                        return 0;
                    }
                }
            }
            
            return originalSetTimeout.apply(this, arguments);
        };
    };

    // ═══════════════════════════════════════════════════════════════════
    // PART 5: JAVASCRIPT-BASED ELEMENT REMOVAL
    // ═══════════════════════════════════════════════════════════════════
    
    const removeAdElements = () => {
        const selectors = [
            // Generic ad containers
            'div[id*="advert"]',
            'div[id*="ad-"]',
            'div[id*="banner"]',
            'div[class*="advertisement"]',
            
            // Google
            'ins[class*="adsbygoogle"]',
            'div[id*="google_ads_"]',
            'iframe[src*="doubleclick"]',
            
            // Facebook/Meta
            '[data-testid*="ad"]',
            '[data-testid*="advert"]',
            
            // YouTube
            '.videoAdUi',
            '.ytp-ad-text-overlay',
            '.ytp-ad-overlay-container',
            
            // Video ads
            '.dm-player-ad-container',
            '.player-ad',
            'div[data-ad-status]',
            
            // Native ads
            '[class*="taboola"]',
            '[class*="outbrain"]',
            '[class*="disqus"]',
            
            // Popups
            '[class*="popup"][class*="ad"]',
            '[class*="modal"][class*="ad"]',
            '[role="dialog"][class*="ad"]',
            
            // Cookie notices  
            '[class*="cookie-consent"]',
            '[class*="gdpr-banner"]',
            '[class*="privacy-banner"]',
            'div[id*="cookie"]',
            'div[id*="gdpr"]',
            
            // Affiliate
            '[class*="affiliate"]',
            '[class*="referral"]'
        ];
        
        let removed = 0;
        selectors.forEach(selector => {
            try {
                document.querySelectorAll(selector).forEach(el => {
                    if (el && el.parentNode) {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        removed++;
                        window.__operaAdBlocker.adsBlocked++;
                    }
                });
            } catch (e) {}
        });
        
        return removed;
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 6: TRACKING PREVENTION (Like Opera browser)
    // ═══════════════════════════════════════════════════════════════════
    
    const preventTracking = () => {
        // Block Google Analytics
        window.ga = function() {};
        window.gtag = function() {};
        window.GoogleAnalyticsObject = null;
        
        // Block Facebook Pixel
        window.fbq = function() {};
        
        // Block hotjar
        window.hj = function() {};
        window.hjBootstrap = function() {};
        
        // Block crazyegg
        window.CE2 = { _queue: [], push: () => {} };
        
        // Block Mixpanel, Amplitude, Segment
        window.mixpanel = { track: () => {}, people: { set: () => {} } };
        window.amplitude = { logEvent: () => {} };
        window.analytics = { track: () => {} };
        
        // Block LinkedIn
        window._linkedin_data_partner_id = null;
        
        // Prevent tracking pixels
        const observer = new MutationObserver(mutations => {
            mutations.forEach(mutation => {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeName === 'IMG') {
                        const src = (node.src || '').toLowerCase();
                        if (src.includes('pixel') || src.includes('track') || 
                            src.includes('beacon') || src.includes('analytics')) {
                            node.src = '';
                            window.__operaAdBlocker.trackersBlocked++;
                        }
                    }
                });
            });
        });
        
        observer.observe(document.body, { childList: true, subtree: true });
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 7: COOKIE BANNER REMOVAL
    // ═══════════════════════════════════════════════════════════════════
    
    const removeCookieBanners = () => {
        // Find and remove cookie/privacy banners
        const bannerSelectors = [
            '[class*="cookie"]',
            '[id*="cookie"]',
            '[class*="gdpr"]', 
            '[id*="gdpr"]',
            '[class*="consent"]',
            '[id*="consent"]',
            '[class*="privacy-banner"]',
            '[class*="privacy-notice"]',
            '[role="dialog"][aria-label*="cookie"]',
            '[role="alertdialog"][aria-label*="cookie"]',
            '[class*="cookie-banner"]',
            '[class*="cookie-notification"]',
            '[class*="cookiebanner"]'
        ];
        
        let removed = 0;
        bannerSelectors.forEach(selector => {
            try {
                document.querySelectorAll(selector).forEach(el => {
                    if (el && el.offsetHeight && el.offsetHeight < 200) {
                        el.remove();
                        removed++;
                        window.__operaAdBlocker.cookieBannersRemoved++;
                    }
                });
            } catch (e) {}
        });
        
        // Click "Accept All" if it exists (smart consent)
        const acceptButtons = [
            'button[class*="accept"]',
            'button[class*="confirm"]',
            'button[class*="agree"]',
            'button[class*="accept-all"]',
            'button[type="submit"][class*="cookie"]',
            'a[class*="accept"]'
        ];
        
        setTimeout(() => {
            acceptButtons.forEach(selector => {
                try {
                    const btn = document.querySelector(selector);
                    if (btn && btn.offsetWidth > 0) {
                        btn.click();
                        window.__operaAdBlocker.cookieBannersRemoved++;
                    }
                } catch (e) {}
            });
        }, 500);
        
        return removed;
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 8: VIDEO AD BLOCKING (YouTube, Vimeo, etc.)
    // ═══════════════════════════════════════════════════════════════════
    
    const blockVideoAds = () => {
        // YouTube pre-roll ads
        if (window.location.hostname.includes('youtube.com') || 
            window.location.hostname.includes('youtu.be')) {
            
            const skipAd = () => {
                // Try to find and click the skip button
                const buttons = document.querySelectorAll('button.ytp-ad-skip-button');
                buttons.forEach(btn => {
                    if (btn.offsetHeight > 0) {
                        btn.click();
                        window.__operaAdBlocker.adsBlocked++;
                    }
                });
            };
            
            // Check every second for ads
            setInterval(skipAd, 1000);
            
            // Also block via player state
            const style = document.createElement('style');
            style.textContent = `
                .html5-video-player.ad-showing .html5-video-container video { display: none !important; }
                ytd-display-ad-renderer { display: none !important; }
                ytd-promoted-sparkles-web-renderer { display: none !important; }
            `;
            document.head.appendChild(style);
        }
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 9: NETWORK REQUEST BLOCKING
    // ═══════════════════════════════════════════════════════════════════
    
    const blockNetworkRequests = () => {
        // Block ad domains via fetch
        const trackerDomains = [
            'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
            'facebook.com/tr', 'analytics.google.com', 'googletagmanager.com',
            'taboola.com', 'outbrain.com', 'adnxs.com', 'criteo.com',
            'segment.com', 'mixpanel.com',
            
            // Manga specific ad domains
            'monetag.com', 'onetag.com', 'propellerads.com', 'exoclick.com', 
            'manga-ad', 'popads', 'popcash', 'adsterra', 'hilltopads',
            
            // Programmatic & exchange ad domains
            'amazon-adsystem.com', 'adsrvr.org', 'thetradedesk.com',
            'pubmatic.com', 'rubiconproject.com', 'indexexchange.com',
            'openx.net', 'sharethrough.com', 'triplelift.com',
            'media.net', 'carbonads.com', 'buysellads.com',
            'adroll.com', 'bidswitch.net', 'casalemedia.com',
            'smartadserver.com', 'yieldmo.com', 'admaven.com',
            'rtmark.net', 'wpadmngr.com', 'acint.net', 'acintpub.com',
            'highcpmgate.com', 'onclicka.com', 'clickadu.com',
            'adcash.com', 'bidvertiser.com', 'revcontent.com',
            'contentad.net', 'mgid.com', 'hilltopads.com',
            'richpush.co', 'pushground.com', 'push.house',
            'go.strpjmp.com', 'strpjmp.com', 'rdrtr.com',
            'clarity.ms', 'hotjar.com', 'fullstory.com',
            'logrocket.com', 'mouseflow.com', 'luckyorange.com'
        ];
        
        // Essential domains that must NEVER be blocked
        const essentialDomains = [
            'realme.com', 'heytap.com', 'heytapmobile.com', 'realmemobile.com',
            'oppo.com', 'oneplus.com', 'obus-in.dc.heytapmobile.com',
            'accounts.google.com', 'googleapis.com', 'gstatic.com',
            'googlevideo.com', 'ytimg.com', 'wikipedia.org', 'wikimedia.org',
            'login.microsoftonline.com', 'appleid.apple.com',
            'challenges.cloudflare.com', 'hcaptcha.com',
            'samsung.com', 'mi.com', 'xiaomi.com', 'bilibili.com'
        ];
        
        // Allowed domains (Safe list) for Manga sites mainly for images
        const allowedDomains = [
            'cdn', 'img', 'upload', 'assets', 'static', 'manga', 'chapter', 'page'
        ];
        
        const originalFetch = window.fetch;
        window.fetch = function(input, init) {
            const url = typeof input === 'string' ? input : input?.url || '';
            const urlLower = url.toLowerCase();
            
            // Never block essential domains
            if (essentialDomains.some(d => urlLower.includes(d))) {
               return originalFetch.apply(this, arguments);
            }
            
            // Never block same-origin requests
            try {
                const urlObj = new URL(url, window.location.href);
                if (urlObj.hostname === window.location.hostname) {
                    return originalFetch.apply(this, arguments);
                }
            } catch(e) {}
            
            // Allow if it looks like a content image
            if (allowedDomains.some(d => urlLower.includes(d))) {
               return originalFetch.apply(this, arguments);
            }
            
            for (let domain of trackerDomains) {
                if (urlLower.includes(domain)) {
                    console.log('[OperaBlocker] Blocked:', url.substring(0, 50));
                    window.__operaAdBlocker.adsBlocked++;
                    return Promise.reject(new Error('Blocked'));
                }
            }
            
            return originalFetch.apply(this, arguments);
        };
        
        // Block ad domains via XMLHttpRequest
        const originalXhrOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            const urlStr = (typeof url === 'string' ? url : url?.toString?.() || '').toLowerCase();
            
            if (essentialDomains.some(d => urlStr.includes(d))) {
                return originalXhrOpen.apply(this, arguments);
            }
            
            try {
                const urlObj = new URL(urlStr, window.location.href);
                if (urlObj.hostname === window.location.hostname) {
                    return originalXhrOpen.apply(this, arguments);
                }
            } catch(e) {}
            
            for (let domain of trackerDomains) {
                if (urlStr.includes(domain)) {
                    console.log('[OperaBlocker] XHR Blocked:', urlStr.substring(0, 50));
                    window.__operaAdBlocker.adsBlocked++;
                    this._blocked = true;
                    return originalXhrOpen.call(this, method, 'about:blank');
                }
            }
            return originalXhrOpen.apply(this, arguments);
        };
        
        const originalXhrSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function() {
            if (this._blocked) return;
            return originalXhrSend.apply(this, arguments);
        };
        
        // Block navigator.sendBeacon to ad/tracking domains
        const originalBeacon = navigator.sendBeacon;
        if (originalBeacon) {
            navigator.sendBeacon = function(url, data) {
                const urlStr = (typeof url === 'string' ? url : url?.toString?.() || '').toLowerCase();
                
                if (essentialDomains.some(d => urlStr.includes(d))) {
                    return originalBeacon.call(navigator, url, data);
                }
                
                for (let domain of trackerDomains) {
                    if (urlStr.includes(domain)) {
                        console.log('[OperaBlocker] Beacon Blocked:', urlStr.substring(0, 50));
                        window.__operaAdBlocker.trackersBlocked++;
                        return false;
                    }
                }
                return originalBeacon.call(navigator, url, data);
            };
        }
        
        // Block WebSocket connections to ad/tracking domains
        const OriginalWebSocket = window.WebSocket;
        window.WebSocket = function(url, protocols) {
            const urlStr = (typeof url === 'string' ? url : '').toLowerCase();
            for (let domain of trackerDomains) {
                if (urlStr.includes(domain)) {
                    console.log('[OperaBlocker] WebSocket Blocked:', urlStr.substring(0, 50));
                    window.__operaAdBlocker.trackersBlocked++;
                    throw new Error('Blocked');
                }
            }
            return new OriginalWebSocket(url, protocols);
        };
        window.WebSocket.prototype = OriginalWebSocket.prototype;
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 9.5: SCRIPT & IFRAME CREATION INTERCEPTION
    // Blocks ad scripts/iframes injected via document.createElement
    // ═══════════════════════════════════════════════════════════════════
    
    const blockElementCreation = () => {
        const adScriptDomains = [
            'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
            'adnxs.com', 'taboola.com', 'outbrain.com', 'criteo.com',
            'exoclick.com', 'propellerads.com', 'monetag.com', 'adsterra.com',
            'hilltopads.com', 'clickadu.com', 'popcash.net', 'popads.net',
            'mgid.com', 'revcontent.com', 'admaven.com', 'rtmark.net',
            'juicyads.com', 'trafficjunky.com', 'exdynsrv.com', 'tsyndicate.com',
            'onclicka.com', 'acint.net', 'wpadmngr.com', 'highcpmgate.com',
            'pagead2.googlesyndication.com', 'tpc.googlesyndication.com',
            'securepubads.g.doubleclick.net', 'pubads.g.doubleclick.net',
            'amazon-adsystem.com', 'adsrvr.org', 'thetradedesk.com',
            'prebid.org', 'rubiconproject.com', 'pubmatic.com',
            'indexexchange.com', 'sharethrough.com', 'triplelift.com',
            'openx.net', 'adroll.com', 'criteo.net', 'media.net',
            'carbonads.com', 'carbonads.net', 'buysellads.com'
        ];
        
        const adScriptPatterns = [
            'adsbygoogle', 'pagead', 'show_ads', 'ad_banner',
            'ad_popup', 'prebid', 'header-bidding', 'hb_adid',
            '/ads/', '/ad/', 'adserver', 'adservice',
            'analytics.js', 'gtag/js', 'gtm.js'
        ];
        
        const essentialScripts = [
            'googleapis.com', 'gstatic.com', 'googlevideo.com',
            'ytimg.com', 'cloudflare.com', 'challenges.cloudflare.com',
            'hcaptcha.com', 'recaptcha.net', 'accounts.google.com',
            'cdn.jsdelivr.net', 'unpkg.com', 'cdnjs.cloudflare.com',
            'jquery', 'react', 'vue', 'angular', 'bootstrap',
            'wikipedia.org', 'wikimedia.org'
        ];
        
        const origCreateElement = document.createElement.bind(document);
        document.createElement = function(tagName) {
            const el = origCreateElement(tagName);
            const tag = tagName.toLowerCase();
            
            if (tag === 'script' || tag === 'iframe') {
                const origSetAttribute = el.setAttribute.bind(el);
                el.setAttribute = function(name, value) {
                    if (name === 'src' || name === 'data-src') {
                        const lowerVal = (value || '').toLowerCase();
                        
                        // Allow essential scripts
                        if (essentialScripts.some(d => lowerVal.includes(d))) {
                            return origSetAttribute(name, value);
                        }
                        
                        // Block known ad domains
                        if (adScriptDomains.some(d => lowerVal.includes(d))) {
                            console.log('[OperaBlocker] Blocked ' + tag + ' creation:', value.substring(0, 80));
                            window.__operaAdBlocker.adsBlocked++;
                            return;
                        }
                        
                        // Block known ad URL patterns
                        if (adScriptPatterns.some(p => lowerVal.includes(p))) {
                            console.log('[OperaBlocker] Blocked ' + tag + ' pattern:', value.substring(0, 80));
                            window.__operaAdBlocker.adsBlocked++;
                            return;
                        }
                    }
                    return origSetAttribute(name, value);
                };
                
                // Also intercept src property setter
                const srcDescriptor = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src') || 
                                      Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'src');
                if (srcDescriptor && srcDescriptor.set) {
                    const origSrcSetter = srcDescriptor.set;
                    try {
                        Object.defineProperty(el, 'src', {
                            set: function(value) {
                                const lowerVal = (value || '').toLowerCase();
                                if (essentialScripts.some(d => lowerVal.includes(d))) {
                                    return origSrcSetter.call(this, value);
                                }
                                if (adScriptDomains.some(d => lowerVal.includes(d)) ||
                                    adScriptPatterns.some(p => lowerVal.includes(p))) {
                                    console.log('[OperaBlocker] Blocked ' + tag + ' src set:', value.substring(0, 80));
                                    window.__operaAdBlocker.adsBlocked++;
                                    return;
                                }
                                return origSrcSetter.call(this, value);
                            },
                            get: srcDescriptor.get ? srcDescriptor.get.bind(el) : undefined,
                            configurable: true
                        });
                    } catch(e) {}
                }
            }
            
            return el;
        };
        
        // Also intercept insertAdjacentHTML which can inject ad elements
        const origInsertAdjacentHTML = Element.prototype.insertAdjacentHTML;
        Element.prototype.insertAdjacentHTML = function(position, text) {
            const lowerText = (text || '').toLowerCase();
            // Block if the HTML contains ad script/iframe sources
            if (adScriptDomains.some(d => lowerText.includes(d))) {
                console.log('[OperaBlocker] Blocked insertAdjacentHTML with ad content');
                window.__operaAdBlocker.adsBlocked++;
                return;
            }
            return origInsertAdjacentHTML.call(this, position, text);
        };
    };
    
    // ═══════════════════════════════════════════════════════════════════
    // PART 10: INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════
    
    // Run CSS injection immediately
    injectCss();
    
    // Initialize protections
    injectHistoryProtection();
    injectClickProtection();
    injectTimeoutsProtection(); // Be careful with this one
    
    // Remove elements when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            removeAdElements();
            if (config.preventTracking) preventTracking();
            if (config.removeCookieBanners) removeCookieBanners();
            if (config.blockVideoAds) blockVideoAds();
        });
    } else {
        removeAdElements();
        if (config.preventTracking) preventTracking();
        if (config.removeCookieBanners) removeCookieBanners();
        if (config.blockVideoAds) blockVideoAds();
    }
    
    // Also run after page load to catch lazy-loaded ads
    window.addEventListener('load', () => {
        removeAdElements();
        removeCookieBanners();
    });
    
    // Monitor for dynamically injected ads
    const mutationObserver = new MutationObserver(() => {
        removeAdElements();
    });
    
    mutationObserver.observe(document.body, {
        childList: true,
        subtree: true
    });
    
    // Element creation interception (blocks createElement ad injection)
    blockElementCreation();
    
    // Network blocking
    blockNetworkRequests();
    
    // Log stats
    console.log('[OperaBlocker] Active - v$BLOCKER_VERSION');
    console.log('[OperaBlocker] Stats:', window.__operaAdBlocker);
})();
        """.trimIndent()
    }
    
    /**
     * Get blocking statistics
     */
    fun getStats(domain: String? = null): AdBlockStats {
        return if (domain != null) {
            statsPerDomain.getOrDefault(domain, AdBlockStats())
        } else {
            AdBlockStats(
                adsBlocked = blockedCount.get(),
                trackersBlocked = trackersBlocked.get(),
                cookieBannersRemoved = cookieBannersRemoved.get()
            )
        }
    }
    
    /**
     * Record a blocked ad
     */
    fun recordBlocked(domain: String) {
        blockedCount.incrementAndGet()
        statsPerDomain.computeIfAbsent(domain) { AdBlockStats() }
            .apply { adsBlocked++ }
    }
    
    /**
     * Get blocker version
     */
    fun getVersion(): String = BLOCKER_VERSION
}
