package com.sun.alasbrowser.engine

import android.util.Log

object CompatibilityHintDetector {
    private const val TAG = "CompatHint"
    
    private val siteIssues = mutableMapOf<String, MutableList<SiteIssue>>()
    
    private const val ISSUE_THRESHOLD = 2
    private const val ISSUE_WINDOW_MS = 30_000L
    
    enum class IssueType {
        DROPDOWN_FAILURE,
        PROMPT_DISMISSED,
        RENDER_ERROR,
        FORM_SUBMISSION_FAILURE
    }
    
    data class SiteIssue(
        val type: IssueType,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun recordIssue(hostname: String, type: IssueType) {
        val host = hostname.lowercase()
        val issues = siteIssues.getOrPut(host) { mutableListOf() }
        issues.add(SiteIssue(type))
        
        val cutoff = System.currentTimeMillis() - ISSUE_WINDOW_MS
        issues.removeAll { it.timestamp < cutoff }
        
        Log.d(TAG, "Issue recorded for $host: $type (total recent: ${issues.size})")
    }
    
    fun shouldRecommendWebView(hostname: String): Boolean {
        val host = hostname.lowercase()
        if (!SiteEnginePolicy.shouldShowHint(host)) return false
        
        val issues = siteIssues[host] ?: return false
        val cutoff = System.currentTimeMillis() - ISSUE_WINDOW_MS
        val recentIssues = issues.count { it.timestamp >= cutoff }
        
        return recentIssues >= ISSUE_THRESHOLD
    }
    
    fun clearIssues(hostname: String) {
        siteIssues.remove(hostname.lowercase())
    }
    
    fun clearAll() {
        siteIssues.clear()
    }
}
