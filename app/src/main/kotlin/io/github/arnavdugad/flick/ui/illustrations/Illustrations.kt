package io.github.arnavdugad.flick.ui.illustrations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.arnavdugad.flick.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/*
 * Original vector illustrations drawn in Compose. All are decorative (hidden from TalkBack) and
 * freeze when the user has disabled animations.
 */

/** Flick's mark: an upward chevron with motion trails (matches the launcher icon). */
@Composable
fun FlickMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = min(size.width, size.height) / 108f
        val ox = (size.width - 108f * s) / 2f
        val oy = (size.height - 108f * s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val chevron = Path().apply {
            moveTo(p(37f, 57f).x, p(37f, 57f).y)
            lineTo(p(54f, 40f).x, p(54f, 40f).y)
            lineTo(p(71f, 57f).x, p(71f, 57f).y)
        }
        drawPath(chevron, color, style = Stroke(8.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val trail1 = Path().apply {
            moveTo(p(40f, 69f).x, p(40f, 69f).y)
            quadraticTo(p(54f, 61f).x, p(54f, 61f).y, p(68f, 69f).x, p(68f, 69f).y)
        }
        drawPath(trail1, color.copy(alpha = 0.6f), style = Stroke(5.5f * s, cap = StrokeCap.Round))
        val trail2 = Path().apply {
            moveTo(p(45f, 78.5f).x, p(45f, 78.5f).y)
            quadraticTo(p(54f, 73.5f).x, p(54f, 73.5f).y, p(63f, 78.5f).x, p(63f, 78.5f).y)
        }
        drawPath(trail2, color.copy(alpha = 0.3f), style = Stroke(4.5f * s, cap = StrokeCap.Round))
    }
}

/**
 * A phone showing a vertical feed that advances, with a face beside it nodding in sync —
 * "nod to go to the next reel".
 */
@Composable
fun FeedIllustration(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "feed")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle",
    )
    val t = if (reduced) 0f else cycle
    // 0..0.55 hold, 0.55..1 advance one card. The head nods during the advance.
    val advance = if (t < 0.55f) 0f else FastOutSlowInEasing.transform((t - 0.55f) / 0.45f)
    val nod = if (t < 0.45f || t > 0.8f) 0f else sin(((t - 0.45f) / 0.35f) * PI).toFloat()

    val c = MaterialTheme.colorScheme
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val phoneW = min(w * 0.42f, h * 0.5f)
        val phoneH = phoneW * 1.9f
        val phoneLeft = w * 0.42f - phoneW / 2f
        val phoneTop = (h - phoneH) / 2f
        val radius = phoneW * 0.14f

        drawRoundRect(c.surfaceContainerHighest, Offset(phoneLeft, phoneTop), Size(phoneW, phoneH), CornerRadius(radius))
        val inset = phoneW * 0.06f
        val screenLeft = phoneLeft + inset
        val screenTop = phoneTop + inset
        val screenW = phoneW - inset * 2
        val screenH = phoneH - inset * 2
        drawRoundRect(c.surface, Offset(screenLeft, screenTop), Size(screenW, screenH), CornerRadius(radius * 0.7f))

        val cardH = screenH * 0.62f
        val pitch = cardH + screenH * 0.05f
        clipRect(screenLeft, screenTop, screenLeft + screenW, screenTop + screenH) {
            for (i in 0..2) {
                val top = screenTop + screenH * 0.06f + i * pitch - advance * pitch
                val left = screenLeft + screenW * 0.07f
                val cw = screenW * 0.86f
                drawRoundRect(c.primaryContainer, Offset(left, top), Size(cw, cardH), CornerRadius(radius * 0.6f))
                // avatar + caption lines
                drawCircle(c.primary, cw * 0.07f, Offset(left + cw * 0.15f, top + cardH * 0.82f))
                drawRoundRect(
                    c.onPrimaryContainer.copy(alpha = 0.35f),
                    Offset(left + cw * 0.27f, top + cardH * 0.79f),
                    Size(cw * 0.5f, cardH * 0.035f),
                    CornerRadius(cardH),
                )
                drawRoundRect(
                    c.onPrimaryContainer.copy(alpha = 0.2f),
                    Offset(left + cw * 0.27f, top + cardH * 0.86f),
                    Size(cw * 0.32f, cardH * 0.035f),
                    CornerRadius(cardH),
                )
                // play glyph
                val cx = left + cw / 2f
                val cy = top + cardH * 0.4f
                val g = cw * 0.1f
                val play = Path().apply {
                    moveTo(cx - g * 0.6f, cy - g)
                    lineTo(cx + g, cy)
                    lineTo(cx - g * 0.6f, cy + g)
                    close()
                }
                drawPath(play, c.onPrimaryContainer.copy(alpha = 0.5f))
            }
        }

        // Face, nodding.
        val faceR = phoneW * 0.32f
        val faceCenter = Offset(phoneLeft + phoneW + faceR * 0.9f, phoneTop + phoneH * 0.62f)
        translate(top = nod * faceR * 0.18f) {
            drawFace(faceCenter, faceR, c.tertiaryContainer, c.onTertiaryContainer, lookDown = nod)
        }
        // Motion arrow next to the phone while advancing.
        if (advance > 0f && advance < 1f) {
            val ax = phoneLeft - phoneW * 0.16f
            val alpha = sin(advance * PI).toFloat()
            val arrow = Path().apply {
                moveTo(ax, phoneTop + phoneH * 0.62f)
                lineTo(ax, phoneTop + phoneH * 0.38f)
                moveTo(ax - phoneW * 0.07f, phoneTop + phoneH * 0.45f)
                lineTo(ax, phoneTop + phoneH * 0.38f)
                lineTo(ax + phoneW * 0.07f, phoneTop + phoneH * 0.45f)
            }
            drawPath(arrow, c.primary.copy(alpha = alpha), style = Stroke(phoneW * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawFace(center: Offset, r: Float, fill: Color, ink: Color, lookDown: Float) {
    drawCircle(fill, r, center)
    val eyeY = center.y - r * 0.12f + lookDown * r * 0.12f
    drawCircle(ink, r * 0.09f, Offset(center.x - r * 0.35f, eyeY))
    drawCircle(ink, r * 0.09f, Offset(center.x + r * 0.35f, eyeY))
    val smile = Path().apply {
        moveTo(center.x - r * 0.35f, center.y + r * 0.3f + lookDown * r * 0.1f)
        quadraticTo(
            center.x, center.y + r * 0.55f + lookDown * r * 0.1f,
            center.x + r * 0.35f, center.y + r * 0.3f + lookDown * r * 0.1f,
        )
    }
    drawPath(smile, ink, style = Stroke(r * 0.09f, cap = StrokeCap.Round))
}

/**
 * A hand sweeping over the top of a phone; ripples appear from the proximity sensor when the hand
 * passes over it. [pulseKey] changes whenever a real wave is detected, flashing a ring.
 */
@Composable
fun WaveIllustration(modifier: Modifier = Modifier, pulseKey: Int = 0, sensorCovered: Boolean = false) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(700))
        }
    }
    val c = MaterialTheme.colorScheme
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val phoneW = min(w * 0.5f, h * 0.7f)
        val phoneLeft = (w - phoneW) / 2f
        val phoneTop = h * 0.42f
        val r = phoneW * 0.14f
        drawRoundRect(c.surfaceContainerHighest, Offset(phoneLeft, phoneTop), Size(phoneW, h), CornerRadius(r))
        drawRoundRect(
            c.surface,
            Offset(phoneLeft + phoneW * 0.06f, phoneTop + phoneW * 0.06f),
            Size(phoneW * 0.88f, h),
            CornerRadius(r * 0.7f),
        )
        // earpiece + sensor
        val sensor = Offset(phoneLeft + phoneW * 0.36f, phoneTop + phoneW * 0.035f)
        drawRoundRect(
            c.outline,
            Offset(phoneLeft + phoneW * 0.42f, phoneTop + phoneW * 0.025f),
            Size(phoneW * 0.16f, phoneW * 0.02f),
            CornerRadius(phoneW),
        )

        // Hand position: smooth sweep left↔right above the sensor.
        val sweep = if (reduced) 0f else sin(phase * 2 * PI).toFloat()
        val handX = sensor.x + sweep * phoneW * 0.55f
        val handY = phoneTop - phoneW * 0.32f
        val over = sensorCovered || abs(handX - sensor.x) < phoneW * 0.15f

        // Sensor ripples.
        val sensorColor = if (over) c.primary else c.outline
        drawCircle(sensorColor, phoneW * 0.022f, sensor)
        if (over) {
            for (i in 1..2) {
                drawCircle(
                    c.primary.copy(alpha = 0.35f / i),
                    phoneW * 0.06f * i,
                    sensor,
                    style = Stroke(phoneW * 0.012f),
                )
            }
        }
        if (pulse.value > 0f) {
            drawCircle(
                c.tertiary.copy(alpha = pulse.value * 0.8f),
                phoneW * (0.1f + (1f - pulse.value) * 0.5f),
                sensor,
                style = Stroke(phoneW * 0.02f),
            )
        }

        // Motion trail.
        if (!reduced) {
            val trail = Path().apply {
                moveTo(sensor.x - phoneW * 0.6f, handY + phoneW * 0.25f)
                quadraticTo(sensor.x, handY + phoneW * 0.12f, sensor.x + phoneW * 0.6f, handY + phoneW * 0.25f)
            }
            drawPath(trail, c.primary.copy(alpha = 0.18f), style = Stroke(phoneW * 0.025f, cap = StrokeCap.Round))
        }

        drawHand(Offset(handX, handY), phoneW * 0.32f, c.primary, tilt = sweep * 10f)
    }
}

