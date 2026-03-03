package com.sun.alasbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.sun.alasbrowser.data.BrowserPreferences

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NightModeOverlay(
    preferences: BrowserPreferences,
    modifier: Modifier = Modifier
) {
    val isActive = preferences.isNightModeActiveNow()
    if (!isActive) return

    val colorTemp = preferences.nightModeColorTemp
    val dimming = preferences.nightModeDimming

    if (colorTemp <= 0f && dimming <= 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { false }
    ) {
        if (colorTemp > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NightModeTuning.warmOverlayColor(colorTemp))
            )
        }
        if (dimming > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = NightModeTuning.dimOverlayAlpha(dimming)))
            )
        }
    }
}
