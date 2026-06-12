package io.earlisreal.ejournal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.ui.screen.AnalysisScreen
import io.earlisreal.ejournal.ui.screen.CalendarScreen
import io.earlisreal.ejournal.ui.screen.DashboardScreen
import io.earlisreal.ejournal.ui.screen.ImportScreen
import io.earlisreal.ejournal.ui.screen.SettingsScreen
import io.earlisreal.ejournal.ui.screen.TradeLogsScreen
import io.earlisreal.ejournal.ui.shell.AppShell
import io.earlisreal.ejournal.ui.shell.Destination

@Composable
fun App(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    credentialsRepository: CredentialsRepository,
    parsers: List<TransactionParser>,
    alpacaProvider: AlpacaProvider,
    marketDataService: MarketDataService,
) {
    // Startup reconciliation: heals any gaps left by failed or skipped fetches.
    LaunchedEffect(Unit) { marketDataService.requestSync() }

    AppShell(
        portfolioRepository = portfolioRepository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
    ) { destination, filter, nav ->
        when (destination) {
            Destination.DASHBOARD -> DashboardScreen(
                transactionRepository = transactionRepository,
                filter = filter,
            )
            Destination.TRADE_LOGS -> TradeLogsScreen(
                transactionRepository = transactionRepository,
                filter = filter,
            )
            Destination.IMPORT -> ImportScreen(
                transactionRepository = transactionRepository,
                parsers = parsers,
                filter = filter,
                onImportSuccess = { marketDataService.requestSync() },
                marketDataService = marketDataService,
            )
            Destination.CALENDAR -> CalendarScreen(
                transactionRepository = transactionRepository,
                filter = filter,
                onAnalyze = nav.onAnalyze,
            )
            Destination.ANALYSIS -> AnalysisScreen(
                position = nav.selectedAnalysis,
                symbol = filter.portfolio?.market?.symbol ?: "$",
            )
            Destination.SETTINGS -> SettingsScreen(
                themeMode = nav.themeMode,
                onThemeChange = nav.onThemeChange,
                credentialsRepository = credentialsRepository,
                alpacaProvider = alpacaProvider,
                marketDataService = marketDataService,
            )
        }
    }
}
