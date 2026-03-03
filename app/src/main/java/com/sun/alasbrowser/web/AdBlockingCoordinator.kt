package com.sun.alasbrowser.web

import android.util.Log
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

data class BlockDecision(
    val block: Boolean,
    val reason: String = ""
)

data class InjectionPlan(
    val injectAdBlocker: Boolean = true,
    val injectYouTubeBlocker: Boolean = false,
    val injectAntiAdblockBypass: Boolean = false,
    val injectImageProtection: Boolean = true,
    val injectStoragerelayShim: Boolean = false,
    val disableAllInjection: Boolean = false
)

object AdBlockingCoordinator {
    private const val TAG = "AdBlockCoord"

    /**
     * Decide whether to block a network request
     * Called from WebClient.shouldInterceptRequest
     */
    fun shouldInterceptRequest(
        tabId: String?,
        pageUrl: String?,
        requestUrl: String,
        isMainFrame: Boolean,
        adBlockEnabled: Boolean
    ): BlockDecision {
        if (!adBlockEnabled) return BlockDecision(false, "ad_block_disabled")
        if (isMainFrame) return BlockDecision(false, "main_frame")

        if (tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId)) {
            return BlockDecision(false, "auth_mode")
        }

        if (pageUrl != null && AuthCompatibilityEngine.evaluate(pageUrl).active) {
            return BlockDecision(false, "auth_page")
        }

        val host = extractHost(requestUrl)

        val essentialAllowlist = listOf(
            "hcaptcha.com", "assets.hcaptcha.com",
            "challenges.cloudflare.com", "challenge.cloudflare.com", "cf-ns.com",
            "accounts.google.com", "gstatic.com", "cloudflareinsights.com",
            "wikipedia.org", "wikimedia.org",
            "realme.com", "heytap.com", "heytapmobile.com", "realmemobile.com",
            "oppo.com", "oneplus.com", "samsung.com", "mi.com", "xiaomi.com",
            "recaptcha.net", "www.google.com"
        )
        if (essentialAllowlist.any { host.endsWith(it) }) {
            return BlockDecision(false, "essential_domain")
        }

        if (SimpleAdBlocker.YOUTUBE_AD_PATTERNS.any { requestUrl.contains(it) }) {
            return BlockDecision(true, "youtube_ad_pattern")
        }

        if (SimpleAdBlocker.shouldBlock(requestUrl)) {
            return BlockDecision(true, "ad_domain_or_pattern")
        }

        if (SimpleAdBlocker.isPopupAd(requestUrl)) {
            return BlockDecision(true, "popup_ad_domain")
        }

