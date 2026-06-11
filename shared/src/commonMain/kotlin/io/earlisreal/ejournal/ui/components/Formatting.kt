package io.earlisreal.ejournal.ui.components

import kotlinx.datetime.LocalDate
import kotlin.math.abs

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun monthName(month: Int): String = MONTHS[month - 1]

fun longDate(date: LocalDate): String = "${monthName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"

/** Whole-dollar signed money, e.g. "+$1,240" / "−$310". */
fun signedMoney(value: Double): String = (if (value < 0) "−$" else "+$") + "%,.0f".format(abs(value))
