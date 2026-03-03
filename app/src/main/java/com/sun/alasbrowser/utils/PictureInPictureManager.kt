package com.sun.alasbrowser.utils

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

object PictureInPictureManager {
    
    fun isPipSupported(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else {
            false
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPipMode(activity: Activity, aspectRatio: Rational = Rational(16, 9)): Boolean {
        if (!isPipSupported(activity)) return false
        
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()
        
        return try {
            activity.enterPictureInPictureMode(params)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun isInPipMode(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.isInPictureInPictureMode
        } else {
            false
        }
    }
}
