package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.TransactionRepository
import kotlinx.datetime.LocalDate

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
) {
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
    }
}
