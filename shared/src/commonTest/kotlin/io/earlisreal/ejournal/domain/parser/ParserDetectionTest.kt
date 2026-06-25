package io.earlisreal.ejournal.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserDetectionTest {

    // One representative file (header is enough for detect) per auto-detecting broker.
    private val files: Map<String, ByteArray> = mapOf(
        "moomoo" to "\"Side\",\"Symbol\",\"Name\",\"Order Price\",\"Order Qty\",\"Order Amount\",\"Status\",\"Filled@Avg Price\",\"Order Time\"".encodeToByteArray(),
        "TradeZero (CSV)" to "Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note".encodeToByteArray(),
        "Charles Schwab" to ("\"Transactions  for account ...000 as of 09/27/2022\"\n\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\"").encodeToByteArray(),
        "Robinhood" to "Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount".encodeToByteArray(),
        "Webull" to "Name,Symbol,Side,Status,Filled,Total Qty,Price,Avg Price,Time-in-Force,Placed Time,Filled Time".encodeToByteArray(),
        "E*TRADE" to "TransactionDate,TransactionType,SecurityType,Symbol,Quantity,Amount,Price,Commission,Description".encodeToByteArray(),
    )

    private val parsers: List<TransactionParser> = listOf(
        MoomooCsvParser(), TradeZeroCsvParser(), SchwabCsvParser(),
        RobinhoodCsvParser(), WebullCsvParser(), EtradeCsvParser(), GenericCsvParser(),
    )

    @Test
    fun eachFileIsDetectedByExactlyOneParser() {
        for ((broker, file) in files) {
            val matchers = parsers.filter { it.detect(file) }
            assertEquals(1, matchers.size, "$broker matched ${matchers.map { it.brokerName }}")
            assertEquals(broker, matchers.single().brokerName, "$broker routed to the wrong parser")
        }
    }

    @Test
    fun genericNeverAutoDetects() {
        assertTrue(files.values.none { GenericCsvParser().detect(it) })
    }
}
