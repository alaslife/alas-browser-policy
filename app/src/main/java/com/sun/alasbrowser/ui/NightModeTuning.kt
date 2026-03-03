package com.sun.alasbrowser.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Centralized tuning for night-mode overlays so runtime and preview stay in sync.
 */
internal object NightModeTuning {
    private fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun warmOverlayColor(colorTemp: Float): Color {
        val warmth = smoothStep(colorTemp)
        val red = 1f
        val green = (0.90f - warmth * 0.30f).coerceIn(0.52f, 0.90f)
        val blue = (0.78f - warmth * 0.68f).coerceIn(0.06f, 0.78f)
        val alpha = (0.04f + warmth * 0.30f).coerceIn(0f, 0.34f)
        return Color(red, green, blue, alpha)
    }

    fun dimOverlayAlpha(dimming: Float): Float {
        val level = dimming.coerceIn(0f, 1f)
        val eased = 1f - (1f - level).pow(1.45f)
        return (eased * 0.68f).coerceIn(0f, 0.68f)
    }
}

