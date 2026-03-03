package com.sun.alasbrowser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val status: Int, // 0: Pending, 1: Running, 2: Paused, 3: Completed, 4: Failed
    val totalSize: Long,
    val downloadedSize: Long,
    val filePath: String,
    val timestamp: Long,
    val userAgent: String,
    val cookies: String? = null,
    val referer: String? = null,
    val description: String = "",
    // New fields for Per-Page Management
    val pageTitle: String = "",
    val pageUrl: String = "",
    val pageFavicon: String? = null,
    // Field to track progress for stuck download detection
    val lastProgressUpdate: Long = 0L
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_FAILED = 4
    }
}
