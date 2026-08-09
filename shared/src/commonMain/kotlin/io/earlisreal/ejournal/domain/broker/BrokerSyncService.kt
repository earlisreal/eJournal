package io.earlisreal.ejournal.domain.broker

import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio

/** Broker-agnostic entry point used by manual and startup synchronization. */
interface BrokerSyncService {
    val brokerId: String
    val displayName: String

    /** Namespaced per-portfolio setting used by the generic startup coordinator and UI. */
    val autoSyncSettingKey: String
        get() = "$brokerId.autoSyncOnStartup"

    val autoSyncDefault: Boolean
        get() = false

    fun isConfigured(portfolio: Portfolio): Boolean
    fun supportsMarket(market: Market): Boolean
    suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome
}
