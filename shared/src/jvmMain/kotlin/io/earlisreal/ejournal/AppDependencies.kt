package io.earlisreal.ejournal

import io.earlisreal.ejournal.data.JsonCredentialsRepository
import io.earlisreal.ejournal.data.PreferencesSettingsRepository
import io.earlisreal.ejournal.data.SqlDelightMarketDataRepository
import io.earlisreal.ejournal.data.SqlDelightPortfolioRepository
import io.earlisreal.ejournal.data.SqlDelightTransactionRepository
import io.earlisreal.ejournal.data.database.JvmDatabaseFactory
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.marketdata.YahooFinanceProvider
import io.earlisreal.ejournal.domain.parser.GenericCsvParser
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClientImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppDependencies {
    private val db = JvmDatabaseFactory.create()
    private val httpClient = HttpClient(CIO)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val portfolioRepository: PortfolioRepository = SqlDelightPortfolioRepository(db)
    val transactionRepository: TransactionRepository = SqlDelightTransactionRepository(db)
    val settingsRepository: SettingsRepository = PreferencesSettingsRepository()
    val credentialsRepository: CredentialsRepository =
        JsonCredentialsRepository(File(System.getProperty("user.home"), ".ejournal").toPath())
    val marketDataRepository: MarketDataRepository = SqlDelightMarketDataRepository(db)
    val parsers: List<TransactionParser> = listOf(GenericCsvParser())

    val alpacaProvider = AlpacaProvider(httpClient, credentialsRepository)
    val tradeZeroClient: TradeZeroClient = TradeZeroClientImpl(httpClient, credentialsRepository)
    val marketDataService = MarketDataService(
        portfolioRepository = portfolioRepository,
        transactionRepository = transactionRepository,
        marketDataRepository = marketDataRepository,
        yahooProvider = YahooFinanceProvider(httpClient),
        alpacaProvider = alpacaProvider,
        credentialsRepository = credentialsRepository,
        scope = backgroundScope,
    )
}
