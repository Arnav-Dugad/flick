package io.github.arnavdugad.flick.ui.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.arnavdugad.flick.ui.theme.rememberReducedMotion

/** Provided by FlickNavigation so screens can share elements across navigation. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Morphs this element's bounds into the element with the same [key] on the next screen
 * (e.g. a profile card growing into the profile editor's header). No-op with reduced motion.
 */
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (rememberReducedMotion()) return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }
}
