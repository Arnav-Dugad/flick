package io.github.arnavdugad.flick

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.data.ThemeMode
import io.github.arnavdugad.flick.ui.navigation.FlickNavigation
import io.github.arnavdugad.flick.ui.theme.FlickTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = container.settings.settings

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
                        FlickNavigation(startWithOnboarding)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.screenState.refresh()
    }
}
