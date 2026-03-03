package com.sun.alasbrowser.web

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Firefox-style Site Compatibility Rule
 * 
 * Defines per-site behavioral overrides instead of global hacks.
 * Inspired by Firefox's site compatibility layer.
 * 
 * @param hostPattern Regex pattern to match domain/host
 * @param disableJsInjection Don't inject AdBlocker/popup faker scripts
 * @param allowWindowOpen Allow window.open() calls (no popup blocking)
 * @param forceDesktopUA Override User-Agent to desktop version
 * @param disablePopupBlocking Disable all popup/redirect blocking
 * @param allowAggressiveRedirects Allow rapid redirects (for download flows)
 * @param isRemote Whether this rule came from remote server (for priority/tracking)
 * @param notes Documentation/reasoning
 */
data class SiteCompatibilityRule(
    val hostPattern: Regex,
    val disableJsInjection: Boolean = false,
    val allowWindowOpen: Boolean = false,
    val forceDesktopUA: Boolean = false,
    val disablePopupBlocking: Boolean = false,
    val allowAggressiveRedirects: Boolean = false,
    val isRemote: Boolean = false,
    val notes: String = ""
)

/**
 * Central Compatibility Registry
 * 
 * This is the single source of truth for site-specific compatibility rules.
 * Think of it like Firefox's about:compatibility system.
 * 
 * Merges:
 * - Built-in rules (hardcoded, always available)
 * - Remote rules (fetched from server, cached locally)
 * 
 * Rules are matched in order (first match wins).
 * Remote rules override built-in rules for the same host.
 * More specific patterns should come first.
 */
object SiteCompatibilityRegistry {
    private const val TAG = "SiteCompat"
    private var remoteRules: List<SiteCompatibilityRule> = emptyList()
    private val runtimeBypassDomains = ConcurrentHashMap.newKeySet<String>()

    private val builtInRules = listOf(
        // ============================================================
        // APK / MOD / APPLICATION DISTRIBUTION SITES
        // ============================================================
        // These sites have UI-heavy interfaces with dropdowns, version
        // selectors, and accordions that break with aggressive JS injection.
        // They need window.open() for download flows.
        // They also use aggressive redirects for anti-DDoS.

        SiteCompatibilityRule(
            hostPattern = Regex(".*liteapks\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "APK distribution: dropdowns + version selectors, complex download flows"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*modyolo\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "MOD APK: dropdowns for filtering, UI-heavy selection"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*apkmody\\.(?:io|com).*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "MOD APK distribution: dropdown filters, version selection"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*apkdone\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "APK distribution with version history"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*revdl\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "APK distribution with category filters"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*happymod\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "MOD APK: heavy dropdown UI for mod selection"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*apkpure\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "APK distribution with version selector dropdowns"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*apkmirror\\.com.*"),
            disableJsInjection = true,
            allowWindowOpen = true,
            allowAggressiveRedirects = true,
            notes = "APKMirror: dynamic download flow can break with aggressive JS/network ad blocking"
        ),

        // ============================================================
        // MEDIA SITES (YouTube, etc.)
        // ============================================================
        SiteCompatibilityRule(
            hostPattern = Regex(".*youtube\\.com.*"),
            allowWindowOpen = true,
            notes = "Video platform: disable popup blocking for player UI"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*youtu\\.be.*"),
            allowWindowOpen = true,
            notes = "YouTube shortlinks"
        ),

        // ============================================================
        // DEVELOPER SITES
        // ============================================================
        SiteCompatibilityRule(
            hostPattern = Regex(".*github\\.com.*"),
            forceDesktopUA = true,
            notes = "Better desktop UI for code browsing"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*stackoverflow\\.com.*"),
            forceDesktopUA = true,
            notes = "Q&A site: desktop version has better search/filtering"
        ),

        // ============================================================
        // USER GESTURE SITES
        // ============================================================
        // These sites rely on window.open() calls for legitimate features
        SiteCompatibilityRule(
            hostPattern = Regex(".*google\\..*"),
            allowWindowOpen = true,
            notes = "Google search and services"
        ),

        SiteCompatibilityRule(
            hostPattern = Regex(".*facebook\\.com.*"),
            allowWindowOpen = false,  // Block trackers
            notes = "Facebook: aggressive tracking, block most popups"
        ),
    )

