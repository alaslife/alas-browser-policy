package com.sun.alasbrowser.downloads

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.net.toUri
import com.sun.alasbrowser.data.BrowserDatabase
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.DownloadEntity
import com.sun.alasbrowser.service.AlasDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private const val TAG = "AlasDownloadManager"

// Timeout in milliseconds after which a download is considered stuck
private const val STUCK_DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

class AlasDownloadManager(private val context: Context) {

    private val database = BrowserDatabase.getDatabase(context)
    private val downloadDao = database.downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track active jobs
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeJobsMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Configurable via preferences
    private val prefs = BrowserPreferences(context)
    private val NETWORK_THREADS get() = prefs.downloadThreads
    private val MAX_CONCURRENT_DOWNLOADS get() = prefs.maxConcurrentDownloads
    private val MAX_RETRIES get() = prefs.downloadRetryLimit

    // Speed tracking
    private val downloadSpeeds = ConcurrentHashMap<Long, Long>()

    // Track last known progress for stuck detection
    private val lastKnownProgress = ConcurrentHashMap<Long, Long>()

    init {
        // Initialize stuck download detection on startup
        android.util.Log.d(TAG, "Initializing AlasDownloadManager")
        scope.launch {
            cleanStaleDownloads()
            startStuckDownloadMonitor()
        }
    }

