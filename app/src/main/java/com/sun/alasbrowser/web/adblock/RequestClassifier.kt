package com.sun.alasbrowser.web.adblock

/**
 * Classifies web requests by type using MIME type and URL analysis.
 * This enables intelligent blocking - scripts are more likely to be ads than images.
 */
object RequestClassifier {
    
    /**
     * Classify a request based on MIME type and context.
     * 
     * @param url The request URL
     * @param mimeType MIME type from response headers (if available)
     * @param isMainFrame Whether this is the main document
     * @return Classified request type
     */
    fun classify(
        url: String,
        mimeType: String?,
        isMainFrame: Boolean
    ): RequestType {
        
        if (isMainFrame) return RequestType.DOCUMENT
        
        return when {
            mimeType?.contains("javascript") == true -> RequestType.SCRIPT
            mimeType?.contains("image") == true -> RequestType.IMAGE
            mimeType?.contains("video") == true -> RequestType.MEDIA
            mimeType?.contains("audio") == true -> RequestType.MEDIA
            mimeType?.contains("css") == true -> RequestType.STYLE
            mimeType?.contains("font") == true -> RequestType.FONT
            
            // Fallback to URL-based detection
            url.contains(".js") -> RequestType.SCRIPT
            url.contains(".css") -> RequestType.STYLE
            url.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|svg).*")) -> RequestType.IMAGE
            url.matches(Regex(".*\\.(mp4|webm|ogg|mp3|wav).*")) -> RequestType.MEDIA
            url.matches(Regex(".*\\.(woff|woff2|ttf|otf|eot).*")) -> RequestType.FONT
            
            else -> RequestType.OTHER
        }
    }
}
