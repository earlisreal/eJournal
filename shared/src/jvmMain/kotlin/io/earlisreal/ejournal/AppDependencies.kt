package io.earlisreal.ejournal

import io.earlisreal.ejournal.data.JsonCredentialsRepository
import io.earlisreal.ejournal.data.PreferencesSettingsRepository
import io.earlisreal.ejournal.data.SqlDelightMarketDataRepository
import io.earlisreal.ejournal.data.SqlDelightPortfolioRepository
import io.earlisreal.ejournal.data.SqlDelightPortfolioSettingsRepository
import io.earlisreal.ejournal.data.SqlDelightTransactionRepository
import io.earlisreal.ejournal.data.database.JvmDatabaseFactory
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.marketdata.YahooFinanceProvider
import io.earlisreal.ejournal.domain.marketdata.toBackgroundTask
import io.earlisreal.ejournal.domain.ClosedPositionService
import io.earlisreal.ejournal.domain.StartupSyncCoordinator
import io.earlisreal.ejournal.domain.parser.GenericCsvParser
import io.earlisreal.ejournal.domain.parser.MoomooCsvParser
import io.earlisreal.ejournal.domain.parser.TradeZeroCsvParser
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClientImpl
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppDependencies {
    private val db = JvmDatabaseFactory.create()
    private val httpClient = HttpClient(CIO)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val portfolioRepository: PortfolioRepository = SqlDelightPortfolioRepository(db)
    val transactionRepository: TransactionRepository = SqlDelightTransactionRepository(db)
    val settingsRepository: SettingsRepository = PreferencesSettingsRepository()
    val portfolioSettingsRepository: PortfolioSettingsRepository = SqlDelightPortfolioSettingsRepository(db)
    val credentialsRepository: CredentialsRepository =
        JsonCredentialsRepository(File(System.getProperty("user.home"), ".ejournal").toPath())
    val marketDataRepository: MarketDataRepository = SqlDelightMarketDataRepository(db)
    // moomoo & TradeZero first so auto-detect routes to them; Generic last (manual-only fallback).
    val parsers: List<TransactionParser> = listOf(MoomooCsvParser(), TradeZeroCsvParser(), GenericCsvParser())

    val closedPositionService = ClosedPositionService(transactionRepository)

    val alpacaProvider = AlpacaProvider(httpClient, credentialsRepository)
    val tradeZeroClient: TradeZeroClient = TradeZeroClientImpl(httpClient, credentialsRepository)
    val marketDataService = MarketDataService(
        portfolioRepository = portfolioRepository,
        closedPositions = closedPositionService,
        marketDataRepository = marketDataRepository,
        yahooProvider = YahooFinanceProvider(httpClient),
        alpacaProvider = alpacaProvider,
        credentialsRepository = credentialsRepository,
        scope = backgroundScope,
    )

    val backgroundTaskTracker = BackgroundTaskTracker()

    val tradeZeroSyncService =
        TradeZeroSyncService(tradeZeroClient, transactionRepository, backgroundTaskTracker, portfolioSettingsRepository)

    val startupSyncCoordinator = StartupSyncCoordinator(
        settingsRepository = settingsRepository,
        credentialsRepository = credentialsRepository,
        portfolioRepository = portfolioRepository,
        portfolioSettings = portfolioSettingsRepository,
        tradeZeroSyncService = tradeZeroSyncService,
        requestMarketDataSync = { marketDataService.requestSync() },
    )

    init {
        // Mirror market-data sync into the global status bar without coupling the service to the UI.
        marketDataService.status
            .onEach { status ->
                status.toBackgroundTask(retry = { marketDataService.requestSync() })
                    ?.let(backgroundTaskTracker::update)
            }
            .launchIn(backgroundScope)
    }
}