    // Clean up downloads that were running when app was killed
    private suspend fun cleanStaleDownloads() {
        try {
            // Reset any downloads that were left in RUNNING state to PENDING
            // (they will be restarted by the stuck monitor or on next app start)
            val runningDownloads = downloadDao.getDownloadsByStatus(DownloadEntity.STATUS_RUNNING)
            if (runningDownloads.isNotEmpty()) {
                android.util.Log.d(TAG, "Found ${runningDownloads.size} stale running downloads, resetting to pending")
                runningDownloads.forEach { download ->
                    downloadDao.updateStatus(download.id, DownloadEntity.STATUS_PENDING)
                }
            }

            // Also check for pending downloads that haven't been touched in a while
            val staleThreshold = System.currentTimeMillis() - STUCK_DOWNLOAD_TIMEOUT_MS
            val stalePending = downloadDao.getStaleDownloads(DownloadEntity.STATUS_PENDING, staleThreshold)
            if (stalePending.isNotEmpty()) {
                android.util.Log.d(TAG, "Found ${stalePending.size} stale pending downloads")
                stalePending.forEach { download ->
                    // Only reset if it hasn't been updated in timeout period
                    if (download.lastProgressUpdate > 0 && download.lastProgressUpdate < staleThreshold) {
                        downloadDao.updateStatus(download.id, DownloadEntity.STATUS_PENDING)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error cleaning stale downloads", e)
        }
    }

    // Periodically check for stuck downloads and restart them
    private fun startStuckDownloadMonitor() {
        scope.launch {
            while (isActive) {
                delay(30000) // Check every 30 seconds
                try {
                    checkAndRestartStuckDownloads()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error in stuck download monitor", e)
                }
            }
        }
    }

    private suspend fun checkAndRestartStuckDownloads() {
        val threshold = System.currentTimeMillis() - STUCK_DOWNLOAD_TIMEOUT_MS
        val stuckDownloads = downloadDao.getStuckRunningDownloads(threshold)

        if (stuckDownloads.isNotEmpty()) {
            android.util.Log.w(TAG, "Found ${stuckDownloads.size} stuck downloads, restarting them")
            stuckDownloads.forEach { download ->
                // Cancel any existing active job
                activeJobsMutex.withLock {
                    activeJobs[download.id]?.cancel()
                    activeJobs.remove(download.id)
                }

                // Reset to pending and restart
                downloadDao.updateStatus(download.id, DownloadEntity.STATUS_PENDING)

                // Restart the download
                startDownloadJob(
                    download.id,
                    download.url,
                    download.fileName,
                    download.userAgent,
                    download.cookies,
                    download.referer
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Resuming stuck download: ${download.fileName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startDownload(
        url: String, 
        userAgent: String?, 
        contentDisposition: String?, 
        mimeType: String?, 
        customFileName: String? = null, 
        cookie: String? = null, 
        referer: String? = null, 
        pageTitle: String? = null, 
        pageUrl: String? = null,
        contentLength: Long = 0L
    ) {
        android.util.Log.d(TAG, "=== START DOWNLOAD CALLED ===")
        android.util.Log.d(TAG, "URL: $url")
        
        scope.launch {
            try {
                var fileName = customFileName ?: extractFileName(url, contentDisposition, mimeType)
                
                // If extracted name is gibberish (hash, encoded URL segment), prefer page title
                if (isGibberishName(fileName) && !pageTitle.isNullOrBlank()) {
                    val cleanTitle = pageTitle
                        .replace(Regex("\\s*[-–|]\\s*(APKMirror|APKPure|Uptodown|Download|Free Download).*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s+APK\\s*(Download)?\\s*$", RegexOption.IGNORE_CASE), "")
                        .trim()
                        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                    if (cleanTitle.isNotBlank() && cleanTitle.length in 2..120) {
                        val ext = if (fileName.contains(".")) "." + fileName.substringAfterLast(".") else ""
                        fileName = cleanTitle + ext
                        android.util.Log.d(TAG, "Used page title as filename: $fileName")
                    }
                }
                
                // Fix .bin extension even for custom filenames
                fileName = fixFileExtension(fileName, mimeType, pageTitle, pageUrl, url)
                
                // Truncate if still too long (max 150 chars including extension)
                fileName = truncateFileName(fileName, 150)
                
                android.util.Log.d(TAG, "Extracted filename: $fileName")
                
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                
                var file = File(downloadDir, fileName)
                
                // Security: validate file stays within download directory
                if (!file.canonicalPath.startsWith(downloadDir.canonicalPath)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Invalid filename", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                var existingId: Long? = null
                
                // Check for existence and handle conflicts BEFORE creating any dummy files
                if (file.exists()) {
                    val existingEntity = downloadDao.getDownloadByPath(file.absolutePath)
                    if (existingEntity != null) {
                        if (existingEntity.status == DownloadEntity.STATUS_COMPLETED) {
                            fileName = getUniqueFileName(downloadDir, fileName)
                            file = File(downloadDir, fileName)
                        } else {
                            existingId = existingEntity.id
                        }
                    } else {
                        fileName = getUniqueFileName(downloadDir, fileName)
                        file = File(downloadDir, fileName)
                    }
                }
                
                if (existingId == null) {
                    try {
                        file.createNewFile()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Cannot create file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
                
                val effectiveUserAgent = if (userAgent.isNullOrBlank()) {
                    com.sun.alasbrowser.engine.ChromiumCompat.MOBILE_UA
                } else {
                    userAgent
                }
                
                val id = existingId ?: downloadDao.insert(
                    DownloadEntity(
                        url = url,
                        title = if (!pageTitle.isNullOrBlank()) pageTitle else fileName,
                        fileName = fileName,
                        mimeType = mimeType ?: "application/octet-stream",
                        status = DownloadEntity.STATUS_PENDING,
                        totalSize = if (contentLength > 0) contentLength else 0L,
                        downloadedSize = 0L,
                        filePath = file.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        userAgent = effectiveUserAgent,
                        cookies = cookie,
                        referer = referer,
                        description = "",
                        pageTitle = pageTitle ?: "",
                        pageUrl = pageUrl ?: ""
                    )
                )
                
                startDownloadJob(id, url, fileName, effectiveUserAgent, cookie, referer)
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start download", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkQueue() {
        scope.launch {
            try {
                activeJobsMutex.withLock {
                    val runningCount = activeJobs.size
                    if (runningCount < MAX_CONCURRENT_DOWNLOADS) {
                        val pending = downloadDao.getDownloadsByStatus(DownloadEntity.STATUS_PENDING)
                            .sortedBy { it.timestamp }
                        
                        val slotsAvailable = MAX_CONCURRENT_DOWNLOADS - runningCount
                        
                        pending.take(slotsAvailable).forEach { entity ->
                            if (!activeJobs.containsKey(entity.id)) {
                                startDownloadJob(
                                    entity.id, 
                                    entity.url, 
                                    entity.fileName, 
                                    entity.userAgent, 
                                    entity.cookies, 
                                    entity.referer
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in checkQueue", e)
            }
        }
    }

    data class ServerCapabilities(
        val contentLength: Long = -1L,
        val supportsResume: Boolean = false,
        val contentDisposition: String? = null,
        val contentType: String? = null,
        val finalUrl: String? = null
    )

    private fun checkCapabilities(url: String, userAgent: String?, cookie: String?, referer: String?): ServerCapabilities {
        fun buildRequest(method: String, range: String? = null): Request {
            val prefs = BrowserPreferences(context)
            val effectiveUrl = if (prefs.proxyEnabled && !url.contains("alas-proxy.onrender.com")) {
                "https://alas-proxy.onrender.com/proxy?url=" + URLEncoder.encode(url, "UTF-8")
            } else {
                url
            }
            
            return Request.Builder()
                .url(effectiveUrl)
                .method(method, null)
                .header("User-Agent", userAgent ?: com.sun.alasbrowser.engine.ChromiumCompat.DESKTOP_UA)
                .apply {
                    if (!cookie.isNullOrEmpty()) header("Cookie", cookie)
                    if (!referer.isNullOrEmpty()) header("Referer", referer)
                    if (range != null) header("Range", range)
                    try {
                        val uri = URI(url)
                        val origin = "${uri.scheme}://${uri.host}"
                        header("Origin", origin)
                    } catch (e: Exception) {
                        // Ignore URI parsing errors
                    }
                }
                .build()
        }

        try {
            client.newCall(buildRequest("HEAD")).execute().use { response ->
                val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val ranges = response.header("Accept-Ranges")
                val disposition = response.header("Content-Disposition")
                val contentType = response.header("Content-Type")
                val finalUrl = response.request.url.toString()
                
                if (response.isSuccessful || response.code == 206) {
                    val supportsResume = ranges == "bytes" || response.code == 206
                    if (length > 0) return ServerCapabilities(length, supportsResume, disposition, contentType, finalUrl)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "HEAD request failed, trying GET range", e)
        }
        
        try {
            client.newCall(buildRequest("GET", "bytes=0-0")).execute().use { response ->
                val contentRange = response.header("Content-Range")
                val length = contentRange?.substringAfter("/")?.toLongOrNull() 
                    ?: response.header("Content-Length")?.toLongOrNull() ?: -1L
                val disposition = response.header("Content-Disposition")
                val contentType = response.header("Content-Type")
                val finalUrl = response.request.url.toString()
                
                val supportsResume = response.code == 206
                return ServerCapabilities(length, supportsResume && length > 0, disposition, contentType, finalUrl)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Capability check failed", e)
            return ServerCapabilities()
        }
    }

    private fun startDownloadJob(
        id: Long, 
        url: String, 
        fileName: String, 
        userAgent: String?, 
        cookie: String?, 
        referer: String?
    ) {
        android.util.Log.d(TAG, "Starting download job for ID: $id, URL: $url")
        
        val job = scope.launch {
            val entity = downloadDao.getDownloadById(id) ?: return@launch
            var speedJob: Job? = null
            try {
                AlasDownloadService.start(context)
                downloadDao.updateStatus(id, DownloadEntity.STATUS_RUNNING)
                // Initialize last progress update time when starting
                downloadDao.updateLastProgressTime(id, System.currentTimeMillis())
                android.util.Log.d(TAG, "Checking capabilities for: $url")
                
                val caps = withContext(Dispatchers.IO) {
                    checkCapabilities(url, userAgent, cookie, referer)
                }
                android.util.Log.d(TAG, "Capabilities: size=${caps.contentLength}, supportsResume=${caps.supportsResume}, disposition=${caps.contentDisposition}, contentType=${caps.contentType}")
                
                var totalSize = if (caps.contentLength > 0) caps.contentLength else entity.totalSize
                if (totalSize != entity.totalSize) {
                    downloadDao.updateTotalSize(id, totalSize)
                }
                
                var finalPath = if (entity.filePath.isNotBlank()) {
                    entity.filePath 
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName).absolutePath
                }
                
                // Try to fix filename from server response headers (Content-Disposition, Content-Type, redirect URL)
                val currentFile = File(finalPath)
                val betterName = resolveFileNameFromHeaders(
                    currentFile.name, caps.contentDisposition, caps.contentType, caps.finalUrl
                )
                if (betterName != null && betterName != currentFile.name) {
                    val newFile = File(currentFile.parentFile, betterName)
                    if (!newFile.exists()) {
                        if (currentFile.exists()) {
                            currentFile.renameTo(newFile)
                        }
                        finalPath = newFile.absolutePath
                        downloadDao.updateFileName(id, betterName)
                        android.util.Log.d(TAG, "Fixed filename from headers: ${currentFile.name} -> $betterName")
                    }
                }
                
                if (entity.filePath != finalPath) {
                    downloadDao.updateFilePath(id, finalPath)
                }
                
                val progressTracker = AtomicLong(entity.downloadedSize)
                
                speedJob = launch {
                    var lastBytes = progressTracker.get()
                    while (isActive) {
                        val currentBytes = progressTracker.get()
                        val diff = currentBytes - lastBytes
                        downloadSpeeds[id] = diff
                        lastBytes = currentBytes
                        // Update last progress time periodically while download is active
                        downloadDao.updateLastProgressTime(id, System.currentTimeMillis())
                        updateServiceNotification(
                            entity.title, 
                            currentBytes, 
                            totalSize, 
                            diff
                        )
                        delay(1000)
                    }
                }
                
                val isResume = entity.downloadedSize > 0
                val useMultiThread = caps.supportsResume && totalSize > 5 * 1024 * 1024 && !isResume
                
                android.util.Log.d(TAG, "Starting download: totalSize=$totalSize, supportsResume=${caps.supportsResume}, multiThread=$useMultiThread, isResume=$isResume")
                
                val file = File(finalPath)
                val success = if (useMultiThread) { 
                    downloadMultiThreaded(file, url, totalSize, cookie, referer, userAgent, id, progressTracker)
                } else {
                    downloadSingleThreaded(file, url, entity.downloadedSize, cookie, referer, userAgent, id, progressTracker)
                }
                android.util.Log.d(TAG, "Download finished with result: $success")
                
                speedJob.cancel()
                
                if (success >= 0) {
                    downloadDao.updateStatus(id, DownloadEntity.STATUS_COMPLETED)
                    downloadDao.updateProgress(id, totalSize)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download complete: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: CancellationException) {
                android.util.Log.d(TAG, "Download cancelled for ID: $id")
                downloadDao.updateStatus(id, DownloadEntity.STATUS_PAUSED)
                speedJob?.cancel()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Download failed for ID: $id", e)
                downloadDao.updateStatus(id, DownloadEntity.STATUS_FAILED)
                val errorMsg = when {
                    e is java.net.UnknownHostException -> "No internet connection"
                    e is java.net.SocketTimeoutException -> "Connection timed out"
                    e is javax.net.ssl.SSLException -> "Secure connection failed"
                    e is java.io.FileNotFoundException -> "Storage access denied"
                    e.message?.contains("No space") == true -> "Not enough storage space"
                    e.message?.contains("403") == true -> "Access denied by server"
                    e.message?.contains("404") == true -> "File not found on server"
                    e.message?.contains("Max retries") == true -> "Unstable connection, try again later"
                    else -> "Download failed: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
                speedJob?.cancel()
            } finally {
                activeJobsMutex.withLock {
                    activeJobs.remove(id)
                }
                downloadSpeeds.remove(id)
                checkQueue()
                if (activeJobs.isEmpty()) {
                    AlasDownloadService.stop(context)
                }
            }
        }
        
        scope.launch {
            activeJobsMutex.withLock {
                activeJobs[id] = job
            }
        }
    }

    private suspend fun downloadSingleThreaded(
        file: File,
        url: String,
        downloadedBytes: Long,
        cookie: String?,
        referer: String?,
        userAgent: String?,
        downloadId: Long,
        progressTracker: AtomicLong
    ): Long {
        android.util.Log.d(TAG, "downloadSingleThreaded: url=$url, path=${file.absolutePath}")
        
        var downloaded = downloadedBytes
        progressTracker.set(downloaded)

        val prefs = BrowserPreferences(context)
        val effectiveUrl = if (prefs.proxyEnabled && !url.contains("alas-proxy.onrender.com")) {
            "https://alas-proxy.onrender.com/proxy?url=" + URLEncoder.encode(url, "UTF-8")
        } else {
            url
        }

        val effectiveUserAgent = if (userAgent.isNullOrBlank()) {
            com.sun.alasbrowser.engine.ChromiumCompat.MOBILE_UA
        } else {
            userAgent
        }
        
        val request = Request.Builder()
            .url(effectiveUrl)
            .header("User-Agent", effectiveUserAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .apply {
                if (downloaded > 0) {
                    header("Range", "bytes=$downloaded-")
                }
                if (!cookie.isNullOrEmpty()) {
                    header("Cookie", cookie)
                }
                val effectiveReferer = if (!referer.isNullOrEmpty()) {
                    referer
                } else {
                    try {
                        val uri = URI(url)
                        "${uri.scheme}://${uri.host}/"
                    } catch (_: Exception) { null }
                }
                if (effectiveReferer != null) {
                    header("Referer", effectiveReferer)
                }
                try {
                    val uri = URI(url)
                    header("Origin", "${uri.scheme}://${uri.host}")
                } catch (_: Exception) {}
            }
            .build()
            
        return client.newCall(request).execute().use { response ->
            android.util.Log.d(TAG, "downloadSingleThreaded: response code=${response.code}")
            
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 403) {
                    android.util.Log.d(TAG, "Got 403, falling back to system DownloadManager")
                    return fallbackToSystemDownloader(downloadId, file.name, url, effectiveUserAgent, cookie, referer)
                }
                throw IOException("Network error: ${response.code}")
            }
            
            // Check response headers for better filename/extension
            tryFixFileNameFromResponse(file, response, downloadId)
            
            val body = response.body ?: throw IOException("No response body")
            body.byteStream().use { source ->
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(downloaded)
                    
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()
                    
                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        raf.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        progressTracker.set(downloaded)
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 1000) {
                            downloadDao.updateProgress(downloadId, downloaded)
                            downloadDao.updateLastProgressTime(downloadId, now)
                            lastUpdate = now
                        }
                    }
                }
            }
            downloaded
        }
    }

    private suspend fun downloadMultiThreaded(
        file: File,
        url: String,
        totalSize: Long,
        cookie: String?,
        referer: String?,
        userAgent: String?,
        downloadId: Long,
        progressTracker: AtomicLong
    ): Long {
        if (!file.exists()) {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(totalSize)
            }
        }
        
        progressTracker.set(0L)
        
        val chunkSize = totalSize / NETWORK_THREADS
        
        coroutineScope {
            val jobs = (0 until NETWORK_THREADS).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunkSize
                    val end = if (i == NETWORK_THREADS - 1) totalSize - 1 else (start + chunkSize - 1)
                    downloadPartWithRetry(
                        url = url,
                        file = file,
                        start = start,
                        end = end,
                        progressTracker = progressTracker,
                        userAgent = userAgent,
                        cookie = cookie,
                        referer = referer,
                        downloadId = downloadId
                    )
                }
            }
            jobs.awaitAll()
        }
        return totalSize
    }

    private suspend fun downloadPartWithRetry(
        url: String,
        file: File,
        start: Long,
        end: Long,
        progressTracker: AtomicLong,
        userAgent: String?,
        cookie: String?,
        referer: String?,
        downloadId: Long
    ) {
        var currentPos = start
        var attempts = 0
        val maxRetries = MAX_RETRIES
        var bytesReadInThisAttempt = 0L
        
        while (currentPos <= end && attempts < maxRetries && coroutineContext.isActive) {
            try {
                val prefs = BrowserPreferences(context)
                val effectiveUrl = if (prefs.proxyEnabled && !url.contains("alas-proxy.onrender.com")) {
                    "https://alas-proxy.onrender.com/proxy?url=" + URLEncoder.encode(url, "UTF-8")
                } else {
                    url
                }

                val request = Request.Builder()
                    .url(effectiveUrl)
                    .header("User-Agent", userAgent ?: com.sun.alasbrowser.engine.ChromiumCompat.MOBILE_UA)
                    .header("Range", "bytes=$currentPos-$end")
                    .apply {
                        if (!cookie.isNullOrEmpty()) header("Cookie", cookie)
                        if (!referer.isNullOrEmpty()) header("Referer", referer)
                        try {
                            val uri = URI(url)
                            header("Origin", "${uri.scheme}://${uri.host}")
                        } catch (e: Exception) {
                            // Ignore URI parsing errors
                        }
                    }
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IOException("HTTP ${response.code}")
                    }
                    
                    val body = response.body ?: throw IOException("No response body")
                    body.byteStream().use { source ->
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(currentPos)
                            
                            val buffer = ByteArray(64 * 1024)
                            var bytes: Int
                            val updateThreshold = 2 * 1024 * 1024L
                            var bytesSinceLastDbUpdate = 0L
                            
                            while (source.read(buffer).also { bytes = it } != -1) {
                                coroutineContext.ensureActive()
                                raf.write(buffer, 0, bytes)
                                currentPos += bytes
                                val bytesAdded = progressTracker.addAndGet(bytes.toLong())
                                bytesReadInThisAttempt += bytes
                                bytesSinceLastDbUpdate += bytes
                                
                                if (bytesSinceLastDbUpdate >= updateThreshold) {
                                    downloadDao.updateProgress(downloadId, bytesAdded)
                                    downloadDao.updateLastProgressTime(downloadId, System.currentTimeMillis())
                                    bytesSinceLastDbUpdate = 0L
                                }
                            }
                        }
                    }
                }
                
                if (currentPos > end) break
                
            } catch (e: Exception) {
                if (bytesReadInThisAttempt > 512 * 1024) {
                    attempts = 0
                    bytesReadInThisAttempt = 0L
                } else {
                    attempts++
                }
                
                val backoff = (attempts * 1000L).coerceAtMost(10000L)
                delay(backoff)
                
                if (attempts == maxRetries) {
                    throw IOException("Max retries exceeded", e)
                }
            }
        }
    }

    private fun updateServiceNotification(title: String, downloaded: Long, total: Long, speedBytesPerSec: Long) {
        val intent = android.content.Intent(context, AlasDownloadService::class.java).apply {
            putExtra("title", title)
            putExtra("progress", if (total > 0) ((downloaded * 100) / total).toInt() else -1)
            putExtra("speed", formatSpeed(speedBytesPerSec))
            
            val etaSecs = if (speedBytesPerSec > 0) (total - downloaded) / speedBytesPerSec else -1L
            putExtra("eta", formatDuration(etaSecs))
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update service notification", e)
        }
    }
    
    private fun formatSpeed(bytes: Long): String {
        return if (bytes > 1024 * 1024) {
            String.format("%.1f MB/s", bytes / (1024f * 1024f))
        } else {
            String.format("%.0f KB/s", bytes / 1024f)
        }
    }
    
    private fun formatDuration(secs: Long): String {
        return when {
            secs < 0 -> "--"
            secs > 3600 -> "${secs / 3600}h ${(secs % 3600) / 60}m"
            else -> "${secs / 60}m ${secs % 60}s"
        }
    }

    fun pause(id: Long) {
        android.util.Log.d(TAG, "Pausing download: $id")
        scope.launch {
            activeJobsMutex.withLock {
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
            }
            
            try {
                downloadDao.updateStatus(id, DownloadEntity.STATUS_PAUSED)
                android.util.Log.d(TAG, "Download $id paused successfully")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to update pause status for $id", e)
            }
        }
    }
    
    fun resume(id: Long) {
        android.util.Log.d(TAG, "Resuming download: $id")
        scope.launch {
            downloadDao.updateStatus(id, DownloadEntity.STATUS_PENDING)
            checkQueue()
        }
    }
    
    fun cancel(id: Long) {
        android.util.Log.d(TAG, "Cancelling download: $id")
        scope.launch {
            activeJobsMutex.withLock {
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
            }
            
            try {
                val entity = downloadDao.getDownloadById(id)
                downloadDao.deleteById(id)
                
                entity?.let {
                    val file = File(it.filePath)
                    if (file.exists()) {
                        file.delete()
                        android.util.Log.d(TAG, "File deleted: ${it.filePath}")
                    }
                }
                android.util.Log.d(TAG, "Download $id cancelled and removed")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to cancel download $id", e)
            }
        }
    }
    
    fun remove(id: Long) {
        android.util.Log.d(TAG, "Removing download entry: $id")
        scope.launch {
            activeJobsMutex.withLock {
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
            }
            
            try {
                downloadDao.deleteById(id)
                android.util.Log.d(TAG, "Download entry $id removed from database")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to remove download $id", e)
            }
        }
    }
    
    fun removeWithFile(id: Long) {
        cancel(id)
    }
    
    fun cancelDownload(id: Long) = cancel(id)
    fun pauseDownload(id: Long) = pause(id)
    fun resumeDownload(id: Long) = resume(id)

    fun extractFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        var fileName: String? = null
        
        try {
            if (!contentDisposition.isNullOrBlank()) {
                val encodedPattern = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\s]+)""", RegexOption.IGNORE_CASE)
                fileName = encodedPattern.find(contentDisposition)?.groupValues?.get(1)?.let {
                    try {
                        URLDecoder.decode(it, "UTF-8")
                    } catch (e: Exception) {
                        it
                    }
                }
                
                if (fileName.isNullOrBlank()) {
                    val quotedPattern = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                    fileName = quotedPattern.find(contentDisposition)?.groupValues?.get(1)?.let {
                        try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it }
                    }
                }
                
                if (fileName.isNullOrBlank()) {
                    val unquotedPattern = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
                    fileName = unquotedPattern.find(contentDisposition)?.groupValues?.get(1)?.let {
                        try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it }
                    }
                }
            }
            
            if (fileName.isNullOrBlank()) {
                try {
                    // First check query parameters for filename hints
                    val fullUri = Uri.parse(url)
                    for (param in listOf("file", "name", "filename", "download", "title")) {
                        val qVal = fullUri.getQueryParameter(param)
                        if (!qVal.isNullOrBlank() && qVal.contains(".")) {
                            fileName = try { URLDecoder.decode(qVal, "UTF-8") } catch (_: Exception) { qVal }
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            
            if (fileName.isNullOrBlank()) {
                try {
                    val coreUrl = url.substringBefore('?').substringBefore('#')
                    val uri = Uri.parse(coreUrl)
                    val path = uri.path
                    if (!path.isNullOrBlank()) {
                        val cleanPath = if (path.endsWith("/")) path.dropLast(1) else path
                        val lastSegment = cleanPath.substringAfterLast('/')
                        
                        if (lastSegment.isNotBlank()) {
                            fileName = try {
                                URLDecoder.decode(lastSegment, "UTF-8")
                            } catch (e: Exception) {
                                lastSegment
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Failed to parse URL for filename", e)
                }
            }
            
            if (fileName.isNullOrBlank()) {
                val guessed = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                if (!guessed.isNullOrBlank()) {
                    fileName = guessed
                }
            }
            
            if (fileName.isNullOrBlank()) {
                fileName = "download_${System.currentTimeMillis()}"
            }
            
            fileName = fileName.trim()
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .replace("..", "_") // Prevent directory traversal
                .trimStart('.') // No hidden files
                .ifBlank { "download_${System.currentTimeMillis()}" }
            
            return fileName!!
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting filename", e)
            return "download_${System.currentTimeMillis()}.bin"
        }
    }

    private fun fixFileExtension(
        fileName: String,
        mimeType: String?,
        pageTitle: String?,
        pageUrl: String?,
        url: String
    ): String {
        val currentExt = if (fileName.contains(".")) {
            fileName.substringAfterLast(".").lowercase().trim()
        } else {
            ""
        }

        // Dots in version names (e.g. 21.09.266) are NOT real file extensions.
        val hasRealExtension = isLikelyRealExtension(currentExt)
        val isGenericExt = !hasRealExtension || currentExt == "bin" || currentExt == "download"
        
        if (isGenericExt) {
            val cleanMime = mimeType?.substringBefore(";")?.trim()?.lowercase()
            val expectedExt = cleanMime?.let { 
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it) 
            }
            
            // Check URL for known file extensions (including query params)
            val urlExtMatch = Regex("""\.(apks|apkm|apk|xapk|pdf|zip|rar|7z|mp4|mp3|mkv|avi|exe|msi|dmg|iso|tar|gz|bz2|deb|rpm)\b""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.get(1)?.lowercase()

            // Bundle APK formats should always win over generic MIME guesses.
            val urlImpliesBundle = urlExtMatch == "apks" || urlExtMatch == "apkm" || urlExtMatch == "xapk" ||
                url.contains(".apkm", ignoreCase = true) ||
                url.contains("bundle", ignoreCase = true)
            val pageImpliesBundle = pageTitle?.contains("bundle", ignoreCase = true) == true ||
                pageTitle?.contains("split apk", ignoreCase = true) == true ||
                pageUrl?.contains(".apks", ignoreCase = true) == true ||
                pageUrl?.contains(".apkm", ignoreCase = true) == true ||
                pageUrl?.contains("bundle", ignoreCase = true) == true
            
            val resolvedExt = when {
                // Always prioritize bundle formats over generic APK MIME.
                urlImpliesBundle -> when (urlExtMatch) {
                    "xapk" -> "xapk"
                    "apkm" -> "apkm"
                    else -> "apks"
                }
                pageImpliesBundle -> "apks"
                // Prioritize specific extensions found in URL (apks, xapk before generic apk)
                urlExtMatch != null && urlExtMatch != "apk" && (expectedExt == null || expectedExt == "bin" || expectedExt == "zip") -> urlExtMatch
                expectedExt != null && expectedExt != "bin" -> expectedExt
                cleanMime == "application/vnd.android.package-archive" -> "apk"
                cleanMime == "application/x-apks" || cleanMime == "application/x-apk-splits" -> "apks"
                cleanMime == "application/octet-stream" && pageTitle?.contains("apk", ignoreCase = true) == true -> "apk"
                cleanMime == "application/octet-stream" && pageUrl?.contains("apk", ignoreCase = true) == true -> "apk"
                cleanMime == "application/zip" || cleanMime == "application/x-zip-compressed" -> "zip"
                cleanMime == "application/pdf" -> "pdf"
                urlExtMatch != null -> urlExtMatch
                pageTitle?.contains("apks", ignoreCase = true) == true -> "apks"
                pageTitle?.contains("apk", ignoreCase = true) == true -> "apk"
                pageUrl?.contains(".apks", ignoreCase = true) == true -> "apks"
                pageUrl?.contains(".apk", ignoreCase = true) == true -> "apk"
                pageUrl?.contains("apk", ignoreCase = true) == true -> "apk"
                else -> null
            }
            
            if (resolvedExt != null) {
                return if (!hasRealExtension) {
                    "$fileName.$resolvedExt"
                } else {
                    "${fileName.substringBeforeLast(".")}.$resolvedExt"
                }.also {
                    android.util.Log.d(TAG, "Fixed extension: .$currentExt -> .$resolvedExt => $it")
                }
            }
        }
        
        return fileName
    }

    private fun isLikelyRealExtension(ext: String): Boolean {
        if (ext.isBlank()) return false
        if (!ext.matches(Regex("^[a-z0-9]{1,8}$"))) return false

        val known = setOf(
            "apk", "apks", "apkm", "xapk", "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp3", "m4a", "ogg", "flac", "wav",
            "mp4", "mkv", "webm", "avi", "mov",
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico",
            "json", "xml", "html", "css", "js",
            "exe", "msi", "dmg", "iso", "jar", "bin"
        )
        return ext in known
    }

    private val MIME_TO_EXT = mapOf(
        "application/vnd.android.package-archive" to "apk",
        "application/x-apks" to "apks",
        "application/x-apk-splits" to "apks",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/x-zip-compressed" to "zip",
        "application/x-rar-compressed" to "rar",
        "application/x-7z-compressed" to "7z",
        "application/x-tar" to "tar",
        "application/gzip" to "gz",
        "application/x-bzip2" to "bz2",
        "application/x-xz" to "xz",
        "application/java-archive" to "jar",
        "application/x-iso9660-image" to "iso",
        "video/mp4" to "mp4",
        "video/x-matroska" to "mkv",
        "video/webm" to "webm",
        "audio/mpeg" to "mp3",
        "audio/ogg" to "ogg",
        "audio/flac" to "flac",
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "text/html" to "html",
        "text/plain" to "txt",
        "application/json" to "json",
        "application/xml" to "xml"
    )

    private fun extFromMime(mime: String?): String? {
        val clean = mime?.substringBefore(";")?.trim()?.lowercase() ?: return null
        return MIME_TO_EXT[clean]
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(clean)?.takeIf { it != "bin" }
    }

    private fun isGibberishName(name: String): Boolean {
        val base = if (name.contains(".")) name.substringBeforeLast(".") else name
        if (base.length > 60) return true
        if (base.length > 30 && !base.contains(" ") && !base.contains("-")) {
            val alnumRatio = base.count { it.isLetterOrDigit() }.toFloat() / base.length
            val upperRatio = base.count { it.isUpperCase() }.toFloat() / base.length
            if (upperRatio > 0.4 && alnumRatio > 0.8) return true
        }
        return false
    }

    private fun resolveFileNameFromHeaders(
        currentName: String,
        contentDisposition: String?,
        contentType: String?,
        finalUrl: String?
    ): String? {
        try {
            // 1. Try Content-Disposition header for real filename
            if (!contentDisposition.isNullOrBlank()) {
                val headerName = extractFileName("", contentDisposition, contentType)
                if (!headerName.startsWith("download_") && !isGibberishName(headerName)) {
                    val fixed = fixExtIfMissing(headerName, contentType)
                    return truncateFileName(fixed, 150)
                }
            }

            // 2. Try redirect URL (final URL after redirects often has the real filename)
            if (!finalUrl.isNullOrBlank() && finalUrl != "about:blank") {
                val redirectName = extractFileName(finalUrl, null, contentType)
                if (!redirectName.startsWith("download_") && !isGibberishName(redirectName)) {
                    val fixed = fixExtIfMissing(redirectName, contentType)
                    return truncateFileName(fixed, 150)
                }
            }

            // 3. If current name has no extension, add one from Content-Type
            val currentExt = if (currentName.contains(".")) currentName.substringAfterLast(".").lowercase() else ""
            if (!isLikelyRealExtension(currentExt) || currentExt == "bin" || currentExt == "download") {
                val ext = extFromMime(contentType)
                if (ext != null) {
                    val baseName = if (!isLikelyRealExtension(currentExt)) currentName else currentName.substringBeforeLast(".")
                    return truncateFileName("$baseName.$ext", 150)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "resolveFileNameFromHeaders failed", e)
        }
        return null
    }

    private fun fixExtIfMissing(name: String, contentType: String?): String {
        val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
        if (ext.isNotEmpty() && ext != "bin" && ext != "download") return name
        val resolved = extFromMime(contentType) ?: return name
        return if (ext.isEmpty()) "$name.$resolved" else "${name.substringBeforeLast(".")}.$resolved"
    }

    private suspend fun tryFixFileNameFromResponse(file: File, response: okhttp3.Response, downloadId: Long) {
        try {
            val disposition = response.header("Content-Disposition")
            val contentType = response.header("Content-Type")
            val finalUrl = response.request.url.toString()
            
            val betterName = resolveFileNameFromHeaders(file.name, disposition, contentType, finalUrl)
            
            if (betterName != null && betterName != file.name) {
                val newFile = File(file.parentFile, betterName)
                if (!newFile.exists() && file.renameTo(newFile)) {
                    downloadDao.updateFilePath(downloadId, newFile.absolutePath)
                    downloadDao.updateFileName(downloadId, betterName)
                    android.util.Log.d(TAG, "Renamed file from ${file.name} to $betterName")
                }
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "tryFixFileNameFromResponse failed", e)
        }
    }

    private fun truncateFileName(fileName: String, maxLength: Int): String {
        if (fileName.length <= maxLength) return fileName
        val ext = if (fileName.contains(".")) "." + fileName.substringAfterLast(".") else ""
        val nameWithoutExt = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
        val maxNameLen = maxLength - ext.length
        return if (maxNameLen > 10) {
            nameWithoutExt.take(maxNameLen) + ext
        } else {
            fileName.take(maxLength)
        }
    }

    private fun getUniqueFileName(dir: File, fileName: String): String {
        var newName = fileName
        var file = File(dir, newName)
        var count = 1
        
        val nameWithoutExt = if (fileName.contains(".")) {
            fileName.substringBeforeLast(".")
        } else {
            fileName
        }
        val ext = if (fileName.contains(".")) {
            "." + fileName.substringAfterLast(".")
        } else {
            ""
        }

        while (file.exists()) {
            newName = "$nameWithoutExt ($count)$ext"
            file = File(dir, newName)
            count++
        }
        return newName
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AlasDownloadManager? = null
        
        @JvmStatic
        fun getInstance(context: Context): AlasDownloadManager {
            return INSTANCE ?: synchronized(this) {
                AlasDownloadManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }

    fun downloadFromBase64(base64Data: String, mimeType: String, suggestedFilename: String?) {
        scope.launch {
            try {
                val pureBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }

                val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                
                var fileName = suggestedFilename
                if (fileName.isNullOrBlank()) {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                    fileName = "download_${System.currentTimeMillis()}.$ext"
                }

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                
                val file = File(downloadDir, getUniqueFileName(downloadDir, fileName))
                
                file.writeBytes(decodedBytes)

                val entity = DownloadEntity(
                    url = "data:$mimeType,base64",
                    title = file.name,
                    fileName = file.name,
                    mimeType = mimeType,
                    status = DownloadEntity.STATUS_COMPLETED,
                    totalSize = decodedBytes.size.toLong(),
                    downloadedSize = decodedBytes.size.toLong(),
                    filePath = file.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    userAgent = ""
                )
                downloadDao.insert(entity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download complete: ${file.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to save base64 download", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun fallbackToSystemDownloader(
        downloadId: Long,
        fileName: String,
        url: String,
        userAgent: String,
        cookie: String?,
        referer: String?
    ): Long {
        android.util.Log.d(TAG, "Using system DownloadManager for: $url")
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        
        val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Downloading via system downloader...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            
            addRequestHeader("User-Agent", userAgent)
            if (!cookie.isNullOrEmpty()) {
                addRequestHeader("Cookie", cookie)
            }
            if (!referer.isNullOrEmpty()) {
                addRequestHeader("Referer", referer)
            } else {
                try {
                    val uri = URI(url)
                    addRequestHeader("Referer", "${uri.scheme}://${uri.host}/")
                } catch (_: Exception) {}
            }
        }
        
        try {
            val systemDownloadId = downloadManager.enqueue(request)
            android.util.Log.d(TAG, "System download started with ID: $systemDownloadId")
            
            downloadDao.updateStatus(downloadId, DownloadEntity.STATUS_COMPLETED)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Using system downloader (check notification)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to enqueue system download", e)
            throw e
        }
        
        return -2L
    }
}
