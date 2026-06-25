package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs

/**
 * Parses Interactive Brokers' Activity Statement CSV — a multi-section file where column 1 is the section
 * name and column 2 is the row type (Header/Data/SubTotal/Total). We walk line by line, tracking the
 * current `Trades` header (it repeats per asset class), keep only `Trades,Data` rows in the `Stocks` asset
 * category, and skip everything else (other sections, SubTotal/Total, options/other asset classes).
 * Direction is the sign of Quantity (no Buy/Sell column). Date/Time is intraday. Flex Query is out of scope.
 */
class IbkrCsvParser : TransactionParser {
    override val brokerName = "Interactive Brokers"
    override val supportedExtensions = listOf("csv")

    private fun isTradesHeader(line: String) = line.startsWith("Trades,Header,DataDiscriminator")

    override fun detect(content: ByteArray): Boolean = locateHeader(content, ::isTradesHeader) != null

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val keys = NaturalKeyFactory("ibkr")
        val txns = mutableListOf<Transaction>()
        var nonTrade = 0
        var options = 0
        var unparsed = 0
        var index: Map<String, Int>? = null

        val text = content.decodeToString().removePrefix("﻿")
        for (rawLine in text.split("\n")) {
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) continue
            val c = parseCsvLine(line)
            if (c.size < 2 || c[0] != "Trades") continue
            when (c[1]) {
                "Header" -> {
                    index = c.withIndex().associate { (i, name) -> name.trim().lowercase() to i }
                    continue
                }
                "Data" -> {} // handled below
                else -> continue // SubTotal, Total, ...
            }
            val idx = index ?: continue
            val asset = c.field(idx, "Asset Category").orEmpty()
            when {
                asset == "Stocks" -> {} // keep
                asset.contains("Option", ignoreCase = true) -> { options++; continue }
                else -> { nonTrade++; continue } // Forex, Futures, Bonds, ...
            }
            val tx = runCatching {
                val symbol = c.field(idx, "Symbol").orEmpty()
                val qty = cleanMoney(c.field(idx, "Quantity"))
                val price = cleanMoney(c.field(idx, "T. Price"))
                val datetime = parseIbkrDateTime(c.field(idx, "Date/Time").orEmpty())
                if (symbol.isEmpty() || qty == null || qty == 0.0 || price == null || datetime == null) {
                    return@runCatching null
                }
                val dir = if (qty < 0) Action.SELL else Action.BUY
                val shares = abs(qty)
                val fees = abs(cleanMoney(c.field(idx, "Comm/Fee")) ?: 0.0)
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

    // Activity Statement Date/Time is "YYYY-MM-DD, HH:MM:SS" (comma-space). Convert to ISO and parse.
    private fun parseIbkrDateTime(raw: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(raw.trim().replace(", ", "T")) }.getOrNull()
}
