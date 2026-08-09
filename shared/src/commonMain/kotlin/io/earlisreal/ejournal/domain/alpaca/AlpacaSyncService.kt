package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Market
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AlpacaSyncService(
    private val client: AlpacaBrokerClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : BrokerSyncService {

    override val brokerId: String = "alpaca"
    override val displayName: String
        get() = credentialsRepository?.getAlpacaCredentials()?.environment?.let { "Alpaca · ${it.label}" }
            ?: "Alpaca"

    override fun isConfigured(): Boolean =
        credentialsRepository == null || credentialsRepository.getAlpacaCredentials() != null

    override fun supportsMarket(market: Market): Boolean = market == Market.US_STOCKS

    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome {
        val until = now()
        val lastSyncedAt = portfolioSettings.getString(portfolioId, AlpacaSettings.LAST_SYNCED_AT)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val after = lastSyncedAt?.minus(OVERLAP)
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching Alpaca fills…")

        return try {
            when (val result = client.fetchFills(portfolioId, after, until)) {
                is AlpacaFetchResult.Success -> {
                    val inserted = result.transactions.count { transactionRepository.insert(it) != null }
                    // Cursor advancement is deliberately last: a failed insert leaves the old
                    // cursor in place, so the next attempt safely re-fetches the overlap.
                    portfolioSettings.putString(portfolioId, AlpacaSettings.LAST_SYNCED_AT, until.toString())
                    val detail = buildString {
                        append("Imported $inserted new transaction(s)")
                        if (result.skippedOptions > 0) append(" · ${result.skippedOptions} options skipped")
                        if (result.skippedCrypto > 0) append(" · ${result.skippedCrypto} crypto fills skipped")
                    }
                    handle.succeed(detail)
                    BrokerSyncOutcome.Imported(inserted, result.skippedOptions, result.skippedCrypto)
                }
                AlpacaFetchResult.InvalidCredentials -> {
                    handle.fail("Invalid Alpaca credentials — update them in Settings")
                    BrokerSyncOutcome.InvalidCredentials
                }
                is AlpacaFetchResult.NetworkError -> {
                    handle.fail("Alpaca network error: ${result.message}")
                    BrokerSyncOutcome.NetworkError(result.message)
                }
            }
        } catch (e: Exception) {
            handle.fail("Alpaca import failed: ${e.message ?: "request failed"}")
            throw e
        }
    }

    companion object {
        const val TASK_ID = "alpaca-import"
        const val TASK_LABEL = "Alpaca import"
        val OVERLAP = 3.days
    }
}
