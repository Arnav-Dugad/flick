package io.github.arnavdugad.flick.ui.screens.triggers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import io.github.arnavdugad.flick.vision.VisionPreview
import kotlin.math.roundToInt

/**
 * Horizontal signal meter: the bar is the live signal, the notch is where the gesture fires.
 * [pulseKey] changes each time the gesture is recognised and flashes the bar.
 */
@Composable
fun LevelMeter(level: Float, threshold: Float?, pulseKey: Int, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(level.coerceIn(0f, 1f), FlickMotion.spatialFast(), label = "level")
    val flash = remember { Animatable(0f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(600))
        }
    }
    val c = MaterialTheme.colorScheme
    val description = stringResource(
        R.string.detail_meter_cd,
        (level * 100).roundToInt(),
        ((threshold ?: 1f) * 100).roundToInt(),
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { stateDescription = description },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.surfaceContainerHighest, cornerRadius = r)
        val reached = threshold != null && level >= threshold
        val barColor = if (reached || flash.value > 0f) c.tertiary else c.primary
        if (shown > 0f) {
            drawRoundRect(barColor, size = Size(size.width * shown, size.height), cornerRadius = r)
        }
        if (flash.value > 0f) {
            drawRoundRect(c.tertiary.copy(alpha = flash.value * 0.35f), cornerRadius = r)
        }
        if (threshold != null) {
            val x = size.width * threshold.coerceIn(0f, 1f)
            drawLine(c.onSurface, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 3.dp.toPx())
        }
    }
}

/** Tracking dots from the camera — never the image itself. Mirrored like a selfie. */
@Composable
fun LandmarkPreview(points: VisionPreview.Points, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
    ) {
        drawRoundRect(c.surfaceContainerHighest, cornerRadius = CornerRadius(28.dp.toPx()))
        drawRoundRect(
            c.outlineVariant,
            cornerRadius = CornerRadius(28.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )
        points.face?.let { pts ->
            var i = 0
            while (i + 1 < pts.size) {
                drawCircle(c.primary, 2.dp.toPx(), Offset(pts[i] * size.width, pts[i + 1] * size.height))
                i += 2
            }
        }
        points.hand?.let { pts ->
            for ((a, b) in HAND_BONES) {
                if (b * 2 + 1 >= pts.size) continue
                drawLine(
                    c.tertiary,
                    Offset(pts[a * 2] * size.width, pts[a * 2 + 1] * size.height),
                    Offset(pts[b * 2] * size.width, pts[b * 2 + 1] * size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            var i = 0
            while (i + 1 < pts.size) {
                drawCircle(c.tertiary, 4.dp.toPx(), Offset(pts[i] * size.width, pts[i + 1] * size.height))
                i += 2
            }
        }
    }
}

private val HAND_BONES = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)
