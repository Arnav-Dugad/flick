package io.github.arnavdugad.flick.ui.screens.features

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.mindful.MindfulSettings
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.ui.components.FlickCard
import kotlinx.coroutines.launch

@Composable
fun MindfulScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    val stats by c.mindful.stats.collectAsStateWithLifecycle()
    val m = settings.mindful
    fun save(v: MindfulSettings) = scope.launch { c.settings.setMindful(v) }

    FeatureScaffold(title = stringResource(R.string.mind_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.mind_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                SwitchListItem(
                    title = stringResource(R.string.mind_enable),
                    body = stringResource(R.string.mind_today, stats.reels, stats.minutes),
                    checked = m.enabled,
                    onChange = { save(m.copy(enabled = it)) },
                )
            }
        }
        item {
            FlickCard {
                LimitSlider(
                    title = stringResource(R.string.mind_reels),
                    value = m.reelLimit,
                    steps = REEL_STEPS,
                    label = { if (it == 0) stringResource(R.string.mind_off) else pluralStringResource(R.plurals.mind_reels_value, it, it) },
                    enabled = m.enabled,
                    onChange = { save(m.copy(reelLimit = it)) },
                )
                LimitSlider(
                    title = stringResource(R.string.mind_minutes),
                    value = m.minuteLimit,
                    steps = MINUTE_STEPS,
                    label = { if (it == 0) stringResource(R.string.mind_off) else pluralStringResource(R.plurals.mind_minutes_value, it, it) },
                    enabled = m.enabled,
                    onChange = { save(m.copy(minuteLimit = it)) },
                )
            }
        }
        item {
            FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                SwitchListItem(
                    title = stringResource(R.string.mind_auto_pause),
                    body = stringResource(R.string.mind_auto_pause_body),
                    checked = m.autoPause,
                    enabled = m.enabled && m.minuteLimit > 0,
                    onChange = { save(m.copy(autoPause = it)) },
                )
            }
        }
    }
}

private val REEL_STEPS = listOf(0, 10, 20, 30, 40, 50, 75, 100, 150, 200)
private val MINUTE_STEPS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
private fun LimitSlider(
    title: String,
    value: Int,
    steps: List<Int>,
    label: @Composable (Int) -> String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val start = steps.indexOf(value).takeIf { it >= 0 } ?: steps.indexOfFirst { it >= value }.coerceAtLeast(0)
    var index by remember(value) { mutableFloatStateOf(start.toFloat()) }
    Row(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(label(steps[index.toInt()]), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = index,
        onValueChange = { index = it },
        onValueChangeFinished = { onChange(steps[index.toInt()]) },
        valueRange = 0f..(steps.size - 1).toFloat(),
        steps = steps.size - 2,
        enabled = enabled,
    )
}
