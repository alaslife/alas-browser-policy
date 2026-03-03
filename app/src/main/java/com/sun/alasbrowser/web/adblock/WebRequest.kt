package com.sun.alasbrowser.web.adblock

/**
 * Represents a web request with type classification for intelligent blocking.
 */
data class WebRequest(
    val url: String,
    val host: String,
    val type: RequestType,
    val isThirdParty: Boolean
)
