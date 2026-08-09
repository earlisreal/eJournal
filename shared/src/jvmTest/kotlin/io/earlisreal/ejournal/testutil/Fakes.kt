package io.earlisreal.ejournal.testutil

import io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioBrokerCredentials
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import io.earlisreal.ejournal.domain.tradezero.TradeZeroAccount
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroConnectionResult
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
    var result: TradeZeroFetchResult = TradeZeroFetchResult.Success(emptyList(), TradeZeroAccount("tz-account")),
    private val log: MutableList<String>? = null,
    private val connection: TradeZeroConnectionResult = TradeZeroConnectionResult.Connected(TradeZeroAccount("tz-account")),
) : TradeZeroClient {
    var fetchCount = 0
        private set
    var lastCredentials: TradeZeroBrokerCredentials? = null
        private set
    var lastPortfolioId: Long? = null
        private set
    var lastFrom: LocalDate? = null
        private set
    var lastTo: LocalDate? = null
        private set

    override suspend fun testConnection(credentials: TradeZeroBrokerCredentials): TradeZeroConnectionResult {
        lastCredentials = credentials
        return connection
    }

    override suspend fun fetchOrders(
        credentials: TradeZeroBrokerCredentials,
        portfolioId: Long,
        from: LocalDate,
        to: LocalDate,
    ): TradeZeroFetchResult {
        fetchCount++
        lastCredentials = credentials
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
    filterPrefs: FilterPrefs? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) : SettingsRepository {
    private var storedFilterPrefs: FilterPrefs? = filterPrefs
    private var storedTheme: ThemeMode = themeMode
    override fun getThemeMode(): ThemeMode = storedTheme
    override fun setThemeMode(mode: ThemeMode) { storedTheme = mode }
    override fun getFilterPrefs(): FilterPrefs? = storedFilterPrefs
    override fun setFilterPrefs(prefs: FilterPrefs) { storedFilterPrefs = prefs }
}

class FakePortfolioSettingsRepository : PortfolioSettingsRepository {
    private val store = mutableMapOf<Pair<Long, String>, String>()
    override suspend fun getString(portfolioId: Long, key: String): String? = store[portfolioId to key]
    override suspend fun putString(portfolioId: Long, key: String, value: String) { store[portfolioId to key] = value }
    override suspend fun getBoolean(portfolioId: Long, key: String, default: Boolean): Boolean =
        store[portfolioId to key]?.toBooleanStrictOrNull() ?: default
    override suspend fun putBoolean(portfolioId: Long, key: String, value: Boolean) { store[portfolioId to key] = value.toString() }
    override suspend fun clearNamespace(portfolioId: Long, namespace: String) {
        store.keys.removeAll { it.first == portfolioId && it.second.startsWith(namespace) }
    }
    override suspend fun clear(portfolioId: Long) { store.keys.removeAll { it.first == portfolioId } }
}

class FakeCredentialsRepository(
    private var alpaca: AlpacaMarketDataCredentials? = null,
    portfolioBrokers: Map<String, PortfolioBrokerCredentials> = emptyMap(),
) : CredentialsRepository {
    val portfolioCredentials = portfolioBrokers.toMutableMap()

    override fun getAlpacaMarketDataCredentials(): AlpacaMarketDataCredentials? = alpaca
    override fun setAlpacaMarketDataCredentials(credentials: AlpacaMarketDataCredentials) { alpaca = credentials }
    override fun getPortfolioBrokerCredentials(credentialRef: String): PortfolioBrokerCredentials? = portfolioCredentials[credentialRef]
    override fun setPortfolioBrokerCredentials(credentialRef: String, credentials: PortfolioBrokerCredentials) {
        portfolioCredentials[credentialRef] = credentials
    }
    override fun deletePortfolioBrokerCredentials(credentialRef: String) { portfolioCredentials.remove(credentialRef) }
}

class FakePortfolioRepository(
    initialPortfolios: List<Portfolio> = emptyList(),
) : PortfolioRepository {
    private val portfolios = initialPortfolios.toMutableList()
    var updateFailure: Throwable? = null
    override suspend fun getAll(): List<Portfolio> = portfolios.toList()
    override suspend fun getById(id: Long): Portfolio? = portfolios.firstOrNull { it.id == id }
    override suspend fun insert(name: String, market: Market, broker: Broker?, alpacaEnvironment: AlpacaEnvironment?): Portfolio {
        val id = (portfolios.maxOfOrNull { it.id } ?: 0L) + 1
        val portfolio = Portfolio(id, name, market, broker, "fake-ref-$id", alpacaEnvironment)
        portfolios += portfolio
        return portfolio
    }
    override suspend fun update(id: Long, name: String, market: Market, broker: Broker?, alpacaEnvironment: AlpacaEnvironment?) {
        updateFailure?.let { throw it }
        val index = portfolios.indexOfFirst { it.id == id }
        if (index >= 0) portfolios[index] = portfolios[index].copy(name = name, market = market, broker = broker, alpacaEnvironment = alpacaEnvironment)
    }
    override suspend fun delete(id: Long) { portfolios.removeAll { it.id == id } }
}
