package io.earlisreal.ejournal.testutil

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroFetchResult
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

fun tx(externalId: String?, symbol: String = "AAPL"): Transaction = Transaction(
    id = 0,
    portfolioId = 1,
    symbol = symbol,
    datetime = LocalDateTime(2026, 6, 1, 9, 30),
    action = Action.BUY,
    price = 100.0,
    shares = 10.0,
    fees = 1.0,
    externalId = externalId,
)

class FakeTradeZeroClient(
    var result: TradeZeroFetchResult = TradeZeroFetchResult.Success(emptyList()),
    private val log: MutableList<String>? = null,
    private val connection: ConnectionResult = ConnectionResult.Connected,
) : TradeZeroClient {
    var fetchCount = 0
        private set
    var lastPortfolioId: Long? = null
        private set
    var lastFrom: LocalDate? = null
        private set
    var lastTo: LocalDate? = null
        private set

    override suspend fun testConnection(): ConnectionResult = connection

    override suspend fun fetchOrders(portfolioId: Long, from: LocalDate, to: LocalDate): TradeZeroFetchResult {
        fetchCount++
        lastPortfolioId = portfolioId
        lastFrom = from
        lastTo = to
        log?.add("tz")
        return result
    }
}

class FakeTransactionRepository(
    private val duplicateExternalIds: Set<String> = emptySet(),
) : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private var nextId = 1L

    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> = emptyList()
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime): List<Transaction> = emptyList()

    override suspend fun insert(transaction: Transaction): Long? {
        if (transaction.externalId != null && transaction.externalId in duplicateExternalIds) return null
        inserted += transaction
        return nextId++
    }

    override suspend fun delete(id: Long) {}
    override suspend fun countByPortfolio(portfolioId: Long): Long = 0
    override suspend fun deleteByPortfolio(portfolioId: Long) {}
}

class FakeSettingsRepository(
    var autoSync: Boolean = true,
    filterPrefs: FilterPrefs? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) : SettingsRepository {
    private var storedFilterPrefs: FilterPrefs? = filterPrefs
    private var storedTheme: ThemeMode = themeMode
    override fun getThemeMode(): ThemeMode = storedTheme
    override fun setThemeMode(mode: ThemeMode) { storedTheme = mode }
    override fun getFilterPrefs(): FilterPrefs? = storedFilterPrefs
    override fun setFilterPrefs(prefs: FilterPrefs) { storedFilterPrefs = prefs }
    override fun getAutoSyncTradeZeroOnStartup(): Boolean = autoSync
    override fun setAutoSyncTradeZeroOnStartup(enabled: Boolean) { autoSync = enabled }
}

class FakeCredentialsRepository(
    var tradeZero: TradeZeroCredentials? = null,
    private var alpaca: AlpacaCredentials? = null,
) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = alpaca
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { alpaca = credentials }
    override fun getTradeZeroCredentials(): TradeZeroCredentials? = tradeZero
    override fun setTradeZeroCredentials(credentials: TradeZeroCredentials) { tradeZero = credentials }
}

class FakePortfolioRepository(
    private val portfolios: List<Portfolio> = emptyList(),
) : PortfolioRepository {
    override suspend fun getAll(): List<Portfolio> = portfolios
    override suspend fun getById(id: Long): Portfolio? = portfolios.firstOrNull { it.id == id }
    override suspend fun insert(name: String, market: Market): Long = 0
    override suspend fun update(id: Long, name: String, market: Market) {}
    override suspend fun delete(id: Long) {}
}
