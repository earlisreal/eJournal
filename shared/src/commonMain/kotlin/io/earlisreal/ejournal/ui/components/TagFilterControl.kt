package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.PillShape
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * TopBar control for the global tag filter: a pill showing the active count, opening a dropdown of
 * tags (checkbox each) plus an Any/All match toggle and a Clear action. Loads the current vocabulary
 * each time it opens so tags created via the manager show up without an app restart.
 */
@Composable
fun TagFilterControl(
    tagRepository: TagRepository,
    selectedTagIds: Set<Long>,
    tagMatch: TagMatch,
    onToggleTag: (Long) -> Unit,
    onSetMatch: (TagMatch) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    LaunchedEffect(expanded) { if (expanded) tags = tagRepository.getAll() }

    val active = selectedTagIds.isNotEmpty()
    val label = if (active) "Tags · ${selectedTagIds.size}" else "Tags"
    Box(modifier) {
        Row(
            Modifier
                .clip(PillShape)
                .background(if (active) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surfaceElevated)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                color = if (active) AppTheme.colors.accent else AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("▾", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppTheme.colors.surface),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("Match", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
                MatchChip("Any", tagMatch == TagMatch.ANY) { onSetMatch(TagMatch.ANY) }
                MatchChip("All", tagMatch == TagMatch.ALL) { onSetMatch(TagMatch.ALL) }
            }
            HorizontalDivider(color = AppTheme.colors.border)
            if (tags.isEmpty()) {
                Text(
                    "No tags yet.",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            tags.forEach { tag ->
                val checked = tag.id in selectedTagIds
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleTag(tag.id) }
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
            if (active) {
                HorizontalDivider(color = AppTheme.colors.border)
                Text(
                    "Clear filter",
                    modifier = Modifier.fillMaxWidth().clickable { onClear() }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MatchChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AppTheme.colors.accent else AppTheme.colors.surfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
