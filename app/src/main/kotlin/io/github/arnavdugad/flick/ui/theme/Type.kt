package io.github.arnavdugad.flick.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val base = Typography()

/**
 * Expressive scale on the system font: heavier, tighter display and headline styles for strong
 * hierarchy; body styles keep the M3 defaults for readability at 200% font size.
 */
val FlickTypography = Typography(
    displayLarge = base.displayLarge.tight(FontWeight.SemiBold),
    displayMedium = base.displayMedium.tight(FontWeight.SemiBold),
    displaySmall = base.displaySmall.tight(FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.tight(FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.tight(FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.tight(FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall,
)

private fun TextStyle.tight(weight: FontWeight) = copy(
    fontWeight = weight,
    letterSpacing = (-0.02).em,
    lineHeight = if (lineHeight.isSp) (lineHeight.value * 0.98f).sp else lineHeight,
)
