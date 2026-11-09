package io.github.arnavdugad.flick.di

import android.app.Application
import io.github.arnavdugad.flick.data.SettingsRepository
import io.github.arnavdugad.flick.engine.FlickEngine
import io.github.arnavdugad.flick.feedback.Haptics
import io.github.arnavdugad.flick.system.ScreenStateMonitor
import io.github.arnavdugad.flick.trigger.proximity.ProximityTriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: every long-lived object is created once here.
 * Components reach it through `context.container` (see FlickApp).
 */
class AppContainer(val app: Application) {

    /** Lives as long as the process. Main-thread by default; hop to IO explicitly. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(app) }

    val screenState: ScreenStateMonitor by lazy { ScreenStateMonitor(app) }

    val haptics: Haptics by lazy { Haptics(app) }

    val proximity: ProximityTriggerSource by lazy { ProximityTriggerSource(app) }

    val engine: FlickEngine by lazy {
        FlickEngine(
            settings = settings,
            screenState = screenState,
            haptics = haptics,
            proximity = proximity,
            scope = appScope,
        )
    }
}
