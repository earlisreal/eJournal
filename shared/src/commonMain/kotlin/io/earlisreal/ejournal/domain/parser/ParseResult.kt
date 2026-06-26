package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Transaction

/** What a [TransactionParser] produced from one file: the trades plus a tally of rows it deliberately did not emit. */
data class ParseResult(
    val transactions: List<Transaction>,
    val skipped: SkipSummary = SkipSummary(),
)

/** Rows a parser recognized but did not turn into [Transaction]s, by reason — surfaced in the import summary. */
data class SkipSummary(
    val nonTrade: Int = 0,   // dividends, interest, transfers, fees, splits, cancelled orders, footer/total rows
    val options: Int = 0,    // option legs (cannot be modeled as BUY/SELL)
    val unparsed: Int = 0,   // rows that looked like data but failed to parse
    val offMarket: Int = 0,  // trades whose asset class doesn't match the target portfolio's market (e.g. crypto in a stocks portfolio)
) {
    operator fun plus(other: SkipSummary) = SkipSummary(
        nonTrade = nonTrade + other.nonTrade,
        options = options + other.options,
        unparsed = unparsed + other.unparsed,
        offMarket = offMarket + other.offMarket,
    )

    val total: Int get() = nonTrade + options + unparsed + offMarket
}
