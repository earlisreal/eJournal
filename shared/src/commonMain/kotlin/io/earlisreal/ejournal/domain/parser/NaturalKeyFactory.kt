package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlin.math.round

/**
 * Builds dedup `externalId`s for broker imports as a natural key:
 * `prefix:symbol:second:ACTION:normalizedShares#ordinal`.
 *
 * Mirrors [io.earlisreal.ejournal.domain.tradezero.TradeZeroExternalIdFactory] (which stays separate
 * because it is shared with TradeZero's live API sync). Price is excluded on purpose (feeds report it at
 * different precision); datetime is truncated to the second; the `#ordinal` disambiguates otherwise-identical
 * rows within one file so re-imports stay idempotent. For date-only brokers the second is `00:00:00`, so
 * same-day same-symbol same-direction same-qty fills share a key and the ordinal keeps them distinct.
 *
 * One instance per parse (it accumulates ordinals); not thread-safe.
 */
class NaturalKeyFactory(private val prefix: String) {
    private val occurrences = mutableMapOf<String, Int>()

    fun create(symbol: String, datetime: kotlinx.datetime.LocalDateTime, action: Action, shares: Double): String {
        val base = "$prefix:$symbol:${atSecond(datetime)}:${action.name}:${normalizeShares(shares)}"
        val ordinal = occurrences.getOrElse(base) { 0 }
        occurrences[base] = ordinal + 1
        return "$base#$ordinal"
    }

    private fun atSecond(dt: kotlinx.datetime.LocalDateTime): String {
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        val ss = dt.second.toString().padStart(2, '0')
        return "${dt.date}T$hh:$mm:$ss"
    }

    private fun normalizeShares(shares: Double): String = (round(shares * 10_000.0) / 10_000.0).toString()
}
