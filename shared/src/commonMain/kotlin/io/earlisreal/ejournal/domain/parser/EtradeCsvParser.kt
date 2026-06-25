package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlin.math.abs

/**
 * Parses E*TRADE's classic Transaction History CSV (`DownloadTxnHistory.csv`). Direction is in
 * `TransactionType` (Bought/Sold); `SecurityType` distinguishes equity from options (OPTN -> skipped).
 * Non-trade types (Dividend, Interest, Contribution, Adjustment, ...) are skipped. Quantity is signed for
 * sells (we take the absolute value). Single `Commission` fee column. Dates are `MM/DD/YY`, date-only ->
 * midnight. (Only the classic layout is handled; the newer post-merger "Activity" and Power E*TRADE layouts
 * have different headers and won't match `detect()`.)
 */
class EtradeCsvParser : TransactionParser {
    override val brokerName = "E*TRADE"
    override val supportedExtensions = listOf("csv")

    private fun isHeader(line: String) =
        line.contains("TransactionType") && line.contains("SecurityType")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val loc = locateHeader(content, ::isHeader) ?: return ParseResult(emptyList())
        val keys = NaturalKeyFactory("etrade")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0; var options = 0; var unparsed = 0

        for (line in loc.dataLines) {
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            val type = c.field(loc.index, "TransactionType").orEmpty()
            val dir = when {
                type.equals("Bought", ignoreCase = true) -> Action.BUY
                type.equals("Sold", ignoreCase = true) -> Action.SELL
                else -> { nonTrade++; continue }
            }
            if (c.field(loc.index, "SecurityType").orEmpty().contains("OPT", ignoreCase = true)) {
                options++; continue
            }
            val tx = runCatching {
                val symbol = c.field(loc.index, "Symbol").orEmpty()
                val shares = cleanMoney(c.field(loc.index, "Quantity"))
                val price = cleanMoney(c.field(loc.index, "Price"))
                val datetime = parseUsDateTime(c.field(loc.index, "TransactionDate").orEmpty())
                if (symbol.isEmpty() || shares == null || price == null || datetime == null) return@runCatching null
                val qty = abs(shares)
                Transaction(
                    id = 0L, portfolioId = portfolioId, symbol = symbol, datetime = datetime,
                    action = dir, price = price, shares = qty,
                    fees = cleanMoney(c.field(loc.index, "Commission")) ?: 0.0,
                    externalId = keys.create(symbol, datetime, dir, qty),
                )
            }.getOrNull()
            if (tx != null) txns += tx else unparsed++
        }
        return ParseResult(txns, SkipSummary(nonTrade, options, unparsed))
    }
}
