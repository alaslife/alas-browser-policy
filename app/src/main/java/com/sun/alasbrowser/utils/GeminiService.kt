package com.sun.alasbrowser.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val API_KEY = "AIzaSyDBtI8e_IM5cPPYHtVjrHVi77ylT-6sDec" // In production, move to local.properties
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeText(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Truncate text if too long to avoid token limits (Gemini Flash has ~1M context, but let's be safe/efficient)
            // 100k chars is plenty for a summary
            val safeText = if (text.length > 100000) text.substring(0, 100000) else text
            
            val jsonBody = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Summarize the following article concisely. Use 3-5 short bullet points. Each bullet should be 1 sentence max. Be direct, no filler phrases like \"The article states\" or \"Based on the context\".\\n\\n$safeText")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$API_KEY")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("API Error: ${response.code} - $responseBody"))
            }

            if (responseBody == null) {
                return@withContext Result.failure(IOException("Empty response"))
            }

            // Parse response
            // Structure: { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val summary = parts.getJSONObject(0).optString("text")
                    return@withContext Result.success(summary)
                }
            }

            return@withContext Result.failure(IOException("Could not parse summary from response"))

        } catch (e: Exception) {
            Log.e("GeminiService", "Error summarizing text", e)
            return@withContext Result.failure(e)
        }
    }
    
    suspend fun askAboutPage(pageContent: String, question: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeText = if (pageContent.length > 100000) pageContent.substring(0, 100000) else pageContent
            
            val prompt = buildString {
                append("You are a smart browser assistant. Match your response length to the question complexity.\n\n")
                append("Rules:\n")
                append("- For definition/meaning questions (e.g. \"X mean\", \"what is X\", \"define X\"): Reply in 1 sentence, max 15 words. No quotes, no evidence, no article references.\n")
                append("- For simple factual questions: Reply in 1-2 sentences.\n")
                append("- For complex/analytical questions: Reply concisely in 2-4 sentences.\n")
                append("- NEVER start with \"Based on the context provided\" or \"The article states\".\n")
                append("- Be direct. No filler.\n\n")
                append("Page content:\n$safeText\n\n")
                append("Question: $question")
            }
            
            val jsonBody = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$API_KEY")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("API Error: ${response.code} - $responseBody"))
            }

            if (responseBody == null) {
                return@withContext Result.failure(IOException("Empty response"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val answer = parts.getJSONObject(0).optString("text")
                    return@withContext Result.success(answer)
                }
            }

            return@withContext Result.failure(IOException("Could not parse answer from response"))

        } catch (e: Exception) {
            Log.e("GeminiService", "Error asking question", e)
            return@withContext Result.failure(e)
        }
    }
}
