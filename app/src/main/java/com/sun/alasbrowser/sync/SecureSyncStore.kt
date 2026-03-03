package com.sun.alasbrowser.sync

import android.content.Context
import com.sun.alasbrowser.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

interface SecureSyncStore {
    @Throws(IOException::class)
    fun upload(userKey: String, encryptedPayloadJson: String)

    @Throws(IOException::class)
    fun download(userKey: String): String?
}

class HttpSecureSyncStore(
    private val baseUrl: String,
    private val apiKey: String
) : SecureSyncStore {
    override fun upload(userKey: String, encryptedPayloadJson: String) {
        val endpoint = "$baseUrl/bookmarks/$userKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        conn.outputStream.use { it.write(encryptedPayloadJson.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IOException("Secure sync upload failed: HTTP $code")
        }
        conn.disconnect()
    }

    override fun download(userKey: String): String? {
        val endpoint = "$baseUrl/bookmarks/$userKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect()
            return null
        }
        if (code !in 200..299) {
            throw IOException("Secure sync download failed: HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }
}

class LocalFileSecureSyncStore(context: Context) : SecureSyncStore {
    private val dir = File(context.filesDir, "secure_sync").apply { mkdirs() }

    override fun upload(userKey: String, encryptedPayloadJson: String) {
        File(dir, "$userKey.bookmarks.sync.json").writeText(encryptedPayloadJson)
    }

    override fun download(userKey: String): String? {
        val file = File(dir, "$userKey.bookmarks.sync.json")
        return if (file.exists()) file.readText() else null
    }
}

fun createSecureSyncStore(context: Context): SecureSyncStore {
    val url = BuildConfig.SECURE_SYNC_URL.trim().trimEnd('/')
    return if (url.isNotBlank()) {
        HttpSecureSyncStore(url, BuildConfig.SECURE_SYNC_API_KEY)
    } else {
        LocalFileSecureSyncStore(context)
    }
}
