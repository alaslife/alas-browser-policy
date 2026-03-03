package com.sun.alasbrowser.utils

import android.webkit.GeolocationPermissions

/**
 * Stores the pending WebView geolocation callback so it can be resolved
 * when the Activity receives the permission result.
 */
object GeolocationPermissionHelper {

    private var pendingOrigin: String? = null
    private var pendingCallback: GeolocationPermissions.Callback? = null

    fun storePending(origin: String?, callback: GeolocationPermissions.Callback?) {
        pendingOrigin = origin
        pendingCallback = callback
    }

    fun onPermissionResult(granted: Boolean) {
        pendingCallback?.invoke(pendingOrigin, granted, granted)
        pendingOrigin = null
        pendingCallback = null
    }

    fun hasPending(): Boolean = pendingCallback != null
}
