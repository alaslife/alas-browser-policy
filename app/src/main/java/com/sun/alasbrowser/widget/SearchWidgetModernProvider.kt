package com.sun.alasbrowser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.sun.alasbrowser.MainActivity
import com.sun.alasbrowser.R

/**
 * Material 3 / Android 17 inspired search widget
 * Features modern Material You design with rounded corners and Material icons
 */
class SearchWidgetModernProvider : AppWidgetProvider() {
    private val tag = "SearchWidgetModern"

    companion object {
        const val ACTION_SEARCH_CLICK = "com.sun.alasbrowser.SEARCH_CLICK"
        const val ACTION_VOICE_CLICK = "com.sun.alasbrowser.VOICE_CLICK"
        const val ACTION_CAMERA_CLICK = "com.sun.alasbrowser.CAMERA_CLICK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
        // Read updated preferences
        val prefs = com.sun.alasbrowser.data.BrowserPreferences(context)
        val currentTheme = prefs.appTheme
        val systemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Color palette — surface, text/icons, accent button bg, accent icon tint
        var surfaceColor: Int
        var onSurfaceVariantColor: Int
        var primaryContainerColor: Int
        var onPrimaryContainerColor: Int

        // Resolve colors based on theme
        when (currentTheme) {
            com.sun.alasbrowser.data.AppTheme.LIGHT -> {
                surfaceColor = 0xFFF7F7F8.toInt()
                onSurfaceVariantColor = 0xFF6B6B76.toInt()
                primaryContainerColor = 0xFFFF8C42.toInt()
                onPrimaryContainerColor = 0xFFFFFFFF.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.DARK -> {
                surfaceColor = 0xFF1C1C1E.toInt()
                onSurfaceVariantColor = 0xFF9A9AA0.toInt()
                primaryContainerColor = 0xFFFDB889.toInt()
                onPrimaryContainerColor = 0xFF1A1A1A.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.DARK_WHITE -> {
                surfaceColor = 0xFF18181B.toInt()
                onSurfaceVariantColor = 0xFF9A9AA0.toInt()
                primaryContainerColor = 0xFFE4E4E7.toInt()
                onPrimaryContainerColor = 0xFF18181B.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.MINT -> {
                surfaceColor = 0xFF162B22.toInt()
                onSurfaceVariantColor = 0xFF7CC4B8.toInt()
                primaryContainerColor = 0xFF5EEAA0.toInt()
                onPrimaryContainerColor = 0xFF0A1F16.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.PURPLE -> {
                surfaceColor = 0xFF1E0A2E.toInt()
                onSurfaceVariantColor = 0xFFCDA8E0.toInt()
                primaryContainerColor = 0xFFD946EF.toInt()
                onPrimaryContainerColor = 0xFFFFFFFF.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.MIDNIGHT_AZURE -> {
                surfaceColor = 0xFF0C1929.toInt()
                onSurfaceVariantColor = 0xFF7EB3E8.toInt()
                primaryContainerColor = 0xFF2563EB.toInt()
                onPrimaryContainerColor = 0xFFFFFFFF.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.REDBULL_WINTER -> {
                surfaceColor = 0xFF1E2A30.toInt()
                onSurfaceVariantColor = 0xFFA0B8C4.toInt()
                primaryContainerColor = 0xFF38BDF8.toInt()
                onPrimaryContainerColor = 0xFF0C1F2E.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.DARK_CRIMSON -> {
                surfaceColor = 0xFF200808.toInt()
                onSurfaceVariantColor = 0xFFEF9A9A.toInt()
                primaryContainerColor = 0xFFEF4444.toInt()
                onPrimaryContainerColor = 0xFFFFFFFF.toInt()
            }
            com.sun.alasbrowser.data.AppTheme.BEIGE -> {
                surfaceColor = 0xFFFAEBD7.toInt()
                onSurfaceVariantColor = 0xFF5D4037.toInt()
                primaryContainerColor = 0xFF8D6E63.toInt()
                onPrimaryContainerColor = 0xFFFFFFFF.toInt()
            }
            else -> {
                // System default — try Material You dynamic colors on Android 12+
                surfaceColor = if (systemDark) 0xFF1C1C1E.toInt() else 0xFFF7F7F8.toInt()
                onSurfaceVariantColor = if (systemDark) 0xFF9A9AA0.toInt() else 0xFF6B6B76.toInt()
                primaryContainerColor = if (systemDark) 0xFFFDB889.toInt() else 0xFFFF8C42.toInt()
                onPrimaryContainerColor = if (systemDark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        if (systemDark) {
                            surfaceColor = context.getColor(android.R.color.system_neutral1_900)
                            onSurfaceVariantColor = context.getColor(android.R.color.system_neutral2_200)
                            primaryContainerColor = context.getColor(android.R.color.system_accent1_300)
                            onPrimaryContainerColor = context.getColor(android.R.color.system_accent1_900)
                        } else {
                            surfaceColor = context.getColor(android.R.color.system_neutral1_50)
                            onSurfaceVariantColor = context.getColor(android.R.color.system_neutral2_700)
                            primaryContainerColor = context.getColor(android.R.color.system_accent1_100)
                            onPrimaryContainerColor = context.getColor(android.R.color.system_accent1_900)
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        // Keep action chips subtle instead of loud accent fills.
        primaryContainerColor = ColorUtils.blendARGB(
            surfaceColor,
            onSurfaceVariantColor,
            if (systemDark) 0.26f else 0.12f
        )
        onPrimaryContainerColor = ColorUtils.blendARGB(
            onSurfaceVariantColor,
            if (systemDark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt(),
            if (systemDark) 0.12f else 0.08f
        )

        val views = RemoteViews(context.packageName, R.layout.widget_search_nothing_style)
        
        // Apply dynamic colors
        views.setInt(R.id.widget_bg, "setColorFilter", surfaceColor)
        views.setInt(R.id.search_icon, "setColorFilter", onSurfaceVariantColor)
        views.setTextColor(R.id.search_text, onSurfaceVariantColor)
        views.setInt(R.id.voice_bg, "setColorFilter", primaryContainerColor)
        views.setInt(R.id.camera_bg, "setColorFilter", primaryContainerColor)
        views.setInt(R.id.voice_icon, "setColorFilter", onPrimaryContainerColor)
        views.setInt(R.id.camera_icon, "setColorFilter", onPrimaryContainerColor)


        // Search bar click - opens MainActivity with search focus
        val searchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SEARCH_CLICK
            putExtra("open_search", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val searchPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 0,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.search_container, searchPendingIntent)

        // Voice search click
        val voiceIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VOICE_CLICK
            putExtra("open_voice_search", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val voicePendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 1,
            voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.voice_button, voicePendingIntent)

        // Camera/Lens search click
        val cameraIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_CAMERA_CLICK
            putExtra("open_camera_search", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val cameraPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + 2,
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.camera_button, cameraPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(tag, "Modern widget render failed, falling back to simple layout", e)
            val fallback = RemoteViews(context.packageName, R.layout.widget_search_safe)

            val searchIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_SEARCH_CLICK
                putExtra("open_search", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_VOICE_CLICK
                putExtra("open_voice_search", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val cameraIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_CAMERA_CLICK
                putExtra("open_camera_search", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            fallback.setOnClickPendingIntent(
                R.id.widget_search_bar,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 0,
                    searchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            fallback.setOnClickPendingIntent(
                R.id.widget_mic_icon,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 1,
                    voiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            fallback.setOnClickPendingIntent(
                R.id.widget_camera_icon,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 2,
                    cameraIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, fallback)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
