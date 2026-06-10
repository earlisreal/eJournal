package io.earlisreal.ejournal

import androidx.compose.runtime.Composable
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.screen.ImportScreen
import io.earlisreal.ejournal.ui.screen.TradeLogsScreen
import io.earlisreal.ejournal.ui.shell.AppShell
import io.earlisreal.ejournal.ui.shell.Destination

@Composable
fun App(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    parsers: List<TransactionParser>,
) {
    AppShell(
        portfolioRepository = portfolioRepository,
        settingsRepository = settingsRepository,
    ) { destination, _ ->
        when (destination) {
            Destination.IMPORT -> ImportScreen(
                portfolioRepository = portfolioRepository,
                transactionRepository = transactionRepository,
                parsers = parsers,
                onImportSuccess = { /* cross-screen navigation wired in a later phase; no-op for now */ },
            )
            Destination.TRADE_LOGS -> TradeLogsScreen()
            else -> EmptyState(
                title = "${destination.label} — coming soon",
                subtitle = "This screen arrives in a later phase.",
            )
        }
    }
}
