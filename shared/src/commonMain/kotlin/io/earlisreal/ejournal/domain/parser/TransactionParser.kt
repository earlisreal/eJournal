package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Transaction

interface TransactionParser {
    val brokerName: String
    val supportedExtensions: List<String>
    fun parse(content: ByteArray, portfolioId: Long): List<Transaction>
}
