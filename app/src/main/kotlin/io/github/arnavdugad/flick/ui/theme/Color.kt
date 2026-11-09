package io.github.arnavdugad.flick.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Flick's fallback palette ("Ember"): a warm signal-orange primary over ink neutrals, used
 * when dynamic colour is off or unavailable (Android 8–11). Tones follow the M3 tonal system.
 */

val FlickLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFA23F16),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF380D00),
    inversePrimary = Color(0xFFFFB59B),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF3A6A3E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBF0BA),
    onTertiaryContainer = Color(0xFF002106),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceTint = Color(0xFFA23F16),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFE8D6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E5DE),
    surfaceContainerHighest = Color(0xFFF1DFD8),
)

val FlickDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5B1A00),
    primaryContainer = Color(0xFF812800),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFFA23F16),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA0D4A0),
    onTertiary = Color(0xFF053911),
    tertiaryContainer = Color(0xFF225127),
    onTertiaryContainer = Color(0xFFBBF0BA),
    background = Color(0xFF1A110E),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1A110E),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB59B),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF392E2B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423733),
    surfaceDim = Color(0xFF1A110E),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322F),
)

/** True-black variant for OLED screens: backgrounds go to #000, containers stay just above it. */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
