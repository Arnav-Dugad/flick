package io.github.arnavdugad.flick.ui.screens.triggers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerCategory
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.labelRes
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(onBack: () -> Unit, onOpenTrigger: (TriggerType) -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    val statuses by container.engine.statuses.collectAsStateWithLifecycle()
    val permissions = rememberPermissionSnapshot()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.triggers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.triggers_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            for (category in TriggerCategory.entries) {
                val types = TriggerType.entries.filter { it.category == category }
                item(key = "h_$category") { SectionHeader(stringResource(category.labelRes)) }
                item(key = "c_$category") {
                    FlickCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                        Column {
                            for (type in types) {
                                val usedIn = settings.profiles.filter { type in it.bindings }
                                val action = usedIn.firstOrNull()?.bindings?.get(type)
                                val problem = triggerProblem(type, statuses[type], permissions)
                                ListItem(
                                    headlineContent = { Text(triggerLabel(type)) },
                                    supportingContent = {
                                        Text(
                                            problem
                                                ?: if (usedIn.isNotEmpty()) {
                                                    stringResource(R.string.library_used_in, usedIn.joinToString { it.name })
                                                } else {
                                                    stringResource(R.string.library_unused)
                                                },
                                            color = when {
                                                problem != null && action != null -> MaterialTheme.colorScheme.error
                                                action != null -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    },
                                    leadingContent = {
                                        IconBadge(
                                            triggerIcon(type),
                                            size = 40.dp,
                                            container = if (action != null) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            content = if (action != null) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { onOpenTrigger(type) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
