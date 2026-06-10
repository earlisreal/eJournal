package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.TransactionParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ImportStatus {
    object Idle : ImportStatus()
    object Importing : ImportStatus()
    data class Success(val count: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

data class ImportState(
    val portfolios: List<Portfolio> = emptyList(),
    val selectedPortfolio: Portfolio? = null,
    val selectedParser: TransactionParser? = null,
    val parsedTransactions: List<Transaction> = emptyList(),
    val status: ImportStatus = ImportStatus.Idle,
)

class ImportViewModel(
    private val transactionRepository: TransactionRepository,
    private val portfolioRepository: PortfolioRepository,
    val parsers: List<TransactionParser>,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val portfolios = portfolioRepository.getAll()
            _state.value = _state.value.copy(
                portfolios = portfolios,
                selectedPortfolio = portfolios.firstOrNull(),
                selectedParser = parsers.firstOrNull(),
            )
        }
    }

    fun selectPortfolio(portfolio: Portfolio) {
        _state.value = _state.value.copy(selectedPortfolio = portfolio)
    }

    fun selectParser(parser: TransactionParser) {
        _state.value = _state.value.copy(selectedParser = parser, parsedTransactions = emptyList())
    }

    fun parseFiles(files: List<ByteArray>) {
        val portfolio = _state.value.selectedPortfolio ?: return
        val parser = _state.value.selectedParser ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val transactions = files.flatMap { parser.parse(it, portfolio.id) }
            _state.value = _state.value.copy(
                parsedTransactions = transactions,
                status = ImportStatus.Idle,
            )
        }
    }

    fun import(onSuccess: () -> Unit) {
        val transactions = _state.value.parsedTransactions
        if (transactions.isEmpty()) return
        _state.value = _state.value.copy(status = ImportStatus.Importing)
        viewModelScope.launch {
            try {
                transactions.forEach { transactionRepository.insert(it) }
                _state.value = _state.value.copy(
                    status = ImportStatus.Success(transactions.size),
                    parsedTransactions = emptyList(),
                )
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = ImportStatus.Error(e.message ?: "Import failed")
                )
            }
        }
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = ImportStatus.Idle)
    }
}
