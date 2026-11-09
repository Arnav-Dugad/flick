package io.github.arnavdugad.flick.ui.screens.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.action.isSupported
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.ui.ActionCatalog
import io.github.arnavdugad.flick.ui.actionIcon
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.androidVersionName
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ActionLabViewModel = viewModel { ActionLabViewModel(context.container) }
    val delay by vm.delaySeconds.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val state by vm.engineState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Resolve labels for results outside composition by capturing them up front.
    val okFormat = stringResource(R.string.lab_result_ok, "%s")
    val failFormat = stringResource(R.string.lab_result_fail, "%s")
    val labels = (ActionCatalog.gestures + ActionCatalog.system + ActionCatalog.media).associateWith { actionLabel(it) }
    LaunchedEffect(Unit) {
        vm.results.collect { (action, ok) ->
            val label = labels[action] ?: ""
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar((if (ok) okFormat else failFormat).format(label))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lab_title)) },
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.lab_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            if (!state.serviceConnected) {
                item {
                    FlickCard(color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            stringResource(R.string.lab_service_off),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.lab_delay), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        ActionLabViewModel.DELAYS.forEachIndexed { i, seconds ->
                            SegmentedButton(
                                selected = delay == seconds,
                                onClick = { vm.setDelay(seconds) },
                                shape = SegmentedButtonDefaults.itemShape(i, ActionLabViewModel.DELAYS.size),
                            ) {
                                Text(
                                    if (seconds == 0) {
                                        stringResource(R.string.lab_delay_now)
                                    } else {
                                        stringResource(R.string.lab_delay_seconds, seconds)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            group(R.string.lab_group_gestures, ActionCatalog.gestures, pending, state.serviceConnected, vm::run)
            group(R.string.lab_group_system, ActionCatalog.system, pending, state.serviceConnected, vm::run)
            group(R.string.lab_group_media, ActionCatalog.media, pending, state.serviceConnected, vm::run)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: Int,
    actions: List<Action>,
    pending: Map<Action, Int>,
    connected: Boolean,
    onRun: (Action) -> Unit,
) {
    item(key = "h$title") { SectionHeader(stringResource(title), Modifier.padding(top = 12.dp)) }
    items(actions, key = { it.toString() }) { action ->
        val supported = action !is Action.Global || action.action.isSupported()
        val countdown = pending[action]
        ListItem(
            headlineContent = { Text(actionLabel(action)) },
            supportingContent = when {
                action is Action.Global && !supported -> {
                    { Text(stringResource(R.string.act_needs_android, androidVersionName(action.action.minSdk))) }
                }
                countdown != null && countdown > 0 -> {
                    { Text(stringResource(R.string.lab_running_in, countdown)) }
                }
                else -> null
            },
            leadingContent = { IconBadge(actionIcon(action), size = 40.dp) },
            trailingContent = {
                FilledTonalButton(
                    onClick = { onRun(action) },
                    enabled = supported && connected && countdown == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.action_run)) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
