package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSettings
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeBrokerSyncService(
    override val brokerId: String,
    private val log: MutableList<String>,
    private val configured: Boolean = true,
    private val supported: Boolean = true,
    private val fail: Boolean = false,
    private val cancel: Boolean = false,
) : BrokerSyncService {
    override val displayName: String = brokerId
    override fun isConfigured(): Boolean = configured
    override fun supportsMarket(market: Market): Boolean = supported
    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome {
        log += brokerId
        if (cancel) throw CancellationException("cancelled")
        if (fail) error("$brokerId down")
        return BrokerSyncOutcome.Imported(1)
    }
}

class StartupSyncCoordinatorTest {

    private val today = LocalDate(2026, 6, 7)

    private fun filter(portfolioId: Long?) =
        FilterPrefs(portfolioId, DateRangePreset.ALL_TIME, null, null, Segment.ALL)

    private fun creds() = FakeCredentialsRepository(tradeZero = TradeZeroCredentials("k", "s"))

    private fun portfolios(vararg ids: Long) =
        FakePortfolioRepository(ids.map { Portfolio(it, "P$it", Market.US_STOCKS) })

    private fun coordinator(
        log: MutableList<String>,
        client: FakeTradeZeroClient,
        settings: FakeSettingsRepository,
        credentials: FakeCredentialsRepository,
        portfolios: FakePortfolioRepository,
        portfolioSettings: FakePortfolioSettingsRepository,
    ): StartupSyncCoordinator {
        val service = TradeZeroSyncService(
            client = client,
            transactionRepository = FakeTransactionRepository(),
            tracker = BackgroundTaskTracker(),
            portfolioSettings = portfolioSettings,
            today = { today },
            credentialsRepository = credentials,
        )
        return StartupSyncCoordinator(
            settingsRepository = settings,
            portfolioRepository = portfolios,
            portfolioSettings = portfolioSettings,
            brokerSyncServices = listOf(service),
            requestMarketDataSync = { log.add("md") },
        )
    }

    @Test
    fun syncsTradeZeroBeforeMarketDataWhenSelectedPortfolioOptedIn() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, true)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = filter(5L)),
            credentials = creds(),
            portfolios = portfolios(5L),
            portfolioSettings = portfolioSettings,
        ).run()

        assertEquals(listOf("tz", "md"), log)
        assertEquals(1, client.fetchCount)
        assertEquals(5L, client.lastPortfolioId)
        // No prior sync recorded → first-run one-year backfill.
        assertEquals(today.minus(365, DateTimeUnit.DAY), client.lastFrom)
        assertEquals(today, client.lastTo)
    }

    @Test
    fun skipsTradeZeroWhenAutoSyncDisabledForSelectedPortfolio() = runTest {
        // Auto-sync is opt-in: a selected, existing portfolio that never enabled it is skipped (default off).
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = filter(5L)),
            credentials = creds(),
            portfolios = portfolios(5L),
            portfolioSettings = FakePortfolioSettingsRepository(),
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun skipsTradeZeroWhenNoPortfolioIsSelected() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(7L, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, true)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = null),
            credentials = creds(),
            portfolios = portfolios(7L),
            portfolioSettings = portfolioSettings,
        ).run()

        // Nothing selected — auto-import must not pick a portfolio on the user's behalf even if one opted in.
        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun skipsTradeZeroWhenSelectedPortfolioIsMissingFromDb() = runTest {
        // Deleted ejournal.db, but credentials + the saved selection survived in OS prefs.
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(99L, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, true)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = filter(99L)),
            credentials = creds(),
            portfolios = portfolios(7L),
            portfolioSettings = portfolioSettings,
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun skipsTradeZeroWhenNoCredentials() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, true)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = filter(5L)),
            credentials = FakeCredentialsRepository(tradeZero = null),
            portfolios = portfolios(5L),
            portfolioSettings = portfolioSettings,
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun skipsTradeZeroWhenNoPortfolioAvailable() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(filterPrefs = null),
            credentials = creds(),
            portfolios = FakePortfolioRepository(emptyList()),
            portfolioSettings = FakePortfolioSettingsRepository(),
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun `runs every enabled broker before market data`() = runTest {
        val log = mutableListOf<String>()
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, "alpaca.autoSyncOnStartup", true)
        portfolioSettings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        val coordinator = StartupSyncCoordinator(
            settingsRepository = FakeSettingsRepository(filterPrefs = filter(5L)),
            portfolioRepository = portfolios(5L),
            portfolioSettings = portfolioSettings,
            brokerSyncServices = listOf(
                FakeBrokerSyncService("alpaca", log),
                FakeBrokerSyncService("tradezero", log),
            ),
            requestMarketDataSync = { log += "md" },
        )

        coordinator.run()

        assertEquals(listOf("alpaca", "tradezero", "md"), log)
    }

    @Test
    fun `continues other brokers and market data after one broker fails`() = runTest {
        val log = mutableListOf<String>()
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, "alpaca.autoSyncOnStartup", true)
        portfolioSettings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        StartupSyncCoordinator(
            settingsRepository = FakeSettingsRepository(filterPrefs = filter(5L)),
            portfolioRepository = portfolios(5L),
            portfolioSettings = portfolioSettings,
            brokerSyncServices = listOf(
                FakeBrokerSyncService("alpaca", log, fail = true),
                FakeBrokerSyncService("tradezero", log),
            ),
            requestMarketDataSync = { log += "md" },
        ).run()

        assertEquals(listOf("alpaca", "tradezero", "md"), log)
    }

    @Test
    fun `skips unconfigured and unsupported brokers`() = runTest {
        val log = mutableListOf<String>()
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, "alpaca.autoSyncOnStartup", true)
        portfolioSettings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        StartupSyncCoordinator(
            settingsRepository = FakeSettingsRepository(filterPrefs = filter(5L)),
            portfolioRepository = portfolios(5L),
            portfolioSettings = portfolioSettings,
            brokerSyncServices = listOf(
                FakeBrokerSyncService("alpaca", log, configured = false),
                FakeBrokerSyncService("tradezero", log, supported = false),
            ),
            requestMarketDataSync = { log += "md" },
        ).run()

        assertEquals(listOf("md"), log)
    }

    @Test
    fun `rethrows startup cancellation`() = runTest {
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putBoolean(5L, "alpaca.autoSyncOnStartup", true)
        val coordinator = StartupSyncCoordinator(
            settingsRepository = FakeSettingsRepository(filterPrefs = filter(5L)),
            portfolioRepository = portfolios(5L),
            portfolioSettings = portfolioSettings,
            brokerSyncServices = listOf(FakeBrokerSyncService("alpaca", mutableListOf(), cancel = true)),
            requestMarketDataSync = {},
        )

        assertFailsWith<CancellationException> { coordinator.run() }
    }
}
