package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import kotlinx.coroutines.CancellationException

/**
 * Orchestrates broker imports before market data. Each configured broker owns its market support and
 * sync implementation; startup only applies the selected portfolio and its opt-in setting.
 */
class StartupSyncCoordinator(
    private val settingsRepository: SettingsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val brokerSyncServices: List<BrokerSyncService>,
    private val requestMarketDataSync: () -> Unit,
) {
    suspend fun run() {
        // Only auto-import into a portfolio the user explicitly selected AND that still exists in
        // this database. The selection lives in OS storage, outside ejournal.db, so a recreated DB
        // can leave a stale selection behind.
        val selectedPortfolio = settingsRepository.getFilterPrefs()?.portfolioId
            ?.let { id -> portfolioRepository.getAll().firstOrNull { it.id == id } }

        if (selectedPortfolio != null) {
            for (service in brokerSyncServices) {
                if (!service.isConfigured() || !service.supportsMarket(selectedPortfolio.market)) continue
                if (!portfolioSettings.getBoolean(
                        selectedPortfolio.id,
                        service.autoSyncSettingKey,
                        service.autoSyncDefault,
                    )
                ) continue

                // A failure in one broker must not suppress another broker or market data.
                try {
                    service.syncIncremental(selectedPortfolio.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("[${service.displayName}] startup sync failed: ${e.message}")
                }
            }
        }
        requestMarketDataSync()
    }
}
