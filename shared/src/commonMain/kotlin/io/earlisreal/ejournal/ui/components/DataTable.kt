package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Generic table. `columns` are header labels; `cells` provides each row's cell strings.
 * `weights` (optional) sets per-column flex; defaults to equal.
 */
@Composable
fun <T> DataTable(
    columns: List<String>,
    rows: List<T>,
    cells: (T) -> List<String>,
    modifier: Modifier = Modifier,
    weights: List<Float> = columns.map { 1f },
) {
    Column(
        modifier = modifier.border(1.dp, AppTheme.colors.border, CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            columns.forEachIndexed { i, col ->
                Text(
                    col,
                    modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        LazyColumn {
            itemsIndexed(rows) { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    cells(row).forEachIndexed { i, cell ->
                        Text(
                            cell,
                            modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                            color = AppTheme.colors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}
