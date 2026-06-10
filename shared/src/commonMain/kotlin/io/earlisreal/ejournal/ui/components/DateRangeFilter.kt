package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.ui.theme.AppTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val PRESET_LABELS = listOf(
    DateRangePreset.THIS_WEEK to "Week",
    DateRangePreset.THIS_MONTH to "Month",
    DateRangePreset.THIS_QUARTER to "Quarter",
    DateRangePreset.YTD to "YTD",
    DateRangePreset.LAST_YEAR to "Last Yr",
    DateRangePreset.ALL_TIME to "All Time",
)

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilter(
    preset: DateRangePreset,
    customRange: DateRange?,
    onChange: (DateRangePreset, DateRange?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PRESET_LABELS.forEach { (p, label) ->
            Chip(label = label, active = preset == p) { onChange(p, null) }
        }
        Chip(label = "Custom…", active = preset == DateRangePreset.CUSTOM) { showPicker = true }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customRange?.from?.toEpochMillis(),
            initialSelectedEndDateMillis = customRange?.to?.toEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val from = state.selectedStartDateMillis?.toLocalDate()
                    val to = state.selectedEndDateMillis?.toLocalDate()
                    if (from != null && to != null) onChange(DateRangePreset.CUSTOM, DateRange(from, to))
                    showPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state)
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val accent = AppTheme.colors.accent
    Text(
        text = label,
        color = if (active) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clickable { onClick() }
            .then(
                if (active) Modifier.drawBehind {
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = accent,
                        start = Offset(0f, size.height - stroke / 2),
                        end = Offset(size.width, size.height - stroke / 2),
                        strokeWidth = stroke,
                    )
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
