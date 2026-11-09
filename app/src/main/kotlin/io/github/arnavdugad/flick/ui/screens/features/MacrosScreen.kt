package io.github.arnavdugad.flick.ui.screens.features

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.macro.Macro
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.IconBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MacrosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    val state by c.engine.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<Macro?>(null) }

    val savedFormat = stringResource(R.string.mac_saved, "%s")
    LaunchedEffect(Unit) {
        c.macroRecording.saved.collect { snackbar.showSnackbar(savedFormat.format(it)) }
    }

    FeatureScaffold(title = stringResource(R.string.mac_title), onBack = onBack, snackbar = snackbar) {
        item {
            Text(
                stringResource(R.string.mac_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FlickCard {
                if (!state.serviceConnected) {
                    Text(stringResource(R.string.mac_needs_service), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.mac_record_how), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(top = 12.dp))
                    Button(
                        onClick = {
                            val size = c.engine.executor?.screenSize() ?: return@Button
                            c.macroRecording.start(size.first, size.second)
                            (context as? Activity)?.moveTaskToBack(true)
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Rounded.FiberManualRecord, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.mac_record))
                    }
                }
            }
        }
        if (settings.macros.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.mac_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        for (m in settings.macros) {
            item(key = m.id) {
                MacroRow(
                    macro = m,
                    canRun = state.serviceConnected,
                    onTest = {
                        c.appScope.launch {
                            delay(3_000)
                            c.engine.executor?.playMacro(m)
                        }
                        (context as? Activity)?.moveTaskToBack(true)
                    },
                    onRename = { renaming = m },
                    onDelete = { scope.launch { c.settings.deleteMacro(m.id) } },
                )
            }
        }
    }

    renaming?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.mac_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { c.settings.upsertMacro(m.copy(name = name.trim())) }
                    renaming = null
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MacroRow(macro: Macro, canRun: Boolean, onTest: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    FlickCard(contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconBadge(Icons.Rounded.PlayArrow, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.mac_steps, macro.steps.size, macro.steps.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTest, enabled = canRun) { Text(stringResource(R.string.mac_test)) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.profile_more_cd)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mac_rename)) }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.mac_delete)) }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}
