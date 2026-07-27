package com.gerwinkuijntjes.hours.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = DarkBackground,
    primaryContainer = GreenTintDark,
    onPrimaryContainer = DarkInk,
    secondary = DarkMutedInk,
    // Drives the navigation bar's selected pill; left at the Material default it
    // lands on a purple that has nothing to do with the rest of the app.
    secondaryContainer = GreenTintDark,
    onSecondaryContainer = Green80,
    error = Rust80,
    errorContainer = RustTintDark,
    onErrorContainer = Rust80,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkHairline,
    outlineVariant = DarkHairline
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = GreenTint,
    onPrimaryContainer = Ink,
    secondary = MutedInk,
    secondaryContainer = GreenTint,
    onSecondaryContainer = Green40,
    error = Rust40,
    errorContainer = RustTint,
    onErrorContainer = DeepRust,
    background = Cream,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    surfaceVariant = SoftSand,
    onSurfaceVariant = MutedInk,
    outline = Hairline,
    outlineVariant = Hairline
)

@Composable
fun HoursTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
