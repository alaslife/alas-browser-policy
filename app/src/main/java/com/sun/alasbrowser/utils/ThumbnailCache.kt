package com.sun.alasbrowser.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Disk-based thumbnail cache for faster performance
 * Stores thumbnails in app cache directory
 */
object ThumbnailCache {
    private const val CACHE_DIR = "thumbnails"
    private const val MAX_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    private const val TAG = "ThumbnailCache"
    
    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun urlToFileName(url: String): String {
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val hash = md5.digest(url.toByteArray())
            hash.joinToString("") { "%02x".format(it) } + ".jpg"
        } catch (e: Exception) {
            "default.jpg"
        }
    }
    
    /**
     * Save thumbnail to disk cache
     */
    @Synchronized
    fun saveThumbnail(context: Context, url: String, bitmap: Bitmap?) {
        if (bitmap == null || url.isEmpty()) return
        
        try {
            val cacheDir = getCacheDir(context)
            val fileName = urlToFileName(url)
            val file = File(cacheDir, fileName)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            // Log.d(TAG, "Saved thumbnail for $url at ${file.absolutePath}")
            
            // Clean old cache if needed
            cleanCacheIfNeeded(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail for $url", e)
        }
    }
    
    /**
     * Load thumbnail from disk cache
     */
    @Synchronized
    fun loadThumbnail(context: Context, url: String): Bitmap? {
        if (url.isEmpty()) return null
        
        try {
            val cacheDir = getCacheDir(context)
            val fileName = urlToFileName(url)
            val file = File(cacheDir, fileName)
            
            if (file.exists()) {
                // Log.d(TAG, "Loaded thumbnail for $url")
                return BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail for $url", e)
        }
        return null
    }
    
    /**
     * Clean cache if it exceeds max size
     */
    private fun cleanCacheIfNeeded(context: Context) {
        try {
            val cacheDir = getCacheDir(context)
            val files = cacheDir.listFiles() ?: return
            
            val totalSize = files.sumOf { it.length() }
            
            if (totalSize > MAX_CACHE_SIZE) {
                // Sort by last modified and delete oldest
                files.sortedBy { it.lastModified() }
                    .take(files.size / 4) // Delete oldest 25%
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean cache", e)
        }
    }
    
    /**
     * Clear all thumbnails
     */
    @Synchronized
    fun clearCache(context: Context) {
        try {
            val cacheDir = getCacheDir(context)
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }
}
