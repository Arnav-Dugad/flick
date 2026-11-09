package io.github.arnavdugad.flick.ui.screens.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerCategory
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.Profile
import io.github.arnavdugad.flick.core.profiles.ProfileTemplate
import io.github.arnavdugad.flick.core.profiles.ProfileTemplates
import io.github.arnavdugad.flick.data.AppEntry
import io.github.arnavdugad.flick.ui.components.AppIcon
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.labelRes
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel

/** Multi-select list of launchable apps with search. Apps in other profiles say so. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    profile: Profile,
    allProfiles: List<Profile>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = context.container.apps.launchableApps() }
    var selected by remember { mutableStateOf(profile.packages) }
    var query by remember { mutableStateOf("") }
    val owner = remember(allProfiles) {
        allProfiles.filter { it.id != profile.id }.flatMap { p -> p.packages.map { it to p.name } }.toMap()
    }
    ModalBottomSheet(
        onDismissRequest = { onDone(selected) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.profile_app_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            val list = apps
            if (list == null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            } else {
                val filtered = list.filter {
                    query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                }.sortedByDescending { it.packageName in profile.packages }
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.profile_no_app_results),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        val other = owner[app.packageName]
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = other?.let { { Text(stringResource(R.string.profile_app_in_other, it)) } },
                            leadingContent = { AppIcon(app.packageName) },
                            trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) {
                                selected = if (it) selected + app.packageName else selected - app.packageName
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { onDone(selected) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(16.dp)
                        .heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

/** Grouped list of gestures to add to a profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerPickerSheet(profile: Profile, onPick: (TriggerType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Text(
                    stringResource(R.string.profile_pick_gesture),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
            }
            for (category in TriggerCategory.entries) {
                item(key = "h$category") { SectionHeader(stringResource(category.labelRes), Modifier.padding(horizontal = 20.dp)) }
                items(TriggerType.entries.filter { it.category == category }, key = { it.name }) { t ->
                    val allowed = profile.allows(t)
                    val already = t in profile.bindings
                    val enabled = allowed && !already
                    ListItem(
                        headlineContent = { Text(triggerLabel(t)) },
                        supportingContent = when {
                            !allowed -> { { Text(stringResource(R.string.profile_gesture_unavailable_here)) } }
                            already -> { { Text(stringResource(R.string.profile_gesture_already)) } }
                            else -> null
                        },
                        leadingContent = { IconBadge(triggerIcon(t), size = 40.dp) },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = enabled) { onPick(t) },
                    )
                }
            }
        }
    }
}

/** Choose a template for a new profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(onPick: (ProfileTemplate) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.profiles_start_from),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            for (t in ProfileTemplates.creatable) {
                ListItem(
                    headlineContent = { Text(t.name) },
                    supportingContent = {
                        Text(
                            t.bindings.keys.take(3).map { triggerLabel(it) }.joinToString(" · ")
                                .ifEmpty { stringResource(R.string.profile_no_gestures) },
                            maxLines = 1,
                        )
                    },
                    leadingContent = {
                        IconBadge(t.bindings.keys.firstOrNull()?.let(::triggerIcon) ?: Icons.Rounded.Add, size = 40.dp)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPick(t) },
                )
            }
        }
    }
}
