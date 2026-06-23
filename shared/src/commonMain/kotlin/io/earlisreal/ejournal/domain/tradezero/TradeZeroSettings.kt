package io.earlisreal.ejournal.domain.tradezero

/**
 * Keys (and defaults) for TradeZero's per-portfolio settings, stored via
 * [io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository]. Keeping them here keeps the
 * settings table generic — a future broker just defines its own namespaced keys.
 */
object TradeZeroSettings {
    /** ISO date through which this portfolio has been synced; absent means "never synced". */
    const val LAST_SYNCED_DATE = "tradezero.lastSyncedDate"

    /** Whether this portfolio auto-pulls TradeZero on startup. */
    const val AUTO_SYNC_ON_STARTUP = "tradezero.autoSyncOnStartup"

    /** Auto-sync is opt-in per portfolio: a portfolio never auto-pulls unless explicitly enabled. */
    const val AUTO_SYNC_DEFAULT = false
}
