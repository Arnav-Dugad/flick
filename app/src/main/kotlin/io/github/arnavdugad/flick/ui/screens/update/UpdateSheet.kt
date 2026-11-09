package io.github.arnavdugad.flick.ui.screens.update

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arnavdugad.flick.BuildConfig
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.update.UpdateInfo
import io.github.arnavdugad.flick.system.SystemIntents
import io.github.arnavdugad.flick.update.UpdateError
import io.github.arnavdugad.flick.update.UpdateState
import java.text.DateFormat
import java.util.Date

/** The update sheet: release notes, then download → verify → install, with every failure explained. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updates = context.container.updates
    val state by updates.state.collectAsStateWithLifecycle()

    // Returning from the "install unknown apps" screen: carry on if it was allowed.
    LifecycleResumeEffect(state) {
        val s = state
        if (s is UpdateState.NeedsInstallPermission && context.packageManager.canRequestPackageInstalls()) {
            updates.downloadAndInstall(s.info)
        }
        onPauseOrDispose { }
    }

    val info: UpdateInfo? = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.NeedsInstallPermission -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = {
            updates.dismiss()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            if (info != null) {
                Text(
                    stringResource(R.string.update_sheet_title, info.versionName),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(
                        R.string.update_sheet_subtitle,
                        BuildConfig.VERSION_NAME,
                        Formatter.formatShortFileSize(context, info.apkSize),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (info.notes.isBlank()) {
                                androidx.compose.ui.text.AnnotatedString(stringResource(R.string.update_no_notes))
                            } else {
                                MarkdownLite.render(info.notes, MaterialTheme.typography.titleSmall.toSpanStyle())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Verified, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.update_verified_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Body(state = state, onAction = { action ->
                when (action) {
                    SheetAction.Install -> info?.let(updates::downloadAndInstall)
                    SheetAction.Skip -> {
                        info?.let(updates::skip)
                        onDismiss()
                    }
                    SheetAction.Later -> {
                        updates.dismiss()
                        onDismiss()
                    }
                    SheetAction.Cancel -> updates.cancelDownload()
                    SheetAction.OpenPermission -> context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    SheetAction.OpenRelease -> info?.releaseUrl?.let { SystemIntents.openUrl(context, it) }
                }
            })
        }
    }
}

private enum class SheetAction { Install, Skip, Later, Cancel, OpenPermission, OpenRelease }

@Composable
private fun Body(state: UpdateState, onAction: (SheetAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state) {
            is UpdateState.Available -> {
                Button(onClick = { onAction(SheetAction.Install) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.update_install), style = MaterialTheme.typography.titleMedium)
                }
                Row {
                    TextButton(onClick = { onAction(SheetAction.Skip) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.update_skip))
                    }
                    TextButton(onClick = { onAction(SheetAction.Later) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            }
            is UpdateState.Downloading -> {
                val pct = (state.progress * 100).toInt()
                Text(
                    if (state.progress < 0) {
                        stringResource(R.string.update_downloading_unknown)
                    } else {
                        stringResource(R.string.update_downloading, pct)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.progress < 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { onAction(SheetAction.Cancel) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_cancel))
                }
            }
            is UpdateState.Installing -> {
                Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is UpdateState.NeedsInstallPermission -> {
                Text(stringResource(R.string.update_permission_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.update_permission_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onAction(SheetAction.OpenPermission) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.update_permission_action))
                }
            }
            is UpdateState.Failed -> {
                Text(
                    errorText(state.error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Row {
                    if (state.info != null && state.error !is UpdateError.SignatureMismatch) {
                        Button(onClick = { onAction(SheetAction.Install) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.update_retry))
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                    if (state.info?.releaseUrl != null) {
                        OutlinedButton(onClick = { onAction(SheetAction.OpenRelease) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.update_view_release))
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
fun errorText(error: UpdateError): String = when (error) {
    UpdateError.Offline -> stringResource(R.string.update_err_offline)
    is UpdateError.RateLimited -> error.resetEpochSeconds?.let {
        stringResource(R.string.update_err_rate_limited_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)))
    } ?: stringResource(R.string.update_err_rate_limited)
    UpdateError.NoReleases -> stringResource(R.string.update_err_no_releases)
    is UpdateError.InvalidRelease -> stringResource(R.string.update_err_invalid)
    is UpdateError.Incompatible -> stringResource(R.string.update_err_incompatible)
    UpdateError.ChecksumMismatch -> stringResource(R.string.update_err_checksum)
    UpdateError.SignatureMismatch -> stringResource(R.string.update_err_signature)
    UpdateError.Storage -> stringResource(R.string.update_err_storage)
    UpdateError.Cancelled -> stringResource(R.string.update_err_cancelled)
    is UpdateError.InstallFailed -> stringResource(R.string.update_err_install)
    is UpdateError.Unknown -> stringResource(R.string.update_err_unknown)
}
