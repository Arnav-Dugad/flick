package io.github.arnavdugad.flick.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import io.github.arnavdugad.flick.core.model.Actions
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.permission
import io.github.arnavdugad.flick.ui.components.rememberPermissionRequest
import io.github.arnavdugad.flick.ui.components.rememberPermissionSnapshot
import io.github.arnavdugad.flick.ui.components.triggerProblem
import io.github.arnavdugad.flick.ui.triggerDescription
import io.github.arnavdugad.flick.ui.triggerIcon
import io.github.arnavdugad.flick.ui.triggerLabel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.system.AccessibilityStatus
import io.github.arnavdugad.flick.system.InstallSource
import io.github.arnavdugad.flick.system.SystemIntents
import io.github.arnavdugad.flick.ui.components.Bullet
import io.github.arnavdugad.flick.ui.components.NumberedStep
import io.github.arnavdugad.flick.ui.illustrations.EnableServiceMockup
import io.github.arnavdugad.flick.ui.illustrations.FeedIllustration
import io.github.arnavdugad.flick.ui.illustrations.PrivacyIllustration
import io.github.arnavdugad.flick.ui.illustrations.RestrictedSettingsMockup
import io.github.arnavdugad.flick.ui.illustrations.WaveIllustration
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OnboardingStep { WELCOME, PRIVACY, RESTRICTED, ENABLE, TRY }

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()

    var a11yEnabled by remember { mutableStateOf(AccessibilityStatus.isEnabledInSettings(context)) }
    LifecycleResumeEffect(Unit) {
        a11yEnabled = AccessibilityStatus.isEnabledInSettings(context)
        onPauseOrDispose { }
    }

    val steps = remember {
        buildList {
            add(OnboardingStep.WELCOME)
            add(OnboardingStep.PRIVACY)
            if (InstallSource.likelyRestricted(context) && !AccessibilityStatus.isEnabledInSettings(context)) {
                add(OnboardingStep.RESTRICTED)
            }
            add(OnboardingStep.ENABLE)
            add(OnboardingStep.TRY)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(steps.indices)]
    var forward by remember { mutableStateOf(true) }

    fun next() {
        forward = true
        if (index < steps.lastIndex) {
            index++
        } else {
            scope.launch {
                container.settings.setOnboardingComplete(true)
                onFinished()
            }
        }
    }

    fun back() {
        forward = false
        if (index > 0) index--
    }

    // Auto-advance once the service gets enabled while on the "turn on" step.
    LaunchedEffect(step, a11yEnabled) {
        if (step == OnboardingStep.ENABLE && a11yEnabled) {
            delay(900)
            next()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index > 0) {
                IconButton(onClick = ::back) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            StepDots(count = steps.size, current = index, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(FlickMotion.screenSlide) { it / 3 * dir } + fadeIn(FlickMotion.effects()))
                    .togetherWith(slideOutHorizontally(FlickMotion.screenSlide) { -it / 4 * dir } + fadeOut(FlickMotion.effects()))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { target ->
            when (target) {
                OnboardingStep.WELCOME -> WelcomeStep(onNext = ::next)
                OnboardingStep.PRIVACY -> PrivacyStep(onNext = ::next)
                OnboardingStep.RESTRICTED -> RestrictedStep(
                    onOpenAppInfo = { SystemIntents.openAppDetails(context) },
                    onNext = ::next,
                )
                OnboardingStep.ENABLE -> EnableStep(
                    enabled = a11yEnabled,
                    onOpenSettings = { SystemIntents.openAccessibilitySettings(context) },
                    onNext = ::next,
                )
                OnboardingStep.TRY -> TryStep(onFinish = ::next)
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.step_of, current + 1, count)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else 8.dp, FlickMotion.spatialFast(), label = "dotW")
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                FlickMotion.effects(),
                label = "dotC",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Common layout: illustration on top, scrollable text, buttons pinned to the bottom. */
@Composable
private fun StepLayout(
    illustration: @Composable (Modifier) -> Unit,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            illustration(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .aspectRatio(1.25f)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            body()
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun htmlString(id: Int): AnnotatedString = AnnotatedString.fromHtml(stringResource(id))

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepLayout(
        illustration = { FeedIllustration(it) },
        title = stringResource(R.string.onb_welcome_title),
        primaryLabel = stringResource(R.string.onb_welcome_cta),
        onPrimary = onNext,
    ) {
        BodyText(stringResource(R.string.onb_welcome_body))
    }
}

@Composable
private fun PrivacyStep(onNext: () -> Unit) {
    StepLayout(
        illustration = { PrivacyIllustration(it) },
        title = stringResource(R.string.onb_privacy_title),
        primaryLabel = stringResource(R.string.onb_privacy_cta),
        onPrimary = onNext,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Bullet(Icons.Rounded.Lock, stringResource(R.string.onb_privacy_1_title), stringResource(R.string.onb_privacy_1_body))
            Bullet(Icons.Rounded.NoAccounts, stringResource(R.string.onb_privacy_2_title), stringResource(R.string.onb_privacy_2_body))
            Bullet(Icons.Rounded.Visibility, stringResource(R.string.onb_privacy_3_title), stringResource(R.string.onb_privacy_3_body))
            Bullet(Icons.Rounded.Code, stringResource(R.string.onb_privacy_4_title), stringResource(R.string.onb_privacy_4_body))
        }
    }
}

@Composable
private fun RestrictedStep(onOpenAppInfo: () -> Unit, onNext: () -> Unit) {
    StepLayout(
        illustration = { RestrictedSettingsMockup(it) },
        title = stringResource(R.string.onb_restricted_title),
        primaryLabel = stringResource(R.string.onb_restricted_cta),
        onPrimary = onOpenAppInfo,
        secondaryLabel = stringResource(R.string.onb_restricted_done),
        onSecondary = onNext,
    ) {
        BodyText(stringResource(R.string.onb_restricted_body))
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NumberedStep(1, { Text(htmlString(R.string.onb_restricted_step1), style = MaterialTheme.typography.bodyLarge) })
            NumberedStep(2, { Text(htmlString(R.string.onb_restricted_step2), style = MaterialTheme.typography.bodyLarge) })
            NumberedStep(3, { Text(htmlString(R.string.onb_restricted_step3), style = MaterialTheme.typography.bodyLarge) })
        }
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(
                stringResource(R.string.onb_restricted_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun EnableStep(enabled: Boolean, onOpenSettings: () -> Unit, onNext: () -> Unit) {
    StepLayout(
        illustration = { EnableServiceMockup(enabled, it) },
        title = stringResource(R.string.onb_enable_title),
        primaryLabel = stringResource(if (enabled) R.string.action_continue else R.string.action_open_settings),
        onPrimary = if (enabled) onNext else onOpenSettings,
        secondaryLabel = if (enabled) null else stringResource(R.string.action_skip),
        onSecondary = onNext,
    ) {
        BodyText(stringResource(R.string.onb_enable_body))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_enable_never),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(htmlString(R.string.onb_enable_steps), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            if (enabled) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(if (enabled) R.string.onb_enable_ready else R.string.onb_enable_waiting),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Gestures offered as a first try, in order of how universally they work. */
private val FIRST_GESTURES = listOf(
    TriggerType.HAND_OPEN_PALM,
    TriggerType.FACE_SMILE,
    TriggerType.HEAD_NOD,
    TriggerType.BACK_DOUBLE_KNOCK,
    TriggerType.PROXIMITY_WAVE,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TryStep(onFinish: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val engine = container.engine
    val scope = rememberCoroutineScope()
    val statuses by engine.statuses.collectAsStateWithLifecycle()
    val permissions = rememberPermissionSnapshot()
    val requestPermission = rememberPermissionRequest { engine.refreshSources() }

    val options = remember(statuses) {
        FIRST_GESTURES.filter { statuses[it] != SourceStatus.NO_HARDWARE && statuses[it] != SourceStatus.UNSUPPORTED }
    }
    var selected by rememberSaveable { mutableStateOf(TriggerType.HAND_OPEN_PALM) }
    var detected by remember(selected) { mutableIntStateOf(0) }

    LifecycleResumeEffect(selected) {
        engine.setCalibrating(selected)
        engine.refreshSources()
        onPauseOrDispose { engine.setCalibrating(null) }
    }
    LaunchedEffect(selected) {
        engine.events.collect { if (it.trigger == selected) detected++ }
    }

    StepLayout(
        illustration = { m ->
            if (selected == TriggerType.PROXIMITY_WAVE) {
                WaveIllustration(m, pulseKey = detected)
            } else {
                Box(m, contentAlignment = Alignment.Center) {
                    IconBadge(
                        triggerIcon(selected),
                        size = 160.dp,
                        container = if (detected > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        content = if (detected > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        title = stringResource(R.string.onb_try_title),
        primaryLabel = stringResource(R.string.action_finish),
        onPrimary = {
            scope.launch {
                container.settings.setBinding(selected, Actions.Next)
                onFinish()
            }
        },
    ) {
        BodyText(stringResource(R.string.onb_try_body))
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in options) {
                FilterChip(
                    selected = t == selected,
                    onClick = { selected = t },
                    label = { Text(triggerLabel(t)) },
                    leadingIcon = { Icon(triggerIcon(t), null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(triggerDescription(selected), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        val needsPermission = !permissions.granted(selected)
        if (needsPermission) {
            Text(
                stringResource(R.string.onb_try_camera),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { selected.permission?.let(requestPermission) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.detail_grant)) }
            return@StepLayout
        }

        val problem = triggerProblem(selected, statuses[selected], permissions)
        Surface(
            color = if (detected > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (detected > 0) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when {
                        detected > 0 -> stringResource(R.string.onb_try_success, detected)
                        problem != null -> problem
                        else -> stringResource(R.string.onb_try_waiting)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (detected > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onb_try_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
