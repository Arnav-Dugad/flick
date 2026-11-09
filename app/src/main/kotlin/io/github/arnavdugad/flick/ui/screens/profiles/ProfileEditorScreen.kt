package io.github.arnavdugad.flick.ui.screens.profiles

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.Profile
import io.github.arnavdugad.flick.core.profiles.ProfileKind
import io.github.arnavdugad.flick.ui.actionIcon
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.components.AppIcon
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.appLabel
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.navigation.sharedBounds
import io.github.arnavdugad.flick.ui.screens.home.ActionPickerSheet
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String,
    onBack: () -> Unit,
    onOpenTrigger: (TriggerType, String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val container = LocalContext.current.container
    val repo = container.settings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val statuses by container.engine.statuses.collectAsStateWithLifecycle()
    val permissions = rememberPermissionSnapshot()
    val all = settings?.profiles.orEmpty()
    val profile = all.firstOrNull { it.id == profileId }

    // Deleted (or not loaded yet): leave once settings are known.
    LaunchedEffect(settings, profile) {
        if (settings != null && profile == null) onBack()
    }
    if (profile == null) return

    var showApps by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var pickActionFor by remember { mutableStateOf<TriggerType?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (profile.kind == ProfileKind.APPS) {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.profile_more_cd))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val copyName = stringResource(R.string.profile_copy_suffix, profile.name)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_duplicate)) },
                                onClick = {
                                    menu = false
                                    val copy = profile.copy(id = UUID.randomUUID().toString(), name = copyName, packages = emptySet())
                                    scope.launch { repo.upsertProfile(copy) }
                                    onOpenProfile(copy.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_delete)) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
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
            item(key = "header") {
                HeaderCard(
                    profile = profile,
                    onRename = { name -> scope.launch { repo.upsertProfile(profile.copy(name = name)) } },
                    onEnabled = { scope.launch { repo.setProfileEnabled(profile.id, it) } },
                )
            }

            if (profile.kind == ProfileKind.EVERYWHERE) {
                item(key = "everywhere") {
                    FlickCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.profile_everywhere_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                item(key = "apps_h") { SectionHeader(stringResource(R.string.profile_apps)) }
                item(key = "apps") {
                    FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                        if (profile.packages.isEmpty()) {
                            Text(
                                stringResource(R.string.profile_no_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                        val installed = profile.packages.filter { container.apps.isInstalled(it) }.sorted()
                        for (pkg in installed) {
                            val label = appLabel(pkg)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp),
                            ) {
                                AppIcon(pkg)
                                Spacer(Modifier.width(16.dp))
                                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch { repo.setProfilePackages(profile.id, profile.packages - pkg) }
                                }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.profile_remove_app_cd, label))
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { showApps = true },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_add_apps))
                        }
                    }
                }
            }

            item(key = "gestures_h") { SectionHeader(stringResource(R.string.profile_gestures)) }
            item(key = "gestures") {
                FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    val bound = TriggerType.entries.filter { it in profile.bindings }
                    if (bound.isEmpty()) {
                        Text(
                            stringResource(R.string.profile_no_gestures),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    for (t in bound) {
                        val action = profile.bindings.getValue(t)
                        val problem = if (!profile.allows(t)) {
                            stringResource(R.string.profile_gesture_unavailable_here)
                        } else {
                            triggerProblem(t, statuses[t], permissions)
                        }
                        val label = triggerLabel(t)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { pickActionFor = t }
                                .heightIn(min = 64.dp)
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            IconBadge(triggerIcon(t), size = 40.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        actionIcon(action),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text(
                                        problem ?: actionLabel(action),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(onClick = { onOpenTrigger(t, profile.id) }) {
                                Icon(Icons.Rounded.Tune, stringResource(R.string.profile_tune_cd, label))
                            }
                            IconButton(onClick = { scope.launch { repo.setBinding(profile.id, t, null) } }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.profile_remove_gesture_cd, label))
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { showTriggerPicker = true },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_add_gesture))
                    }
                }
            }
        }
    }

    if (showApps) {
        AppPickerSheet(
            profile = profile,
            allProfiles = all,
            onDone = {
                showApps = false
                scope.launch { repo.setProfilePackages(profile.id, it) }
            },
            onDismiss = { showApps = false },
        )
    }
    if (showTriggerPicker) {
        TriggerPickerSheet(
            profile = profile,
            onPick = {
                showTriggerPicker = false
                pickActionFor = it
            },
            onDismiss = { showTriggerPicker = false },
        )
    }
    pickActionFor?.let { t ->
        ActionPickerSheet(
            title = triggerLabel(t),
            selected = profile.bindings[t],
            onSelect = {
                pickActionFor = null
                scope.launch { repo.setBinding(profile.id, t, it) }
            },
            onDismiss = { pickActionFor = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.profile_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.deleteProfile(profile.id) }
                }) { Text(stringResource(R.string.profile_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HeaderCard(profile: Profile, onRename: (String) -> Unit, onEnabled: (Boolean) -> Unit) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    val enabledCd = stringResource(R.string.profile_enabled_cd, profile.name)
    FlickCard(
        modifier = Modifier.sharedBounds("profile-${profile.id}"),
        color = if (profile.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (profile.kind == ProfileKind.APPS) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) onRename(it.trim())
                        },
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.profile_gesture_count, profile.bindings.size, profile.bindings.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = profile.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = enabledCd },
            )
        }
    }
}
