package com.sun.alasbrowser.data

enum class WebViewDarkMode(val displayName: String, val description: String) {
    LIGHT_PREFERRED("Light", "Light theme for app and websites"),
    AUTOMATIC("Automatic", "Follow device dark mode settings"),
    DARK_PREFERRED("Dark", "Dark theme for app and websites");

    companion object {
        fun fromString(value: String): WebViewDarkMode {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                AUTOMATIC
            }
        }
    }
}
