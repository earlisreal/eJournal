package io.earlisreal.ejournal.domain.parser

/**
 * Imports an eToro account statement in `.xlsx` form. eToro offers the same statement as PDF or XLSX; the
 * XLSX is the structured source (the PDF is just a paginated render), so we read it directly. The
 * spreadsheet plumbing ([Xlsx], JVM-only) is kept separate from the trade semantics ([parseEtoroActivity],
 * in commonMain) so the mapping logic stays platform-agnostic and unit-testable without xlsx bytes.
 */
class EtoroXlsxParser : TransactionParser {
    override val brokerName = "eToro"
    override val supportedExtensions = listOf("xlsx")

    override fun detect(content: ByteArray): Boolean =
        runCatching { isEtoroStatement(Xlsx.read(content).sheetNames) }.getOrDefault(false)

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val workbook = runCatching { Xlsx.read(content) }.getOrNull() ?: return ParseResult(emptyList())
        return parseEtoroActivity(workbook.rows(ETORO_ACTIVITY_SHEET), portfolioId)
    }
}
