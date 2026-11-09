package io.github.arnavdugad.flick.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.BuildConfig
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.data.ThemeMode
import io.github.arnavdugad.flick.system.AccessibilityStatus
import io.github.arnavdugad.flick.system.SystemIntents
import io.github.arnavdugad.flick.ui.components.FlickCard
import io.github.arnavdugad.flick.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.settings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = FlickSettings())
    var a11yOn by remember { mutableStateOf(AccessibilityStatus.isEnabledInSettings(context)) }
    LifecycleResumeEffect(Unit) {
        a11yOn = AccessibilityStatus.isEnabledInSettings(context)
        onPauseOrDispose { }
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader(stringResource(R.string.set_appearance)) }
            item {
                FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            val modes = ThemeMode.entries
                            modes.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { scope.launch { repo.setThemeMode(mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.set_theme_system
                                                ThemeMode.LIGHT -> R.string.set_theme_light
                                                ThemeMode.DARK -> R.string.set_theme_dark
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.set_amoled),
                        body = stringResource(R.string.set_amoled_body),
                        checked = settings.amoledBlack,
                        onChange = { scope.launch { repo.setAmoledBlack(it) } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.set_dynamic),
                        body = stringResource(if (dynamicAvailable) R.string.set_dynamic_body else R.string.set_dynamic_unavailable),
                        checked = settings.dynamicColor && dynamicAvailable,
                        enabled = dynamicAvailable,
                        onChange = { scope.launch { repo.setDynamicColor(it) } },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.set_feedback)) }
            item {
                FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    SwitchRow(
                        title = stringResource(R.string.set_haptics),
                        body = stringResource(R.string.set_haptics_body),
                        checked = settings.haptics,
                        onChange = { scope.launch { repo.setHaptics(it) } },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.set_setup)) }
            item {
                FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    LinkRow(
                        icon = { Icon(Icons.Rounded.AccessibilityNew, null) },
                        title = stringResource(R.string.set_accessibility),
                        body = stringResource(if (a11yOn) R.string.set_accessibility_on else R.string.set_accessibility_off),
                        onClick = { SystemIntents.openAccessibilitySettings(context) },
                    )
                    LinkRow(
                        icon = { Icon(Icons.Rounded.Replay, null) },
                        title = stringResource(R.string.set_replay_onboarding),
                        body = null,
                        onClick = onReplayOnboarding,
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.set_about)) }
            item {
                FlickCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    LinkRow(
                        icon = { Icon(Icons.Rounded.Info, null) },
                        title = stringResource(R.string.set_version),
                        body = BuildConfig.VERSION_NAME,
                        onClick = null,
                    )
                    LinkRow(
                        icon = { Icon(Icons.Rounded.Code, null) },
                        title = stringResource(R.string.set_source),
                        body = "github.com/${BuildConfig.GITHUB_REPO}",
                        onClick = { SystemIntents.openUrl(context, "https://github.com/${BuildConfig.GITHUB_REPO}") },
                    )
                    LinkRow(
                        icon = { Icon(Icons.Rounded.Gavel, null) },
                        title = stringResource(R.string.set_license),
                        body = stringResource(R.string.set_license_value),
                        onClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
    )
}

@Composable
private fun LinkRow(
    icon: @Composable () -> Unit,
    title: String,
    body: String?,
    onClick: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        leadingContent = icon,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
