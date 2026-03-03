package com.sun.alasbrowser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.RemoteViews
import com.sun.alasbrowser.MainActivity
import com.sun.alasbrowser.R

class SearchWidgetProvider : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_search)
        
        // Intent to open browser with search
        val searchIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sun.alasbrowser.WIDGET_SEARCH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val searchPendingIntent = PendingIntent.getActivity(
            context,
            0,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent for voice search - opens app with voice search trigger
        val voiceIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sun.alasbrowser.WIDGET_VOICE_SEARCH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val voicePendingIntent = PendingIntent.getActivity(
            context,
            1,
            voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent for camera search - opens app with camera trigger
        val cameraIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.sun.alasbrowser.CAMERA_CLICK"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val cameraPendingIntent = PendingIntent.getActivity(
            context,
            2,
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set click listeners
        views.setOnClickPendingIntent(R.id.widget_search_bar, searchPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_mic_icon, voicePendingIntent)
        views.setOnClickPendingIntent(R.id.widget_camera_icon, cameraPendingIntent)
        
        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }
}
