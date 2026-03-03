package com.sun.alasbrowser.data

enum class AppTheme(val displayName: String, val description: String) {
    SYSTEM("System Default", "Follow system theme"),
    LIGHT("Light", "Classic white with black"),
    DARK("Dark", "Original theme"),
    DARK_WHITE("Dark-White", "New modern theme, Recommended"),
    MINT("Mint", "Lime-mint shades"),
    PURPLE("Purple", "Lavender-purple tones"),
    MIDNIGHT_AZURE("Midnight Azure", "Deep blue tones"),
    REDBULL_WINTER("Redbull Winter Edition", "Theme inspired by Red Bull Fuji Apple & Ginger"),
    DARK_CRIMSON("Dark crimson", "Deep red tones"),
    BEIGE("Beige", "Theme made with logo");

    companion object {
        fun fromString(value: String): AppTheme {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                SYSTEM
            }
        }
    }
}
