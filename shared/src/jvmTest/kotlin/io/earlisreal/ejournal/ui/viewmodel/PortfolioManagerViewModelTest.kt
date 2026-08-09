package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.repository.AlpacaBrokerCredentials
import io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials
import io.earlisreal.ejournal.domain.alpaca.AlpacaAccount
import io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerClient
import io.earlisreal.ejournal.domain.alpaca.AlpacaConnectionResult
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import io.earlisreal.ejournal.domain.alpaca.AlpacaFetchResult
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.testutil.FakeCredentialsRepository
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioManagerViewModelTest {

    private val account = AlpacaAccount("account-1", "number-1", "ACTIVE")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `creates manual portfolio without credentials`() = runTest {
        val portfolioRepository = FakePortfolioRepository()
        val credentials = FakeCredentialsRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) {}

        viewModel.create("Manual", Market.PH_STOCKS, broker = null, draft = null)
        viewModel.state.first { it.portfolios.size == 1 }

        val portfolio = portfolioRepository.getAll().single()
        assertNull(portfolio.broker)
        assertTrue(credentials.portfolioCredentials.isEmpty())
    }

    @Test
    fun `creates Alpaca portfolio with and without credentials`() = runTest {
        val credentials = FakeCredentialsRepository()
        val portfolioRepository = FakePortfolioRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) {}

        viewModel.create("No Keys", Market.US_STOCKS, Broker.ALPACA, draft = null)
        viewModel.state.first { it.portfolios.size == 1 }
        viewModel.create(
            "With Keys",
            Market.US_STOCKS,
            Broker.ALPACA,
            BrokerCredentialDraft.Alpaca("key", "secret", AlpacaEnvironment.LIVE),
        )
        viewModel.state.first { it.portfolios.size == 2 }

        val saved = portfolioRepository.getAll().single { it.name == "With Keys" }
        assertEquals(Broker.ALPACA, saved.broker)
        assertEquals(
            AlpacaBrokerCredentials("key", "secret", AlpacaEnvironment.LIVE),
            credentials.portfolioCredentials[saved.credentialRef],
        )
    }

    @Test
    fun `copied global credentials are saved independently`() = runTest {
        val credentials = FakeCredentialsRepository()
        credentials.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("global", "global-secret"))
        val portfolioRepository = FakePortfolioRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) {}

        val copied = viewModel.globalAlpacaCredentials()!!
        viewModel.create(
            "Copied",
            Market.US_STOCKS,
            Broker.ALPACA,
            BrokerCredentialDraft.Alpaca(copied.keyId, copied.secretKey),
        )
        viewModel.state.first { it.portfolios.size == 1 }
        val portfolio = portfolioRepository.getAll().single()

        credentials.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("changed", "changed-secret"))

        assertEquals(
            AlpacaBrokerCredentials("global", "global-secret", AlpacaEnvironment.PAPER),
            credentials.portfolioCredentials[portfolio.credentialRef],
        )
    }

    @Test
    fun `rejects partial credential pairs`() = runTest {
        val portfolioRepository = FakePortfolioRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            FakeCredentialsRepository(),
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) {}

        viewModel.create(
            "Incomplete",
            Market.US_STOCKS,
            Broker.ALPACA,
            BrokerCredentialDraft.Alpaca(keyId = "only-key"),
        )

        assertTrue(viewModel.state.value.error!!.contains("both broker credential fields"))
        assertTrue(portfolioRepository.getAll().isEmpty())
    }

    @Test
    fun `creates TradeZero portfolio`() = runTest {
        val credentials = FakeCredentialsRepository()
        val portfolioRepository = FakePortfolioRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) {}

        viewModel.create(
            "TradeZero",
            Market.US_STOCKS,
            Broker.TRADEZERO,
            BrokerCredentialDraft.TradeZero("tz-key", "tz-secret"),
        )
        viewModel.state.first { it.portfolios.size == 1 }

        val portfolio = portfolioRepository.getAll().single()
        assertEquals(
            io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials("tz-key", "tz-secret"),
            credentials.portfolioCredentials[portfolio.credentialRef],
        )
    }

    @Test
    fun `changing credentials clears broker namespace and preserves credential reference`() = runTest {
        val portfolio = io.earlisreal.ejournal.domain.model.Portfolio(1L, "Alpaca", Market.US_STOCKS, Broker.ALPACA, "ref-1")
        val credentials = FakeCredentialsRepository(
            portfolioBrokers = mapOf("ref-1" to AlpacaBrokerCredentials("old", "old-secret", AlpacaEnvironment.PAPER)),
        )
        val settings = FakePortfolioSettingsRepository()
        settings.putString(1L, "alpaca.lastSyncedDate", "2026-01-01")
        val portfolioRepository = FakePortfolioRepository(listOf(portfolio))
        val changed = CompletableDeferred<Unit>()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            settings,
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) { changed.complete(Unit) }

        viewModel.update(
            1L,
            "Alpaca",
            Market.US_STOCKS,
            Broker.ALPACA,
            BrokerCredentialDraft.Alpaca("new", "new-secret", AlpacaEnvironment.LIVE),
        )
        changed.await()

        assertEquals("ref-1", portfolioRepository.getById(1L)!!.credentialRef)
        assertNull(settings.getString(1L, "alpaca.lastSyncedDate"))
        assertEquals(
            AlpacaBrokerCredentials("new", "new-secret", AlpacaEnvironment.LIVE),
            credentials.portfolioCredentials["ref-1"],
        )
    }

    @Test
    fun `changing to manual removes broker credentials but preserves portfolio`() = runTest {
        val portfolio = io.earlisreal.ejournal.domain.model.Portfolio(1L, "Alpaca", Market.US_STOCKS, Broker.ALPACA, "ref-1")
        val credentials = FakeCredentialsRepository(
            portfolioBrokers = mapOf("ref-1" to AlpacaBrokerCredentials("key", "secret", AlpacaEnvironment.PAPER)),
        )
        val portfolioRepository = FakePortfolioRepository(listOf(portfolio))
        val changed = CompletableDeferred<Unit>()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) { changed.complete(Unit) }

        viewModel.update(1L, "Manual", Market.US_STOCKS, broker = null, draft = null)
        changed.await()

        val updated = portfolioRepository.getById(1L)!!
        assertEquals("ref-1", updated.credentialRef)
        assertNull(credentials.portfolioCredentials["ref-1"])
    }

    @Test
    fun `delete cleans portfolio credentials`() = runTest {
        val portfolio = io.earlisreal.ejournal.domain.model.Portfolio(1L, "TradeZero", Market.US_STOCKS, Broker.TRADEZERO, "ref-1")
        val credentials = FakeCredentialsRepository(
            portfolioBrokers = mapOf("ref-1" to io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials("key", "secret")),
        )
        val portfolioRepository = FakePortfolioRepository(listOf(portfolio))
        val deleted = CompletableDeferred<Unit>()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            RecordingAlpacaClient(account),
            FakeTradeZeroClient(),
        ) { deleted.complete(Unit) }

        viewModel.requestDelete(portfolio)
        viewModel.state.first { it.pendingDelete != null }
        viewModel.confirmDelete()
        deleted.await()

        assertTrue(portfolioRepository.getAll().isEmpty())
        assertNull(credentials.portfolioCredentials["ref-1"])
    }

    @Test
    fun `test connection uses draft values without saving`() = runTest {
        val credentials = FakeCredentialsRepository()
        val alpaca = RecordingAlpacaClient(account)
        val portfolioRepository = FakePortfolioRepository()
        val viewModel = PortfolioManagerViewModel(
            portfolioRepository,
            FakeTransactionRepository(),
            FakePortfolioSettingsRepository(),
            credentials,
            alpaca,
            FakeTradeZeroClient(),
        ) {}

        viewModel.testConnection(BrokerCredentialDraft.Alpaca("draft-key", "draft-secret", AlpacaEnvironment.LIVE))
        val result = viewModel.state.first { it.connectionTest != null }.connectionTest

        assertIs<BrokerConnectionTestResult.Alpaca>(result)
        assertEquals(
            AlpacaBrokerCredentials("draft-key", "draft-secret", AlpacaEnvironment.LIVE),
            alpaca.lastCredentials,
        )
        assertTrue(credentials.portfolioCredentials.isEmpty())
        assertTrue(portfolioRepository.getAll().isEmpty())
    }
}

private class RecordingAlpacaClient(
    private val account: AlpacaAccount,
) : AlpacaBrokerClient {
    var lastCredentials: AlpacaBrokerCredentials? = null

    override suspend fun testConnection(credentials: AlpacaBrokerCredentials): AlpacaConnectionResult {
        lastCredentials = credentials
        return AlpacaConnectionResult.Connected(account, credentials.environment)
    }

    override suspend fun fetchFills(
        credentials: AlpacaBrokerCredentials,
        portfolioId: Long,
        after: kotlin.time.Instant?,
        until: kotlin.time.Instant?,
    ): AlpacaFetchResult {
        lastCredentials = credentials
        return AlpacaFetchResult.Success(emptyList(), account)
    }
}
