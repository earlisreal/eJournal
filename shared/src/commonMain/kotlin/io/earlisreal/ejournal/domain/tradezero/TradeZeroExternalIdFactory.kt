package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.math.round

/**
 * Builds the dedup [externalId][io.earlisreal.ejournal.domain.model.Transaction.externalId] shared by
 * the TradeZero API sync ([TradeZeroClientImpl]) and the TradeZero CSV import
 * ([io.earlisreal.ejournal.domain.parser.TradeZeroCsvParser]) so the same fill from either source
 * collapses to one row via `INSERT OR IGNORE`.
 *
 * The CSV carries no execution id, so the key is a natural key: `tz:symbol:second:SIDE:shares`.
 * - **Price is excluded on purpose**: the API and CSV feeds report it at different precision, and a
 *   mismatch would silently defeat dedup. The occurrence ordinal below already keeps the count of
 *   identical same-second fills correct, and both rows still store their own price.
 * - **Datetime is truncated to the second**: the API can yield sub-second instants while the CSV is
 *   second-granular; they must land on the same key.
 * - **An occurrence ordinal `#n`** disambiguates otherwise-identical fills within one second. The set
 *   of ids for such a group is `{#0..#k-1}` regardless of traversal order, so re-import/re-sync stays
 *   idempotent and the two sources dedup against each other.
 *
 * One instance per fetch/parse (it accumulates ordinals); it is not thread-safe.
 */
class TradeZeroExternalIdFactory {
    private val occurrences = mutableMapOf<String, Int>()

    fun create(symbol: String, datetime: LocalDateTime, action: Action, shares: Double): String {
        val base = "tz:$symbol:${atSecond(datetime)}:${action.name}:${normalizeShares(shares)}"
        val ordinal = occurrences.getOrElse(base) { 0 }
        occurrences[base] = ordinal + 1
        return "$base#$ordinal"
    }

    private fun atSecond(dt: LocalDateTime): String {
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        val ss = dt.second.toString().padStart(2, '0')
        return "${dt.date}T$hh:$mm:$ss"
    }

    // Round to 4dp so float noise can't split the same logical share count across feeds.
    private fun normalizeShares(shares: Double): String = (round(shares * 10_000.0) / 10_000.0).toString()
}
