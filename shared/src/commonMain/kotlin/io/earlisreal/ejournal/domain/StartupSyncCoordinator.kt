package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * Orchestrates the startup sync. TradeZero runs first (when enabled and configured) because the
 * market-data sync derives what to fetch from the transactions TradeZero imports — so any freshly
 * pulled trades are covered by the same startup pass. Market data always runs, even if TradeZero
 * was skipped or failed.
 */
class StartupSyncCoordinator(
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val tradeZeroSyncService: TradeZeroSyncService,
    private val requestMarketDataSync: () -> Unit,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    suspend fun run() {
        if (settingsRepository.getAutoSyncTradeZeroOnStartup() &&
            credentialsRepository.getTradeZeroCredentials() != null
        ) {
            val portfolioId = settingsRepository.getFilterPrefs()?.portfolioId
                ?: portfolioRepository.getAll().firstOrNull()?.id
            if (portfolioId != null) {
                val to = today()
                val from = to.minus(LOOKBACK_DAYS, DateTimeUnit.DAY) // last 7 days, matching the manual Sync button
                tradeZeroSyncService.sync(portfolioId, from, to)
            }
        }
        requestMarketDataSync()
    }

    private companion object {
        const val LOOKBACK_DAYS = 6
    }
}
