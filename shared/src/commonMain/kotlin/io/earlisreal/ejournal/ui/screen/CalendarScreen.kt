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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TransactionRepository
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
    transactionRepository: TransactionRepository,
    filter: FilterState,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val vm = viewModel { CalendarViewModel(transactionRepository, today.year, today.monthNumber) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter.portfolio, filter.segment) {
        vm.load(filter.portfolio?.id, filter.segment)
    }

    ScreenScaffold(title = "Calendar") {
        if (filter.portfolio == null) {
            EmptyState(title = "No portfolio selected", subtitle = "Import transactions to get started.")
        } else {
            val symbol = filter.portfolio.market.symbol
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AppTextButton(text = "◀", onClick = { vm.previousMonth() })
                    Text(
                        "${monthName(state.month)} ${state.year}",
                        color = AppTheme.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppTextButton(text = "▶", onClick = { vm.nextMonth() })
                    Spacer(Modifier.weight(1f))
                    Text(
                        signedMoney(state.monthTotal, symbol),
                        color = if (state.monthTotal >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss,
                        style = NumberTextStyle,
                    )
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
