package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses Fidelity's brokerage transaction-history CSV (`History_for_Account_*.csv` / `Accounts_History.csv`).
 * The file has a UTF-8 BOM + two blank preamble lines before the header (handled by `locateHeader`), and a
 * multi-line disclaimer + "Date downloaded ..." footer. Column order varies between exports (Quantity and
 * Price ($) can swap), so everything is read by name. Trades are `Action` rows containing "YOU BOUGHT" /
 * "YOU SOLD"; option legs (leading-dash symbol) and non-trade rows (dividends, reinvestments, interest,
 * transfers, contributions, tax) are skipped. Dates are date-only -> midnight.
 */
class FidelityCsvParser : TransactionParser {
    override val brokerName = "Fidelity"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) = line.startsWith("Run Date,") && line.contains("Amount ($)")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("fidelity")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0
        var options = 0
        var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            // Data rows always have a parseable Run Date; the disclaimer/"Date downloaded" footer does not.
            if (parseUsDate(c.field(loc.index, "Run Date").orEmpty()) == null) break
            val symbol = c.field(loc.index, "Symbol").orEmpty()
            if (symbol.startsWith("-")) { options++; continue } // Fidelity option symbol " -ROOT..."
            val dir = when {
                c.field(loc.index, "Action").orEmpty().contains("YOU BOUGHT", ignoreCase = true) -> Action.BUY
                c.field(loc.index, "Action").orEmpty().contains("YOU SOLD", ignoreCase = true) -> Action.SELL
                else -> { nonTrade++; continue }
            }
            val tx = runCatching {
                val shares = cleanMoney(c.field(loc.index, "Quantity"))
                val price = cleanMoney(c.field(loc.index, "Price ($)"))
                val datetime = parseUsDateTime(c.field(loc.index, "Run Date").orEmpty())
                if (symbol.isEmpty() || shares == null || price == null || datetime == null) return@runCatching null
                val qty = abs(shares)
                val fees = (cleanMoney(c.field(loc.index, "Commission ($)")) ?: 0.0) +
                    (cleanMoney(c.field(loc.index, "Fees ($)")) ?: 0.0)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = qty, fees = fees,
                    externalId = keys.create(symbol, datetime, dir, qty),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade, options, unparsed))
    }
}
