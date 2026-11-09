package io.github.arnavdugad.flick.ui.screens.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.action.isSupported
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.ui.ActionCatalog
import io.github.arnavdugad.flick.ui.actionIcon
import io.github.arnavdugad.flick.ui.actionLabel
import io.github.arnavdugad.flick.ui.components.IconBadge
import io.github.arnavdugad.flick.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    title: String,
    selected: Action?,
    onSelect: (Action) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val supported = { a: Action -> a !is Action.Global || a.action.isSupported() }
    val groups = listOf(
        R.string.lab_group_gestures to ActionCatalog.gestures,
        R.string.lab_group_system to ActionCatalog.system.filter(supported),
        R.string.lab_group_media to ActionCatalog.media,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
            }
            for ((header, actions) in groups) {
                item { SectionHeader(stringResource(header), Modifier.padding(horizontal = 20.dp)) }
                items(actions) { action ->
                    val isSelected = action == selected
                    ListItem(
                        headlineContent = { Text(actionLabel(action)) },
                        leadingContent = { IconBadge(actionIcon(action), size = 40.dp) },
                        trailingContent = {
                            if (isSelected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(action) },
                    )
                }
            }
        }
    }
}
