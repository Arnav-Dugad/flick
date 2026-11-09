package io.github.arnavdugad.flick.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/** Flick's spring-based motion tokens. Springs feel physical and interruptible. */
object FlickMotion {
    /** Default spatial spring: slightly bouncy, for things that move or resize. */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    /** Fast spatial spring for small elements (toggles, chips). */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)

    /** Non-bouncy spring for colour and opacity. */
    fun <T> effects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    val screenSlide: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

/** True when the user turned animations off in system settings; decorative loops should stop. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
