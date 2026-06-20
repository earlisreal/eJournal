package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.domain.marketdata.SyncStatus
import io.earlisreal.ejournal.domain.marketdata.describe
import io.earlisreal.ejournal.domain.marketdata.isFailure
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

/** One quiet line describing the market data sync, with a Retry action when it failed. */
@Composable
fun MarketDataSyncStatus(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when (status) {
        is SyncStatus.Idle -> return
        is SyncStatus.Syncing -> "Fetching market data… ${status.completed}/${status.total} symbols"
        is SyncStatus.Finished -> status.result.describe()
    }
    val failedRun = status is SyncStatus.Finished && status.result.isFailure()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Text(text, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        if (failedRun) AppTextButton(text = "Retry", onClick = onRetry)
    }
}
