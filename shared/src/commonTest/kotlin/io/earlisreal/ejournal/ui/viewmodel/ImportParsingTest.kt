package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.parser.GenericCsvParser
import io.earlisreal.ejournal.domain.parser.MoomooCsvParser
import io.earlisreal.ejournal.domain.parser.SchwabCsvParser
import io.earlisreal.ejournal.domain.parser.TradeZeroCsvParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportParsingTest {

    private val parsers = listOf(MoomooCsvParser(), TradeZeroCsvParser(), GenericCsvParser())
    private val portfolioId = 1L

    private val moomooFile =
        (""""Side","Symbol","Name","Order Price","Order Qty","Order Amount","Status","Filled@Avg Price","Order Time","Order Type","Time-in-Force","Allow Pre-Market","Session","Trigger price","Position Opening","Markets","Currency","Order Source","Fill Qty","Fill Price","Fill Amount","Fill Time","Markets","Currency","Counterparty","Remarks","Platform Fees","Settlement Fees","Consolidated Audit Trail Fees","SEC Fees","Trading Activity Fees","Total"""" + "\n" +
            """"Buy","SUNE","SUNation Energy","2.47","172","424.84","Filled","172@2.47","Jun 8, 2026 06:51:04 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","172","2.47","424.84","Jun 8, 2026 06:51:05 ET","US","USD","","","0.99","0.52","0","","","1.51"""")
            .encodeToByteArray()

    private val tzFile =
        ("Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note\n" +
            "ESA06710,06/24/2021,06/28/2021,USD,2,B,SE,2,294.248,10:29:39,0.99,0,0,0.033,3.14E-05,0,0,-588.496,-589.5190314,LAMP,,")
            .encodeToByteArray()

    private val unknownFile = "foo,bar,baz\n1,2,3".encodeToByteArray()

    @Test
    fun autoRoutesEachFileToTheParserThatDetectsIt() {
        val result = parseImportFiles(listOf(moomooFile, tzFile), parsers, override = null, portfolioId, market = Market.US_STOCKS)
        assertEquals(2, result.transactions.size)
        assertEquals(1, result.perParser["moomoo"])
        assertEquals(1, result.perParser["TradeZero (CSV)"])
        assertEquals(0, result.unrecognizedFiles)
    }

    @Test
    fun autoMarksFilesNoParserDetectsAsUnrecognized() {
        val result = parseImportFiles(listOf(unknownFile), parsers, override = null, portfolioId, market = Market.US_STOCKS)
        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.unrecognizedFiles)
    }

    @Test
    fun overrideForcesChosenParserAndIgnoresDetection() {
        // A moomoo file but the user forced the TradeZero parser: it produces nothing and is NOT
        // re-routed to the moomoo parser — proving the override path is taken over auto-detect.
        val result = parseImportFiles(listOf(moomooFile), parsers, override = TradeZeroCsvParser(), portfolioId, market = Market.US_STOCKS)
        assertTrue(result.transactions.isEmpty())
        assertEquals(0, result.unrecognizedFiles)
        assertEquals(setOf("TradeZero (CSV)"), result.perParser.keys)
    }

    @Test
    fun overrideParsesWhenFileMatchesChosenParser() {
        val result = parseImportFiles(listOf(tzFile), parsers, override = TradeZeroCsvParser(), portfolioId, market = Market.US_STOCKS)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun aggregatesSkippedRowsAcrossFiles() {
        val schwab = SchwabCsvParser()
        val schwabFile = (
            "\"Transactions  for account ...000 as of 09/27/2022\"\n" +
                "\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\"\n" +
                "\"02/09/2021\",\"Buy\",\"BNDX\",\"x\",\"81\",\"\$57.99\",\"\",\"-\$4697.99\"\n" +
                "\"03/04/2021\",\"Cash Dividend\",\"BNDX\",\"x\",\"\",\"\",\"\",\"\$3.65\""
            ).encodeToByteArray()
        val result = parseImportFiles(listOf(schwabFile), listOf(schwab), override = null, portfolioId, market = Market.US_STOCKS)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.nonTrade)
    }
}
