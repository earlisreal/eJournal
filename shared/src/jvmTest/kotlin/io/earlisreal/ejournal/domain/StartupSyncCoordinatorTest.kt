package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
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

    private fun portfolio(id: Long = 5L, broker: Broker? = Broker.TRADEZERO) =
        Portfolio(id, "P$id", Market.US_STOCKS, broker, "ref-$id")

    private fun coordinator(
        log: MutableList<String>,
        portfolios: List<Portfolio> = listOf(portfolio()),
        services: List<BrokerSyncService>,
        settings: FakePortfolioSettingsRepository = FakePortfolioSettingsRepository(),
    ) = StartupSyncCoordinator(
        portfolioRepository = FakePortfolioRepository(portfolios),
        portfolioSettings = settings,
        brokerSyncServices = services,
        requestMarketDataSync = { log += "md" },
    )

    @Test
    fun autoSyncedPortfolioRunsBeforeMarketData() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
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
            services = listOf(FakeBrokerSyncService("tradezero", log)),
        ).run()

        assertEquals(listOf("md"), log)
    }

    @Test
    fun everyOptedInPortfolioRunsBeforeMarketData() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        settings.putBoolean(6L, "alpaca.autoSyncOnStartup", true)
        val changed = coordinator(
            log,
            portfolios = listOf(portfolio(5L), portfolio(6L, Broker.ALPACA)),
            services = listOf(FakeBrokerSyncService("alpaca", log), FakeBrokerSyncService("tradezero", log)),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "alpaca", "md"), log)
        assertEquals(setOf(5L, 6L), changed)
    }

    @Test
    fun brokerFailureStillRunsMarketData() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        coordinator(
            log,
            services = listOf(FakeBrokerSyncService("tradezero", log, fail = true)),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "md"), log)
    }

    @Test
    fun brokerFailureDoesNotBlockOtherPortfolios() = runTest {
        val log = mutableListOf<String>()
        val settings = FakePortfolioSettingsRepository()
        settings.putBoolean(5L, "tradezero.autoSyncOnStartup", true)
        settings.putBoolean(6L, "alpaca.autoSyncOnStartup", true)
        val changed = coordinator(
            log,
            portfolios = listOf(portfolio(5L), portfolio(6L, Broker.ALPACA)),
            services = listOf(
                FakeBrokerSyncService("tradezero", log, fail = true),
                FakeBrokerSyncService("alpaca", log),
            ),
            settings = settings,
        ).run()

        assertEquals(listOf("tradezero", "alpaca", "md"), log)
        assertEquals(setOf(6L), changed)
    }

    @Test
    fun noEligiblePortfolioRunsMarketDataOnly() = runTest {
        val log = mutableListOf<String>()
        val service = FakeBrokerSyncService("tradezero", log)
        coordinator(log, services = listOf(service)).run()
        coordinator(log, portfolios = listOf(portfolio(broker = null)), services = listOf(service)).run()

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
                services = listOf(FakeBrokerSyncService("tradezero", log, cancel = true)),
                settings = settings,
            ).run()
        }
    }
}
