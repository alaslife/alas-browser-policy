package com.sun.alasbrowser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
private fun getColorScheme(appTheme: com.sun.alasbrowser.data.AppTheme, systemDark: Boolean): androidx.compose.material3.ColorScheme {
    val useDark = when (appTheme) {
        com.sun.alasbrowser.data.AppTheme.SYSTEM -> systemDark
        com.sun.alasbrowser.data.AppTheme.LIGHT, com.sun.alasbrowser.data.AppTheme.BEIGE -> false
        else -> true // All others are dark-based
    }

    return when (appTheme) {
        com.sun.alasbrowser.data.AppTheme.LIGHT -> lightColorScheme(
            primary = Color(0xFFFF8C42),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFFFF8C42),
            onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF00897B),
            onTertiary = Color(0xFFFFFFFF),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFF5F5F5),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF666666),
            outline = Color(0xFFE0E0E0),
            outlineVariant = Color(0xFFE0E0E0),
            error = Color(0xFFE53935),
            onError = Color(0xFFFFFFFF)
        )
        com.sun.alasbrowser.data.AppTheme.BEIGE -> lightColorScheme(
            primary = Color(0xFF8D6E63),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF8D6E63),
            onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFFA1887F),
            background = Color(0xFFFFF8E1), // Beige-ish
            onBackground = Color(0xFF3E2723),
            surface = Color(0xFFFFE0B2),
            onSurface = Color(0xFF3E2723),
            surfaceVariant = Color(0xFFFFECB3),
            onSurfaceVariant = Color(0xFF4E342E),
            outline = Color(0xFFD7CCC8),
            outlineVariant = Color(0xFFD7CCC8)
        )
        com.sun.alasbrowser.data.AppTheme.DARK_WHITE -> darkColorScheme(
            primary = Color(0xFFECEFF1), // White-ish Blue
            onPrimary = Color(0xFF000000),
            secondary = Color(0xFFCFD8DC),
            onSecondary = Color(0xFF000000),
            tertiary = Color(0xFFB0BEC5),
            background = Color(0xFF101010),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1C1C1E),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF262629),
            onSurfaceVariant = Color(0xFFA0A0A0),
            outline = Color(0xFF424242),
            outlineVariant = Color(0xFF3C4043)
        )
        com.sun.alasbrowser.data.AppTheme.MINT -> darkColorScheme(
            primary = Color(0xFF69F0AE),
            onPrimary = Color(0xFF000000),
            secondary = Color(0xFFB9F6CA),
            onSecondary = Color(0xFF000000),
            tertiary = Color(0xFF00E676),
            background = Color(0xFF00150F), // Very dark mint
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1B2E24),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF23382D),
            onSurfaceVariant = Color(0xFF80CBC4),
            outline = Color(0xFF2E4D40)
        )
        com.sun.alasbrowser.data.AppTheme.PURPLE -> darkColorScheme(
            primary = Color(0xFFE040FB),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFFEA80FC),
            onSecondary = Color(0xFF000000),
            tertiary = Color(0xFFD500F9),
            background = Color(0xFF0F0014),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF2A0F36),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF381446),
            onSurfaceVariant = Color(0xFFE1BEE7),
            outline = Color(0xFF4A148C)
        )
        com.sun.alasbrowser.data.AppTheme.MIDNIGHT_AZURE -> darkColorScheme(
            primary = Color(0xFF2979FF),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF448AFF),
            onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF2962FF),
            background = Color(0xFF010B19),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF0D1D36),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF132742),
            onSurfaceVariant = Color(0xFF90CAF9),
            outline = Color(0xFF1565C0)
        )
        com.sun.alasbrowser.data.AppTheme.REDBULL_WINTER -> darkColorScheme(
            primary = Color(0xFF4FC3F7), // Icy Blue
            onPrimary = Color(0xFF000000),
            secondary = Color(0xFFB3E5FC),
            onSecondary = Color(0xFF000000),
            tertiary = Color(0xFFFF5252), // Red accent
            onTertiary = Color(0xFFFFFFFF),
            background = Color(0xFF102027),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF263238),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF37474F),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF546E7A)
        )
        com.sun.alasbrowser.data.AppTheme.DARK_CRIMSON -> darkColorScheme(
            primary = Color(0xFFFF1744),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFFFF5252),
            onSecondary = Color(0xFF000000),
            tertiary = Color(0xFFD50000),
            background = Color(0xFF1A0000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF2D0A0A),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF420F0F),
            onSurfaceVariant = Color(0xFFFF8A80),
            outline = Color(0xFF880E4F)
        )
        else -> darkColorScheme( // Standard Dark
            primary = Color(0xFFFDB889),
            onPrimary = Color(0xFF030303),
            secondary = Color(0xFFFDB889),
            onSecondary = Color(0xFF030303),
            tertiary = Color(0xFF4DB6AC),
            onTertiary = Color(0xFF030303),
            background = Color(0xFF030303),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF2C2C2E),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0xFF808080),
            outline = Color(0xFF424242),
            outlineVariant = Color(0xFF3C4043),
            error = Color(0xFFFF6B6B),
            onError = Color(0xFFFFFFFF)
        )
    }
}

@Composable
fun AlasBrowserTheme(
    appTheme: com.sun.alasbrowser.data.AppTheme,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = getColorScheme(appTheme, systemDark)
    
    val darkTheme = when (appTheme) {
        com.sun.alasbrowser.data.AppTheme.SYSTEM -> systemDark
        com.sun.alasbrowser.data.AppTheme.LIGHT, com.sun.alasbrowser.data.AppTheme.BEIGE -> false
        else -> true
    }

    LaunchedEffect(darkTheme) {
        AlasColors.setDarkTheme(darkTheme)
    }
    
    val view = LocalView.current
    val context = LocalContext.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            
            // Get window insets controller
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            
            // Use appropriate icons/text color on status and navigation bars
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
            
            // Set system bar colors to match theme background
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
