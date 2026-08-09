package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.AlpacaBrokerCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AlpacaSyncService(
    private val client: AlpacaBrokerClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val now: () -> Instant = { Clock.System.now() },
) : BrokerSyncService {

    private val syncMutex = Mutex()

    override val brokerId: String = "alpaca"
    override val displayName: String = "Alpaca"

    override fun isConfigured(portfolio: Portfolio): Boolean =
        portfolio.broker == Broker.ALPACA &&
            credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef) is AlpacaBrokerCredentials

    override fun supportsMarket(market: Market): Boolean = market == Market.US_STOCKS

    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome =
        syncMutex.withLock { syncIncrementalLocked(portfolioId) }

    private suspend fun syncIncrementalLocked(portfolioId: Long): BrokerSyncOutcome {
        val portfolio = portfolioRepository.getById(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        if (portfolio.broker != Broker.ALPACA) return BrokerSyncOutcome.NotConfigured
        val credentials = credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef)
            as? AlpacaBrokerCredentials
            ?: return BrokerSyncOutcome.NotConfigured
        val until = now()
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching Alpaca fills…")

        return try {
            val connection = when (val result = client.testConnection(credentials)) {
                is AlpacaConnectionResult.Connected -> result
                AlpacaConnectionResult.InvalidCredentials -> {
                    handle.fail("Invalid Alpaca credentials — update this portfolio's broker configuration")
                    return BrokerSyncOutcome.InvalidCredentials
                }
                is AlpacaConnectionResult.NetworkError -> {
                    handle.fail("Alpaca network error: ${result.message}")
                    return BrokerSyncOutcome.NetworkError(result.message)
                }
            }
            val source = AlpacaSettings.source(connection.environment, connection.account.id)
            findBindingConflict(portfolioId, source)?.let { portfolio ->
                val message =
                    "This Alpaca ${connection.environment.label} account is already linked to portfolio \"${portfolio.name}\""
                handle.fail(message)
                return BrokerSyncOutcome.AccountAlreadyBound(portfolio.name)
            }

            val lastSyncedSource = portfolioSettings.getString(portfolioId, AlpacaSettings.LAST_SYNCED_SOURCE)
            val lastSyncedAt = if (lastSyncedSource == source) {
                portfolioSettings.getString(portfolioId, AlpacaSettings.LAST_SYNCED_AT)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            } else {
                null
            }
            val after = lastSyncedAt?.minus(OVERLAP)

            when (val result = client.fetchFills(credentials, portfolioId, after, until)) {
                is AlpacaFetchResult.Success -> {
                    check(result.account.id == connection.account.id) {
                        "Alpaca account changed during synchronization"
                    }
                    val inserted = result.transactions.count { transactionRepository.insert(it) != null }
                    // Cursor advancement is deliberately last: a failed insert leaves the old
                    // source and cursor in place, so the next attempt safely re-fetches the overlap.
                    portfolioSettings.putString(portfolioId, AlpacaSettings.LAST_SYNCED_AT, until.toString())
                    // Write the cursor before its identity. If the second write fails, the old
                    // source makes the next run discard this cursor and perform a safe backfill.
                    portfolioSettings.putString(portfolioId, AlpacaSettings.LAST_SYNCED_SOURCE, source)
                    val detail = buildString {
                        append("Imported $inserted new transaction(s)")
                        result.detail.skipped.forEach { (reason, count) ->
                            if (count > 0) append(" · $count $reason skipped")
                        }
                    }
                    handle.succeed(detail)
                    BrokerSyncOutcome.Imported(inserted, result.detail)
                }
                AlpacaFetchResult.InvalidCredentials -> {
                    handle.fail("Invalid Alpaca credentials — update this portfolio's broker configuration")
                    BrokerSyncOutcome.InvalidCredentials
                }
                is AlpacaFetchResult.NetworkError -> {
                    handle.fail("Alpaca network error: ${result.message}")
                    BrokerSyncOutcome.NetworkError(result.message)
                }
            }
        } catch (e: CancellationException) {
            handle.cancel()
            throw e
        } catch (e: Exception) {
            handle.fail("Alpaca import failed: ${e.message ?: "request failed"}")
            throw e
        }
    }

    private suspend fun findBindingConflict(portfolioId: Long, source: String) =
        portfolioRepository.getAll().firstOrNull { portfolio ->
            if (portfolio.id == portfolioId) return@firstOrNull false
            if (portfolioSettings.getString(portfolio.id, AlpacaSettings.LAST_SYNCED_SOURCE) == source) {
                return@firstOrNull true
            }
            transactionRepository.getByPortfolio(portfolio.id).any { transaction ->
                transaction.externalId?.startsWith("alpaca:$source:") == true
            }
        }

    companion object {
        const val TASK_ID = "alpaca-import"
        const val TASK_LABEL = "Alpaca import"
        val OVERLAP = 3.days
    }
}
