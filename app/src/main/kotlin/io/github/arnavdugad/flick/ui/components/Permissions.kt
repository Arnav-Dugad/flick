package io.github.arnavdugad.flick.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.core.model.TriggerRequirement
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus

/** The runtime permission a trigger needs, if any. */
val TriggerType.permission: String?
    get() = when (requirement) {
        TriggerRequirement.CAMERA -> Manifest.permission.CAMERA
        TriggerRequirement.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        else -> null
    }

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Granted state of the permissions Flick uses, refreshed every time the screen resumes. */
class PermissionSnapshot(val camera: Boolean, val microphone: Boolean) {
    fun granted(type: TriggerType): Boolean = when (type.requirement) {
        TriggerRequirement.CAMERA -> camera
        TriggerRequirement.MICROPHONE -> microphone
        else -> true
    }
}

@Composable
fun rememberPermissionSnapshot(): PermissionSnapshot {
    val context = LocalContext.current
    fun read() = PermissionSnapshot(
        camera = context.hasPermission(Manifest.permission.CAMERA),
        microphone = context.hasPermission(Manifest.permission.RECORD_AUDIO),
    )
    var snapshot by remember { mutableStateOf(read()) }
    LifecycleResumeEffect(Unit) {
        snapshot = read()
        onPauseOrDispose { }
    }
    return snapshot
}

/** Asks for a runtime permission; [onResult] receives whether it was granted. */
@Composable
fun rememberPermissionRequest(onResult: (Boolean) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) { { permission: String -> launcher.launch(permission) } }
}

/** A short problem description for a trigger, or null when it's good to go. */
@Composable
fun triggerProblem(type: TriggerType, status: SourceStatus?, permissions: PermissionSnapshot): String? = when {
    !permissions.granted(type) -> stringResource(
        if (type.requirement == TriggerRequirement.CAMERA) R.string.src_no_permission_camera else R.string.src_no_permission_mic,
    )
    status == SourceStatus.NO_HARDWARE -> stringResource(R.string.src_no_hardware)
    status == SourceStatus.UNSUPPORTED -> stringResource(
        if (type.requirement == TriggerRequirement.MICROPHONE) R.string.src_unsupported_voice else R.string.src_unsupported,
    )
    status == SourceStatus.BUSY -> stringResource(R.string.src_busy)
    status == SourceStatus.BLOCKED -> stringResource(R.string.src_blocked)
    status == SourceStatus.ERROR -> stringResource(R.string.src_error)
    else -> null
}
