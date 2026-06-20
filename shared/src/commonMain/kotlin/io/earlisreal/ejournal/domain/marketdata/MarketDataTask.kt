package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.background.BackgroundTask
import io.earlisreal.ejournal.background.TaskProgress
import io.earlisreal.ejournal.background.TaskState

const val MARKET_DATA_TASK_ID = "market-data"
private const val MARKET_DATA_LABEL = "Market data"

/** Human-readable summary of a finished sync; shared by the status bar and the inline import/settings line. */
fun SyncResult.describe(): String = when {
    keysRejected -> "Alpaca keys rejected — check Settings"
    failedSymbols.isNotEmpty() -> "Market data failed for ${failedSymbols.size} symbol(s)"
    needsKeys -> "Market data synced — add Alpaca keys in Settings for intraday older than 30 days"
    fetchedSymbols > 0 -> "Market data fetched for $fetchedSymbols symbol(s)"
    else -> "Market data up to date"
}

/** True when a finished run needs the user's attention (failed fetches or rejected keys). */
fun SyncResult.isFailure(): Boolean = keysRejected || failedSymbols.isNotEmpty()

/**
 * Map the market-data sync's [SyncStatus] onto a generic [BackgroundTask] for the status bar.
 * Returns null for [SyncStatus.Idle] so no entry appears before the first sync.
 */
fun SyncStatus.toBackgroundTask(retry: () -> Unit): BackgroundTask? = when (this) {
    is SyncStatus.Idle -> null
    is SyncStatus.Syncing -> BackgroundTask(
        id = MARKET_DATA_TASK_ID,
        label = MARKET_DATA_LABEL,
        state = TaskState.Running(
            if (total > 0) TaskProgress.Determinate(completed, total) else TaskProgress.Indeterminate,
        ),
        detail = if (total > 0) "$completed/$total symbols" else "Checking…",
    )
    is SyncStatus.Finished -> BackgroundTask(
        id = MARKET_DATA_TASK_ID,
        label = MARKET_DATA_LABEL,
        state = if (result.isFailure()) TaskState.Failed else TaskState.Success,
        detail = result.describe(),
        retry = if (result.isFailure()) retry else null,
    )
}
