package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Market

/**
 * A [TransactionParser] whose source can mix asset classes (eToro statements hold both stocks and
 * crypto). Given the target portfolio's [market], it emits only the matching trades and tallies the
 * rest as [SkipSummary.offMarket]. The import flow calls this overload when a parser implements it,
 * falling back to the asset-agnostic [TransactionParser.parse] for every other (single-asset) broker.
 */
interface MarketAwareParser : TransactionParser {
    fun parse(content: ByteArray, portfolioId: Long, market: Market): ParseResult
}
