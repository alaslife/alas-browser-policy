package com.sun.alasbrowser.web

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

enum class NavigationClassification {
    USER_NAVIGATION,
    AD_REDIRECT,
    AUTH_FLOW,
    DOWNLOAD_FLOW,
    YOUTUBE_AD_REDIRECT,
    SUSPICIOUS_REDIRECT,
    UNKNOWN
}

data class NavigationEvent(
    val tabId: String,
    val url: String,
    val previousUrl: String?,
    val hasUserGesture: Boolean,
    val timeSinceLastNav: Long,
    val isMainFrame: Boolean = true,
    val classification: NavigationClassification = NavigationClassification.UNKNOWN
)

object SmartBackEngine {
    private const val TAG = "SmartBack"
    private const val MAX_STACK_SIZE = 80
    private const val RAPID_REDIRECT_MS = 500L
    private const val SUSPICIOUS_REDIRECT_MS = 1500L

    private val realStacks = ConcurrentHashMap<String, java.util.ArrayDeque<String>>()

    private val lastGesture = ConcurrentHashMap<String, Long>()

    private val recentRedirects = ConcurrentHashMap<String, MutableList<RedirectEntry>>()

    private val lastCommitted = ConcurrentHashMap<String, String>()

    // Tracks whether the current gesture has already been used for a real navigation.
    // Once consumed, any further cross-domain navigation using the same gesture is an ad redirect.
    private val gestureConsumed = ConcurrentHashMap<String, Boolean>()

