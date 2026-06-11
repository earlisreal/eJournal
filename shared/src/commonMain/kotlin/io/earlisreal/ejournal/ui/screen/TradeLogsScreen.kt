package io.earlisreal.ejournal.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.TradeLogsTable
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.viewmodel.TradeLogsViewModel

@Composable
fun TradeLogsScreen(
    transactionRepository: TransactionRepository,
    filter: FilterState,
) {
    val vm = viewModel { TradeLogsViewModel(transactionRepository) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter) {
        vm.load(filter.portfolio?.id, filter.dateRange, filter.segment)
    }

    ScreenScaffold(title = "Trade Logs") {
        when {
            filter.portfolio == null -> EmptyState(
                title = "No portfolio selected",
                subtitle = "Import transactions to get started.",
            )
            state.loading -> LoadingIndicator()
            state.positions.isEmpty() -> EmptyState(
                title = "No closed positions in this range",
                subtitle = "Adjust the date range or segment in the top bar.",
            )
            else -> TradeLogsTable(
                positions = state.positions,
                sortColumn = state.sortColumn,
                sortDirection = state.sortDirection,
                onSort = vm::sortBy,
                symbol = filter.portfolio?.market?.symbol ?: "$",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
