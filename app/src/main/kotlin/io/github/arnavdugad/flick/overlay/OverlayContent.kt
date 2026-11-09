package io.github.arnavdugad.flick.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.core.mindful.Nudge
import io.github.arnavdugad.flick.core.mindful.NudgeKind
import io.github.arnavdugad.flick.feature.autoscroll.AutoScrollStatus
import io.github.arnavdugad.flick.ui.illustrations.FlickMark
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import io.github.arnavdugad.flick.ui.theme.rememberReducedMotion
import io.github.arnavdugad.flick.vision.VisionPreview
import kotlinx.coroutines.delay

/** A soft glow along every screen edge, played once per [pulse]. Purely visual and untouchable. */
@Composable
fun EdgeGlow(pulse: Int) {
    val reduced = rememberReducedMotion()
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        if (reduced) {
            glow.snapTo(0.7f)
            delay(250)
            glow.snapTo(0f)
        } else {
            glow.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
            glow.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val a = glow.value
        if (a <= 0f) return@Canvas
        val t = 26.dp.toPx()
        val c = color.copy(alpha = 0.6f * a)
        drawRect(Brush.verticalGradient(listOf(c, Color.Transparent), 0f, t), size = Size(size.width, t))
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, c), size.height - t, size.height),
            topLeft = Offset(0f, size.height - t),
            size = Size(size.width, t),
        )
        drawRect(Brush.horizontalGradient(listOf(c, Color.Transparent), 0f, t), size = Size(t, size.height))
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, c), size.width - t, size.width),
            topLeft = Offset(size.width - t, 0f),
            size = Size(t, size.height),
        )
    }
}

/**
 * Shown whenever the camera is on. Tapping it pauses Flick at once (the kill switch). With
 * [points] it also shows the tracking dots — never the camera image.
 */
