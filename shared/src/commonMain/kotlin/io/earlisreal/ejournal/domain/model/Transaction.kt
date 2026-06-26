package io.earlisreal.ejournal.domain.model

import kotlinx.datetime.LocalDateTime

data class Transaction(
    val id: Long,
    val portfolioId: Long,
    val symbol: String,
    val datetime: LocalDateTime,
    val action: Action,
    val price: Double,
    val shares: Double,
    val fees: Double,
    /**
     * Stable identifier used to deduplicate imports. TradeZero (both API sync and CSV import) uses a
     * shared natural key (e.g. "tz:AAPL:2026-06-16T00:00:00:BUY:100.0#0") so the same fill from either
     * source collapses to one row; moomoo uses "moomoo:...". Null for sources with no stable key
     * (manual entry) — those are never deduped.
     */
    val externalId: String? = null,
)
