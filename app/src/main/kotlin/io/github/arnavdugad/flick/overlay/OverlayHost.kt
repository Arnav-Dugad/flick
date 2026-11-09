package io.github.arnavdugad.flick.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.theme.FlickTheme

/** Lifecycle + saved state for Compose views living in accessibility overlay windows. */
class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * Adds and removes `TYPE_ACCESSIBILITY_OVERLAY` windows. These need no "display over other apps"
 * permission and don't change what the accessibility service sees.
 */
class OverlayHost(private val service: AccessibilityService) {

    private val wm: WindowManager = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner().also { it.start() }
    private val views = HashSet<View>()

    fun addCompose(params: WindowManager.LayoutParams, content: @Composable () -> Unit): ComposeView {
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { OverlayTheme(content) }
        }
        add(view, params)
        return view
    }

    fun add(view: View, params: WindowManager.LayoutParams) {
        runCatching { wm.addView(view, params) }
            .onSuccess { views += view }
            .onFailure { Log.w(TAG, "addView failed", it) }
    }

    fun update(view: View, params: WindowManager.LayoutParams) {
        if (view in views) runCatching { wm.updateViewLayout(view, params) }
    }

    fun remove(view: View?) {
        if (view == null || view !in views) return
        runCatching { wm.removeViewImmediate(view) }
        views -= view
    }

    fun destroy() {
        views.toList().forEach(::remove)
        owner.destroy()
    }

    companion object {
        private const val TAG = "FlickOverlay"

        fun params(
            width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
            height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
            touchable: Boolean = true,
            gravity: Int = Gravity.TOP or Gravity.START,
            x: Int = 0,
            y: Int = 0,
        ) = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}

/** Flick's theme for overlays, following the user's appearance settings. */
@Composable
private fun OverlayTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s by context.container.settings.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    FlickTheme(themeMode = s.themeMode, amoledBlack = s.amoledBlack, dynamicColor = s.dynamicColor, content = content)
}
