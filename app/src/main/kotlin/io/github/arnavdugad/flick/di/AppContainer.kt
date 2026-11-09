package io.github.arnavdugad.flick.di

import android.app.Application
import android.view.inputmethod.InputMethodManager
import io.github.arnavdugad.flick.data.AppInfoRepository
import io.github.arnavdugad.flick.data.SettingsRepository
import io.github.arnavdugad.flick.engine.FlickEngine
import io.github.arnavdugad.flick.feedback.Haptics
import io.github.arnavdugad.flick.system.ScreenStateMonitor
import io.github.arnavdugad.flick.trigger.camera.FaceTriggerSource
import io.github.arnavdugad.flick.trigger.camera.HandTriggerSource
import io.github.arnavdugad.flick.trigger.keys.VolumeKeyTriggerSource
import io.github.arnavdugad.flick.trigger.motion.MotionTriggerSource
import io.github.arnavdugad.flick.trigger.proximity.ProximityTriggerSource
import io.github.arnavdugad.flick.trigger.voice.VoiceTriggerSource
import io.github.arnavdugad.flick.vision.CameraController
import io.github.arnavdugad.flick.vision.VisionPreview
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

    val camera: CameraController by lazy { CameraController(app, appScope) }
    val visionPreview: VisionPreview by lazy { VisionPreview() }

    val proximity: ProximityTriggerSource by lazy { ProximityTriggerSource(app) }
    val motion: MotionTriggerSource by lazy { MotionTriggerSource(app) }
    val volumeKeys: VolumeKeyTriggerSource by lazy { VolumeKeyTriggerSource(app) }
    val face: FaceTriggerSource by lazy { FaceTriggerSource(app, camera, appScope, visionPreview) }
    val hand: HandTriggerSource by lazy { HandTriggerSource(app, camera, appScope, visionPreview) }
    val voice: VoiceTriggerSource by lazy { VoiceTriggerSource(app) }

    val apps: AppInfoRepository by lazy { AppInfoRepository(app) }

    /** System UI, the platform and every installed keyboard: overlays, not apps. */
    private fun transientPackages(): Set<String> {
        val imes = runCatching {
            app.getSystemService(InputMethodManager::class.java)?.inputMethodList?.map { it.packageName }
        }.getOrNull().orEmpty()
        return setOf("com.android.systemui", "android") + imes
    }

    val engine: FlickEngine by lazy {
        FlickEngine(
            settings = settings,
            screenState = screenState,
            haptics = haptics,
            sources = listOf(proximity, motion, volumeKeys, face, hand, voice),
            volumeKeys = volumeKeys,
            scope = appScope,
            ownPackage = app.packageName,
            transientPackages = transientPackages(),
        )
    }
}
