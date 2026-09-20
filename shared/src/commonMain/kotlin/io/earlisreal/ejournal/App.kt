package io.earlisreal.ejournal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.PositionNoteService
import io.earlisreal.ejournal.domain.StartupSyncCoordinator
import io.earlisreal.ejournal.domain.CURRENT_NETWORK_DISCLOSURE_VERSION
import io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerClient
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.moomoo.MoomooClient
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.update.UpdateManager
import io.earlisreal.ejournal.ui.screen.AnalysisScreen
import io.earlisreal.ejournal.ui.screen.CalendarScreen
import io.earlisreal.ejournal.ui.screen.DashboardScreen
import io.earlisreal.ejournal.ui.screen.ImportScreen
import io.earlisreal.ejournal.ui.screen.ReportsScreen
import io.earlisreal.ejournal.ui.screen.SettingsScreen
import io.earlisreal.ejournal.ui.screen.TradeLogsScreen
import io.earlisreal.ejournal.ui.screen.NetworkDisclosureScreen
import io.earlisreal.ejournal.ui.shell.AppShell
import io.earlisreal.ejournal.ui.shell.Destination
import io.earlisreal.ejournal.ui.theme.resolveDarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun App(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    portfolioSettings: PortfolioSettingsRepository,
    credentialsRepository: CredentialsRepository,
    marketDataRepository: MarketDataRepository,
    parsers: List<TransactionParser>,
    alpacaProvider: AlpacaProvider,
    alpacaBrokerClient: AlpacaBrokerClient,
    marketDataService: MarketDataService,
    tradeZeroClient: TradeZeroClient,
    moomooClient: MoomooClient,
    backgroundTaskTracker: BackgroundTaskTracker,
    brokerSyncServices: List<BrokerSyncService>,
    startupSyncCoordinator: StartupSyncCoordinator,
    startDestination: Destination,
    initialPortfolios: List<Portfolio>,
    positionNotes: PositionNoteService,
    positionTags: PositionTagService,
    tagRepository: TagRepository,
    updateManager: UpdateManager? = null,
    onExit: () -> Unit = {},
) {
    var disclosureAccepted by remember {
        mutableStateOf(settingsRepository.getNetworkDisclosureVersion() == CURRENT_NETWORK_DISCLOSURE_VERSION)
    }
    var disclosureMarketDataEnabled by remember { mutableStateOf(settingsRepository.getOnlineMarketDataEnabled()) }
    var disclosureUpdateChecksEnabled by remember { mutableStateOf(settingsRepository.getAutomaticUpdateChecksEnabled()) }
    var startupChangedPortfolioIds by remember { mutableStateOf(emptySet<Long>()) }

    val systemDark = isSystemInDarkTheme()

    if (!disclosureAccepted) {
        NetworkDisclosureScreen(
            marketDataEnabled = disclosureMarketDataEnabled,
            updateChecksEnabled = disclosureUpdateChecksEnabled,
            onMarketDataChange = { disclosureMarketDataEnabled = it },
            onUpdateChecksChange = { disclosureUpdateChecksEnabled = it },
            onContinue = {
                settingsRepository.setOnlineMarketDataEnabled(disclosureMarketDataEnabled)
                settingsRepository.setAutomaticUpdateChecksEnabled(disclosureUpdateChecksEnabled)
                settingsRepository.setNetworkDisclosureVersion(CURRENT_NETWORK_DISCLOSURE_VERSION)
                disclosureAccepted = true
            },
            onExit = onExit,
        )
        return
    }

    LaunchedEffect(Unit) {
        startupChangedPortfolioIds = withContext(Dispatchers.IO) { startupSyncCoordinator.run() }
        updateManager?.requestAutomaticCheck()
    }

    AppShell(
        portfolioRepository = portfolioRepository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
        portfolioSettings = portfolioSettings,
        credentialsRepository = credentialsRepository,
        alpacaBrokerClient = alpacaBrokerClient,
        tradeZeroClient = tradeZeroClient,
        moomooClient = moomooClient,
        tagRepository = tagRepository,
        backgroundTaskTracker = backgroundTaskTracker,
        initialDestination = startDestination,
        initialPortfolios = initialPortfolios,
        updateManager = updateManager,
    ) { destination, filter, nav ->
        val isDarkTheme = resolveDarkMode(nav.themeMode, systemDark)
        when (destination) {
            Destination.DASHBOARD -> DashboardScreen(
                positionTags = positionTags,
                filter = filter,
                refreshOnStartup = filter.portfolio?.id?.let(startupChangedPortfolioIds::contains) == true,
                onAnalyze = nav.onAnalyze,
                onViewAllTrades = { nav.onNavigate(Destination.TRADE_LOGS) },
                onOpenReports = { nav.onNavigate(Destination.REPORTS) },
                onSelectTag = nav.onSelectTag,
            )
            Destination.TRADE_LOGS -> TradeLogsScreen(
                positionTags = positionTags,
                tagRepository = tagRepository,
                filter = filter,
                onAnalyze = nav.onAnalyze,
                onTagDeleted = nav.onTagDeleted,
            )
            Destination.IMPORT -> ImportScreen(
                transactionRepository = transactionRepository,
                parsers = parsers,
                portfolioSettings = portfolioSettings,
                filter = filter,
                onImportSuccess = { marketDataService.requestSync() },
                brokerSyncServices = brokerSyncServices,
            )
            Destination.CALENDAR -> CalendarScreen(
                positionTags = positionTags,
                filter = filter,
                onAnalyze = nav.onAnalyze,
            )
            Destination.REPORTS -> ReportsScreen(
                positionTags = positionTags,
                filter = filter,
                onSelectTag = nav.onSelectTag,
            )
            Destination.ANALYSIS -> AnalysisScreen(
                positions = nav.analysisPositions,
                initialIndex = nav.analysisIndex,
                marketDataRepository = marketDataRepository,
                positionNotes = positionNotes,
                positionTags = positionTags,
                tagRepository = tagRepository,
                portfolioId = nav.analysisPortfolioId,
                portfolioName = nav.analysisPortfolioName,
                isDarkTheme = isDarkTheme,
                // The analysis snapshot owns its portfolio context; the top-bar portfolio may
                // change while analysis remains open.
                symbol = nav.analysisPositions.firstOrNull()?.market?.symbol
                    ?: filter.portfolio?.market?.symbol ?: "$",
                sourceDestination = nav.analysisSource,
                onBack = nav.onBackFromAnalysis,
                onTagDeleted = nav.onTagDeleted,
            )
            Destination.SETTINGS -> SettingsScreen(
                themeMode = nav.themeMode,
                onThemeChange = nav.onThemeChange,
                credentialsRepository = credentialsRepository,
                alpacaProvider = alpacaProvider,
                marketDataService = marketDataService,
                settingsRepository = settingsRepository,
                updateManager = updateManager,
            )
        }
    }
}
