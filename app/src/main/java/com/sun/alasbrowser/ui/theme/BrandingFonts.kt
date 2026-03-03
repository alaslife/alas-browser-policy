package com.sun.alasbrowser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.R

/**
 * Custom fonts for the app
 */

// Custom font families
val customFontFamily = FontFamily.Default

// Standard font families (existing)
val cyreneRegularFontFamily = FontFamily(
    Font(R.font.cyrene_regular, FontWeight.Normal)
)

val dotphoriaFontFamily = FontFamily(
    Font(R.font.dotphoria, FontWeight.Normal)
)

/**
 * Extended typography with custom fonts
 */
object BrandingTypography {
    
    /**
     * Large header styling
     */
    val brandingLarge = TextStyle(
        fontFamily = customFontFamily,
        fontSize = 64.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 72.sp,
        letterSpacing = 0.sp
    )
    
    val brandingMedium = TextStyle(
        fontFamily = customFontFamily,
        fontSize = 48.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 56.sp,
        letterSpacing = 0.sp
    )
    
    val brandingSmall = TextStyle(
        fontFamily = customFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    )
    
    /**
     * App name/title styling
     */
    val appTitle = TextStyle(
        fontFamily = customFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
    
    /**
     * Subtitle styling
     */
    val appSubtitle = TextStyle(
        fontFamily = customFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
}

/**
 * Create extended typography that includes custom fonts
 */
fun createBrandedTypography(baseTypography: Typography): BrandedTypography {
    return BrandedTypography(
        displayLarge = baseTypography.displayLarge.copy(
            fontFamily = customFontFamily
        ),
        displayMedium = baseTypography.displayMedium.copy(
            fontFamily = customFontFamily
        ),
        headlineLarge = baseTypography.headlineLarge.copy(
            fontFamily = customFontFamily,
            letterSpacing = 0.sp
        ),
        headlineMedium = baseTypography.headlineMedium.copy(
            fontFamily = customFontFamily,
            letterSpacing = 0.sp
        ),
        titleLarge = baseTypography.titleLarge.copy(
            fontFamily = customFontFamily,
            letterSpacing = 0.sp
        ),
        bodyLarge = baseTypography.bodyLarge,
        bodyMedium = baseTypography.bodyMedium,
        bodySmall = baseTypography.bodySmall,
        labelLarge = baseTypography.labelLarge,
        labelMedium = baseTypography.labelMedium,
        labelSmall = baseTypography.labelSmall
    )
}

/**
 * Data class for branded typography (extending Material3 Typography)
 */
data class BrandedTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val titleLarge: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle
)
