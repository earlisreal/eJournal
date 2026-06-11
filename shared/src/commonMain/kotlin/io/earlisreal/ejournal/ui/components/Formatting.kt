package io.earlisreal.ejournal.ui.components

import kotlinx.datetime.LocalDate
import kotlin.math.abs

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun monthName(month: Int): String = MONTHS[month - 1]

fun longDate(date: LocalDate): String = "${monthName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"

/** Whole-unit signed money with a market symbol, e.g. "+$1,240" / "−₱310". */
fun signedMoney(value: Double, symbol: String = "$"): String =
    (if (value < 0) "−" else "+") + symbol + "%,.0f".format(abs(value))
