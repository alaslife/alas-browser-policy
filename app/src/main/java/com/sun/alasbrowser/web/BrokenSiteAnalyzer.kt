package com.sun.alasbrowser.web

import android.util.Log
import java.io.File

/**
 * Broken Site Analyzer & Auto-Learner
 * 
 * Detects sites with issues (broken dropdowns, non-functional UI, etc.)
 * and reports them to the remote server for automatic rule generation.
 * 
 * Inspired by:
 * - Firefox's WebCompat Reporter
 * - Chromium's Site Issues reporting
 * 
 * Simple heuristics to detect common UX issues:
 * ✗ Dropdowns not responding
 * ✗ Forms not submitting
 * ✗ Buttons appearing unclickable
 * ✗ Navigation loops (redirect spam)
 * ✗ White screen or long loading
 */
class BrokenSiteAnalyzer {
    
    companion object {
        private const val TAG = "BrokenSite"
        private const val MAX_ISSUES = 100
        private const val REPORT_COOLDOWN_MS = 60000  // 1 minute between reports per domain
    }

    private val issueLog = mutableListOf<BrokenSiteIssue>()
    private val reportedDomains = mutableMapOf<String, Long>()

    /**
     * Record a site interaction issue
     * 
     * Called when GeckoView detects UI problems (e.g., no response to taps)
     */
    fun recordDropdownFailure(url: String, details: String) {
        recordIssue(
            url = url,
            type = "dropdown_failure",
            severity = "high",
            details = details
        )
    }

    fun recordFormSubmitFailure(url: String, formSelector: String) {
        recordIssue(
            url = url,
            type = "form_submit_failure",
            severity = "high",
            details = "Form not responding: $formSelector"
        )
    }

    fun recordNavigationLoop(url: String, redirectCount: Int) {
        recordIssue(
            url = url,
            type = "navigation_loop",
            severity = "medium",
            details = "Redirect loop detected ($redirectCount redirects)"
        )
    }

    fun recordLongLoadTime(url: String, durationMs: Long) {
        if (durationMs > 30000) {  // Over 30 seconds
            recordIssue(
                url = url,
                type = "long_load_time",
                severity = "low",
                details = "Page took ${durationMs}ms to load"
            )
        }
    }

    fun recordWhiteScreen(url: String, screenshots: ByteArray? = null) {
        recordIssue(
            url = url,
            type = "white_screen",
            severity = "high",
            details = "Page rendered as blank/white"
        )
    }

    private fun recordIssue(
        url: String,
        type: String,
        severity: String,
        details: String
    ) {
        if (issueLog.size >= MAX_ISSUES) {
            issueLog.removeAt(0)  // FIFO eviction
        }

        val issue = BrokenSiteIssue(
            url = url,
            host = try { 
                java.net.URL(url).host ?: "unknown" 
            } catch (e: Exception) { 
                "unknown" 
            },
            type = type,
            severity = severity,
            details = details,
            timestamp = System.currentTimeMillis(),
            appVersion = getAppVersion()
        )

        issueLog.add(issue)
        Log.d(TAG, "🔴 Recorded issue: $type on ${issue.host}")
    }

    /**
     * Check if issue should be reported to server
     * Throttles per-domain to avoid spam
     */
    fun shouldReportIssue(host: String): Boolean {
        val lastReport = reportedDomains[host] ?: 0
        val now = System.currentTimeMillis()
        return (now - lastReport) > REPORT_COOLDOWN_MS
    }

    /**
     * Get latest issue for a domain (for debugging)
     */
    fun getLatestIssue(host: String): BrokenSiteIssue? {
        return issueLog.lastOrNull { it.host == host }
    }

    /**
     * Get all recorded issues
     */
    fun getAllIssues(): List<BrokenSiteIssue> = issueLog.toList()

    /**
     * Export issues as JSON for reporting
     * 
     * Format:
     * {
     *   "issues": [...],
     *   "count": 5,
     *   "reportedAt": "2026-02-08T12:00:00Z"
     * }
     */
    fun exportAsJson(): String {
        val now = System.currentTimeMillis()
        val issues = issueLog.take(10)  // Latest 10 issues
        
        val json = buildString {
            append("{\n")
            append("  \"issues\": [\n")
            
            issues.forEachIndexed { index, issue ->
                append(issue.toJson())
                if (index < issues.size - 1) append(",")
                append("\n")
            }
            
            append("  ],\n")
            append("  \"count\": ${issueLog.size},\n")
            append("  \"reportedAt\": \"${java.time.Instant.now()}\"\n")
            append("}\n")
        }
        
        return json
    }

    /**
     * Report issues to remote server
     * 
     * Server endpoint: POST /api/broken-sites
     * Returns: { "newRules": [...] } with auto-generated fixes
     */
    suspend fun reportIssues(onNewRules: (List<SiteCompatibilityRule>) -> Unit) {
        if (issueLog.isEmpty()) {
            Log.d(TAG, "No issues to report")
            return
        }

        try {
            val json = exportAsJson()
            Log.d(TAG, "📤 Reporting ${issueLog.size} issues to server")
            
            // TODO: Implement actual HTTP POST when backend is ready
            // For now, log the payload
            Log.d(TAG, "Report payload: $json")
            
            // Clear after reporting
            issueLog.clear()
            Log.d(TAG, "✓ Cleared issue log after reporting")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report issues", e)
        }
    }

    private fun getAppVersion(): String {
        // Would read from BuildConfig or PackageManager in real app
        return "1.0.0"
    }

    /**
     * Clear all recorded issues
     */
    fun clear() {
        issueLog.clear()
        reportedDomains.clear()
        Log.d(TAG, "✓ Cleared all issues")
    }
}

/**
 * Single broken site issue record
 */
data class BrokenSiteIssue(
    val url: String,
    val host: String,
    val type: String,  // dropdown_failure, form_submit_failure, navigation_loop, etc.
    val severity: String,  // high, medium, low
    val details: String,
    val timestamp: Long,
    val appVersion: String
) {
    fun toJson(): String {
        return """    {
      "url": "$url",
      "host": "$host",
      "type": "$type",
      "severity": "$severity",
      "details": "${details.replace("\"", "\\\"")}",
      "timestamp": $timestamp,
      "appVersion": "$appVersion"
    }"""
    }
}
