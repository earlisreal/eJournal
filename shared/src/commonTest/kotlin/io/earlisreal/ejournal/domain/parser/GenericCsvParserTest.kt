package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GenericCsvParserTest {

    private val parser = GenericCsvParser()
    private val portfolioId = 1L

    private fun csv(vararg rows: String): ByteArray =
        ("Date,Symbol,Action,Price,Shares,Fees\n" + rows.joinToString("\n"))
            .encodeToByteArray()

    @Test
    fun parsesBasicBuyRow() {
        val content = csv("2024-01-01T09:30,BDO,BUY,100.0,200.0,20.0")
        val result = parser.parse(content, portfolioId).transactions
        assertEquals(1, result.size)
        assertEquals("BDO", result[0].symbol)
        assertEquals(Action.BUY, result[0].action)
        assertEquals(100.0, result[0].price)
        assertEquals(200.0, result[0].shares)
        assertEquals(20.0, result[0].fees)
        assertEquals(LocalDateTime.parse("2024-01-01T09:30"), result[0].datetime)
        assertEquals(portfolioId, result[0].portfolioId)
    }

    @Test
    fun parsesMultipleRows() {
        val content = csv(
            "2024-01-01T09:30,BDO,BUY,100.0,200.0,20.0",
            "2024-01-10T09:30,BDO,SELL,120.0,200.0,25.0"
        )
        val result = parser.parse(content, portfolioId).transactions
        assertEquals(2, result.size)
        assertEquals(Action.SELL, result[1].action)
    }

    @Test
    fun skipsBlankLines() {
        val content = csv(
            "2024-01-01T09:30,BDO,BUY,100.0,200.0,20.0",
            "",
            "2024-01-10T09:30,BDO,SELL,120.0,200.0,25.0"
        )
        assertEquals(2, parser.parse(content, portfolioId).transactions.size)
    }

    @Test
    fun skipsMalformedRowsButKeepsValidOnes() {
        val content = csv(
            "2024-01-01T09:30,BDO,BUY,100.0,200.0,20.0",
            "2024-01-02T09:30,BDO,NOPE,100.0,200.0,20.0", // unknown action -> Action.valueOf throws
            "not,enough,columns",                         // too short -> IndexOutOfBounds
            "2024-01-03T09:30,BDO,SELL,abc,200.0,20.0",    // non-numeric price -> toDouble throws
            "2024-01-10T09:30,BDO,SELL,120.0,200.0,25.0",
        )
        val result = parser.parse(content, portfolioId)
        assertEquals(2, result.transactions.size)
        assertEquals(3, result.skipped.unparsed)
        assertEquals(Action.BUY, result.transactions[0].action)
        assertEquals(Action.SELL, result.transactions[1].action)
    }

    @Test
    fun neverAutoDetects() {
        assertFalse(parser.detect(csv("2024-01-01T09:30,BDO,BUY,100.0,200.0,20.0")))
    }

    @Test
    fun actionIsCaseInsensitive() {
        val content = csv("2024-01-01T09:30,BDO,buy,100.0,200.0,20.0")
        assertEquals(Action.BUY, parser.parse(content, portfolioId).transactions[0].action)
    }
}
