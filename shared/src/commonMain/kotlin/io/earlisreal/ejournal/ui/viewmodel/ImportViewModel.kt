package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSettings
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
    /** null means Auto-detect: each dropped file is routed to the parser whose `detect()` matches. */
    val selectedParser: TransactionParser? = null,
    val parsedTransactions: List<Transaction> = emptyList(),
    /** Human-readable breakdown of the last parse (per-broker counts / unrecognized files). */
    val detectionSummary: String? = null,
    val status: ImportStatus = ImportStatus.Idle,
    /** Whether the currently-shown portfolio auto-pulls TradeZero on startup. */
    val autoSyncOnStartup: Boolean = false,
)

/** Import targets the globally-selected portfolio (passed to [parseFiles]); it does not own portfolio state. */
class ImportViewModel(
    private val transactionRepository: TransactionRepository,
    val parsers: List<TransactionParser>,
    private val portfolioSettings: PortfolioSettingsRepository,
) : ViewModel() {

    // Defaults to Auto-detect (null selectedParser).
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Loads the per-portfolio auto-sync flag; call when the shown portfolio changes. */
    fun loadAutoSync(portfolioId: Long) {
        viewModelScope.launch {
            val enabled = portfolioSettings.getBoolean(
                portfolioId, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, TradeZeroSettings.AUTO_SYNC_DEFAULT,
            )
            _state.value = _state.value.copy(autoSyncOnStartup = enabled)
        }
    }

    fun setAutoSyncOnStartup(portfolioId: Long, enabled: Boolean) {
        _state.value = _state.value.copy(autoSyncOnStartup = enabled)
        viewModelScope.launch {
            portfolioSettings.putBoolean(portfolioId, TradeZeroSettings.AUTO_SYNC_ON_STARTUP, enabled)
        }
    }

    fun selectParser(parser: TransactionParser?) {
        _state.value = _state.value.copy(
            selectedParser = parser,
            parsedTransactions = emptyList(),
            detectionSummary = null,
        )
    }

    fun parseFiles(files: List<ByteArray>, portfolioId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = parseImportFiles(files, parsers, _state.value.selectedParser, portfolioId)
            _state.value = _state.value.copy(
                parsedTransactions = result.transactions,
                detectionSummary = buildSummary(result, files.size),
                status = ImportStatus.Idle,
            )
        }
    }

    private fun buildSummary(result: ImportParseResult, fileCount: Int): String? {
        if (fileCount == 0) return null
        val parts = result.perParser.entries
            .filter { it.value > 0 }
            .map { "${it.key}: ${it.value}" }
            .toMutableList()
        if (result.unrecognizedFiles > 0) parts += "${result.unrecognizedFiles} file(s) not recognized"
        if (parts.isEmpty()) return "No transactions found in the selected file(s)."
        return parts.joinToString(" · ")
    }

    fun import(onSuccess: () -> Unit) {
        val transactions = _state.value.parsedTransactions
        if (transactions.isEmpty()) return
        _state.value = _state.value.copy(status = ImportStatus.Importing)
        viewModelScope.launch {
            try {
                // insert() returns null for rows skipped as duplicates (idempotent re-imports),
                // so count actual inserts rather than parsed rows — matching TradeZeroSyncService.
                val inserted = transactions.count { transactionRepository.insert(it) != null }
                _state.value = _state.value.copy(
                    status = ImportStatus.Success(inserted),
                    parsedTransactions = emptyList(),
                    detectionSummary = null,
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