    private data class RedirectEntry(
        val from: String,
        val to: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val youtubeAdRedirectPatterns = setOf(
        "youtube.com/redirect",
        "googleadservices.com/pagead/aclk",
        "doubleclick.net/",
        "adurl=",
        "gclid=",
        "gbraid=",
        "wbraid=",
        "youtube.com/embed/",
        "youtube-nocookie.com/embed/",
        "&autoplay=1",
        "?autoplay=1"
    )

    private val adRedirectPatterns = setOf(
        "/redirect.php", "/redir.php", "/out.php", "/go.php",
        "/click.php", "/clk.php", "/track.php",
        "/aff_c?", "/aff_",
        "?redirect=", "&redirect=",
        "?url=http", "&url=http",
        "?target=http", "&target=http",
        "?destination=http", "&destination=http",
        "/popunder", "/popup", "/interstitial",
        "tsyndicate.com", "trkinator.com", "clksite.com",
        "clkrev.com", "strpjmp.com", "exdynsrv.com",
        "monetag.com", "propellerads.com"
    )

    fun onUserGesture(tabId: String) {
        lastGesture[tabId] = System.currentTimeMillis()
        gestureConsumed[tabId] = false
    }

    fun onNavigationEvent(
        tabId: String,
        url: String,
        previousUrl: String?,
        hasUserGesture: Boolean,
        timeSinceLastNav: Long
    ): NavigationClassification {
        if (url.isBlank() || url.startsWith("about:")) return NavigationClassification.UNKNOWN

        val classification = classifyNavigation(tabId, url, previousUrl, hasUserGesture, timeSinceLastNav)

        when (classification) {
            NavigationClassification.USER_NAVIGATION,
            NavigationClassification.AUTH_FLOW,
            NavigationClassification.DOWNLOAD_FLOW -> {
                pushToRealStack(tabId, url)
            }
            NavigationClassification.AD_REDIRECT,
            NavigationClassification.YOUTUBE_AD_REDIRECT,
            NavigationClassification.SUSPICIOUS_REDIRECT -> {
                trackRedirect(tabId, previousUrl ?: "", url)
                Log.d(TAG, "🚫 ${classification.name}: $url (not added to history)")
            }
            NavigationClassification.UNKNOWN -> {
                pushToRealStack(tabId, url)
            }
        }

        lastCommitted[tabId] = url
        return classification
    }

    private fun classifyNavigation(
        tabId: String,
        url: String,
        previousUrl: String?,
        hasUserGesture: Boolean,
        timeSinceLastNav: Long
    ): NavigationClassification {
        val lowerUrl = url.lowercase()

        // 1. Auth flow detection (highest priority - never interfere)
        val authDecision = AuthCompatibilityEngine.evaluate(url)
        if (authDecision.active) {
            return NavigationClassification.AUTH_FLOW
        }
        if (AuthCompatibilityEngine.isTabInAuthMode(tabId)) {
            return NavigationClassification.AUTH_FLOW
        }

        // 2. Download flow detection
        if (SimpleAdBlocker.isDownloadCdnUrl(url)) {
            return NavigationClassification.DOWNLOAD_FLOW
        }
        if (SimpleAdBlocker.isLegitimateDownloadSequence(url, previousUrl)) {
            return NavigationClassification.DOWNLOAD_FLOW
        }

        // 3. YouTube ad redirect detection (generalized)
        if (isYouTubeAdRedirect(url, previousUrl)) {
            return NavigationClassification.YOUTUBE_AD_REDIRECT
        }

        // 4. Known ad/popup domain
        if (SimpleAdBlocker.isPopupAd(url) || SimpleAdBlocker.shouldBlock(url)) {
            return NavigationClassification.AD_REDIRECT
        }

        // 5. Ad redirect URL patterns
        if (adRedirectPatterns.any { lowerUrl.contains(it) } && !hasUserGesture) {
            return NavigationClassification.AD_REDIRECT
        }

        // 6. Rapid redirect chain detection (no gesture + fast)
        if (!hasUserGesture && timeSinceLastNav < RAPID_REDIRECT_MS) {
            val prevHost = previousUrl?.let { extractHost(it) } ?: ""
            if (prevHost.isNotEmpty() && extractHost(url) != prevHost) {
                val redirectCount = getRecentRedirectCount(tabId, 2000)
                if (redirectCount >= 2) {
                    return NavigationClassification.AD_REDIRECT
                }
                return NavigationClassification.SUSPICIOUS_REDIRECT
            }
        }

        // 7. Suspicious redirect (fast cross-domain without gesture)
        if (!hasUserGesture && timeSinceLastNav < SUSPICIOUS_REDIRECT_MS) {
            val prevHost = previousUrl?.let { extractHost(it) } ?: ""
            if (prevHost.isNotEmpty() && extractHost(url) != prevHost && !SimpleAdBlocker.isDownloadCdnUrl(url)) {
                return NavigationClassification.SUSPICIOUS_REDIRECT
            }
        }

        // 7.5 Gesture consumption check — a single tap/click should only count for ONE navigation.
        // If the gesture was already consumed by a previous navigation, any subsequent
        // cross-domain redirect riding on the same gesture timestamp is an ad hijack.
        if (hasUserGesture) {
            val alreadyConsumed = gestureConsumed[tabId] == true
            if (alreadyConsumed) {
                // Gesture was already used for a real navigation — this is a follow-up redirect
                val prevHost = previousUrl?.let { extractHost(it) } ?: ""
                val curHost = extractHost(url)
                val isSameDomain = prevHost.isNotEmpty() && (curHost == prevHost
                        || curHost.endsWith(".$prevHost") || prevHost.endsWith(".$curHost"))
                if (!isSameDomain && !SimpleAdBlocker.isDownloadCdnUrl(url)) {
                    Log.d(TAG, "🚫 Ad hijacked consumed gesture: $previousUrl -> $url")
                    return NavigationClassification.AD_REDIRECT
                }
            }
            // Mark gesture as consumed — the first cross-domain navigation gets it
            gestureConsumed[tabId] = true
            return NavigationClassification.USER_NAVIGATION
        }

        // 9. Same-domain navigation without gesture (e.g., SPA, auto-redirect) = likely fine
        val host = extractHost(url)
        val prevHost = previousUrl?.let { extractHost(it) } ?: ""
        if (host == prevHost || host.endsWith(".$prevHost") || prevHost.endsWith(".$host")) {
            return NavigationClassification.USER_NAVIGATION
        }

        return NavigationClassification.USER_NAVIGATION
    }

    fun isYouTubeAdRedirect(url: String, referrer: String?): Boolean {
        val lowerUrl = url.lowercase()

        val isYouTube = lowerUrl.contains("youtube.com") ||
                lowerUrl.contains("youtu.be") ||
                lowerUrl.contains("googlevideo.com")

        if (!isYouTube) {
            return youtubeAdRedirectPatterns.any { lowerUrl.contains(it) } &&
                    (lowerUrl.contains("adurl=") || lowerUrl.contains("gclid="))
        }

        val hasAdParams = youtubeAdRedirectPatterns.any { lowerUrl.contains(it) }

        val referrerHost = referrer?.let { extractHost(it) } ?: ""
        val isFromExternalSite = referrerHost.isNotEmpty() &&
                !referrerHost.contains("youtube") &&
                !referrerHost.contains("google")

        if (isFromExternalSite) {
            Log.d(TAG, "🎬 YouTube ad redirect from external site: $referrerHost -> YouTube")
            return true
        }

        if (hasAdParams) {
            Log.d(TAG, "🎬 YouTube ad redirect detected: $url")
            return true
        }

        return false
    }

    fun getSafeBackUrl(tabId: String, currentUrl: String): String? {
        val stack = realStacks[tabId] ?: return null
        if (stack.isEmpty()) return null

        val normCurrent = normalizeUrl(currentUrl)
        val top = stack.peekLast() ?: return null
        val normTop = normalizeUrl(top)

        if (normTop == normCurrent) {
            val list = stack.toList()
            if (list.size >= 2) {
                for (i in list.lastIndex - 1 downTo 0) {
                    val candidate = list[i]
                    if (normalizeUrl(candidate) != normCurrent && !isAdPage(candidate)) {
                        return candidate
                    }
                }
            }
            return null
        }

        if (!isAdPage(top)) {
            return top
        }

        val list = stack.toList()
        for (i in list.lastIndex downTo 0) {
            if (!isAdPage(list[i])) {
                return list[i]
            }
        }
        return null
    }

    fun popCurrentUrl(tabId: String): Boolean {
        val stack = realStacks[tabId] ?: return false
        if (stack.isEmpty()) return false
        stack.removeLast()
        return true
    }

    fun canGoBack(tabId: String): Boolean {
        val stack = realStacks[tabId] ?: return false
        return stack.size >= 2
    }

    fun peekPreviousUrl(tabId: String): String? {
        val stack = realStacks[tabId] ?: return null
        val list = stack.toList()
        return if (list.size >= 2) list[list.lastIndex - 1] else null
    }

    fun getRealHistory(tabId: String, count: Int = 10): List<String> {
        val stack = realStacks[tabId] ?: return emptyList()
        return stack.toList().takeLast(count)
    }

    fun hadRecentAdRedirect(tabId: String, withinMs: Long = 2000): Boolean {
        val redirects = recentRedirects[tabId] ?: return false
        val now = System.currentTimeMillis()
        return redirects.any { now - it.timestamp < withinMs }
    }

    fun clearTab(tabId: String) {
        realStacks.remove(tabId)
        lastGesture.remove(tabId)
        gestureConsumed.remove(tabId)
        recentRedirects.remove(tabId)
        lastCommitted.remove(tabId)
    }

    private fun pushToRealStack(tabId: String, url: String) {
        val stack = realStacks.getOrPut(tabId) { java.util.ArrayDeque() }
        if (stack.peekLast() != url) {
            if (stack.size >= MAX_STACK_SIZE) stack.removeFirst()
            stack.addLast(url)
            Log.d(TAG, "📍 Real navigation: $url (stack size: ${stack.size})")
        }
    }

    private fun trackRedirect(tabId: String, from: String, to: String) {
        val redirects = recentRedirects.getOrPut(tabId) { mutableListOf() }
        redirects.add(RedirectEntry(from, to))
        while (redirects.size > 10) redirects.removeAt(0)
    }

    private fun getRecentRedirectCount(tabId: String, withinMs: Long): Int {
        val redirects = recentRedirects[tabId] ?: return 0
        val cutoff = System.currentTimeMillis() - withinMs
        return redirects.count { it.timestamp > cutoff }
    }

    private fun isAdPage(url: String): Boolean {
        return SimpleAdBlocker.isPopupAd(url) ||
                SimpleAdBlocker.shouldBlock(url) ||
                SimpleAdBlocker.isMangaAdPage(url) ||
                adRedirectPatterns.any { url.lowercase().contains(it) }
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/').lowercase()
    }

    private fun extractHost(url: String): String {
        return try {
            url.lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/").firstOrNull()
                ?.split(":")?.firstOrNull() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
