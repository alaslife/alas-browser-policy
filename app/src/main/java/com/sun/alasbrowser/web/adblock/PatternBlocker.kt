package com.sun.alasbrowser.web.adblock

/**
 * Simple Trie-based pattern matcher for URL patterns.
 * Much faster than checking each pattern with contains().
 * 
 * For production, consider using Aho-Corasick library:
 * implementation 'org.ahocorasick:ahocorasick:0.6.3'
 */
class PatternBlocker(patterns: Collection<String>) {
    
    private val patterns: Set<String> = patterns.map { it.lowercase() }.toSet()
    
    /**
     * Check if URL matches any pattern.
     * Currently uses simple contains for compatibility.
     * TODO: Implement Aho-Corasick for O(M) matching.
     */
    fun matches(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return patterns.any { lowerUrl.contains(it) }
    }
}
