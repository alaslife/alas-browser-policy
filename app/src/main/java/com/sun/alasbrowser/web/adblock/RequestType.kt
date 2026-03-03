package com.sun.alasbrowser.web.adblock

/**
 * Request type classification for intelligent ad blocking.
 * Different request types require different blocking strategies.
 */
enum class RequestType {
    DOCUMENT,   // Main page load
    SCRIPT,     // JavaScript files (primary ad vector)
    IMAGE,      // Images (including tracking pixels)
    MEDIA,      // Video/Audio
    XHR,        // XMLHttpRequest
    FETCH,      // Fetch API
    STYLE,      // CSS files
    FONT,       // Web fonts
    OTHER       // Everything else
}
