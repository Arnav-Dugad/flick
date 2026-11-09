package io.github.arnavdugad.flick

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.lifecycleScope
import io.github.arnavdugad.flick.ui.screens.update.UpdateSheet
import io.github.arnavdugad.flick.update.UpdateNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.data.ThemeMode
import io.github.arnavdugad.flick.ui.LocalMacroNames
import io.github.arnavdugad.flick.ui.navigation.FlickNavigation
import io.github.arnavdugad.flick.ui.theme.FlickTheme

/** Lets any screen open the update sheet. */
val LocalOpenUpdate = staticCompositionLocalOf<() -> Unit> { {} }

class MainActivity : ComponentActivity() {

    private var showUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = container.settings.settings
        handleIntent(intent)
        checkForUpdateOnOpen()

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            val dark = when (s?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FlickTheme(
                themeMode = s?.themeMode ?: ThemeMode.SYSTEM,
                amoledBlack = s?.amoledBlack ?: false,
                dynamicColor = s?.dynamicColor ?: true,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Wait for settings so we never flash onboarding at returning users.
                    if (s != null) {
                        val startWithOnboarding = rememberSaveable { !s.onboardingComplete }
                        CompositionLocalProvider(
                            LocalOpenUpdate provides { showUpdate = true },
                            LocalMacroNames provides s.macros.associate { it.id to it.name },
                        ) {
                            FlickNavigation(startWithOnboarding)
                        }
                    }
                    if (showUpdate) UpdateSheet(onDismiss = { showUpdate = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotifications.EXTRA_OPEN_UPDATE, false) == true) {
            showUpdate = true
            lifecycleScope.launch { container.updates.check(force = true) }
        }
    }

    /** Opening Flick checks for updates (at most every few hours; see UpdateRepository). */
    private fun checkForUpdateOnOpen() {
        if (BuildConfig.DEBUG) return
        lifecycleScope.launch {
            if (container.updates.prefs.first().autoCheck) container.updates.check(force = false)
        }
    }

    override fun onResume() {
        super.onResume()
        container.screenState.refresh()
        container.engine.appUiVisible = true
    }

    override fun onPause() {
        container.engine.appUiVisible = false
        super.onPause()
    }
}
