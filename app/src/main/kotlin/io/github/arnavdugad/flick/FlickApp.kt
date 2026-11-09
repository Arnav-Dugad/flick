package io.github.arnavdugad.flick

import android.app.Application
import android.content.Context
import io.github.arnavdugad.flick.di.AppContainer
import io.github.arnavdugad.flick.update.UpdateWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FlickApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.updates.cleanUp()
        // Debug builds can't install release-signed updates, so they never check in the background.
        if (!BuildConfig.DEBUG) {
            container.appScope.launch {
                container.updates.prefs
                    .map { it.autoCheck to it.wifiOnly }
                    .distinctUntilChanged()
                    .collect { (enabled, wifiOnly) -> UpdateWorker.schedule(this@FlickApp, enabled, wifiOnly) }
            }
        }
    }
}

/** The app-wide dependency container, reachable from any component with a Context. */
val Context.container: AppContainer
    get() = (applicationContext as FlickApp).container
