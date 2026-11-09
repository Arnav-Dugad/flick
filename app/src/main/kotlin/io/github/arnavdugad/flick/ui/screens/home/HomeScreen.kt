package io.github.arnavdugad.flick.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerRequirement
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.engine.EngineState
import io.github.arnavdugad.flick.engine.EngineStatus
import io.github.arnavdugad.flick.system.AccessibilityStatus
import io.github.arnavdugad.flick.system.SystemIntents
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.components.BigToggle
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.rememberPermissionRequest
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLab: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTriggers: () -> Unit,
    onOpenTrigger: (TriggerType) -> Unit,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel { HomeViewModel(context.container) }
    val state by vm.engineState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val permissions = rememberPermissionSnapshot()
    val requestPermission = rememberPermissionRequest { vm.refreshSources() }

    var enabledInSettings by remember { mutableStateOf(AccessibilityStatus.isEnabledInSettings(context)) }
    var practicing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(practicing) {
        enabledInSettings = AccessibilityStatus.isEnabledInSettings(context)
        context.container.screenState.refresh()
        vm.refreshSources()
        vm.setPracticeMode(practicing)
        onPauseOrDispose { vm.setPracticeMode(false) }
    }

    var practiceCount by remember { mutableIntStateOf(0) }
    var lastPracticed by remember { mutableStateOf<TriggerType?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect {
            if (it.practice) {
                practiceCount++
                lastPracticed = it.trigger
            }
        }
    }

    val bindings = settings?.bindings.orEmpty()
    val bound = TriggerType.entries.filter { it in bindings }
    val needsCamera = bound.any { it.requirement == TriggerRequirement.CAMERA } && !permissions.camera
    val needsMic = bound.any { it.requirement == TriggerRequirement.MICROPHONE } && !permissions.microphone
    val cameraStatus = bound.firstOrNull { it.requirement == TriggerRequirement.CAMERA }?.let { statuses[it] }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
            item(key = "master") { MasterCard(state = state, onToggle = vm::setMasterEnabled) }

            item(key = "setup") {
                val serviceDead = enabledInSettings && !state.serviceConnected
                AnimatedVisibility(
                    visible = !enabledInSettings || serviceDead,
                    enter = fadeIn(FlickMotion.effects()) + expandVertically(FlickMotion.spatial()),
                    exit = fadeOut(FlickMotion.effects()) + shrinkVertically(FlickMotion.spatial()),
                ) {
                    val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
                    NoticeCard(
                        title = stringResource(if (serviceDead) R.string.home_service_dead_title else R.string.home_setup_title),
                        body = stringResource(if (serviceDead) R.string.home_service_dead_body else R.string.home_setup_body) +
                            if (serviceDead && samsung) "\n\n" + stringResource(R.string.home_samsung_tip) else "",
                        action = stringResource(R.string.action_open_settings),
                        onAction = { SystemIntents.openAccessibilitySettings(context) },
                    )
                }
            }
            if (needsCamera) {
                item(key = "cam_perm") {
                    NoticeCard(
                        title = stringResource(R.string.home_perm_camera_title),
                        body = stringResource(R.string.home_perm_camera_body),
                        action = stringResource(R.string.detail_grant),
                        onAction = { requestPermission(Manifest.permission.CAMERA) },
                    )
                }
            }
            if (needsMic) {
                item(key = "mic_perm") {
                    NoticeCard(
                        title = stringResource(R.string.home_perm_mic_title),
                        body = stringResource(R.string.home_perm_mic_body),
                        action = stringResource(R.string.detail_grant),
                        onAction = { requestPermission(Manifest.permission.RECORD_AUDIO) },
                    )
                }
            }
            if (!needsCamera && (cameraStatus == SourceStatus.BLOCKED || cameraStatus == SourceStatus.BUSY)) {
                item(key = "cam_state") {
                    val blocked = cameraStatus == SourceStatus.BLOCKED
                    NoticeCard(
                        title = stringResource(if (blocked) R.string.home_camera_blocked_title else R.string.src_busy),
                        body = stringResource(if (blocked) R.string.home_camera_blocked_body else R.string.home_camera_busy_body),
                        icon = Icons.Rounded.Info,
                        warning = blocked,
                    )
                }
            }

            item(key = "gestures_h") { SectionHeader(stringResource(R.string.home_gestures_title)) }
            item(key = "gestures") {
                GesturesCard(
                    bound = bound,
                    labelFor = { t -> bindings[t]?.let { actionLabel(it) }.orEmpty() },
                    problemFor = { t -> triggerProblem(t, statuses[t], permissions) },
                    onOpenTrigger = onOpenTrigger,
                    onManage = onOpenTriggers,
                )
            }
            item(key = "practice") {
                PracticeCard(
                    practicing = practicing,
                    count = practiceCount,
                    last = lastPracticed,
                    enabled = bound.isNotEmpty(),
                    onToggle = { practicing = !practicing },
                )
            }

            item(key = "tools") { SectionHeader(stringResource(R.string.home_tools_title)) }
            item(key = "lab") {
                ToolRow(
                    title = stringResource(R.string.home_lab_title),
                    body = stringResource(R.string.home_lab_body),
                    icon = Icons.Rounded.Science,
                    onClick = onOpenLab,
                )
            }
            item(key = "settings") {
                ToolRow(
                    title = stringResource(R.string.settings),
                    body = stringResource(R.string.home_settings_body),
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun MasterCard(state: EngineState, onToggle: (Boolean) -> Unit) {
    val on = state.masterEnabled
    val container by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        FlickMotion.effects(),
        label = "masterBg",
    )
    val content by animateColorAsState(
        if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        FlickMotion.effects(),
        label = "masterFg",
    )
    val status = stringResource(
        when (state.status) {
            EngineStatus.SERVICE_OFF -> R.string.home_status_service_off
            EngineStatus.PAUSED_BY_USER -> R.string.home_status_paused
            EngineStatus.SCREEN_OFF -> R.string.home_status_screen_off
            EngineStatus.ACTIVE -> R.string.home_status_active
        },
    )
    val title = stringResource(if (on) R.string.home_on_title else R.string.home_paused_title)
    FlickCard(
        color = container,
        shape = MaterialTheme.shapes.extraLarge,
        onClick = { onToggle(!on) },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = content)
                Spacer(Modifier.height(4.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(16.dp))
            BigToggle(
                checked = on,
                onCheckedChange = onToggle,
                contentDescription = stringResource(R.string.home_master_toggle_cd),
                stateText = title,
            )
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Rounded.Warning,
    warning: Boolean = true,
) {
    val bg = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    FlickCard(color = bg) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = fg)
                if (action != null && onAction != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
                }
            }
        }
    }
}

