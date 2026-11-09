package io.github.arnavdugad.flick.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.Actions
import io.github.arnavdugad.flick.engine.EngineState
import io.github.arnavdugad.flick.engine.EngineStatus
import io.github.arnavdugad.flick.engine.FiredTrigger
import io.github.arnavdugad.flick.system.AccessibilityStatus
import io.github.arnavdugad.flick.system.SystemIntents
import io.github.arnavdugad.flick.ui.actionIcon
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.components.BigToggle
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.StatusPill
import io.github.arnavdugad.flick.ui.illustrations.WaveIllustration
import io.github.arnavdugad.flick.ui.triggerLabel
import io.github.arnavdugad.flick.ui.theme.FlickMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenLab: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel { HomeViewModel(context.container) }
    val state by vm.engineState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lastEvent by vm.lastEvent.collectAsStateWithLifecycle()
    val covered by vm.sensorCovered.collectAsStateWithLifecycle()

    var enabledInSettings by remember { mutableStateOf(AccessibilityStatus.isEnabledInSettings(context)) }
    var practicing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(practicing) {
        enabledInSettings = AccessibilityStatus.isEnabledInSettings(context)
        context.container.screenState.refresh()
        vm.setPracticeMode(practicing)
        onPauseOrDispose { vm.setPracticeMode(false) }
    }

    // Live counters driven only by new events (never by a replayed old one).
    var eventCount by remember { mutableIntStateOf(0) }
    var practiceCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.events.collect {
            eventCount++
            if (it.practice) practiceCount++
        }
    }

    var showPicker by remember { mutableStateOf(false) }
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
            item(key = "master") {
                MasterCard(
                    state = state,
                    onToggle = vm::setMasterEnabled,
                )
            }
            item(key = "setup") {
                val needsSetup = !enabledInSettings
                val serviceDead = enabledInSettings && !state.serviceConnected
                AnimatedVisibility(
                    visible = needsSetup || serviceDead,
                    enter = fadeIn(FlickMotion.effects()) + expandVertically(FlickMotion.spatial()),
                    exit = fadeOut(FlickMotion.effects()) + shrinkVertically(FlickMotion.spatial()),
                ) {
                    SetupCard(
                        serviceDead = serviceDead,
                        onOpen = { SystemIntents.openAccessibilitySettings(context) },
                    )
                }
            }
            item(key = "wave") {
                WaveCard(
                    actionLabel = settings?.waveAction?.let { actionLabel(it) } ?: "",
                    actionIcon = settings?.waveAction?.let(::actionIcon),
                    available = vm.proximityAvailable,
                    listening = state.listening,
                    covered = covered,
                    pulseKey = eventCount,
                    onChange = { showPicker = true },
                )
            }
            item(key = "practice") {
                PracticeCard(
                    practicing = practicing,
                    count = practiceCount,
                    available = vm.proximityAvailable,
                    onToggle = { practicing = !practicing },
                )
            }
            lastEvent?.let { event ->
                item(key = "last") { LastEventRow(event) }
            }
            item(key = "tools") { SectionHeader(stringResource(R.string.home_tools_title)) }
            item(key = "lab") {
                ToolRow(
                    title = stringResource(R.string.home_lab_title),
                    body = stringResource(R.string.home_lab_body),
                    icon = { IconBadge(Icons.Rounded.Science) },
                    onClick = onOpenLab,
                )
            }
            item(key = "settings") {
                ToolRow(
                    title = stringResource(R.string.settings),
                    body = stringResource(R.string.home_settings_body),
                    icon = { IconBadge(Icons.Rounded.Settings) },
                    onClick = onOpenSettings,
                )
            }
        }
    }

    if (showPicker) {
        ActionPickerSheet(
            title = stringResource(R.string.pick_action_title),
            selected = settings?.waveAction ?: Actions.Next,
            onSelect = {
                vm.setWaveAction(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
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
private fun SetupCard(serviceDead: Boolean, onOpen: () -> Unit) {
    FlickCard(color = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (serviceDead) R.string.home_service_dead_title else R.string.home_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (serviceDead) R.string.home_service_dead_body else R.string.home_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }
    }
}

@Composable
private fun WaveCard(
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    available: Boolean,
    listening: Boolean,
    covered: Boolean,
    pulseKey: Int,
    onChange: () -> Unit,
) {
    FlickCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_wave_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.home_wave_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WaveIllustration(
                Modifier.size(width = 96.dp, height = 80.dp),
                pulseKey = pulseKey,
                sensorCovered = covered,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!available) {
            StatusPill(stringResource(R.string.home_sensor_missing), active = false)
            return@FlickCard
        }
        StatusPill(
            stringResource(if (covered) R.string.home_sensor_near else R.string.home_sensor_clear),
            active = covered && listening,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_wave_action_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            AssistChip(
                onClick = onChange,
                label = { Text(actionLabel) },
                leadingIcon = actionIcon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun PracticeCard(practicing: Boolean, count: Int, available: Boolean, onToggle: () -> Unit) {
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
            Text(
                pluralStringResource(R.plurals.home_try_count, count, count),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onToggle,
            enabled = available,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (practicing) R.string.home_try_stop else R.string.home_try_start))
        }
    }
}

@Composable
private fun LastEventRow(event: FiredTrigger) {
    val trigger = triggerLabel(event.trigger)
    val text = if (event.practice || event.action == null) {
        stringResource(R.string.home_last_event_practice, trigger)
    } else {
        stringResource(R.string.home_last_event, trigger, actionLabel(event.action))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ToolRow(title: String, body: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    FlickCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