@Composable
fun PrivacyBadge(points: VisionPreview.Points?, paused: Boolean, onPause: () -> Unit, onUndo: () -> Unit) {
    val badgeCd = stringResource(R.string.ov_badge_cd)
    Surface(
        onClick = if (paused) onUndo else onPause,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.semantics { if (!paused) contentDescription = badgeCd },
    ) {
        AnimatedContent(targetState = paused, label = "badge", transitionSpec = {
            (fadeIn(FlickMotion.effects()) + scaleIn(FlickMotion.spatialFast(), initialScale = 0.9f))
                .togetherWith(fadeOut(FlickMotion.effects()))
        }) { isPaused ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (isPaused) {
                    Text(stringResource(R.string.ov_paused), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.ov_undo),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                } else {
                    Box(
                        Modifier
                            .size(8.dp)
                            .padding(0.dp),
                    ) {
                        Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF34C759)) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Videocam, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
                    if (points?.face != null || points?.hand != null) {
                        Spacer(Modifier.width(8.dp))
                        val dotColor = MaterialTheme.colorScheme.inversePrimary
                        Canvas(Modifier.size(width = 30.dp, height = 40.dp)) {
                            points.face?.let { pts ->
                                var i = 0
                                while (i + 1 < pts.size) {
                                    drawCircle(dotColor, 0.8.dp.toPx(), Offset(pts[i] * size.width, pts[i + 1] * size.height))
                                    i += 6
                                }
                            }
                            points.hand?.let { pts ->
                                var i = 0
                                while (i + 1 < pts.size) {
                                    drawCircle(dotColor, 1.5.dp.toPx(), Offset(pts[i] * size.width, pts[i + 1] * size.height))
                                    i += 2
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class BubbleAction { SCROLL_UP, SCROLL_DOWN, BACK, HOME, AUTO_SCROLL, PAUSE }

/**
 * The floating mini-controller. Collapsed it's a small, draggable Flick button that fades when
 * idle; tapped, it opens a row of quick actions that closes by itself.
 */
@Composable
fun FloatingBubble(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onAction: (BubbleAction) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var touchKey by remember { mutableIntStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(touchKey, expanded) {
        idle = false
        delay(if (expanded) 5_000 else 3_000)
        if (expanded) {
            expanded = false
            onExpandedChange(false)
        } else {
            idle = true
        }
    }
    val alpha by animateFloatAsState(if (idle && !expanded) 0.45f else 1f, FlickMotion.effects(), label = "idle")
    val openCd = stringResource(R.string.ov_bubble_cd)

    Surface(
        shape = CircleShape,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        shadowElevation = if (expanded) 6.dp else 0.dp,
        modifier = Modifier
            .alpha(alpha)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchKey++ },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(if (expanded) 4.dp else 6.dp)) {
            Surface(
                onClick = {
                    touchKey++
                    expanded = !expanded
                    onExpandedChange(expanded)
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = if (expanded) 0.dp else 4.dp,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = openCd },
            ) {
                FlickMark(Modifier.padding(6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (expanded) {
                val items = listOf(
                    BubbleAction.SCROLL_UP to (Icons.Rounded.KeyboardDoubleArrowUp to R.string.act_scroll_up),
                    BubbleAction.SCROLL_DOWN to (Icons.Rounded.KeyboardDoubleArrowDown to R.string.act_scroll_down),
                    BubbleAction.BACK to (Icons.AutoMirrored.Rounded.ArrowBack to R.string.act_back),
                    BubbleAction.HOME to (Icons.Rounded.Home to R.string.act_home),
                    BubbleAction.AUTO_SCROLL to (Icons.Rounded.PlayArrow to R.string.act_auto_scroll),
                    BubbleAction.PAUSE to (Icons.Rounded.PowerSettingsNew to R.string.act_pause_flick),
                )
                for ((action, info) in items) {
                    val (icon, label) = info
                    IconButton(onClick = {
                        touchKey++
                        onAction(action)
                    }) { Icon(icon, stringResource(label)) }
                }
            }
        }
    }
}

/** Controls shown while auto-scroll runs. */
@Composable
fun AutoScrollPill(
    status: AutoScrollStatus,
    speed: Int,
    onPauseResume: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val running = status == AutoScrollStatus.RUNNING
                IconButton(onClick = onPauseResume) {
                    Icon(
                        if (status == AutoScrollStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        stringResource(if (running) R.string.ov_pause else R.string.ov_resume),
                    )
                }
                IconButton(onClick = onSlower) { Icon(Icons.Rounded.Remove, stringResource(R.string.ov_slower)) }
                Text(
                    stringResource(R.string.ov_speed, speed),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 64.dp),
                )
                IconButton(onClick = onFaster) { Icon(Icons.Rounded.Add, stringResource(R.string.ov_faster)) }
                IconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, stringResource(R.string.ov_stop)) }
            }
            val hint = when (status) {
                AutoScrollStatus.WAITING_FOR_FACE -> R.string.ov_wait_face
                AutoScrollStatus.TOUCHED -> R.string.ov_touched
                else -> null
            }
            if (hint != null) {
                Text(stringResource(hint), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** Mindful-mode reminder card. */
@Composable
fun NudgeCard(nudge: Nudge, onKeepGoing: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SelfImprovement, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (nudge.kind == NudgeKind.AUTO_PAUSED) R.string.ov_nudge_paused_title else R.string.ov_nudge_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (nudge.kind) {
                    NudgeKind.REELS -> pluralStringResource(R.plurals.ov_nudge_reels, nudge.reels, nudge.reels)
                    NudgeKind.MINUTES -> pluralStringResource(R.plurals.ov_nudge_minutes, nudge.minutes, nudge.minutes)
                    NudgeKind.AUTO_PAUSED -> pluralStringResource(R.plurals.ov_nudge_paused, nudge.minutes, nudge.minutes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (nudge.kind == NudgeKind.AUTO_PAUSED) {
                    TextButton(onClick = onResume) { Text(stringResource(R.string.ov_resume_flick)) }
                    Button(onClick = onKeepGoing) { Text(stringResource(R.string.ov_take_break)) }
                } else {
                    TextButton(onClick = onKeepGoing) { Text(stringResource(R.string.ov_keep_going)) }
                    Button(onClick = onPause) { Text(stringResource(R.string.act_pause_flick)) }
                }
            }
        }
    }
}

/** Top bar while recording a macro. */
@Composable
fun MacroRecorderBar(steps: Int, onDone: () -> Unit, onCancel: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Rounded.FiberManualRecord, null, tint = Color(0xFFFF453A), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.ov_recording, steps, steps), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) }
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.action_done), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
