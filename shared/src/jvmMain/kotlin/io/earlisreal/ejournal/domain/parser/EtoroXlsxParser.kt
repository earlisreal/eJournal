package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Market

/**
 * Imports an eToro account statement in `.xlsx` form. eToro offers the same statement as PDF or XLSX; the
 * XLSX is the structured source (the PDF is just a paginated render), so we read it directly. The
 * spreadsheet plumbing ([Xlsx], JVM-only) is kept separate from the trade semantics ([parseEtoroActivity],
 * in commonMain) so the mapping logic stays platform-agnostic and unit-testable without xlsx bytes.
 *
 * eToro statements mix stocks and crypto, so this is a [MarketAwareParser]: the import flow passes the
 * target portfolio's market and only the matching trades are kept.
 */
class EtoroXlsxParser : MarketAwareParser {
    override val brokerName = "eToro"
    override val supportedExtensions = listOf("xlsx")

    override fun detect(content: ByteArray): Boolean =
        runCatching { isEtoroStatement(Xlsx.read(content).sheetNames) }.getOrDefault(false)

    // The asset-agnostic entry point defaults to the stocks side; the import flow always calls the
    // market-aware overload below, so this is only hit by direct/generic callers.
    override fun parse(content: ByteArray, portfolioId: Long): ParseResult =
        parse(content, portfolioId, Market.US_STOCKS)

    override fun parse(content: ByteArray, portfolioId: Long, market: Market): ParseResult {
        val workbook = runCatching { Xlsx.read(content) }.getOrNull() ?: return ParseResult(emptyList())
        return parseEtoroActivity(workbook.rows(ETORO_ACTIVITY_SHEET), portfolioId, market)
    }
}
