package com.sun.alasbrowser.sync

import android.content.Context
import com.sun.alasbrowser.data.Bookmark
import com.sun.alasbrowser.data.BrowserDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
private data class BookmarkSyncItem(
    val title: String,
    val url: String,
    val timestamp: Long
)

@Serializable
private data class BookmarkSyncPayload(
    val schemaVersion: Int = 1,
    val generatedAt: Long = System.currentTimeMillis(),
    val bookmarks: List<BookmarkSyncItem>
)

data class SecureSyncResult(
    val success: Boolean,
    val message: String
)

class SecureBookmarkSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val database = BrowserDatabase.getDatabase(appContext)
    private val store = createSecureSyncStore(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun uploadBookmarks(userId: String, passphrase: String): SecureSyncResult {
        if (passphrase.length < 8) {
            return SecureSyncResult(false, "Passphrase must be at least 8 characters")
        }
        val list = database.bookmarkDao().getAllBookmarksList()
        val payload = BookmarkSyncPayload(
            bookmarks = list.map { BookmarkSyncItem(it.title, it.url, it.timestamp) }
        )
        val plainJson = json.encodeToString(payload)
        val envelope = SecureSyncCrypto.encrypt(plainJson, passphrase)
        val envelopeJson = json.encodeToString(envelope)
        store.upload(hashUserId(userId), envelopeJson)
        return SecureSyncResult(true, "Secure bookmarks backup uploaded")
    }

    suspend fun downloadAndMergeBookmarks(userId: String, passphrase: String): SecureSyncResult {
        if (passphrase.length < 8) {
            return SecureSyncResult(false, "Passphrase must be at least 8 characters")
        }
        val blob = store.download(hashUserId(userId))
            ?: return SecureSyncResult(false, "No secure backup found")

        val payload = try {
            val envelope = json.decodeFromString<EncryptedSyncEnvelope>(blob)
            val plain = SecureSyncCrypto.decrypt(envelope, passphrase)
            json.decodeFromString<BookmarkSyncPayload>(plain)
        } catch (_: Exception) {
            return SecureSyncResult(false, "Decryption failed. Check your passphrase.")
        }

        val local = database.bookmarkDao().getAllBookmarksList()
        val mergedByUrl = linkedMapOf<String, BookmarkSyncItem>()

        for (item in local.map { BookmarkSyncItem(it.title, it.url, it.timestamp) }) {
            mergedByUrl[item.url] = item
        }
        for (item in payload.bookmarks) {
            val existing = mergedByUrl[item.url]
            if (existing == null || item.timestamp > existing.timestamp) {
                mergedByUrl[item.url] = item
            }
        }

        val merged = mergedByUrl.values.map {
            Bookmark(
                id = 0,
                title = it.title,
                url = it.url,
                timestamp = it.timestamp
            )
        }

        database.bookmarkDao().deleteAll()
        database.bookmarkDao().insertBookmarks(merged)
        return SecureSyncResult(true, "Secure bookmarks restored and merged")
    }

    private fun hashUserId(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(userId.trim().lowercase().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
