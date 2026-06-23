package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSettings
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService

/**
 * Orchestrates the startup sync. TradeZero runs first (when credentials are configured and the
 * selected portfolio has opted in) because the market-data sync derives what to fetch from the
 * transactions TradeZero imports — so any freshly pulled trades are covered by the same startup
 * pass. Market data always runs, even if TradeZero was skipped or failed.
 */
class StartupSyncCoordinator(
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val tradeZeroSyncService: TradeZeroSyncService,
    private val requestMarketDataSync: () -> Unit,
) {
    suspend fun run() {
        if (credentialsRepository.getTradeZeroCredentials() != null) {
            // Only auto-import into a portfolio the user has explicitly selected AND that still
            // exists in this database. The selection lives in OS storage, outside ejournal.db, so a
            // recreated DB can leave a stale selection behind; never sync into a missing portfolio,
            // and never pick one on the user's behalf when nothing is selected.
            val portfolioId = settingsRepository.getFilterPrefs()?.portfolioId
                ?.takeIf { id -> portfolioRepository.getAll().any { it.id == id } }
            // Auto-sync is opt-in per portfolio (default off), so a non-TradeZero portfolio that
            // merely happens to be selected never triggers a pull.
            if (portfolioId != null &&
                portfolioSettings.getBoolean(
                    portfolioId,
                    TradeZeroSettings.AUTO_SYNC_ON_STARTUP,
                    TradeZeroSettings.AUTO_SYNC_DEFAULT,
                )
            ) {
                // The service decides the window: a one-year backfill on first run, then only the
                // gap since the last successful sync.
                tradeZeroSyncService.syncIncremental(portfolioId)
            }
        }
        requestMarketDataSync()
    }
}
