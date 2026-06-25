package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses Tastytrade's transactions CSV (header on line 1; current 21-col and legacy 20-col both read by
 * name). Keeps only `Type == "Trade"` rows with `Instrument Type == "Equity"`; option/future legs and
 * Money-Movement / Receive-Deliver rows are skipped. Direction comes from the `Action` code (BUY_x/SELL_x),
 * NOT the Value/Total sign (those are inconsistent across real exports). Datetime is ISO-8601 with a
 * timezone offset that we drop (wall-clock). Fees = |Commissions| + |Fees|.
 */
class TastytradeCsvParser : TransactionParser {
    override val brokerName = "Tastytrade"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) = line.contains("Sub Type") && line.contains("Instrument Type")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("tasty")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0
        var options = 0
        var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            if (c.field(loc.index, "Type") != "Trade") { nonTrade++; continue } // Money Movement / Receive Deliver
            if (c.field(loc.index, "Instrument Type") != "Equity") { options++; continue } // option/future legs
            val dir = when {
                c.field(loc.index, "Action").orEmpty().startsWith("BUY") -> Action.BUY
                c.field(loc.index, "Action").orEmpty().startsWith("SELL") -> Action.SELL
                else -> { nonTrade++; continue }
            }
            val tx = runCatching {
                val symbol = c.field(loc.index, "Symbol").orEmpty()
                val shares = cleanMoney(c.field(loc.index, "Quantity"))?.let { abs(it) }
                val price = cleanMoney(c.field(loc.index, "Average Price"))?.let { abs(it) }
                val datetime = parseIsoDateTimeDropOffset(c.field(loc.index, "Date").orEmpty())
                if (symbol.isEmpty() || shares == null || price == null || datetime == null) return@runCatching null
                val fees = abs(cleanMoney(c.field(loc.index, "Commissions")) ?: 0.0) +
                    abs(cleanMoney(c.field(loc.index, "Fees")) ?: 0.0)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = shares, fees = fees,
                    externalId = keys.create(symbol, datetime, dir, shares),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade, options, unparsed))
    }
}
