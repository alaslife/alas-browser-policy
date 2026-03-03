package com.sun.alasbrowser.web.adblock

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Learning engine for adaptive ad blocking.
 * Runs OFF the hot path - processes domains in background.
 */
class LearningEngine {
    
    private val queue = ConcurrentLinkedQueue<String>()
    
    /**
     * Report a blocked domain for learning.
     * This is a non-blocking operation.
     */
    fun report(domain: String) {
        queue.offer(domain)
    }
    
    /**
     * Drain the queue and return all reported domains.
     * Should be called periodically (every 30-60 seconds).
     */
    fun drain(): Set<String> {
        val out = mutableSetOf<String>()
        while (true) {
            val domain = queue.poll() ?: break
            out.add(domain)
        }
        return out
    }
    
    /**
     * Get current queue size (for monitoring).
     */
    fun queueSize(): Int = queue.size
}
