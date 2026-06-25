package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses Robinhood's account-activity report CSV (header on line 1, no preamble). Buy/sell direction is in
 * `Trans Code`; option legs (BTO/STO/BTC/STC) and non-trade codes (CDIV, ACH, INT, GOLD, ...) are skipped.
 * Symbol comes from `Instrument`; there is no fee column (fees = 0). Dates are date-only -> midnight.
 * Amounts use `$` and accounting parentheses but are not needed for trades (quantity & price are explicit).
 */
class RobinhoodCsvParser : TransactionParser {
    override val brokerName = "Robinhood"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) = line.contains("Trans Code") && line.contains("Instrument")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("rh")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0; var options = 0; var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            val dir = when (c.field(loc.index, "Trans Code").orEmpty().uppercase()) {
                "BUY" -> Action.BUY
                "SELL" -> Action.SELL
                "BTO", "STO", "BTC", "STC" -> { options++; continue }
                else -> { nonTrade++; continue }
            }
            val tx = runCatching {
                val symbol = c.field(loc.index, "Instrument").orEmpty()
                val shares = cleanMoney(c.field(loc.index, "Quantity"))
                val price = cleanMoney(c.field(loc.index, "Price"))
                val datetime = parseUsDateTime(c.field(loc.index, "Activity Date").orEmpty())
                if (symbol.isEmpty() || shares == null || price == null || datetime == null) return@runCatching null
                val qty = abs(shares)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = qty, fees = 0.0,
                    externalId = keys.create(symbol, datetime, dir, qty),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade, options, unparsed))
    }
}
