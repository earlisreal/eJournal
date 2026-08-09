package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeAlpacaClient(
    var result: AlpacaFetchResult,
) : AlpacaBrokerClient {
    var calls = 0
        private set
    var lastAfter: Instant? = null
        private set
    var lastUntil: Instant? = null
        private set

    override suspend fun testConnection(): AlpacaConnectionResult = AlpacaConnectionResult.InvalidCredentials

    override suspend fun fetchFills(portfolioId: Long, after: Instant?, until: Instant?): AlpacaFetchResult {
        calls++
        lastAfter = after
        lastUntil = until
        return result
    }
}

private class DeduplicatingTransactionRepository(
    private val failOnInsert: Boolean = false,
) : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private val ids = mutableSetOf<String>()

    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> = inserted
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime): List<Transaction> = inserted
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

class AlpacaSyncServiceTest {

    private val now = Instant.parse("2026-06-20T12:00:00Z")
    private val account = AlpacaAccount("acct-1", "PA1234", "ACTIVE")

    private fun success(vararg ids: String, skippedOptions: Int = 0, skippedCrypto: Int = 0) =
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
            account = account,
            skippedOptions = skippedOptions,
            skippedCrypto = skippedCrypto,
        )

    private fun service(
        client: FakeAlpacaClient,
        repo: TransactionRepository = DeduplicatingTransactionRepository(),
        settings: FakePortfolioSettingsRepository = FakePortfolioSettingsRepository(),
        tracker: BackgroundTaskTracker = BackgroundTaskTracker(),
    ): Pair<AlpacaSyncService, FakePortfolioSettingsRepository> {
        val service = AlpacaSyncService(
            client = client,
            transactionRepository = repo,
            tracker = tracker,
            portfolioSettings = settings,
            credentialsRepository = FakeCredentialsRepository(
                alpaca = AlpacaCredentials("key", "secret", AlpacaEnvironment.PAPER),
            ),
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
        // Account resolution is client-owned in production; the fake only records the fill call.
        assertEquals(now, client.lastUntil)
        assertEquals(now.toString(), settings.getString(1L, AlpacaSettings.LAST_SYNCED_AT))
    }

    @Test
    fun `overlap is applied and duplicate fills insert only once`() = runTest {
        val repo = DeduplicatingTransactionRepository()
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, AlpacaSettings.LAST_SYNCED_AT, "2026-06-17T12:00:00Z")
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
        val client = FakeAlpacaClient(success("a", skippedOptions = 3, skippedCrypto = 2))
        val (service, _) = service(client)

        val result = assertIs<BrokerSyncOutcome.Imported>(service.syncIncremental(1L))

        assertEquals(1, result.inserted)
        assertEquals(3, result.skippedOptions)
        assertEquals(2, result.skippedCrypto)
        assertTrue(service.supportsMarket(Market.US_STOCKS))
        assertTrue(!service.supportsMarket(Market.CRYPTO))
    }
}
