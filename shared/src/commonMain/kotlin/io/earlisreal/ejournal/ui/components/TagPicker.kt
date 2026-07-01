package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * A position's tag chips plus an inline "＋ Tag" affordance that opens the [TagEditorMenu]. Meant to
 * sit inside a table row: the add affordance and menu consume their own clicks, so tapping them does
 * not trigger the row's own click (e.g. navigate-to-analysis).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RowScope.TagCell(
    position: ClosedPosition,
    weight: Float,
    allTags: List<Tag>,
    onToggleTag: (ClosedPosition, Tag) -> Unit,
    onCreateTag: (ClosedPosition, String) -> Unit,
    onManageTags: () -> Unit,
) {
    var expanded by remember(position.openingTransactionId) { mutableStateOf(false) }
    Box(Modifier.weight(weight)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            position.tags.forEach { TagChip(it) }
            Text(
                "＋ Tag",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TagEditorMenu(
            expanded = expanded,
            allTags = allTags,
            selectedIds = position.tags.mapTo(mutableSetOf()) { it.id },
            onToggle = { onToggleTag(position, it) },
            onCreate = { onCreateTag(position, it) },
            onManage = { expanded = false; onManageTags() },
            onDismiss = { expanded = false },
        )
    }
}

/** Dropdown editor: toggle existing tags on/off, quick-create a new one, or open the tag manager. */
@Composable
fun TagEditorMenu(
    expanded: Boolean,
    allTags: List<Tag>,
    selectedIds: Set<Long>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(AppTheme.colors.surface),
    ) {
        if (allTags.isEmpty()) {
            Text(
                "No tags yet — create one below.",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        allTags.forEach { tag ->
            val checked = tag.id in selectedIds
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(tag) }
                    .padding(horizontal = Spacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    if (checked) "☑" else "☐",
                    color = if (checked) tagColor(tag.color) else AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                TagColorDot(tag.color)
                Text(tag.name, color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        var newName by remember { mutableStateOf("") }
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("New tag") },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            Text(
                "Add",
                modifier = Modifier
                    .clickable(enabled = newName.isNotBlank()) { onCreate(newName.trim()); newName = "" }
                    .padding(Spacing.sm),
                color = if (newName.isNotBlank()) AppTheme.colors.accent else AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = AppTheme.colors.border)
        Text(
            "Manage tags…",
            modifier = Modifier.fillMaxWidth().clickable { onManage() }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
