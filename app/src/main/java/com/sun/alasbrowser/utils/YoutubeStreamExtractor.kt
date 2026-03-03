package com.sun.alasbrowser.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object YoutubeStreamExtractor {
    private const val TAG = "YoutubeStreamExtractor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val INVIDIOUS_INSTANCES = listOf(
        "https://invidious.snopyta.org",
        "https://invidious.io.lol",
        "https://vid.puffyan.us",
        "https://invidious.kavin.rocks",
        "https://inv.riverside.rocks",
        "https://invidious.namazso.eu"
    )
    
    fun initialize() {
        Log.d(TAG, "YouTube stream extractor initialized")
    }
    
    fun extractStreamUrl(
        youtubeUrl: String, 
        callback: (streamUrl: String?, title: String?, thumbnail: String?, author: String?) -> Unit
    ) {
        scope.launch {
            try {
                val videoId = extractVideoId(youtubeUrl)
                if (videoId == null) {
                    Log.e(TAG, "Could not extract video ID from $youtubeUrl")
                    withContext(Dispatchers.Main) {
                        callback(null, null, null, null)
                    }
                    return@launch
                }
                
                Log.d(TAG, "Extracting stream for videoId: $videoId")
                val thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                
                var json: JSONObject? = null
                var lastError: String? = null
                
                for (instance in INVIDIOUS_INSTANCES) {
                    try {
                        val invidiousUrl = "$instance/api/v1/videos/$videoId"
                        val response = URL(invidiousUrl).openConnection().apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                        }.getInputStream().bufferedReader().readText()
                        
                        json = JSONObject(response)
                        if (json.has("adaptiveFormats") || json.has("formatStreams")) {
                            break
                        } else {
                            Log.w(TAG, "Instance $instance returned no formats for $videoId")
                            json = null
                        }
                    } catch (e: Exception) {
                        lastError = e.message
                        Log.w(TAG, "Failed with instance $instance: ${e.message}")
                        continue
                    }
                }
                
                if (json == null) {
                    Log.e(TAG, "All Invidious instances failed for $videoId. Last error: $lastError")
                    withContext(Dispatchers.Main) {
                        callback(null, null, thumbnail, null)
                    }
                    return@launch
                }
                
                val title = json.optString("title", "YouTube Video")
                val author = json.optString("author", "Unknown")
                
                // Try adaptiveFormats (separate audio/video) first
                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                var bestAudioUrl: String? = null
                var highestBitrate = 0
                
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        val type = format.optString("type", "")
                        // Prefer audio-only streams
                        if (type.contains("audio/")) {
                            val bitrate = format.optInt("bitrate", 0)
                            if (bitrate > highestBitrate) {
                                highestBitrate = bitrate
                                bestAudioUrl = format.optString("url")
                            }
                        }
                    }
                }
                
                // Fallback to combined streams if no adaptive audio found
                if (bestAudioUrl == null) {
                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null && formatStreams.length() > 0) {
                        bestAudioUrl = formatStreams.getJSONObject(0).optString("url")
                    }
                }
                
                Log.d(TAG, "Found best audio URL: ${bestAudioUrl?.take(50)}...")
                
                withContext(Dispatchers.Main) {
                    callback(bestAudioUrl, title, thumbnail, author)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract YouTube stream: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(null, null, null, null)
                }
            }
        }
    }

    fun isYoutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }
    
    fun extractVideoId(url: String): String? {
        return try {
            val lower = url.lowercase()
            when {
                lower.contains("/watch?v=") -> {
                    url.substringAfter("v=").substringBefore("&").substringBefore("#")
                }
                lower.contains("youtu.be/") -> {
                    url.substringAfter("youtu.be/").substringBefore("?").substringBefore("#")
                }
                lower.contains("/embed/") -> {
                    url.substringAfter("embed/").substringBefore("?").substringBefore("#")
                }
                lower.contains("/v/") -> {
                    url.substringAfter("/v/").substringBefore("?").substringBefore("#")
                }
                lower.contains("/shorts/") -> {
                    url.substringAfter("/shorts/").substringBefore("?").substringBefore("#")
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
