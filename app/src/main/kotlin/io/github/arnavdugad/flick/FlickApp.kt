package io.github.arnavdugad.flick

import android.app.Application
import android.content.Context
import io.github.arnavdugad.flick.di.AppContainer

class FlickApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The app-wide dependency container, reachable from any component with a Context. */
val Context.container: AppContainer
    get() = (applicationContext as FlickApp).container
