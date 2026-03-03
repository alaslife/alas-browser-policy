package com.sun.alasbrowser.utils

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher

class FileChooserManager {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        
        val intent = fileChooserParams.createIntent()
        try {
            launcher.launch(intent)
            return true
        } catch (e: Exception) {
            filePathCallback = null
            return false
        }
    }
    
    fun onActivityResult(result: Array<Uri>?) {
        filePathCallback?.onReceiveValue(result)
        filePathCallback = null
    }
    
    fun cancelFileChooser() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }
}
