package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses Charles Schwab's web "History"/"Transactions" CSV export (`*_Transactions_*.csv`). The file has a
 * one-line title preamble before the header and a "Transactions Total" footer row. Trades are the `Action`
 * = Buy/Sell rows; option legs ("... to Open/Close") and everything else (dividends, interest, wires,
 * journals, splits, fees) are skipped. Dates are date-only (no execution time) -> midnight.
 */
class SchwabCsvParser : TransactionParser {
    override val brokerName = "Charles Schwab"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) = line.contains("Fees & Comm")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("schwab")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0; var options = 0; var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            if (c.field(loc.index, "Date") == "Transactions Total") continue // footer, not counted
            val action = c.field(loc.index, "Action").orEmpty()
            val dir = when {
                action.equals("Buy", ignoreCase = true) -> Action.BUY
                action.equals("Sell", ignoreCase = true) -> Action.SELL
                action.contains("to Open", ignoreCase = true) ||
                    action.contains("to Close", ignoreCase = true) -> { options++; continue }
                else -> { nonTrade++; continue }
            }
            val tx = runCatching {
                val symbol = c.field(loc.index, "Symbol").orEmpty()
                val shares = cleanMoney(c.field(loc.index, "Quantity"))
                val price = cleanMoney(c.field(loc.index, "Price"))
                val datetime = parseUsDateTime(c.field(loc.index, "Date").orEmpty())
                if (symbol.isEmpty() || shares == null || price == null || datetime == null) return@runCatching null
                val qty = abs(shares)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = qty,
                    fees = cleanMoney(c.field(loc.index, "Fees & Comm")) ?: 0.0,
                    externalId = keys.create(symbol, datetime, dir, qty),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade, options, unparsed))
    }
}
