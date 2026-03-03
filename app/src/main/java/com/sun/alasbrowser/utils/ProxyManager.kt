package com.sun.alasbrowser.utils

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

object ProxyManager {
    private const val TAG = "ProxyManager"

    fun setProxy(context: Context, host: String, port: Int) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val proxyUrl = "$host:$port"
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule(proxyUrl)
                .addBypassRule("localhost") // Don't proxy local requests
                .build()

            try {
                ProxyController.getInstance().setProxyOverride(
                    proxyConfig,
                    { run -> run.run() }, // Direct executor
                    { Log.d(TAG, "Proxy changed to $proxyUrl") }
                )
                Log.i(TAG, "Proxy enabled: $proxyUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set proxy", e)
            }
        } else {
            Log.e(TAG, "Proxy Override feature not supported by this WebView version")
        }
    }

    fun clearProxy(context: Context) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().clearProxyOverride(
                    { run -> run.run() },
                    { Log.d(TAG, "Proxy cleared") }
                )
                Log.i(TAG, "Proxy disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear proxy", e)
            }
        }
    }
}
