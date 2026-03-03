package com.sun.alasbrowser.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SearchEngine(
    val displayName: String,
    val searchUrl: String,
    val iconUrl: String? = null   // Optional: for UI icons
) {
    GOOGLE(
        "Google",
        "https://www.google.com/search?q=",
        "https://www.google.com/favicon.ico"
    ),
    DUCKDUCKGO(
        "DuckDuckGo",
        "https://duckduckgo.com/?q=",
        "https://duckduckgo.com/favicon.ico"
    ),
    BRAVE(
        "Brave",
        "https://search.brave.com/search?q=",
        "https://search.brave.com/favicon.ico"
    ),
    BING(
        "Bing",
        "https://www.bing.com/search?q=",
        "https://www.bing.com/favicon.ico"
    ),
    YAHOO(
        "Yahoo",
        "https://search.yahoo.com/search?p=",
        "https://search.yahoo.com/favicon.ico"
    ),
    ECOSIA(
        "Ecosia",
        "https://www.ecosia.org/search?q=",
        "https://www.ecosia.org/favicon.ico"
    ),
    PERPLEXITY(
        "Perplexity",
        "https://www.perplexity.ai/search?q=",
        "https://www.perplexity.ai/favicon.ico"
    );

    fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return "$searchUrl$encoded"
    }
}
