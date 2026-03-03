package com.sun.alasbrowser.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object AlasColors {
    private var _isDarkTheme = mutableStateOf(true)
    
    fun setDarkTheme(dark: Boolean) {
        _isDarkTheme.value = dark
    }
    
    val PrimaryBackground: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.background
    
    val SecondaryBackground: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surface
    
    val Accent: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
    
    val TextPrimary: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    
    val TextSecondary: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    
    val UnfocusedIndicator: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outline
    
    val Placeholder: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    
    val CardBackground: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    
    val SuggestionCardBg: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surface
    
    val Divider: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    
    val Success: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    
    val Error: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error
    
    val Warning: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer 
        
    val PinkAccent: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary // Fallback to primary for now to respect theme
        
    val DarkSurface: Color
        @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surface
}
