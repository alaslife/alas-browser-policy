package com.sun.alasbrowser.web.adblock

/**
 * High-performance domain blocker using reversed-host matching.
 * O(M) complexity instead of O(N×M) with contains().
 * 
 * Example: ads.google.com → moc.elgoog.sda
 * This allows efficient suffix matching for domain blocking.
 */
class DomainBlocker(adDomains: Set<String>) {
    
    // Stored as reversed: net.doubleclick
    private val domainSet: Set<String> = adDomains.map { it.reversedDomain() }.toSet()
    
    /**
     * Check if a host should be blocked.
     * Walks up the domain hierarchy checking each level.
     * 
     * Example: ads.google.com checks:
     * 1. moc.elgoog.sda
     * 2. moc.elgoog
     * 3. moc
     */
    fun isBlocked(host: String): Boolean {
        var current = host.reversedDomain()
        while (true) {
            if (domainSet.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot == -1) break
            current = current.substring(dot + 1)
        }
        return false
    }
    
    private fun String.reversedDomain(): String =
        split('.').asReversed().joinToString(".")
}
