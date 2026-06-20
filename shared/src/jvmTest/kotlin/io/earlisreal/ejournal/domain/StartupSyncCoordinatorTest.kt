package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakeSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupSyncCoordinatorTest {

    private val today = LocalDate(2026, 6, 7)

    private fun filter(portfolioId: Long?) =
        FilterPrefs(portfolioId, DateRangePreset.ALL_TIME, null, null, Segment.ALL)

    private fun coordinator(
        log: MutableList<String>,
        client: FakeTradeZeroClient,
        settings: FakeSettingsRepository,
        credentials: FakeCredentialsRepository,
        portfolios: FakePortfolioRepository,
    ): StartupSyncCoordinator {
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), BackgroundTaskTracker())
        return StartupSyncCoordinator(
            settingsRepository = settings,
            credentialsRepository = credentials,
            portfolioRepository = portfolios,
            tradeZeroSyncService = service,
            requestMarketDataSync = { log.add("md") },
            today = { today },
        )
    }

    @Test
    fun syncsTradeZeroBeforeMarketDataWhenEnabledAndConfigured() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(autoSync = true, filterPrefs = filter(5L)),
            credentials = FakeCredentialsRepository(tradeZero = TradeZeroCredentials("k", "s")),
            portfolios = FakePortfolioRepository(),
        ).run()

        assertEquals(listOf("tz", "md"), log)
        assertEquals(1, client.fetchCount)
        assertEquals(5L, client.lastPortfolioId)
        assertEquals(today.minus(6, DateTimeUnit.DAY), client.lastFrom)
        assertEquals(today, client.lastTo)
    }

    @Test
    fun fallsBackToFirstPortfolioWhenNoSavedFilter() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(autoSync = true, filterPrefs = null),
            credentials = FakeCredentialsRepository(tradeZero = TradeZeroCredentials("k", "s")),
            portfolios = FakePortfolioRepository(listOf(Portfolio(7L, "Main", Market.US_STOCKS))),
        ).run()

        assertEquals(listOf("tz", "md"), log)
        assertEquals(7L, client.lastPortfolioId)
    }

    @Test
    fun skipsTradeZeroWhenDisabled() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(autoSync = false, filterPrefs = filter(5L)),
            credentials = FakeCredentialsRepository(tradeZero = TradeZeroCredentials("k", "s")),
            portfolios = FakePortfolioRepository(),
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }

    @Test
    fun skipsTradeZeroWhenNoCredentials() = runTest {
        val log = mutableListOf<String>()
        val client = FakeTradeZeroClient(log = log)
        coordinator(
            log = log,
            client = client,
            settings = FakeSettingsRepository(autoSync = true, filterPrefs = filter(5L)),
            credentials = FakeCredentialsRepository(tradeZero = null),
            portfolios = FakePortfolioRepository(),
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
            settings = FakeSettingsRepository(autoSync = true, filterPrefs = null),
            credentials = FakeCredentialsRepository(tradeZero = TradeZeroCredentials("k", "s")),
            portfolios = FakePortfolioRepository(emptyList()),
        ).run()

        assertEquals(listOf("md"), log)
        assertEquals(0, client.fetchCount)
    }
}
