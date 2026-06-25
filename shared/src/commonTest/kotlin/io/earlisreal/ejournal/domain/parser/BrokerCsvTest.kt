package io.earlisreal.ejournal.domain.parser

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrokerCsvValueTest {

    @Test
    fun cleanMoneyStripsDollarAndCommas() {
        assertEquals(1978.90, cleanMoney("\$1,978.90"))
        assertEquals(57.9999, cleanMoney("\$57.9999"))
        assertEquals(0.06, cleanMoney("\$0.06"))
    }

    @Test
    fun cleanMoneyHandlesLeadingMinusAndAccountingParens() {
        assertEquals(-4697.99, cleanMoney("-\$4697.99"))
        assertEquals(-70.56, cleanMoney("(\$70.56)"))
        assertEquals(-1750.0, cleanMoney("(\$1750.00)"))
    }

    @Test
    fun cleanMoneyReturnsNullForBlanksAndPlaceholders() {
        assertNull(cleanMoney(null))
        assertNull(cleanMoney(""))
        assertNull(cleanMoney("  "))
        assertNull(cleanMoney("--"))
        assertNull(cleanMoney("Processing"))
        assertNull(cleanMoney("N/A"))
    }

    @Test
    fun parseUsDateHandlesUsAndIsoAndTwoDigitYears() {
        assertEquals(LocalDate.parse("2025-05-06"), parseUsDate("05/06/2025"))
        assertEquals(LocalDate.parse("2020-05-26"), parseUsDate("05/26/20"))
        assertEquals(LocalDate.parse("2023-09-18"), parseUsDate("9/18/2023"))
        assertEquals(LocalDate.parse("2024-10-11"), parseUsDate("2024-10-11"))
    }

    @Test
    fun parseUsDateTakesFirstDateOfAsOf() {
        assertEquals(LocalDate.parse("2021-02-25"), parseUsDate("02/25/2021 as of 02/21/2021"))
    }

    @Test
    fun parseUsDateReturnsNullForGarbage() {
        assertNull(parseUsDate(""))
        assertNull(parseUsDate("Transactions Total"))
        assertNull(parseUsDate("13/40/2020"))
    }

    @Test
    fun parseUsDateTimeDefaultsMidnightForDateOnly() {
        assertEquals(LocalDateTime.parse("2025-05-06T00:00:00"), parseUsDateTime("05/06/2025"))
        assertEquals(LocalDateTime.parse("2021-02-25T00:00:00"), parseUsDateTime("02/25/2021 as of 02/21/2021"))
    }

    @Test
    fun parseUsDateTimeParsesTimeAndIgnoresTimezoneToken() {
        assertEquals(LocalDateTime.parse("2021-12-23T09:53:38"), parseUsDateTime("12/23/2021 09:53:38 EST"))
        assertEquals(LocalDateTime.parse("2026-06-24T13:05:00"), parseUsDateTime("6/24/2026 13:05 EDT"))
    }
}
