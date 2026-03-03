package com.sun.alasbrowser.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

object HomeScreenShortcutManager {
    
    fun addShortcut(
        context: Context,
        url: String,
        title: String,
        icon: Bitmap?
    ) {
        try {
            val shortcutId = "shortcut_${url.hashCode()}"
            
            val shortcutIntent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
                setPackage(context.packageName)
            }
            
            val iconCompat = if (icon != null) {
                IconCompat.createWithBitmap(icon)
            } else {
                IconCompat.createWithResource(context, android.R.drawable.ic_menu_view)
            }
            
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(title.take(12))
                .setLongLabel(title)
                .setIcon(iconCompat)
                .setIntent(shortcutIntent)
                .build()
            
            val success = ShortcutManagerCompat.requestPinShortcut(
                context,
                shortcutInfo,
                null
            )
            
            if (success) {
                Toast.makeText(
                    context,
                    "Shortcut created for $title",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Failed to create shortcut",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Error creating shortcut: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
