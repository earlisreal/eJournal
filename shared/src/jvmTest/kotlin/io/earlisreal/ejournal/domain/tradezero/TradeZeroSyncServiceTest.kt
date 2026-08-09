package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import io.earlisreal.ejournal.testutil.tx
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeZeroSyncServiceTest {

    private val from = LocalDate(2026, 6, 1)
    private val to = LocalDate(2026, 6, 7)
    private val today = LocalDate(2026, 6, 23)
    private val credentials = TradeZeroBrokerCredentials("key", "secret")

    private fun portfolio(id: Long = 1L, ref: String = "tz-ref") =
        Portfolio(id, "Trading $id", Market.US_STOCKS, Broker.TRADEZERO, ref)

    private fun credentialsRepo(ref: String = "tz-ref") =
        FakeCredentialsRepository(portfolioBrokers = mapOf(ref to credentials))

    private fun service(
        client: FakeTradeZeroClient,
        settings: FakePortfolioSettingsRepository = FakePortfolioSettingsRepository(),
        portfolios: FakePortfolioRepository = FakePortfolioRepository(listOf(portfolio())),
        credentialsRepository: FakeCredentialsRepository = credentialsRepo(),
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
    ) = TradeZeroSyncService(
        client = client,
        transactionRepository = transactions,
        tracker = BackgroundTaskTracker(),
        portfolioRepository = portfolios,
        portfolioSettings = settings,
        credentialsRepository = credentialsRepository,
        today = { today },
    )

    @Test
    fun successImportsAndUsesPortfolioCredentials() = runTest {
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1")), TradeZeroAccount("tz-account")))
        val service = service(client)

        val outcome = service.sync(1, from, to)

        assertEquals(BrokerSyncOutcome.Imported(1), outcome)
        assertEquals(credentials, client.lastCredentials)
        assertEquals(1L, client.lastPortfolioId)
    }

    @Test
    fun failedFetchReportsInvalidCredentials() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.InvalidCredentials)
        val service = TradeZeroSyncService(
            client,
            FakeTransactionRepository(),
            tracker,
            FakePortfolioRepository(listOf(portfolio())),
            FakePortfolioSettingsRepository(),
            credentialsRepo(),
            today = { today },
        )

        assertEquals(BrokerSyncOutcome.InvalidCredentials, service.sync(1, from, to))
        assertEquals(TaskState.Failed, tracker.tasks.value.single().state)
    }

    @Test
    fun noCredentialsReturnsNotConfiguredWithoutCallingClient() = runTest {
        val client = FakeTradeZeroClient()
        val outcome = service(client, credentialsRepository = FakeCredentialsRepository())
            .syncIncremental(1)

        assertEquals(BrokerSyncOutcome.NotConfigured, outcome)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun firstIncrementalSyncBackfillsAndRecordsAccountSpecificCursor() = runTest {
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1")), TradeZeroAccount("tz-account")))
        val settings = FakePortfolioSettingsRepository()
        val outcome = service(client, settings = settings).syncIncremental(1)

        assertEquals(BrokerSyncOutcome.Imported(1), outcome)
        assertEquals(today.minus(365, DateTimeUnit.DAY), client.lastFrom)
        assertEquals(today, client.lastTo)
        assertEquals(today.toString(), settings.getString(1, TradeZeroSettings.LAST_SYNCED_DATE))
        assertEquals("tradezero:tz-account", settings.getString(1, TradeZeroSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun accountChangeCausesFreshBackfillInsteadOfUsingOldCursor() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1, TradeZeroSettings.LAST_SYNCED_DATE, LocalDate(2026, 6, 1).toString())
        settings.putString(1, TradeZeroSettings.LAST_SYNCED_SOURCE, "tradezero:old-account")
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(emptyList(), TradeZeroAccount("tz-account")))

        service(client, settings = settings).syncIncremental(1)

        assertEquals(today.minus(365, DateTimeUnit.DAY), client.lastFrom)
        assertEquals("tradezero:tz-account", settings.getString(1, TradeZeroSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun sameAccountBoundToAnotherPortfolioIsRejected() = runTest {
        val settings = FakePortfolioSettingsRepository()
        settings.putString(2, TradeZeroSettings.LAST_SYNCED_SOURCE, "tradezero:tz-account")
        val portfolios = FakePortfolioRepository(listOf(portfolio(1), portfolio(2, "other-ref")))
        val client = FakeTradeZeroClient()

        val outcome = service(client, settings = settings, portfolios = portfolios).syncIncremental(1)

        assertIs<BrokerSyncOutcome.AccountAlreadyBound>(outcome)
        assertEquals("Trading 2", outcome.portfolioName)
        assertEquals(0, client.fetchCount)
    }
}