private fun DrawScope.drawHand(center: Offset, s: Float, color: Color, tilt: Float) {
    rotate(tilt, pivot = Offset(center.x, center.y + s * 0.6f)) {
        // palm
        drawRoundRect(
            color,
            Offset(center.x - s * 0.42f, center.y - s * 0.05f),
            Size(s * 0.84f, s * 0.7f),
            CornerRadius(s * 0.3f),
        )
        // fingers
        val fingerW = s * 0.17f
        val lengths = floatArrayOf(0.55f, 0.68f, 0.62f, 0.5f)
        for (i in 0..3) {
            val fx = center.x - s * 0.3f + i * s * 0.2f
            drawLine(
                color,
                Offset(fx, center.y + s * 0.05f),
                Offset(fx, center.y - s * lengths[i]),
                strokeWidth = fingerW,
                cap = StrokeCap.Round,
            )
        }
        // thumb
        drawLine(
            color,
            Offset(center.x - s * 0.36f, center.y + s * 0.35f),
            Offset(center.x - s * 0.7f, center.y + s * 0.02f),
            strokeWidth = fingerW,
            cap = StrokeCap.Round,
        )
    }
}

/** A shield holding a camera lens, gently floating — "your camera, protected". */
@Composable
fun PrivacyIllustration(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "privacy")
    val floatT by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "float",
    )
    val dy = if (reduced) 0f else sin(floatT * 2 * PI).toFloat()
    val c = MaterialTheme.colorScheme
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = min(size.width, size.height) * 0.8f
        val cx = size.width / 2f
        val top = (size.height - s) / 2f + dy * s * 0.02f
        val shield = Path().apply {
            moveTo(cx, top)
            cubicTo(cx + s * 0.25f, top + s * 0.1f, cx + s * 0.38f, top + s * 0.1f, cx + s * 0.4f, top + s * 0.12f)
            cubicTo(cx + s * 0.42f, top + s * 0.55f, cx + s * 0.25f, top + s * 0.82f, cx, top + s * 0.95f)
            cubicTo(cx - s * 0.25f, top + s * 0.82f, cx - s * 0.42f, top + s * 0.55f, cx - s * 0.4f, top + s * 0.12f)
            cubicTo(cx - s * 0.38f, top + s * 0.1f, cx - s * 0.25f, top + s * 0.1f, cx, top)
            close()
        }
        drawPath(shield, c.primaryContainer)
        drawPath(shield, c.primary, style = Stroke(s * 0.025f, join = StrokeJoin.Round))
        val lens = Offset(cx, top + s * 0.45f)
        drawCircle(c.surface, s * 0.17f, lens)
        drawCircle(c.primary, s * 0.17f, lens, style = Stroke(s * 0.03f))
        drawCircle(c.primary, s * 0.08f, lens)
        drawCircle(c.surface.copy(alpha = 0.8f), s * 0.025f, Offset(lens.x - s * 0.03f, lens.y - s * 0.03f))
        // check badge
        val badge = Offset(cx + s * 0.22f, top + s * 0.7f)
        drawCircle(c.tertiary, s * 0.11f, badge)
        val check = Path().apply {
            moveTo(badge.x - s * 0.05f, badge.y)
            lineTo(badge.x - s * 0.012f, badge.y + s * 0.04f)
            lineTo(badge.x + s * 0.055f, badge.y - s * 0.035f)
        }
        drawPath(check, c.onTertiary, style = Stroke(s * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
