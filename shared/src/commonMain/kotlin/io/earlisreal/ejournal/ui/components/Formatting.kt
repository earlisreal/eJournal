package io.earlisreal.ejournal.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun monthName(month: Int): String = MONTHS[month - 1]

fun longDate(date: LocalDate): String = "${monthName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"

/** Signed money with a market symbol and two decimals, e.g. "+$1,240.00" / "−₱310.50". */
fun signedMoney(value: Double, symbol: String = "$"): String =
    (if (value < 0) "−" else "+") + symbol + "%,.2f".format(abs(value))

/**
 * Hold-duration label between two timestamps. Day trades show hours/minutes/seconds
 * ("2h 15m 30s"); swing trades collapse to whole days ("3d").
 */
fun formatHold(entry: LocalDateTime, exit: LocalDateTime, isDay: Boolean): String {
    val seconds = exit.toInstant(TimeZone.UTC).epochSeconds - entry.toInstant(TimeZone.UTC).epochSeconds
    return if (isDay) {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        "${h}h ${m}m ${s}s"
    } else {
        "${seconds / 86400}d"
    }
}
