package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses Webull's order-history export (`Webull_Orders_Records.csv`), one row per order. Keeps orders that
 * actually executed (`Filled` > 0) with `Side` Buy/Sell, using the executed `Filled` quantity and `Avg Price`
 * (not the requested `Total Qty`/limit `Price`). Cancelled/failed/unfilled orders are skipped. `Filled Time`
 * carries a real intraday Eastern wall-clock timestamp (with an EST/EDT token we ignore). No fee column
 * (fees = 0). Options are exported to a separate file and are not handled here.
 */
class WebullCsvParser : TransactionParser {
    override val brokerName = "Webull"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) =
        line.contains("Avg Price") && line.contains("Filled Time") && line.contains("Side")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("webull")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0; var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            val side = c.field(loc.index, "Side").orEmpty()
            val dir = when {
                side.equals("Buy", ignoreCase = true) -> Action.BUY
                side.equals("Sell", ignoreCase = true) -> Action.SELL
                else -> { nonTrade++; continue }
            }
            val filled = cleanMoney(c.field(loc.index, "Filled"))
            if (filled == null || filled <= 0.0) { nonTrade++; continue } // cancelled / failed / unfilled
            val tx = runCatching {
                val symbol = c.field(loc.index, "Symbol").orEmpty()
                val price = cleanMoney(c.field(loc.index, "Avg Price"))
                val datetime = parseUsDateTime(c.field(loc.index, "Filled Time").orEmpty())
                if (symbol.isEmpty() || price == null || datetime == null) return@runCatching null
                val qty = abs(filled)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = qty, fees = 0.0,
                    externalId = keys.create(symbol, datetime, dir, qty),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade = nonTrade, unparsed = unparsed))
    }
}
