package io.github.arnavdugad.flick.ui.illustrations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import io.github.arnavdugad.flick.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * A miniature, generic "App info" screen that loops through the restricted-settings steps:
 * highlight the ⋮ menu, then the "Allow restricted settings" item. Wording is generic because
 * every manufacturer's Settings looks slightly different.
 */
@Composable
fun RestrictedSettingsMockup(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var step by remember { mutableIntStateOf(if (reduced) 1 else 0) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            step = 0
            delay(1400)
            step = 1
            delay(2200)
        }
    }
    val c = MaterialTheme.colorScheme
    val menuRing by animateColorAsState(
        if (step == 0) c.primary else Color.Transparent,
        FlickMotion.effects(),
        label = "ring",
    )
    MockFrame(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(2.dp, menuRing, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, null, tint = c.onSurface, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1B19)),
                    contentAlignment = Alignment.Center,
                ) {
                    FlickMark(Modifier.size(34.dp), color = Color(0xFFFF8A5B))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Flick", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                    Text("App info", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            repeat(3) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(if (it == 1) 0.6f else 0.8f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHighest),
                )
            }
        }
        AnimatedVisibility(
            visible = step == 1,
            enter = fadeIn(FlickMotion.effects()) + scaleIn(FlickMotion.spatial(), initialScale = 0.85f),
            exit = fadeOut(FlickMotion.effects()) + scaleOut(FlickMotion.effects(), targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 38.dp, end = 12.dp),
        ) {
            Column(
                Modifier
                    .width(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceContainerHighest)
                    .padding(vertical = 6.dp),
            ) {
                MenuItem("Uninstall updates", highlighted = false)
                MenuItem("Allow restricted settings", highlighted = true)
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, highlighted: Boolean) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) c.primaryContainer else Color.Transparent)
            .then(if (highlighted) Modifier.border(2.dp, c.primary, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) c.onPrimaryContainer else c.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A miniature accessibility settings row for Flick whose switch flips on. When [enabled] is true
 * (the real service is on) the switch stays on.
 */
@Composable
fun EnableServiceMockup(enabled: Boolean, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var demoOn by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, reduced) {
        if (enabled || reduced) return@LaunchedEffect
        while (true) {
            demoOn = false
            delay(1200)
            demoOn = true
            delay(1600)
        }
    }
    val on = enabled || demoOn
    val pop by animateFloatAsState(if (enabled) 1.04f else 1f, FlickMotion.spatial(), label = "pop")
    val c = MaterialTheme.colorScheme
    MockFrame(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Accessibility", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Downloaded apps", style = MaterialTheme.typography.labelMedium, color = c.primary)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .scale(pop)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) c.primaryContainer else c.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1B19)),
                    contentAlignment = Alignment.Center,
                ) {
                    FlickMark(Modifier.size(30.dp), color = Color(0xFFFF8A5B))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Flick", style = MaterialTheme.typography.titleSmall, color = c.onSurface)
                    Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
                Switch(checked = on, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun MockFrame(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier
            .clearAndSetSemantics { }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
        content = content,
    )
}
