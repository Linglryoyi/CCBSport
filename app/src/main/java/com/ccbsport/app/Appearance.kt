package com.ccbsport.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val label: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark)
}

val PurpleDark = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    surface = Color(0xFF141218), background = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9), onBackground = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0), outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F), surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20), surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B)
)
