package org.avitrack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AviTrackColors(
    val yolkPrimary: Color = Color(0xFFF78E1E),
    val yolkHover: Color = Color(0xFFFFE761),
    val yolkAmber: Color = Color(0xFFFBA919),
    val yolkDeep: Color = Color(0xFFE85B1A),
    val dsmTechBlue: Color = Color(0xFF0A5EB0),
    val combCrimson: Color = Color(0xFFE62E3D),
    val mahoganyDark: Color = Color(0xFF120A07),
    val mahoganySurface: Color = Color(0xFF241009),
    val mahoganyPrimary: Color = Color(0xFF3B190E),
    val cinnamonBorder: Color = Color(0xFF7D3B1E),
    val downCream: Color = Color(0xFFECD1B8),
    val ochreAccent: Color = Color(0xFFE2B466),
    val eggshellCanvas: Color = Color(0xFFFAF6F0),
)

private val AviTrackPalette = AviTrackColors()

val LocalAviTrackColors = staticCompositionLocalOf { AviTrackPalette }

private val DayColorScheme = with(AviTrackPalette) {
    lightColorScheme(
        primary = yolkPrimary,
        onPrimary = mahoganyPrimary,
        primaryContainer = yolkHover,
        onPrimaryContainer = mahoganyPrimary,
        inversePrimary = yolkAmber,
        secondary = cinnamonBorder,
        onSecondary = eggshellCanvas,
        secondaryContainer = downCream,
        onSecondaryContainer = mahoganyPrimary,
        tertiary = dsmTechBlue,
        onTertiary = Color.White,
        tertiaryContainer = ochreAccent,
        onTertiaryContainer = mahoganyPrimary,
        background = eggshellCanvas,
        onBackground = mahoganyPrimary,
        surface = Color.White,
        onSurface = mahoganyPrimary,
        surfaceVariant = downCream,
        onSurfaceVariant = cinnamonBorder,
        surfaceTint = yolkPrimary,
        surfaceBright = Color.White,
        surfaceDim = downCream,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = eggshellCanvas,
        surfaceContainer = eggshellCanvas,
        surfaceContainerHigh = downCream,
        surfaceContainerHighest = downCream,
        inverseSurface = mahoganyPrimary,
        inverseOnSurface = eggshellCanvas,
        error = combCrimson,
        onError = Color.White,
        errorContainer = downCream,
        onErrorContainer = mahoganyPrimary,
        outline = cinnamonBorder,
        outlineVariant = downCream,
        scrim = mahoganyDark,
    )
}

private val NightColorScheme = with(AviTrackPalette) {
    darkColorScheme(
        primary = yolkPrimary,
        onPrimary = mahoganyPrimary,
        primaryContainer = mahoganyPrimary,
        onPrimaryContainer = yolkHover,
        inversePrimary = cinnamonBorder,
        secondary = ochreAccent,
        onSecondary = mahoganyDark,
        secondaryContainer = mahoganyPrimary,
        onSecondaryContainer = downCream,
        tertiary = dsmTechBlue,
        onTertiary = Color.White,
        tertiaryContainer = mahoganyPrimary,
        onTertiaryContainer = ochreAccent,
        background = mahoganyDark,
        onBackground = downCream,
        surface = mahoganySurface,
        onSurface = downCream,
        surfaceVariant = mahoganyPrimary,
        onSurfaceVariant = downCream,
        surfaceTint = yolkPrimary,
        surfaceBright = mahoganyPrimary,
        surfaceDim = mahoganyDark,
        surfaceContainerLowest = mahoganyDark,
        surfaceContainerLow = mahoganySurface,
        surfaceContainer = mahoganySurface,
        surfaceContainerHigh = mahoganyPrimary,
        surfaceContainerHighest = mahoganyPrimary,
        inverseSurface = downCream,
        inverseOnSurface = mahoganyPrimary,
        error = combCrimson,
        onError = Color.White,
        errorContainer = mahoganyPrimary,
        onErrorContainer = eggshellCanvas,
        outline = cinnamonBorder,
        outlineVariant = mahoganyPrimary,
        scrim = mahoganyDark,
    )
}

@Composable
fun AviTrackTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAviTrackColors provides AviTrackPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) NightColorScheme else DayColorScheme,
            content = content,
        )
    }
}
