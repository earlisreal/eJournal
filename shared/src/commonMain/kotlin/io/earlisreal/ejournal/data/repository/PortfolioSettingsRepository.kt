package io.earlisreal.ejournal.data.repository

/**
 * Generic, broker-agnostic per-portfolio key/value settings, backed by the database so they share
 * its lifecycle. Keys are namespaced strings (e.g. "tradezero.lastSyncedDate"). Unlike OS-level
 * preferences, these vanish when the database is recreated — which is what we want for metadata that
 * describes the DB's own content (e.g. a broker sync cursor), so it can never outlive that content.
 */
interface PortfolioSettingsRepository {
    suspend fun getString(portfolioId: Long, key: String): String?
    suspend fun putString(portfolioId: Long, key: String, value: String)

    /** Returns [default] when the key is unset or holds a non-boolean value. */
    suspend fun getBoolean(portfolioId: Long, key: String, default: Boolean): Boolean
    suspend fun putBoolean(portfolioId: Long, key: String, value: Boolean)

    /** Removes every setting for the portfolio (used when the portfolio is deleted). */
    suspend fun clear(portfolioId: Long)
}
