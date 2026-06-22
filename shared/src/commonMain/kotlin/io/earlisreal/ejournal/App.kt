package io.earlisreal.ejournal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.StartupSyncCoordinator
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import io.earlisreal.ejournal.ui.screen.AnalysisScreen
import io.earlisreal.ejournal.ui.screen.CalendarScreen
import io.earlisreal.ejournal.ui.screen.DashboardScreen
import io.earlisreal.ejournal.ui.screen.ImportScreen
import io.earlisreal.ejournal.ui.screen.SettingsScreen
import io.earlisreal.ejournal.ui.screen.TradeLogsScreen
import io.earlisreal.ejournal.ui.shell.AppShell
import io.earlisreal.ejournal.ui.shell.Destination
import io.earlisreal.ejournal.ui.theme.resolveDarkMode

@Composable
fun App(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    credentialsRepository: CredentialsRepository,
    marketDataRepository: MarketDataRepository,
    parsers: List<TransactionParser>,
    alpacaProvider: AlpacaProvider,
    marketDataService: MarketDataService,
    tradeZeroClient: TradeZeroClient,
    backgroundTaskTracker: BackgroundTaskTracker,
    tradeZeroSyncService: TradeZeroSyncService,
    startupSyncCoordinator: StartupSyncCoordinator,
    startDestination: Destination,
    initialPortfolios: List<Portfolio>,
) {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { startupSyncCoordinator.run() } }

    val systemDark = isSystemInDarkTheme()

    AppShell(
        portfolioRepository = portfolioRepository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
        backgroundTaskTracker = backgroundTaskTracker,
        initialDestination = startDestination,
        initialPortfolios = initialPortfolios,
    ) { destination, filter, nav ->
        val isDarkTheme = resolveDarkMode(nav.themeMode, systemDark)
        when (destination) {
            Destination.DASHBOARD -> DashboardScreen(
                transactionRepository = transactionRepository,
                filter = filter,
                onAnalyze = nav.onAnalyze,
                onViewAllTrades = { nav.onNavigate(Destination.TRADE_LOGS) },
            )
            Destination.TRADE_LOGS -> TradeLogsScreen(
                transactionRepository = transactionRepository,
                filter = filter,
                onAnalyze = nav.onAnalyze,
            )
            Destination.IMPORT -> ImportScreen(
                transactionRepository = transactionRepository,
                parsers = parsers,
                filter = filter,
                onImportSuccess = { marketDataService.requestSync() },
                marketDataService = marketDataService,
                tradeZeroSyncService = tradeZeroSyncService,
                tradeZeroConfigured = credentialsRepository.getTradeZeroCredentials() != null,
            )
            Destination.CALENDAR -> CalendarScreen(
                transactionRepository = transactionRepository,
                filter = filter,
                onAnalyze = nav.onAnalyze,
            )
            Destination.ANALYSIS -> AnalysisScreen(
                positions = nav.analysisPositions,
                initialIndex = nav.analysisIndex,
                marketDataRepository = marketDataRepository,
                isDarkTheme = isDarkTheme,
                symbol = filter.portfolio?.market?.symbol ?: "$",
                sourceDestination = nav.analysisSource,
                onBack = nav.onBackFromAnalysis,
            )
            Destination.SETTINGS -> SettingsScreen(
                themeMode = nav.themeMode,
                onThemeChange = nav.onThemeChange,
                credentialsRepository = credentialsRepository,
                alpacaProvider = alpacaProvider,
                marketDataService = marketDataService,
                tradeZeroClient = tradeZeroClient,
                settingsRepository = settingsRepository,
            )
        }
    }
}