@Composable
private fun GesturesCard(
    bound: List<TriggerType>,
    labelFor: @Composable (TriggerType) -> String,
    problemFor: @Composable (TriggerType) -> String?,
    onOpenTrigger: (TriggerType) -> Unit,
    onManage: () -> Unit,
) {
    FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (bound.isEmpty()) {
            Text(
                stringResource(R.string.home_gestures_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        for (t in bound) {
            val problem = problemFor(t)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpenTrigger(t) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                IconBadge(
                    triggerIcon(t),
                    size = 40.dp,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(triggerLabel(t), style = MaterialTheme.typography.titleMedium)
                    Text(
                        problem ?: labelFor(t),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(
            onClick = onManage,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_gestures_manage))
        }
    }
}

@Composable
private fun PracticeCard(practicing: Boolean, count: Int, last: TriggerType?, enabled: Boolean, onToggle: () -> Unit) {
    FlickCard(color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            stringResource(R.string.home_try_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.home_try_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (practicing) {
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = last to count,
                transitionSpec = {
                    (fadeIn(FlickMotion.effects()) + scaleIn(FlickMotion.spatial(), initialScale = 0.8f))
                        .togetherWith(fadeOut(FlickMotion.effects()))
                },
                label = "practice",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) { (trigger, _) ->
                Text(
                    trigger?.let { triggerLabel(it) } ?: stringResource(R.string.home_try_waiting),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (trigger != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onToggle, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (practicing) R.string.home_try_stop else R.string.home_try_start))
        }
    }
}

@Composable
private fun ToolRow(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    FlickCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
