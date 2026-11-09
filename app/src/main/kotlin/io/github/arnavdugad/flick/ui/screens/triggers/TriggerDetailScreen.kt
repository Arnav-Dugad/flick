package io.github.arnavdugad.flick.ui.screens.triggers

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerCategory
import io.github.arnavdugad.flick.core.model.TriggerRequirement
import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.ProfileResolver
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.actionIcon
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.StatusPill
import io.github.arnavdugad.flick.ui.components.hasPermission
import io.github.arnavdugad.flick.ui.components.permission
import io.github.arnavdugad.flick.ui.components.rememberPermissionRequest
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.illustrations.WaveIllustration
import io.github.arnavdugad.flick.ui.screens.home.ActionPickerSheet
import io.github.arnavdugad.flick.ui.triggerDescription
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel
import io.github.arnavdugad.flick.trigger.SourceStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerDetailScreen(type: TriggerType, profileId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val engine = container.engine
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val spec = TriggerSpecs[type]

    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val statuses by engine.statuses.collectAsStateWithLifecycle()
    val permissions = rememberPermissionSnapshot()
    val status = statuses[type]
    val problem = triggerProblem(type, status, permissions)
    var permissionDenied by remember { mutableStateOf(false) }
    val requestPermission = rememberPermissionRequest { granted ->
        permissionDenied = !granted
        engine.refreshSources()
    }

    // Listen for this trigger while the screen is visible; nothing runs on screen meanwhile.
    LifecycleResumeEffect(type) {
        engine.setCalibrating(type)
        engine.refreshSources()
        onPauseOrDispose { engine.setCalibrating(null) }
    }
    val usesCamera = type.requirement == TriggerRequirement.CAMERA
    DisposableEffect(usesCamera) {
        val token = Any()
        if (usesCamera) container.visionPreview.acquire(token)
        onDispose { container.visionPreview.release(token) }
    }

    var detected by remember { mutableIntStateOf(0) }
    LaunchedEffect(type) {
        engine.events.collect { if (it.trigger == type) detected++ }
    }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(type) {
        while (true) withFrameMillis { level = engine.level(type) }
    }

    var showPicker by remember { mutableStateOf(false) }
    val s = settings ?: FlickSettings()
    val profile = profileId?.let { s.profile(it) }
    val binding = profile?.bindings?.get(type)
    val saved = s.tuningFor(type)
    var draft by remember(saved) { mutableStateOf(saved) }
    fun save(t: TriggerTuning) = scope.launch { container.settings.setTuning(type, t) }

    val neutralDone = stringResource(R.string.detail_neutral_done)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(triggerLabel(type)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    IconBadge(triggerIcon(type), size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        triggerDescription(type),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (profile == null) {
                item(key = "used") { UsedInCard(type, s) }
            } else item(key = "does") {
                FlickCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.detail_profile_does, profile.name),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        AssistChip(
                            onClick = { showPicker = true },
                            label = { Text(binding?.let { actionLabel(it) } ?: stringResource(R.string.detail_none)) },
                            leadingIcon = binding?.let { { Icon(actionIcon(it), null, Modifier.size(18.dp)) } },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .widthIn(max = 240.dp),
                        )
                    }
                    if (binding != null) {
                        TextButton(onClick = { scope.launch { container.settings.setBinding(profile.id, type, null) } }) {
                            Text(stringResource(R.string.detail_turn_off))
                        }
                    }
                }
            }

            item(key = "live") {
                LiveCard(
                    type = type,
                    level = level,
                    threshold = if (spec.thresholdRange != null) spec.threshold(draft.sensitivity) else null,
                    detected = detected,
                    problem = problem,
                    status = status,
                    permissionDenied = permissionDenied,
                    onRequestPermission = { type.permission?.let(requestPermission) },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }

            if (usesCamera && problem == null) {
                item(key = "preview") {
                    val points by container.visionPreview.points.collectAsStateWithLifecycle()
                    val fps by container.camera.fps.collectAsStateWithLifecycle()
                    Column {
                        LandmarkPreview(points, Modifier.padding(horizontal = 40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.detail_preview_note) + "  " +
                                stringResource(R.string.detail_camera_fps, fps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            if (spec.hasSensitivity || spec.hasHold || type.requirement != TriggerRequirement.KEYS) {
                item(key = "tuning_h") { SectionHeader(stringResource(R.string.detail_tuning)) }
                item(key = "tuning") {
                    TuningCard(
                        spec = spec,
                        draft = draft,
                        onDraft = { draft = it },
                        onCommit = { save(draft) },
                        onReset = {
                            draft = spec.defaults
                            scope.launch { container.settings.setTuning(type, null) }
                        },
                    )
                }
            }

            if (type.supportsNeutral) {
                item(key = "neutral") {
                    FlickCard {
                        Text(stringResource(R.string.detail_neutral), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.detail_neutral_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                engine.setNeutral(type)
                                scope.launch { snackbar.showSnackbar(neutralDone) }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.detail_neutral))
                        }
                    }
                }
            }

            hintFor(type, profile?.bindings?.keys ?: ProfileResolver.allTriggers(s.profiles))?.let { hint ->
                item(key = "hint") {
                    FlickCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ActionPickerSheet(
            title = triggerLabel(type),
            selected = binding,
            onSelect = {
                profile?.let { p -> scope.launch { container.settings.setBinding(p.id, type, it) } }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

private val TriggerType.supportsNeutral: Boolean
    get() = name.startsWith("HEAD_") || name.startsWith("PHONE_TILT_")

private fun hintFor(type: TriggerType, used: Set<TriggerType>): Int? = when {
    type == TriggerType.PROXIMITY_WAVE -> R.string.detail_proximity_virtual
    (type == TriggerType.HEAD_TILT_DOWN || type == TriggerType.HEAD_TILT_UP) &&
        TriggerType.HEAD_NOD in used -> R.string.detail_nod_conflict
    type.requirement == TriggerRequirement.KEYS -> R.string.detail_volume_hint
    type.requirement == TriggerRequirement.MICROPHONE -> R.string.detail_voice_hint
    else -> null
}

@Composable
private fun UsedInCard(type: TriggerType, s: FlickSettings) {
    val uses = s.profiles.mapNotNull { p -> p.bindings[type]?.let { p to it } }
    FlickCard {
        Text(stringResource(R.string.detail_used_in), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (uses.isEmpty()) {
            Text(
                stringResource(R.string.detail_not_used),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for ((p, action) in uses) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(actionIcon(action), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel(action), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveCard(
    type: TriggerType,
    level: Float,
    threshold: Float?,
    detected: Int,
    problem: String?,
    status: SourceStatus?,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    FlickCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_live),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            StatusPill(
                problem ?: stringResource(if (status == SourceStatus.RUNNING) R.string.src_running else R.string.src_ready),
                active = problem == null && status == SourceStatus.RUNNING,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (problem != null && type.permission != null && !context.hasPermission(type.permission!!)) {
            Text(problem, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (permissionDenied) {
                OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.detail_open_app_settings))
                }
            } else {
                Button(onClick = onRequestPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.detail_grant))
                }
            }
            return@FlickCard
        }
        if (type == TriggerType.PROXIMITY_WAVE) {
            LocalContext.current.container.proximity.sensorName?.let {
                Text(
                    stringResource(R.string.detail_sensor_name, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            WaveIllustration(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                pulseKey = detected,
                sensorCovered = level > 0.5f,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (type.requirement == TriggerRequirement.MICROPHONE) {
            val heard by LocalContext.current.container.voice.heard.collectAsStateWithLifecycle()
            Text(
                if (heard.isBlank()) stringResource(R.string.detail_listening) else stringResource(R.string.detail_heard, heard),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LevelMeter(level = level, threshold = threshold, pulseKey = detected)
        }
        Spacer(Modifier.height(12.dp))
        val points by LocalContext.current.container.visionPreview.points.collectAsStateWithLifecycle()
        val waitingFor = when {
            type.requirement != TriggerRequirement.CAMERA || status != SourceStatus.RUNNING -> null
            type.category == TriggerCategory.HAND -> if (points.hand == null) R.string.detail_waiting_hand else null
            else -> if (points.face == null) R.string.detail_waiting_face else null
        }
        Text(
            when {
                detected > 0 -> pluralStringResource(R.plurals.detail_detected, detected, detected)
                waitingFor != null -> stringResource(waitingFor)
                else -> stringResource(R.string.detail_try_it)
            },
            style = if (detected > 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (detected > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun TuningCard(
    spec: io.github.arnavdugad.flick.core.model.TriggerSpec,
    draft: TriggerTuning,
    onDraft: (TriggerTuning) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
) {
    FlickCard {
        if (spec.hasSensitivity) {
            Text(stringResource(R.string.detail_sensitivity), style = MaterialTheme.typography.titleSmall)
            Slider(
                value = draft.sensitivity,
                onValueChange = { onDraft(draft.copy(sensitivity = it)) },
                onValueChangeFinished = onCommit,
            )
            Row {
                Text(stringResource(R.string.detail_less), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.detail_more), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        spec.holdRange?.let { range ->
            MsSlider(
                label = stringResource(R.string.detail_hold),
                value = draft.holdMs,
                range = range,
                step = 50,
                onChange = { onDraft(draft.copy(holdMs = it)) },
                onCommit = onCommit,
            )
            Spacer(Modifier.height(12.dp))
        }
        MsSlider(
            label = stringResource(R.string.detail_cooldown),
            value = draft.cooldownMs,
            range = spec.cooldownRange,
            step = 100,
            onChange = { onDraft(draft.copy(cooldownMs = it)) },
            onCommit = onCommit,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detail_reset))
        }
    }
}

@Composable
private fun MsSlider(
    label: String,
    value: Long,
    range: LongRange,
    step: Long,
    onChange: (Long) -> Unit,
    onCommit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.detail_ms, value.toInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val steps = ((range.last - range.first) / step - 1).toInt().coerceAtLeast(0)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = steps,
        onValueChangeFinished = onCommit,
        modifier = Modifier.fillMaxWidth(),
    )
}