    /**
     * Match a URL against all compatibility rules.
     * Returns the first matching rule, or null if no match.
     * 
     * Priority:
     * 1. Remote rules (fetched from server, can be updated without rebuild)
     * 2. Built-in rules (hardcoded, always available)
     * 
     * @param url Full URL to match
     * @return Matching SiteCompatibilityRule or null
     */
    fun match(url: String): SiteCompatibilityRule? {
        val host = try {
            java.net.URL(url).host?.lowercase() ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse URL: $url", e)
            return null
        }

        // Runtime auto-recovery rules (highest priority)
        val runtimeDomain = runtimeBypassDomains.firstOrNull { d ->
            host == d || host.endsWith(".$d")
        }
        if (runtimeDomain != null) {
            return SiteCompatibilityRule(
                hostPattern = Regex(".*${Regex.escape(runtimeDomain)}.*"),
                disableJsInjection = true,
                allowWindowOpen = true,
                disablePopupBlocking = true,
                allowAggressiveRedirects = true,
                notes = "Runtime auto-recovery for $runtimeDomain"
            )
        }

        // Check remote rules first (higher priority)
        val remoteMatch = remoteRules.firstOrNull { rule ->
            rule.hostPattern.matches(host)
        }
        if (remoteMatch != null) {
            Log.d(TAG, "✓ Remote rule matched for $host: ${remoteMatch.notes}")
            return remoteMatch
        }

        // Fall back to built-in rules
        val builtInMatch = builtInRules.firstOrNull { rule ->
            rule.hostPattern.matches(host)
        }
        if (builtInMatch != null) {
            Log.d(TAG, "✓ Built-in rule matched for $host: ${builtInMatch.notes}")
            return builtInMatch
        }

        return null
    }

    /**
     * Update registry with remote rules from server
     * Call this periodically and on app startup
     */
    fun updateRemoteRules(rules: List<SiteCompatibilityRule>) {
        remoteRules = rules
        Log.d(TAG, "✓ Updated with ${rules.size} remote rules")
    }

    /**
     * Check if JS injection should be disabled for this URL
     */
    fun shouldDisableJsInjection(url: String): Boolean {
        return match(url)?.disableJsInjection == true
    }

    /**
     * Check if window.open() should be allowed
     */
    fun shouldAllowWindowOpen(url: String): Boolean {
        return match(url)?.allowWindowOpen == true
    }

    /**
     * Check if popup blocking should be disabled
     */
    fun shouldDisablePopupBlocking(url: String): Boolean {
        return match(url)?.disablePopupBlocking == true
    }

    /**
     * Check if aggressive redirects should be allowed
     */
    fun shouldAllowAggressiveRedirects(url: String): Boolean {
        return match(url)?.allowAggressiveRedirects == true
    }

    /**
     * Check if User-Agent should be overridden to desktop
     */
    fun shouldForceDesktopUA(url: String): Boolean {
        return match(url)?.forceDesktopUA == true
    }

    /**
     * Add runtime compatibility bypass for hosts that exhibit white/blank rendering.
     * Applies to the host and subdomains for the current app process.
     */
    fun addRuntimeBypassForHost(hostOrUrl: String, reason: String = "auto-recovery") {
        val normalized = normalizeHost(hostOrUrl)
        if (normalized.isEmpty()) return
        if (runtimeBypassDomains.add(normalized)) {
            Log.w(TAG, "Added runtime compatibility bypass for $normalized ($reason)")
        }
    }

    private fun normalizeHost(hostOrUrl: String): String {
        val host = try {
            java.net.URL(hostOrUrl).host
        } catch (_: Exception) {
            hostOrUrl
        }
        return host.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
            .trim()
    }

    /**
     * Get all rules (both remote and built-in, for debugging)
     */
    fun getAllRules(): List<SiteCompatibilityRule> = remoteRules + builtInRules
}
