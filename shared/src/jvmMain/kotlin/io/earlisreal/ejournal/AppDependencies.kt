package io.earlisreal.ejournal

import io.earlisreal.ejournal.data.PreferencesSettingsRepository
import io.earlisreal.ejournal.data.SqlDelightPortfolioRepository
import io.earlisreal.ejournal.data.SqlDelightTransactionRepository
import io.earlisreal.ejournal.data.database.JvmDatabaseFactory
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.parser.GenericCsvParser
import io.earlisreal.ejournal.domain.parser.TransactionParser

class AppDependencies {
    private val db = JvmDatabaseFactory.create()
    val portfolioRepository: PortfolioRepository = SqlDelightPortfolioRepository(db)
    val transactionRepository: TransactionRepository = SqlDelightTransactionRepository(db)
    val settingsRepository: SettingsRepository = PreferencesSettingsRepository()
    val parsers: List<TransactionParser> = listOf(GenericCsvParser())
}
