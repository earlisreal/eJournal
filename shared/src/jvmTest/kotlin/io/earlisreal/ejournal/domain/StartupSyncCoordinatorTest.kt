package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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
    override fun isConfigured(portfolio: Portfolio): Boolean = configured
    override fun supportsMarket(market: Market): Boolean = supported
    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome {
        log += brokerId
        if (cancel) throw CancellationException("cancelled")
        if (fail) error("$brokerId down")
        return BrokerSyncOutcome.Imported(1)
    }
}

class StartupSyncCoordinatorTest {

    private fun filter(portfolioId: Long?) =
        FilterPrefs(portfolioId, DateRangePreset.ALL_TIME, null, null, Segment.ALL)

    private fun portfolio(id: Long = 5L, broker: Broker? = Broker.TRADEZERO) =
        Portfolio(id, "P$id", Market.US_STOCKS, broker, "ref-$id")

    private fun coordinator(
        log: MutableList<String>,
        selectedId: Long?,
        portfolios: List<Portfolio> = listOf(portfolio()),
        services: List<BrokerSyncService>,
        settings: FakePortfolioSettingsRepository = FakePortfolioSettingsRepository(),
    ) = StartupSyncCoordinator(
        settingsRepository = FakeSettingsRepository(filterPrefs = filter(selectedId)),
        portfolioRepository = FakePortfolioRepository(portfolios),
        portfolioSettings = settings,
        brokerSyncServices = services,
        requestMarketDataSync = { log += "md" },
    )

    @Test
    fun selectedPortfolioBrokerRunsBeforeMarketData() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
            selectedId = 5L,
            services = listOf(FakeBrokerSyncService("tradezero", log)),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "md"), log)
    }

    @Test
    fun selectedMoomooPortfolioRunsOnlyWhenOptedIn() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "moomoo.autoSyncOnStartup", true)

        coordinator(
            log,
            selectedId = 5L,
            portfolios = listOf(portfolio(broker = Broker.MOOMOO)),
            services = listOf(FakeBrokerSyncService("moomoo", log)),
            settings = settings,
        ).run()

        assertEquals(listOf("moomoo", "md"), log)
    }

    @Test
    fun manualPortfolioRunsMarketDataOnly() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
            selectedId = 5L,
            portfolios = listOf(portfolio(broker = null)),
            services = listOf(FakeBrokerSyncService("tradezero", log)),
            settings = settings,
        ).run()

        assertEquals(listOf("md"), log)
    }

    @Test
    fun missingCredentialsRunsMarketDataOnly() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
            selectedId = 5L,
            services = listOf(FakeBrokerSyncService("tradezero", log, configured = false)),
            settings = settings,
        ).run()

        assertEquals(listOf("md"), log)
    }

    @Test
    fun autoSyncDisabledRunsMarketDataOnly() = runTest {
        val log = mutableListOf<String>()
        coordinator(
            log,
            selectedId = 5L,
            services = listOf(FakeBrokerSyncService("tradezero", log)),
        ).run()

        assertEquals(listOf("md"), log)
    }

    @Test
    fun onlySelectedPortfolioBrokerRuns() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        settings.putBoolean(5L, "alpaca.autoSyncOnStartup", true)
        coordinator(
            log,
            selectedId = 5L,
            services = listOf(FakeBrokerSyncService("alpaca", log), FakeBrokerSyncService("tradezero", log)),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "md"), log)
    }

    @Test
    fun brokerFailureStillRunsMarketData() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
            selectedId = 5L,
            services = listOf(FakeBrokerSyncService("tradezero", log, fail = true)),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "md"), log)
    }

    @Test
    fun noOrStaleSelectionRunsMarketDataOnly() = runTest {
        val log = mutableListOf<String>()
        val service = FakeBrokerSyncService("tradezero", log)
        coordinator(log, selectedId = null, services = listOf(service)).run()
        coordinator(log, selectedId = 99L, portfolios = listOf(portfolio()), services = listOf(service)).run()

        assertEquals(listOf("md", "md"), log)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        assertFailsWith<CancellationException> {
            coordinator(
                log,
                selectedId = 5L,
                services = listOf(FakeBrokerSyncService("tradezero", log, cancel = true)),
                settings = settings,
            ).run()
        }
    }
}
