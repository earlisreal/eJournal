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

    @Test
    fun parseIsoDateTimeDropOffsetDropsPositiveAndNegativeOffsets() {
        assertEquals(LocalDateTime.parse("2026-05-21T03:15:45"), parseIsoDateTimeDropOffset("2026-05-21T03:15:45+1200"))
        assertEquals(LocalDateTime.parse("2023-12-29T09:43:35"), parseIsoDateTimeDropOffset("2023-12-29T09:43:35-0800"))
    }

    @Test
    fun parseIsoDateTimeDropOffsetHandlesZuluAndColonOffset() {
        assertEquals(LocalDateTime.parse("2024-01-03T14:00:00"), parseIsoDateTimeDropOffset("2024-01-03T14:00:00Z"))
        assertEquals(LocalDateTime.parse("2026-05-20T07:53:38"), parseIsoDateTimeDropOffset("2026-05-20T07:53:38+02:00"))
    }

    @Test
    fun parseIsoDateTimeDropOffsetKeepsOffsetlessLocal() {
        assertEquals(LocalDateTime.parse("2026-05-20T07:53:38"), parseIsoDateTimeDropOffset("2026-05-20T07:53:38"))
    }

    @Test
    fun parseIsoDateTimeDropOffsetReturnsNullForGarbage() {
        assertNull(parseIsoDateTimeDropOffset(""))
        assertNull(parseIsoDateTimeDropOffset("not a date"))
        assertNull(parseIsoDateTimeDropOffset("2026-05-20"))
    }
}

class BrokerCsvStructureTest {

    @Test
    fun locateHeaderSkipsBomAndPreambleAndReturnsDataLines() {
        val content = (
            "﻿\"Transactions  for account ...000 as of 09/27/2022 02:03:53 AM ET\"\n" +
                "\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\"\n" +
                "\"05/06/2025\",\"Sell\",\"BNDX\",\"x\",\"8\",\"\$247.37\",\"\$0.06\",\"\$1978.90\""
            ).encodeToByteArray()
        val loc = locateHeader(content) { it.contains("Fees & Comm") }!!
        assertEquals("Date", loc.columns[0])
        assertEquals(8, loc.columns.size)
        assertEquals(1, loc.dataLines.size)
        assertEquals(0, loc.index["date"])
        assertEquals(6, loc.index["fees & comm"])
    }

    @Test
    fun locateHeaderReturnsNullWhenNoMatch() {
        val content = "foo,bar,baz\n1,2,3".encodeToByteArray()
        assertNull(locateHeader(content) { it.contains("Fees & Comm") })
    }

    @Test
    fun fieldReadsCellByNameCaseInsensitively() {
        val loc = locateHeader(
            "Date,Action,Symbol\n05/06/2025,Sell,BNDX".encodeToByteArray(),
        ) { it.startsWith("Date,Action") }!!
        val cells = parseCsvLine(loc.dataLines[0])
        assertEquals("Sell", cells.field(loc.index, "Action"))
        assertEquals("BNDX", cells.field(loc.index, "symbol"))
        assertNull(cells.field(loc.index, "Missing"))
    }

    @Test
    fun naturalKeyIsDeterministicAndDisambiguatesSameKeyRows() {
        val keys = NaturalKeyFactory("schwab")
        val dt = LocalDateTime.parse("2025-05-06T00:00:00")
        assertEquals("schwab:BNDX:2025-05-06T00:00:00:SELL:8.0#0", keys.create("BNDX", dt, io.earlisreal.ejournal.domain.model.Action.SELL, 8.0))
        assertEquals("schwab:BNDX:2025-05-06T00:00:00:SELL:8.0#1", keys.create("BNDX", dt, io.earlisreal.ejournal.domain.model.Action.SELL, 8.0))
    }
}
