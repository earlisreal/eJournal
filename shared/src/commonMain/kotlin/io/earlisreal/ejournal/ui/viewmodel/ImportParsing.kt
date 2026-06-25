package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.SkipSummary
import io.earlisreal.ejournal.domain.parser.TransactionParser

/**
 * Outcome of parsing dropped import files.
 *
 * @param transactions all parsed transactions across every file
 * @param perParser broker name -> number of transactions it produced (used for a status summary)
 * @param unrecognizedFiles count of files no parser detected (auto-detect mode only)
 * @param skipped aggregated count of rows parsers recognized but did not emit (non-trade/options/unparsed)
 */
data class ImportParseResult(
    val transactions: List<Transaction>,
    val perParser: Map<String, Int>,
    val unrecognizedFiles: Int,
    val skipped: SkipSummary = SkipSummary(),
)

/**
 * Routes each file to a parser and parses it. When [override] is non-null every file is parsed with
 * that parser (manual selection); otherwise each file goes to the first parser whose `detect()`
 * recognizes it, and files nothing recognizes are counted as unrecognized.
 */
fun parseImportFiles(
    files: List<ByteArray>,
    parsers: List<TransactionParser>,
    override: TransactionParser?,
    portfolioId: Long,
): ImportParseResult {
    val transactions = mutableListOf<Transaction>()
    val perParser = mutableMapOf<String, Int>()
    var unrecognizedFiles = 0
    var skipped = SkipSummary()

    for (file in files) {
        val parser = override ?: parsers.firstOrNull { it.detect(file) }
        if (parser == null) {
            unrecognizedFiles++
            continue
        }
        val result = parser.parse(file, portfolioId)
        transactions += result.transactions
        perParser[parser.brokerName] = (perParser[parser.brokerName] ?: 0) + result.transactions.size
        skipped += result.skipped
    }

    return ImportParseResult(transactions, perParser, unrecognizedFiles, skipped)
}
