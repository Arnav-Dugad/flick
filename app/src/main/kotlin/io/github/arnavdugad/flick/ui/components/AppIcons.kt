package io.github.arnavdugad.flick.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.container

/** An installed app's icon, loaded off the main thread. Decorative: pair it with a label. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, px) {
        value = context.container.apps.icon(packageName, px)
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/** Overlapping icons for up to [max] apps, then "+N". */
@Composable
fun AppIconStack(packages: List<String>, modifier: Modifier = Modifier, max: Int = 4, size: Dp = 28.dp) {
    val shown = packages.take(max)
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, pkg ->
            AppIcon(
                pkg,
                size = size,
                modifier = Modifier
                    .offset(x = (-8).dp * i)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
            )
        }
        val extra = packages.size - shown.size
        if (extra > 0) {
            Text(
                "+$extra",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = (-8).dp * shown.size + 4.dp),
            )
        }
    }
}

/** An app's label, loaded off the main thread (falls back to the package name). */
@Composable
fun appLabel(packageName: String): String {
    val context = LocalContext.current
    val label by produceState(packageName, packageName) {
        value = context.container.apps.label(packageName)
    }
    return label
}
