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

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun monthName(month: Int): String = MONTHS[month - 1]

fun longDate(date: LocalDate): String = "${monthName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"

/** Compact date label, e.g. "Jun 18". */
fun shortDate(date: LocalDate): String = "${MONTHS_SHORT[date.monthNumber - 1]} ${date.dayOfMonth}"

/**
 * Adaptive duration label from a second count: "2d 4h", "3h 12m 5s", "12m 30s", or "45s".
 * Sub-day holds include seconds; multi-day holds collapse to days/hours.
 * Used for the dashboard's average-hold metric, which can span day or swing trades.
 */
fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val days = total / 86400
    val hours = (total % 86400) / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m ${secs}s"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

/** Signed money with a market symbol and two decimals, e.g. "+$1,240.00" / "−₱310.50". */
fun signedMoney(value: Double, symbol: String = "$"): String =
    (if (value < 0) "−" else "+") + symbol + "%,.2f".format(abs(value))

/** Compact money for tight axis labels: "$3.4k", "−$640", "$2.5M", "$0". No leading plus sign. */
fun compactMoney(value: Double, symbol: String = "$"): String {
    val sign = if (value < 0) "−" else ""
    val a = abs(value)
    val mag = when {
        a >= 1_000_000 -> "%.1fM".format(a / 1_000_000)
        a >= 1_000 -> "%.1fk".format(a / 1_000)
        else -> "%.0f".format(a)
    }
    return "$sign$symbol$mag"
}

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
