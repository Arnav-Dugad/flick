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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import io.github.arnavdugad.flick.core.profiles.Profile
import io.github.arnavdugad.flick.core.profiles.ProfileKind
import io.github.arnavdugad.flick.core.profiles.ProfileResolver
import io.github.arnavdugad.flick.ui.components.AppIcon
import io.github.arnavdugad.flick.ui.components.AppIconStack
import io.github.arnavdugad.flick.ui.components.appLabel
import io.github.arnavdugad.flick.ui.navigation.sharedBounds
import io.github.arnavdugad.flick.ui.screens.profiles.TemplatePickerSheet
import kotlinx.coroutines.launch
import java.util.UUID
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
import io.github.arnavdugad.flick.ui.components.BigToggle
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.rememberPermissionRequest
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import io.github.arnavdugad.flick.ui.triggerLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLab: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenProfile: (String) -> Unit,
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

    val profiles = settings?.profiles.orEmpty()
    val used = ProfileResolver.allTriggers(profiles)
    val bound = TriggerType.entries.filter { it in used }
    val lastApp by vm.lastApp.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showTemplates by remember { mutableStateOf(false) }
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

            item(key = "active") {
                ActiveCard(
                    lastApp = lastApp,
                    profile = lastApp?.let { pkg -> ProfileResolver.resolve(pkg, profiles) },
                    onOpenProfile = onOpenProfile,
                )
            }

            item(key = "profiles_h") { SectionHeader(stringResource(R.string.profiles_title)) }
            val ordered = profiles.sortedWith(compareBy({ it.kind == ProfileKind.EVERYWHERE }, { !it.enabled }))
            items(ordered, key = { "p_" + it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    installed = profile.packages.filter { context.container.apps.isInstalled(it) },
                    problem = profile.effectiveBindings.keys.firstNotNullOfOrNull { t ->
                        triggerProblem(t, statuses[t], permissions)
                    },
                    onClick = { onOpenProfile(profile.id) },
                    onEnabled = { on -> scope.launch { context.container.settings.setProfileEnabled(profile.id, on) } },
                )
            }
            item(key = "profiles_new") {
                FilledTonalButton(
                    onClick = { showTemplates = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profiles_new))
                }
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
            item(key = "library") {
                ToolRow(
                    title = stringResource(R.string.home_library_title),
                    body = stringResource(R.string.home_library_body),
                    icon = Icons.Rounded.Gesture,
                    onClick = onOpenLibrary,
                )
            }
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

    NewProfileSheetHost(show = showTemplates, onDismiss = { showTemplates = false }, onCreated = onOpenProfile)
}

@Composable
private fun NewProfileSheetHost(show: Boolean, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    if (!show) return
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    TemplatePickerSheet(
        onPick = { template ->
            onDismiss()
            val id = UUID.randomUUID().toString()
            scope.launch {
                container.settings.upsertProfile(template.toProfile(id = id).copy(packages = emptySet(), enabled = true))
                if (template.packages.isNotEmpty()) container.settings.setProfilePackages(id, template.packages)
                onCreated(id)
            }
        },
        onDismiss = onDismiss,
    )
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
private fun ActiveCard(lastApp: String?, profile: Profile?, onOpenProfile: (String) -> Unit) {
    FlickCard(
        onClick = profile?.let { p -> { onOpenProfile(p.id) } },
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            stringResource(R.string.active_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        if (lastApp == null) {
            Text(stringResource(R.string.active_none), style = MaterialTheme.typography.bodyMedium)
            return@FlickCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(lastApp, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.active_last_app, appLabel(lastApp)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (profile != null) {
                        stringResource(R.string.active_profile, profile.name)
                    } else {
                        stringResource(R.string.active_no_profile)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    installed: List<String>,
    problem: String?,
    onClick: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val enabledCd = stringResource(R.string.profile_enabled_cd, profile.name)
    val bg by animateColorAsState(
        if (profile.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        FlickMotion.effects(),
        label = "profileBg",
    )
    FlickCard(
        onClick = onClick,
        color = bg,
        modifier = Modifier.sharedBounds("profile-${profile.id}"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (profile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (installed.isNotEmpty()) {
                        AppIconStack(installed)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        when {
                            problem != null && profile.enabled -> problem
                            profile.kind == ProfileKind.EVERYWHERE ||
                                installed.isNotEmpty() -> pluralStringResource(
                                R.plurals.profile_gesture_count,
                                profile.bindings.size,
                                profile.bindings.size,
                            )
                            profile.packages.isEmpty() -> stringResource(R.string.profile_no_apps)
                            else -> stringResource(R.string.profile_not_installed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (problem != null && profile.enabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = profile.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = enabledCd },
            )
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
