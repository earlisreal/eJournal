package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FidelityCsvParserTest {

    private val parser = FidelityCsvParser()
    private val portfolioId = 10L

    private val header = "Run Date,Action,Symbol,Description,Type,Quantity,Price ($),Commission ($),Fees ($),Accrued Interest ($),Amount ($),Cash Balance ($),Settlement Date"
    private val buy = "07/07/2025,\"YOU BOUGHT . EXCHANGE FROM FXAIX FIDELITY U.S. BOND INDEX FUND (FXNAX) (Cash)\",FXNAX,\"FIDELITY U.S. BOND INDEX FUND\",Cash,8301.158,10.36,,,,-86000,--,07/07/2025"
    private val sell = "07/07/2025,\"YOU SOLD EXCHANGE TO FXNAX FIDELITY 500 INDEX FUND (FXAIX) (Cash)\",FXAIX,\"FIDELITY 500 INDEX FUND\",Cash,-331.291,217.03,,,,71900,--,07/07/2025"
    private val dividend = "06/30/2025,\"DIVIDEND RECEIVED FIDELITY U.S. BOND INDEX FUND (FXNAX) (Cash)\",FXNAX,\"FIDELITY U.S. BOND INDEX FUND\",Cash,0.000,,,,,2389.28,--,"
    private val reinvest = "06/30/2025,\"REINVESTMENT FIDELITY U.S. BOND INDEX FUND (FXNAX) (Cash)\",FXNAX,\"FIDELITY U.S. BOND INDEX FUND\",Cash,228.858,10.44,,,,-2389.28,--,"
    private val option = "04/24/2026,\"YOU SOLD OPENING TRANSACTION PUT (UMAC) UNUSUAL MACHS INC MAY 01 26 \$13.5 (100 SHS) (Cash)\", -UMAC260501P13.5,\"PUT (UMAC)\",Cash,0.41,-1,0.65,0.02,,40.33,10252.02,04/27/2026"
    private val footer = "\"The data and information in this spreadsheet is provided to you solely for your use and is not for distribution. The spreadsheet is provided for\""
    private val downloaded = "Date downloaded 07/11/2025 7:51 pm"

    // Real Fidelity export shape: UTF-8 BOM, two blank preamble lines, then the header.
    private fun csv(vararg rows: String): ByteArray =
        ("﻿\n\n" + header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsPastBomAndPreamble() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsSchwabHeader() {
        val schwab = "\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\""
        assertFalse(parser.detect(schwab.encodeToByteArray()))
    }

    @Test
    fun parsesBuyAndSellReadingByName() {
        val r = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, r.transactions.size)
        val b = r.transactions.first { it.action == Action.BUY }
        assertEquals("FXNAX", b.symbol)
        assertEquals(8301.158, b.shares)
        assertEquals(10.36, b.price)
        assertEquals(0.0, b.fees)
        assertEquals(LocalDateTime.parse("2025-07-07T00:00:00"), b.datetime)
        val s = r.transactions.first { it.action == Action.SELL }
        assertEquals("FXAIX", s.symbol)
        assertEquals(331.291, s.shares) // abs of -331.291
        assertEquals(217.03, s.price)
    }

    @Test
    fun skipsDividendReinvestAndOptions() {
        val r = parser.parse(csv(buy, dividend, reinvest, option), portfolioId)
        assertEquals(1, r.transactions.size)
        assertEquals(2, r.skipped.nonTrade) // dividend + reinvestment
        assertEquals(1, r.skipped.options)  // UMAC put (leading-dash symbol)
    }

    @Test
    fun stopsAtFooterDisclaimer() {
        val r = parser.parse(csv(buy, "", "", footer, downloaded), portfolioId)
        assertEquals(1, r.transactions.size)
        assertEquals(0, r.skipped.nonTrade) // footer/disclaimer not counted
    }

    @Test
    fun handlesSwappedPriceQuantityColumnOrder() {
        val altHeader = "Run Date,Action,Symbol,Description,Type,Price ($),Quantity,Commission ($),Fees ($),Accrued Interest ($),Amount ($),Cash Balance ($),Settlement Date"
        val altBuy = "07/07/2025,\"YOU BOUGHT FIDELITY U.S. BOND INDEX FUND (FXNAX) (Cash)\",FXNAX,\"x\",Cash,10.36,8301.158,,,,-86000,--,07/07/2025"
        val bytes = ("﻿\n\n" + altHeader + "\n" + altBuy).encodeToByteArray()
        val tx = parser.parse(bytes, portfolioId).transactions.single()
        assertEquals(8301.158, tx.shares) // read by name, not position
        assertEquals(10.36, tx.price)
    }

    @Test
    fun buildsExternalId() {
        val tx = parser.parse(csv(buy), portfolioId).transactions.single()
        assertEquals("fidelity:FXNAX:2025-07-07T00:00:00:BUY:8301.158#0", tx.externalId)
    }
}
