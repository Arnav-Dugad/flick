package io.github.arnavdugad.flick.ui.screens.features

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollMode
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollSettings
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun AutoScrollScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.container.settings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    val a = settings.autoScroll
    fun save(v: AutoScrollSettings) = scope.launch { repo.setAutoScroll(v) }
    var speed by remember(a.speed) { mutableFloatStateOf(a.speed.toFloat()) }

    FeatureScaffold(title = stringResource(R.string.as_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.as_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionHeader(stringResource(R.string.as_mode)) }
        item {
            FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                val modes = listOf(
                    AutoScrollMode.READING to (R.string.as_mode_reading to R.string.as_mode_reading_body),
                    AutoScrollMode.TELEPROMPTER to (R.string.as_mode_teleprompter to R.string.as_mode_teleprompter_body),
                    AutoScrollMode.COOKING to (R.string.as_mode_cooking to R.string.as_mode_cooking_body),
                )
                for ((mode, text) in modes) {
                    ListItem(
                        headlineContent = { Text(stringResource(text.first)) },
                        supportingContent = { Text(stringResource(text.second)) },
                        leadingContent = { RadioButton(selected = a.mode == mode, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(selected = a.mode == mode, role = Role.RadioButton) {
                            save(a.copy(mode = mode))
                        },
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.as_speed)) }
        item {
            FlickCard {
                Row {
                    Text(stringResource(R.string.detail_less), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(speed.toInt().toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.detail_more), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { save(a.copy(speed = speed.toInt())) },
                    valueRange = AutoScrollSettings.MIN_SPEED.toFloat()..AutoScrollSettings.MAX_SPEED.toFloat(),
                    steps = AutoScrollSettings.MAX_SPEED - AutoScrollSettings.MIN_SPEED - 1,
                )
            }
        }
        item {
            FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                SwitchListItem(
                    title = stringResource(R.string.as_face),
                    body = stringResource(R.string.as_face_body),
                    checked = a.pauseOnFaceAway,
                    onChange = { save(a.copy(pauseOnFaceAway = it)) },
                )
            }
        }
        item {
            FlickCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(R.string.as_stops),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
