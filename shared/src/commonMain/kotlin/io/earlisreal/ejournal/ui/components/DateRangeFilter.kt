package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.resolveRange
import io.earlisreal.ejournal.ui.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

private val PRESET_LABELS = listOf(
    DateRangePreset.THIS_WEEK to "Week",
    DateRangePreset.THIS_MONTH to "Month",
    DateRangePreset.THIS_QUARTER to "Quarter",
    DateRangePreset.YTD to "YTD",
    DateRangePreset.LAST_YEAR to "Last Year",
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) showPicker = false }

    val label = PRESET_LABELS.firstOrNull { it.first == preset }?.second
        ?: customRange?.let { "${it.from} – ${it.to}" }
        ?: "Custom dates"
    Pill(
        text = "▾ $label",
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "Choose date range") {
            showPicker = true
        },
    )

    if (showPicker && enabled) {
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
        val initialRange = remember { resolveRange(preset, today, customRange) }
        var pendingPreset by remember { mutableStateOf(preset) }
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialRange.from?.toEpochMillis(),
            initialSelectedEndDateMillis = initialRange.to?.toEpochMillis(),
        )
        val selectedRange = DateRange(
            state.selectedStartDateMillis?.toLocalDate(),
            state.selectedEndDateMillis?.toLocalDate(),
        )
        val selectedPreset = if (pendingPreset != DateRangePreset.CUSTOM && selectedRange == resolveRange(pendingPreset, today)) {
            pendingPreset
        } else DateRangePreset.CUSTOM
        val canApply = selectedPreset == DateRangePreset.ALL_TIME || (selectedRange.from != null && selectedRange.to != null)
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = AppTheme.colors.surface) {
                Column(Modifier.width(560.dp).heightIn(max = 680.dp)) {
                    DateRangePicker(
                        state = state,
                        modifier = Modifier.weight(1f),
                        colors = DatePickerDefaults.colors(
                            containerColor = AppTheme.colors.surface,
                            dayInSelectionRangeContainerColor = AppTheme.colors.accent.copy(alpha = 0.15f),
                            dayInSelectionRangeContentColor = AppTheme.colors.textPrimary,
                        ),
                        title = {
                            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                                Text("Date range", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PRESET_LABELS.forEach { (p, label) ->
                                        FilterChip(
                                            selected = selectedPreset == p,
                                            onClick = {
                                                val range = resolveRange(p, today)
                                                pendingPreset = p
                                                state.setSelection(range.from?.toEpochMillis(), range.to?.toEpochMillis())
                                                state.displayedMonthMillis = (range.from ?: today).toEpochMillis()
                                            },
                                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AppTheme.colors.accent.copy(alpha = 0.15f),
                                                selectedLabelColor = AppTheme.colors.accent,
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        AppTextButton("Cancel", onClick = { showPicker = false })
                        AppPrimaryButton("Apply", enabled = enabled && canApply, onClick = {
                            onChange(selectedPreset, selectedRange.takeIf { selectedPreset == DateRangePreset.CUSTOM })
                            showPicker = false
                        })
                    }
                }
            }
        }
    }
}
