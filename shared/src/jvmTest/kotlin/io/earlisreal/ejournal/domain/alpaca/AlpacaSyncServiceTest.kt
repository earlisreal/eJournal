package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeAlpacaClient(
    var result: AlpacaFetchResult,
    var connection: AlpacaConnectionResult = AlpacaConnectionResult.Connected(
        AlpacaAccount("acct-1", "PA1234", "ACTIVE"),
        AlpacaEnvironment.PAPER,
    ),
) : AlpacaBrokerClient {
    var calls = 0
        private set
    var lastAfter: Instant? = null
        private set
    var lastUntil: Instant? = null
        private set
    var fetchGate: CompletableDeferred<Unit>? = null
    var fetchException: Throwable? = null

    override suspend fun testConnection(): AlpacaConnectionResult = connection

    override suspend fun fetchFills(portfolioId: Long, after: Instant?, until: Instant?): AlpacaFetchResult {
        calls++
        lastAfter = after
        lastUntil = until
        fetchGate?.await()
        fetchException?.let { throw it }
        return result
    }
}

private class DeduplicatingTransactionRepository(
    private val failOnInsert: Boolean = false,
    initialTransactions: List<Transaction> = emptyList(),
) : TransactionRepository {
    val inserted = initialTransactions.toMutableList()
    private val ids = initialTransactions.mapNotNull { it.externalId }.toMutableSet()

    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> = inserted.filter { it.portfolioId == portfolioId }
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime): List<Transaction> =
        getByPortfolio(portfolioId)
    override suspend fun insert(transaction: Transaction): Long? {
        if (failOnInsert) error("database unavailable")
        val id = transaction.externalId
        if (id != null && !ids.add(id)) return null
        inserted += transaction
        return inserted.size.toLong()
    }
    override suspend fun delete(id: Long) {}
    override suspend fun countByPortfolio(portfolioId: Long): Long = inserted.size.toLong()
    override suspend fun deleteByPortfolio(portfolioId: Long) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlpacaSyncServiceTest {

    private val now = Instant.parse("2026-06-20T12:00:00Z")
    private val account = AlpacaAccount("acct-1", "PA1234", "ACTIVE")

    private fun success(
        vararg ids: String,
        skippedOptions: Int = 0,
        skippedNonEquity: Int = 0,
        resultAccount: AlpacaAccount = account,
    ) =
        AlpacaFetchResult.Success(
            transactions = ids.map { id ->
                Transaction(
                    id = 0,
                    portfolioId = 1,
                    symbol = "AAPL",
                    datetime = LocalDateTime(2026, 6, 20, 9, 30),
                    action = Action.BUY,
                    price = 10.0,
                    shares = 1.0,
                    fees = 0.0,
                    externalId = id,
                )
            },
            account = resultAccount,
            detail = BrokerSyncDetail(
                skipped = mapOf(
                    "options" to skippedOptions,
                    "non-US-equity fills" to skippedNonEquity,
                ).filterValues { it > 0 },
            ),
        )

    private fun service(
        client: FakeAlpacaClient,
        repo: TransactionRepository = DeduplicatingTransactionRepository(),
        settings: FakePortfolioSettingsRepository = FakePortfolioSettingsRepository(),
        tracker: BackgroundTaskTracker = BackgroundTaskTracker(),
        credentials: FakeCredentialsRepository = FakeCredentialsRepository(
            alpaca = AlpacaCredentials("key", "secret", AlpacaEnvironment.PAPER),
        ),
        portfolioRepository: FakePortfolioRepository = FakePortfolioRepository(
            listOf(Portfolio(1L, "Trading", Market.US_STOCKS)),
        ),
    ): Pair<AlpacaSyncService, FakePortfolioSettingsRepository> {
        val service = AlpacaSyncService(
            client = client,
            transactionRepository = repo,
            tracker = tracker,
            portfolioRepository = portfolioRepository,
            portfolioSettings = settings,
            credentialsRepository = credentials,
            now = { now },
        )
        return service to settings
    }

    @Test
    fun `first sync imports all fills and advances cursor after success`() = runTest {
        val client = FakeAlpacaClient(success("a", "b"))
        val (service, settings) = service(client)

        val result = service.syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.Imported(2), result)
        assertEquals(1, client.calls)
        assertEquals(now, client.lastUntil)
        assertEquals(null, client.lastAfter)
        assertEquals(now.toString(), settings.getString(1L, AlpacaSettings.LAST_SYNCED_AT))
        assertEquals("paper:acct-1", settings.getString(1L, AlpacaSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun `overlap is applied and duplicate fills insert only once`() = runTest {
        val repo = DeduplicatingTransactionRepository()
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_SOURCE, "paper:acct-1")
        val client = FakeAlpacaClient(success("a", "a", "b"))
        val tracker = BackgroundTaskTracker()
        val (service, _) = service(client, repo, settings, tracker)

        val result = service.syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.Imported(2), result)
        assertEquals(Instant.parse("2026-06-14T12:00:00Z"), client.lastAfter)
        assertEquals(2, repo.inserted.size)
        assertEquals(TaskState.Success, tracker.tasks.value.single().state)
    }

    @Test
    fun `client errors do not advance cursor`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_SOURCE, "paper:acct-1")
        val client = FakeAlpacaClient(AlpacaFetchResult.NetworkError("timeout"))
        val (service, _) = service(client, settings = settings)

        val result = service.syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.NetworkError("timeout"), result)
        assertEquals("2026-06-17T12:00:00Z", settings.getString(1L, AlpacaSettings.LAST_SYNCED_AT))
    }

    @Test
    fun `insert failures propagate and do not advance cursor`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        val client = FakeAlpacaClient(success("a"))
        val tracker = BackgroundTaskTracker()
        val (service, _) = service(client, DeduplicatingTransactionRepository(failOnInsert = true), settings, tracker)

        assertFailsWith<IllegalStateException> { service.syncIncremental(1L) }
        assertEquals(null, settings.getString(1L, AlpacaSettings.LAST_SYNCED_AT))
        assertIs<TaskState.Failed>(tracker.tasks.value.single().state)
    }

    @Test
    fun `skipped counts are carried into generic outcome and only stocks are supported`() = runTest {
        val client = FakeAlpacaClient(success("a", skippedOptions = 3, skippedNonEquity = 2))
        val (service, _) = service(client)

        val result = assertIs<BrokerSyncOutcome.Imported>(service.syncIncremental(1L))

        assertEquals(1, result.inserted)
        assertEquals(3, result.detail.skipped["options"])
        assertEquals(2, result.detail.skipped["non-US-equity fills"])
        assertTrue(service.supportsMarket(Market.US_STOCKS))
        assertTrue(!service.supportsMarket(Market.CRYPTO))
    }

    @Test
    fun `changing environment resets cursor and replaces source`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_SOURCE, "paper:acct-1")
        val credentials = FakeCredentialsRepository(
            alpaca = AlpacaCredentials("key", "secret", AlpacaEnvironment.LIVE),
        )
        val client = FakeAlpacaClient(
            success("live-fill"),
            AlpacaConnectionResult.Connected(account, AlpacaEnvironment.LIVE),
        )
        val (service, _) = service(client, settings = settings, credentials = credentials)

        service.syncIncremental(1L)

        assertEquals(null, client.lastAfter)
        assertEquals("live:acct-1", settings.getString(1L, AlpacaSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun `changing live account resets cursor`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_SOURCE, "live:acct-a")
        val accountB = AlpacaAccount("acct-b", "PA5678", "ACTIVE")
        val client = FakeAlpacaClient(
            success("account-b-fill", resultAccount = accountB),
            AlpacaConnectionResult.Connected(
                accountB,
                AlpacaEnvironment.LIVE,
            ),
        )
        val credentials = FakeCredentialsRepository(
            alpaca = AlpacaCredentials("key", "secret", AlpacaEnvironment.LIVE),
        )
        val (service, _) = service(client, settings = settings, credentials = credentials)

        service.syncIncremental(1L)

        assertEquals(null, client.lastAfter)
        assertEquals("live:acct-b", settings.getString(1L, AlpacaSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun `same account keeps the three day overlap`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_SOURCE, "paper:acct-1")
        val client = FakeAlpacaClient(success("same-account-fill"))
        val (service, _) = service(client, settings = settings)

        service.syncIncremental(1L)

        assertEquals(Instant.parse("2026-06-14T12:00:00Z"), client.lastAfter)
    }

    @Test
    fun `blocks an account already bound to another portfolio`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(2L, AlpacaSettings.LAST_SYNCED_SOURCE, "paper:acct-1")
        val client = FakeAlpacaClient(success("never-fetched"))
        val portfolios = FakePortfolioRepository(
            listOf(
                Portfolio(1L, "Trading", Market.US_STOCKS),
                Portfolio(2L, "Long Term", Market.US_STOCKS),
            ),
        )
        val (service, _) = service(client, settings = settings, portfolioRepository = portfolios)

        val result = service.syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.AccountAlreadyBound("Long Term"), result)
        assertEquals(0, client.calls)
    }

    @Test
    fun `blocks legacy account data in another portfolio even without binding metadata`() = runTest {
        val existing = Transaction(
            id = 1L,
            portfolioId = 2L,
            symbol = "AAPL",
            datetime = LocalDateTime(2026, 6, 1, 9, 30),
            action = Action.BUY,
            price = 10.0,
            shares = 1.0,
            fees = 0.0,
            externalId = "alpaca:paper:acct-1:old-fill",
        )
        val repo = DeduplicatingTransactionRepository(initialTransactions = listOf(existing))
        val client = FakeAlpacaClient(success("never-fetched"))
        val portfolios = FakePortfolioRepository(
            listOf(
                Portfolio(1L, "Trading", Market.US_STOCKS),
                Portfolio(2L, "Long Term", Market.US_STOCKS),
            ),
        )
        val (service, _) = service(client, repo, portfolioRepository = portfolios)

        val result = service.syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.AccountAlreadyBound("Long Term"), result)
        assertEquals(0, client.calls)
    }

    @Test
    fun `serializes concurrent syncs before binding the account`() = runTest {
        val settings = FakePortfolioSettingsRepository()
        val client = FakeAlpacaClient(success("concurrent-fill"))
        client.fetchGate = CompletableDeferred()
        val portfolios = FakePortfolioRepository(
            listOf(
                Portfolio(1L, "Trading", Market.US_STOCKS),
                Portfolio(2L, "Long Term", Market.US_STOCKS),
            ),
        )
        val (service, _) = service(client, settings = settings, portfolioRepository = portfolios)

        val first = async { service.syncIncremental(1L) }
        runCurrent()
        val second = async { service.syncIncremental(2L) }
        runCurrent()

        client.fetchGate?.complete(Unit)

        assertEquals(BrokerSyncOutcome.Imported(1), first.await())
        assertEquals(BrokerSyncOutcome.AccountAlreadyBound("Trading"), second.await())
    }

    @Test
    fun `cancellation propagates and clears the background task`() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeAlpacaClient(success("cancelled"))
        client.fetchException = CancellationException("cancelled")
        val (service, _) = service(client, tracker = tracker)

        assertFailsWith<CancellationException> { service.syncIncremental(1L) }
        assertTrue(tracker.tasks.value.none { it.id == AlpacaSyncService.TASK_ID })
    }
}
