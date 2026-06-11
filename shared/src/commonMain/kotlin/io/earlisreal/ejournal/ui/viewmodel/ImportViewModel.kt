package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TransactionRepository
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
    val selectedParser: TransactionParser? = null,
    val parsedTransactions: List<Transaction> = emptyList(),
    val status: ImportStatus = ImportStatus.Idle,
)

/** Import targets the globally-selected portfolio (passed to [parseFiles]); it does not own portfolio state. */
class ImportViewModel(
    private val transactionRepository: TransactionRepository,
    val parsers: List<TransactionParser>,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState(selectedParser = parsers.firstOrNull()))
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun selectParser(parser: TransactionParser) {
        _state.value = _state.value.copy(selectedParser = parser, parsedTransactions = emptyList())
    }

    fun parseFiles(files: List<ByteArray>, portfolioId: Long) {
        val parser = _state.value.selectedParser ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val transactions = files.flatMap { parser.parse(it, portfolioId) }
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
