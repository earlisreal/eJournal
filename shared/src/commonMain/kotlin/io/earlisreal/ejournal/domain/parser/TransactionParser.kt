package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Transaction

interface TransactionParser {
    val brokerName: String
    val supportedExtensions: List<String>
    fun parse(content: ByteArray, portfolioId: Long): ParseResult

    /**
     * Whether this parser recognizes [content] as its own format (typically by inspecting the
     * header line). Used by the import screen's auto-detect to route each dropped file to the
     * right parser. Must be cheap and side-effect free.
     */
    fun detect(content: ByteArray): Boolean
}