        return BlockDecision(false, "allowed")
    }

    /**
     * Decide whether a navigation should be blocked (popup/redirect)
     * Called from WebClient.shouldOverrideUrlLoading
     */
    fun shouldBlockNavigation(
        tabId: String?,
        currentUrl: String?,
        targetUrl: String,
        adBlockEnabled: Boolean,
        timeSinceGesture: Long
    ): BlockDecision {
        if (!adBlockEnabled) return BlockDecision(false, "ad_block_disabled")

        if (tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId)) {
            return BlockDecision(false, "auth_mode")
        }

        if (AuthCompatibilityEngine.evaluate(targetUrl).active) {
            return BlockDecision(false, "auth_target")
        }

        if (SimpleAdBlocker.isDownloadCdnUrl(targetUrl)) {
            return BlockDecision(false, "download_cdn")
        }

        if (SimpleAdBlocker.isPopupAd(targetUrl)) {
            return BlockDecision(true, "popup_ad")
        }

        if (isYouTubeAdRedirect(targetUrl, currentUrl)) {
            return BlockDecision(true, "youtube_ad_redirect")
        }

        val isInProtectionWindow = timeSinceGesture in 50..4000
        if (isInProtectionWindow) {
            val lowerUrl = targetUrl.lowercase()
            val isKnownAd = SimpleAdBlocker.POPUP_AD_DOMAINS.any { lowerUrl.contains(it) } ||
                lowerUrl.contains("flirt") || lowerUrl.contains("dating") ||
                lowerUrl.contains("casino") || lowerUrl.contains("betting") ||
                lowerUrl.contains("1xbet") || lowerUrl.contains("adult") ||
                lowerUrl.contains("monetag") || lowerUrl.contains("consist.org")
            if (isKnownAd) {
                return BlockDecision(true, "ad_in_protection_window")
            }
        }

        return BlockDecision(false, "allowed")
    }

    /**
     * Get the injection plan for a page
     * Called from WebClient.onPageFinished
     */
    fun getInjectionPlan(
        tabId: String?,
        url: String,
        adBlockEnabled: Boolean
    ): InjectionPlan {
        if (url.isBlank()) return InjectionPlan(disableAllInjection = true)

        val lowerUrl = url.lowercase()

        val isAuthMode = tabId != null && AuthCompatibilityEngine.isTabInAuthMode(tabId)
        val authDecision = AuthCompatibilityEngine.evaluate(url)

        if (isAuthMode || authDecision.active) {
            return InjectionPlan(
                injectAdBlocker = false,
                injectYouTubeBlocker = false,
                injectAntiAdblockBypass = false,
                injectImageProtection = true,
                injectStoragerelayShim = authDecision.allowStoragerelay,
                disableAllInjection = false
            )
        }

        val isEssentialSite = lowerUrl.contains("realme.com") ||
            lowerUrl.contains("heytap.com") || lowerUrl.contains("heytapmobile.com") ||
            lowerUrl.contains("oppo.com") || lowerUrl.contains("oneplus.com") ||
            lowerUrl.contains("samsung.com") || lowerUrl.contains("mi.com") ||
            lowerUrl.contains("xiaomi.com")
        if (isEssentialSite) {
            return InjectionPlan(
                injectAdBlocker = false,
                injectImageProtection = true
            )
        }

        if (SiteCompatibilityRegistry.shouldDisableJsInjection(url)) {
            return InjectionPlan(
                injectAdBlocker = false,
                injectImageProtection = true,
                injectYouTubeBlocker = isYouTubeSite(lowerUrl) && adBlockEnabled
            )
        }

        val isYouTube = isYouTubeSite(lowerUrl)

        return InjectionPlan(
            injectAdBlocker = adBlockEnabled,
            injectYouTubeBlocker = isYouTube && adBlockEnabled,
            injectAntiAdblockBypass = adBlockEnabled && shouldInjectAntiAdblock(lowerUrl),
            injectImageProtection = true,
            injectStoragerelayShim = false,
            disableAllInjection = false
        )
    }

    /**
     * Execute the injection plan on a WebView
     */
    fun executeInjectionPlan(
        view: WebView,
        plan: InjectionPlan,
        adBlockEnabled: Boolean,
        enableBackgroundPlayback: Boolean = false
    ) {
        if (plan.disableAllInjection) return

        if (plan.injectAdBlocker) {
            try {
                view.evaluateJavascript(OperaAdBlockerPro.getOperaLevelBlockerScript(), null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject ad blocker", e)
            }
        }

        if (plan.injectStoragerelayShim) {
            try {
                view.evaluateJavascript(AuthCompatibilityEngine.getStoragerelayShimScript(), null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject storagerelay shim", e)
            }
        }

        if (plan.injectAntiAdblockBypass) {
            try {
                view.evaluateJavascript(getAntiAdblockBypassScript(), null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject anti-adblock bypass", e)
            }
        }
    }

    @Suppress("MaxLineLength")
    fun getAntiAdblockBypassScript(): String = """
        (function() {
            if (window.__alasAntiAdblock) return;
            window.__alasAntiAdblock = true;

            window.fuckAdBlock = undefined;
            window.blockAdBlock = undefined;
            window.BlockAdBlock = undefined;
            window.FuckAdBlock = undefined;
            window.adblockDetector = undefined;
            window.adblock = false;
            window.canRunAds = true;
            window.isAdBlockActive = false;

            var bait = document.createElement('div');
            bait.id = 'adblock-test';
            bait.className = 'ads ad adsbox doubleclick ad-placement carbon-ads';
            bait.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:1px;height:1px;';
            bait.innerHTML = '&nbsp;';
            document.body && document.body.appendChild(bait);

            if (typeof window.detectAdBlock === 'function') {
                window.detectAdBlock = function() { return false; };
            }

            var style = document.createElement('style');
            style.textContent = [
                '[class*="adblock-notice"]',
                '[class*="adblock-warning"]',
                '[class*="adblock-detected"]',
                '[id*="adblock-notice"]',
                '[id*="adblock-warning"]',
                '[id*="adblock-detected"]',
                '[class*="disable-adblock"]',
                '[id*="disable-adblock"]',
                '{ display: none !important; }'
            ].join(',');
            (document.head || document.documentElement).appendChild(style);

            var observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType !== 1) return;
                        var cls = (node.className || '').toLowerCase();
                        var id = (node.id || '').toLowerCase();
                        var text = (node.textContent || '').toLowerCase();
                        if ((cls.includes('adblock') || id.includes('adblock') ||
                             cls.includes('ad-block') || id.includes('ad-block')) &&
                            (text.includes('disable') || text.includes('whitelist') ||
                             text.includes('turn off') || text.includes('detected'))) {
                            node.style.display = 'none';
                            var backdrop = document.querySelector('.modal-backdrop, .overlay-backdrop');
                            if (backdrop) backdrop.style.display = 'none';
                            document.body.style.overflow = 'auto';
                        }
                    });
                });
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }

            console.log('[Alas] Anti-adblock bypass active');
        })();
    """.trimIndent()

    private fun isYouTubeSite(lowerUrl: String): Boolean {
        return lowerUrl.contains("youtube.com") ||
            lowerUrl.contains("youtu.be") ||
            lowerUrl.contains("music.youtube.com")
    }

    private fun isYouTubeAdRedirect(targetUrl: String, currentUrl: String?): Boolean {
        if (currentUrl == null) return false
        val lowerTarget = targetUrl.lowercase()
        val isFromNonYouTube = !currentUrl.lowercase().contains("youtube.com")
        val isToYouTubeAd = lowerTarget.contains("youtube.com") &&
            SimpleAdBlocker.YOUTUBE_AD_PATTERNS.any { lowerTarget.contains(it) }
        return isFromNonYouTube && isToYouTubeAd
    }

    private fun shouldInjectAntiAdblock(lowerUrl: String): Boolean {
        val antiAdblockSites = listOf(
            // News sites
            "forbes.com", "wired.com", "businessinsider.com",
            "independent.co.uk", "theatlantic.com",
            "bild.de", "spiegel.de",
            // Additional news/media
            "theguardian.com", "dailymail.co.uk", "mirror.co.uk",
            "express.co.uk", "telegraph.co.uk", "standard.co.uk",
            "huffpost.com", "buzzfeed.com", "vice.com",
            "cnet.com", "techradar.com", "tomsguide.com",
            "pcmag.com", "zdnet.com", "computerworld.com",
            // Streaming/entertainment
            "crunchyroll.com", "funimation.com",
            "fmovies.to", "123movies", "putlocker",
            // Manga/anime sites
            "mangakakalot.com", "manganato.com", "mangadex.org",
            "kissmanga.com", "mangapark.net", "mangahere.cc",
            "mangafox.me", "readm.org", "mangareader.to",
            "kingofshojo.com", "mangabuddy.com",
            // APK/download sites
            "liteapks.com", "apkpure.com", "apkmirror.com",
            "happymod.com", "an1.com", "modyolo.com",
            // Sports streaming
            "totalsportek.com", "sportsurge.net", "buffstreams",
            // General sites with anti-adblock
            "twitch.tv", "spotify.com", "hulu.com",
            "quora.com", "stackoverflow.com",
            "instructables.com", "wikihow.com"
        )
        return antiAdblockSites.any { lowerUrl.contains(it) }
    }

    private fun extractHost(url: String): String {
        return try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) { "" }
    }

    fun createBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
