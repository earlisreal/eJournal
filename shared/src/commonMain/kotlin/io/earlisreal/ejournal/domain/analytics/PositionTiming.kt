package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

fun marketClock(market: Market): TimeZone = TimeZone.of(when (market) {
    Market.US_STOCKS -> "America/New_York"
    Market.PH_STOCKS -> "Asia/Manila"
    Market.CRYPTO -> "UTC"
})

fun ClosedPosition.verifiedEntryTime() = transactions.firstOrNull()?.verifiedExecutionInstant?.toLocalDateTime(marketClock(market))

fun ClosedPosition.verifiedEntryDate() = verifiedEntryTime()?.date ?: transactions.firstOrNull()?.verifiedMarketDate

fun ClosedPosition.verifiedHoldSeconds(): Double? {
    if (!isClosed) return null
    val opening = transactions.firstOrNull()?.verifiedExecutionInstant ?: return null
    val closing = transactions.lastOrNull()?.verifiedExecutionInstant ?: return null
    if (transactions.first().datetime != entryDatetime || transactions.last().datetime != exitDatetime) return null
    return (closing - opening).toDouble(kotlin.time.DurationUnit.SECONDS).takeIf { it >= 0.0 }
}

/** Eastern execution clocks repeat during fall-back and skip during spring-forward. */
fun unambiguousEasternInstant(local: kotlinx.datetime.LocalDateTime): kotlin.time.Instant? {
    val zone = TimeZone.of("America/New_York")
    val guess = local.toInstant(zone)
    return listOf(guess - 1.hours, guess, guess + 1.hours)
        .filter { it.toLocalDateTime(zone) == local }.singleOrNull()
}
