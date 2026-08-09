package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskHandle
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** Fetches TradeZero orders for the account configured on one portfolio. */
class TradeZeroSyncService(
    private val client: TradeZeroClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : BrokerSyncService {
    override val brokerId: String = "tradezero"
    override val displayName: String = "TradeZero"

    override fun isConfigured(portfolio: Portfolio): Boolean =
        portfolio.broker == Broker.TRADEZERO &&
            credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef) is TradeZeroBrokerCredentials

    override fun supportsMarket(market: Market): Boolean = market == Market.US_STOCKS

    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome {
        val to = today()
        val prepared = prepare(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching orders…")
        return try {
            val connection = when (val result = client.testConnection(prepared.credentials)) {
                is TradeZeroConnectionResult.Connected -> result
                TradeZeroConnectionResult.InvalidCredentials -> {
                    handle.fail("Invalid TradeZero credentials — update this portfolio's broker configuration")
                    return BrokerSyncOutcome.InvalidCredentials
                }
                is TradeZeroConnectionResult.NetworkError -> {
                    handle.fail("TradeZero network error: ${result.message}")
                    return BrokerSyncOutcome.NetworkError(result.message)
                }
            }
            val source = source(connection.account)
            findBindingConflict(portfolioId, source)?.let { portfolio ->
                handle.fail("This TradeZero account is already linked to portfolio \"${portfolio.name}\"")
                return BrokerSyncOutcome.AccountAlreadyBound(portfolio.name)
            }
            val lastSyncedSource = portfolioSettings.getString(portfolioId, TradeZeroSettings.LAST_SYNCED_SOURCE)
            val lastSynced = if (lastSyncedSource == source) {
                portfolioSettings.getString(portfolioId, TradeZeroSettings.LAST_SYNCED_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            } else {
                null
            }
            val from = lastSynced?.minus(OVERLAP_DAYS, DateTimeUnit.DAY)
                ?: to.minus(BACKFILL_DAYS, DateTimeUnit.DAY)
            val outcome = syncPrepared(portfolioId, prepared.credentials, connection.account, from, to, handle)
            if (outcome is BrokerSyncOutcome.Imported) {
                // Write cursor before identity. If identity write fails, the old source forces a safe backfill.
                portfolioSettings.putString(portfolioId, TradeZeroSettings.LAST_SYNCED_DATE, to.toString())
                portfolioSettings.putString(portfolioId, TradeZeroSettings.LAST_SYNCED_SOURCE, source)
            }
            outcome
        } catch (e: CancellationException) {
            handle.cancel()
            throw e
        } catch (e: Exception) {
            handle.fail("TradeZero import failed: ${e.message ?: "request failed"}")
            throw e
        }
    }

    /** Explicit date-range sync used by the import screen and tests. */
    suspend fun sync(portfolioId: Long, from: LocalDate, to: LocalDate): BrokerSyncOutcome {
        val prepared = prepare(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching orders…")
        return try {
            val connection = when (val result = client.testConnection(prepared.credentials)) {
                is TradeZeroConnectionResult.Connected -> result
                TradeZeroConnectionResult.InvalidCredentials -> {
                    handle.fail("Invalid TradeZero credentials — update this portfolio's broker configuration")
                    return BrokerSyncOutcome.InvalidCredentials
                }
                is TradeZeroConnectionResult.NetworkError -> {
                    handle.fail("TradeZero network error: ${result.message}")
                    return BrokerSyncOutcome.NetworkError(result.message)
                }
            }
            val source = source(connection.account)
            findBindingConflict(portfolioId, source)?.let { portfolio ->
                handle.fail("This TradeZero account is already linked to portfolio \"${portfolio.name}\"")
                return BrokerSyncOutcome.AccountAlreadyBound(portfolio.name)
            }
            syncPrepared(portfolioId, prepared.credentials, connection.account, from, to, handle)
        } catch (e: CancellationException) {
            handle.cancel()
            throw e
        } catch (e: Exception) {
            handle.fail("TradeZero import failed: ${e.message ?: "request failed"}")
            throw e
        }
    }

    private suspend fun syncPrepared(
        portfolioId: Long,
        credentials: TradeZeroBrokerCredentials,
        expectedAccount: TradeZeroAccount,
        from: LocalDate,
        to: LocalDate,
        handle: TaskHandle,
    ): BrokerSyncOutcome = when (val result = client.fetchOrders(credentials, portfolioId, from, to)) {
        is TradeZeroFetchResult.Success -> {
            check(result.account.id == expectedAccount.id) { "TradeZero account changed during synchronization" }
            val inserted = result.transactions.count { transactionRepository.insert(it) != null }
            handle.succeed("Imported $inserted new transaction(s)")
            BrokerSyncOutcome.Imported(inserted)
        }
        TradeZeroFetchResult.InvalidCredentials -> {
            handle.fail("Invalid TradeZero credentials — update this portfolio's broker configuration")
            BrokerSyncOutcome.InvalidCredentials
        }
        is TradeZeroFetchResult.NetworkError -> {
            handle.fail("TradeZero network error: ${result.message}")
            BrokerSyncOutcome.NetworkError(result.message)
        }
    }

    private suspend fun prepare(portfolioId: Long): Prepared? {
        val portfolio = portfolioRepository.getById(portfolioId) ?: return null
        if (portfolio.broker != Broker.TRADEZERO) return null
        val credentials = credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef)
            as? TradeZeroBrokerCredentials
            ?: return null
        return Prepared(credentials)
    }

    private suspend fun findBindingConflict(portfolioId: Long, source: String): Portfolio? =
        portfolioRepository.getAll().firstOrNull { portfolio ->
            portfolio.id != portfolioId &&
                portfolioSettings.getString(portfolio.id, TradeZeroSettings.LAST_SYNCED_SOURCE) == source
        }

    private fun source(account: TradeZeroAccount): String = "tradezero:${account.id}"

    private data class Prepared(
        val credentials: TradeZeroBrokerCredentials,
    )

    companion object {
        const val TASK_ID = "tradezero-import"
        const val TASK_LABEL = "TradeZero import"
        const val BACKFILL_DAYS = 365
        const val OVERLAP_DAYS = 3
    }
}
