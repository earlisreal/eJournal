package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import kotlinx.coroutines.CancellationException

/**
 * Orchestrates broker imports before market data. Each configured broker owns its market support and
 * sync implementation; startup applies each portfolio's opt-in setting.
 */
class StartupSyncCoordinator(
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val brokerSyncServices: List<BrokerSyncService>,
    private val requestMarketDataSync: () -> Unit,
) {
    suspend fun run(): Set<Long> {
        val changedPortfolioIds = mutableSetOf<Long>()
        portfolioRepository.getAll().forEach { portfolio ->
            val service = portfolio.broker?.let { broker ->
                brokerSyncServices.firstOrNull { it.brokerId == broker.id }
            }
            if (service != null &&
                service.isConfigured(portfolio) &&
                service.supportsMarket(portfolio.market) &&
                portfolioSettings.getBoolean(
                    portfolio.id,
                    service.autoSyncSettingKey,
                    service.autoSyncDefault,
                )
            ) {
                try {
                    if ((service.syncIncremental(portfolio.id) as? BrokerSyncOutcome.Imported)?.changed == true) {
                        changedPortfolioIds += portfolio.id
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("[${service.displayName}] startup sync failed: ${e.message}")
                }
            }
        }
        requestMarketDataSync()
        return changedPortfolioIds
    }
}
