package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.defaultTagColors
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Create, rename, recolor, and delete tags. Deleting a tag also removes it from every position (the
 * repository cascades the assignments). [onChanged] fires after any mutation so callers can refresh.
 *
 * An in-window modal [Dialog] centered over the main window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerDialog(
    tagRepository: TagRepository,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(defaultTagColors.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() { tags = tagRepository.getAll() }
    LaunchedEffect(Unit) { refresh() }

    fun resetForm() {
        editingId = null; name = ""; color = defaultTagColors.first(); error = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = AppTheme.colors.surface) {
            Column(
                modifier = Modifier.width(460.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text("Tags", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                if (tags.isEmpty()) {
                    Text("No tags yet. Create one below.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    tags.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TagColorDot(t.color, size = 11)
                                Text(t.name, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                AppTextButton(text = "Edit", onClick = { editingId = t.id; name = t.name; color = t.color; error = null })
                                AppTextButton(text = "Delete", onClick = {
                                    scope.launch {
                                        tagRepository.delete(t.id)
                                        if (editingId == t.id) resetForm()
                                        refresh(); onChanged()
                                    }
                                })
                            }
                        }
                    }
                }

                HorizontalDivider(color = AppTheme.colors.border)

                Text(
                    if (editingId == null) "Add tag" else "Edit tag",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    defaultTagColors.forEach { hex ->
                        ColorSwatch(hex, selected = hex == color) { color = hex }
                    }
                }
                error?.let { Text(it, color = AppTheme.colors.loss, style = MaterialTheme.typography.labelSmall) }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppPrimaryButton(
                        text = if (editingId == null) "Add" else "Save",
                        enabled = name.isNotBlank(),
                        onClick = {
                            val id = editingId
                            val nm = name.trim()
                            scope.launch {
                                try {
                                    if (id == null) tagRepository.create(nm, color) else tagRepository.update(id, nm, color)
                                    resetForm(); refresh(); onChanged()
                                } catch (e: Exception) {
                                    error = "A tag named \"$nm\" already exists."
                                }
                            }
                        },
                    )
                    if (editingId != null) AppTextButton(text = "Cancel", onClick = { resetForm() })
                }

                AppTextButton(text = "Close", onClick = onDismiss, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

/** A selectable color swatch in the palette picker. */
@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(tagColor(hex))
            .border(2.dp, if (selected) AppTheme.colors.textPrimary else Color.Transparent, CircleShape)
            .clickable { onClick() },
    )
}
