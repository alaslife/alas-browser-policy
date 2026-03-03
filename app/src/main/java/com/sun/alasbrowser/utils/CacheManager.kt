package com.sun.alasbrowser.utils

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {

    /**
     * Calculates the total cache size of the application.
     * Includes internal cache, external cache, and webview cache databases.
     */
    suspend fun calculateTotalCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        var size: Long = 0

        // Internal Cache
        size += getDirSize(context.cacheDir)

        // External Cache
        context.externalCacheDir?.let {
            size += getDirSize(it)
        }

        // WebView Cache & Databases
        // Note: Exact WebView cache size is hard to get programmatically without root,
        // but we can approximate by checking specific directories.
        // Standard WebView cache directories:
        val appDir = context.filesDir.parentFile
        if (appDir != null && appDir.exists()) {
            val webViewCacheDir = File(appDir, "app_webview")
            if (webViewCacheDir.exists()) {
                size += getDirSize(webViewCacheDir)
            }
            val cacheDir = File(appDir, "cache")
            if (cacheDir.exists()) {
                size += getDirSize(cacheDir)
            }
        }
        
        return@withContext size
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists()) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    size += if (file.isDirectory) {
                        getDirSize(file)
                    } else {
                        file.length()
                    }
                }
            }
        }
        return size
    }

    /**
     * Clears the application cache effectively.
     */
    suspend fun clearCache(context: Context, includeWebStorage: Boolean = true, includeCookies: Boolean = true, onComplete: () -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Clear Standard Path Cache
                deleteDir(context.cacheDir)
                context.externalCacheDir?.let { deleteDir(it) }

                // 2. Clear WebView Files
                val appDir = context.filesDir.parentFile
                if (appDir != null && appDir.exists()) {
                    // Common WebView folders
                    val webViewPaths = listOf(
                        "app_webview/Cache",
                        "app_webview/Code Cache",
                        "app_webview/Service Worker",
                        "app_webview/WebStorage",
                        "app_webview/GPUCache"
                    )
                    
                    webViewPaths.forEach { path ->
                        val file = File(appDir, path)
                        if (file.exists()) {
                            deleteDir(file)
                        }
                    }
                }
                
                // 3. Clear WebView Internal Cache
                context.deleteDatabase("webview.db")
                context.deleteDatabase("webviewCache.db")
                
                // 4. WebStorage (localStorage, etc.)
                if (includeWebStorage) {
                    withContext(Dispatchers.Main) {
                        WebStorage.getInstance().deleteAllData()
                    }
                }

                // 5. Cookies
                if (includeCookies) {
                     withContext(Dispatchers.Main) {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                }
                
                withContext(Dispatchers.Main) {
                     onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 withContext(Dispatchers.Main) {
                    onComplete() // Ensure callback is called even on error
                }
            }
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
            return dir.delete()
        } else return dir != null && dir.delete()
    }
    
    fun formatSize(size: Long): String {
        val kiloByte = 1024L
        val megaByte = kiloByte * 1024L
        val gigaByte = megaByte * 1024L

        return when {
            size < kiloByte -> "$size B"
            size < megaByte -> String.format("%.2f KB", size.toDouble() / kiloByte)
            size < gigaByte -> String.format("%.2f MB", size.toDouble() / megaByte)
            else -> String.format("%.2f GB", size.toDouble() / gigaByte)
        }
    }
}
