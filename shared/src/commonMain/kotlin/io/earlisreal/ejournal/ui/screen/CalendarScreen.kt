package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.AppTextButton
import io.earlisreal.ejournal.ui.components.DayDetailPanel
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.MonthGrid
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.monthName
import io.earlisreal.ejournal.ui.components.signedMoney
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.CalendarViewModel
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun CalendarScreen(
    positionTags: PositionTagService,
    filter: FilterState,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val vm = viewModel { CalendarViewModel(positionTags, today.year, today.monthNumber) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter.portfolio, filter.segment, filter.selectedTagIds, filter.tagMatch) {
        vm.load(filter.portfolio?.id, filter.segment, filter.selectedTagIds, filter.tagMatch)
    }

    ScreenScaffold(title = "Calendar") {
        if (filter.portfolio == null) {
            EmptyState(title = "No portfolio selected", subtitle = "Import transactions to get started.")
        } else {
            val symbol = filter.portfolio.market.symbol
            // Arrow keys step the calendar month (left/up = previous, right/down = next). Focus is
            // requested on entry so the keys work without a click; onKeyEvent (not preview) lets
            // focused day cells keep their own handling and lets unhandled arrows bubble up here
            // even after a day is selected.
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(
                Modifier.fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft, Key.DirectionUp -> { vm.previousMonth(); true }
                                Key.DirectionRight, Key.DirectionDown -> { vm.nextMonth(); true }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AppTextButton(text = "◀", onClick = { vm.previousMonth() }, enabled = state.canGoPrevious)

                    // Year dropdown
                    var yearMenuOpen by remember { mutableStateOf(false) }
                    val yearsToShow = if (state.availableYears.isEmpty()) listOf(state.year) else state.availableYears
                    Box {
                        Text(
                            "${state.year}",
                            color = AppTheme.colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { yearMenuOpen = true },
                        )
                        DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                            yearsToShow.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text("$year") },
                                    onClick = { vm.jumpToMonth(year, state.month); yearMenuOpen = false },
                                )
                            }
                        }
                    }

                    // Month dropdown
                    var monthMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            monthName(state.month),
                            color = AppTheme.colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { monthMenuOpen = true },
                        )
                        DropdownMenu(expanded = monthMenuOpen, onDismissRequest = { monthMenuOpen = false }) {
                            (1..12).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(monthName(m)) },
                                    onClick = { vm.jumpToMonth(state.year, m); monthMenuOpen = false },
                                )
                            }
                        }
                    }

                    AppTextButton(text = "▶", onClick = { vm.nextMonth() }, enabled = state.canGoNext)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "MONTH P&L",
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            signedMoney(state.monthTotal, symbol),
                            color = if (state.monthTotal >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss,
                            style = NumberTextStyle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val dayPositions = state.selectedDate?.let { state.positionsByDay[it] } ?: emptyList()
                    if (maxWidth >= 760.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            MonthGrid(
                                grid = state.grid,
                                summaries = state.summaries,
                                today = today,
                                selectedDate = state.selectedDate,
                                onSelectDay = vm::selectDay,
                                symbol = symbol,
                                modifier = Modifier.weight(1f),
                            )
                            DayDetailPanel(
                                date = state.selectedDate,
                                positions = dayPositions,
                                onAnalyze = onAnalyze,
                                symbol = symbol,
                                modifier = Modifier.width(300.dp).fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            MonthGrid(
                                grid = state.grid,
                                summaries = state.summaries,
                                today = today,
                                selectedDate = state.selectedDate,
                                onSelectDay = vm::selectDay,
                                symbol = symbol,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DayDetailPanel(
                                date = state.selectedDate,
                                positions = dayPositions,
                                onAnalyze = onAnalyze,
                                symbol = symbol,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
