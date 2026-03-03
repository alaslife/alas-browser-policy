package com.sun.alasbrowser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SearchSuggestionService {
    
    private const val GOOGLE_SUGGESTION_URL = "https://suggestqueries.google.com/complete/search?client=firefox&q="
    
    suspend fun getSuggestions(query: String, searchEngine: SearchEngine = SearchEngine.GOOGLE): List<String> {
        if (query.isBlank()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                val url = URL("$GOOGLE_SUGGESTION_URL$encodedQuery")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseSuggestions(response)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {

                emptyList()
            }
        }
    }
    
    private fun parseSuggestions(response: String): List<String> {
        return try {
            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 1) {
                val suggestionsArray = jsonArray.getJSONArray(1)
                (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {

            emptyList()
        }
    }
}
