package com.sun.alasbrowser.data

import android.graphics.Bitmap
import com.sun.alasbrowser.engine.EngineType
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val favicon: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivate: Boolean = false,
    val lastAccessedTime: Long = System.currentTimeMillis(),
    val webViewState: android.os.Bundle? = null,
    val engineType: EngineType = EngineType.WEB_VIEW,
    val parentTabId: String? = null
)
