package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** Result of a TradeZero pull, for callers that want to react (import screen, startup). */
sealed interface TradeZeroSyncOutcome {
    data class Imported(val inserted: Int) : TradeZeroSyncOutcome
    data object InvalidCredentials : TradeZeroSyncOutcome
    data class NetworkError(val message: String) : TradeZeroSyncOutcome
}

/**
 * Fetches TradeZero orders, inserts the new ones, and reports progress to the status bar.
 * Shared by the Import screen's manual sync and the startup auto-sync so both behave identically.
 */
class TradeZeroSyncService(
    private val client: TradeZeroClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    /**
     * Syncs only the gap since the last successful sync for this portfolio. The first ever sync
     * backfills a full year; later syncs start at the last-synced date minus a small overlap so
     * late-settling fills are recaught (the externalId dedup absorbs the re-fetched rows). The
     * last-synced cursor is advanced only when the fetch succeeds. The cursor lives in the database
     * (per-portfolio), so a recreated DB correctly falls back to a fresh one-year backfill.
     */
    suspend fun syncIncremental(portfolioId: Long): TradeZeroSyncOutcome {
        val to = today()
        val lastSynced = portfolioSettings.getString(portfolioId, TradeZeroSettings.LAST_SYNCED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val from = lastSynced?.minus(OVERLAP_DAYS, DateTimeUnit.DAY)
            ?: to.minus(BACKFILL_DAYS, DateTimeUnit.DAY)
        val outcome = sync(portfolioId, from, to)
        if (outcome is TradeZeroSyncOutcome.Imported) {
            portfolioSettings.putString(portfolioId, TradeZeroSettings.LAST_SYNCED_DATE, to.toString())
        }
        return outcome
    }

    suspend fun sync(portfolioId: Long, from: LocalDate, to: LocalDate): TradeZeroSyncOutcome {
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching orders…")
        return when (val result = client.fetchOrders(portfolioId, from, to)) {
            is TradeZeroFetchResult.Success -> {
                val inserted = result.transactions.count { transactionRepository.insert(it) != null }
                handle.succeed("Imported $inserted new transaction(s)")
                TradeZeroSyncOutcome.Imported(inserted)
            }
            TradeZeroFetchResult.InvalidCredentials -> {
                handle.fail("Invalid TradeZero credentials — update them in Settings")
                TradeZeroSyncOutcome.InvalidCredentials
            }
            is TradeZeroFetchResult.NetworkError -> {
                handle.fail("TradeZero network error: ${result.message}")
                TradeZeroSyncOutcome.NetworkError(result.message)
            }
        }
    }

    companion object {
        const val TASK_ID = "tradezero-import"
        const val TASK_LABEL = "TradeZero import"

        /** First-ever sync backfills this many days (the endpoint's one-year maximum). */
        const val BACKFILL_DAYS = 365

        /** Incremental syncs re-fetch this many days before the cursor to catch late-settling fills. */
        const val OVERLAP_DAYS = 3
    }
}
