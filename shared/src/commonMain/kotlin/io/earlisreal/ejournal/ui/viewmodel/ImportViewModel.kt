package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.TransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Per-broker startup flags for the currently-shown portfolio. */
    val autoSyncByBroker: Map<String, Boolean> = emptyMap(),
) {
    /** Compatibility accessor for callers that only know about the legacy TradeZero flag. */
    val autoSyncOnStartup: Boolean
        get() = autoSyncByBroker["tradezero"] ?: false
}

/** Import targets the globally-selected portfolio (passed to [parseFiles]); it does not own portfolio state. */
class ImportViewModel(
    private val transactionRepository: TransactionRepository,
    val parsers: List<TransactionParser>,
    private val portfolioSettings: PortfolioSettingsRepository,
) : ViewModel() {

    // Defaults to Auto-detect (null selectedParser).
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Loads each broker's per-portfolio auto-sync flag; call when the shown portfolio changes. */
    fun loadAutoSync(portfolioId: Long, brokers: List<BrokerSyncService>) {
        val settings = brokers.map { Triple(it.brokerId, it.autoSyncSettingKey, it.autoSyncDefault) }
        loadAutoSyncSettings(portfolioId, settings)
    }

    /** Legacy overload retained for callers that only render TradeZero. */
    fun loadAutoSync(portfolioId: Long) {
        loadAutoSyncSettings(portfolioId, listOf(Triple("tradezero", "tradezero.autoSyncOnStartup", false)))
    }

    private fun loadAutoSyncSettings(portfolioId: Long, brokers: List<Triple<String, String, Boolean>>) {
        viewModelScope.launch {
            val values = brokers.associate { (id, key, default) ->
                id to portfolioSettings.getBoolean(portfolioId, key, default)
            }
            _state.value = _state.value.copy(autoSyncByBroker = values)
        }
    }

    fun setAutoSyncOnStartup(portfolioId: Long, brokerId: String, settingKey: String, enabled: Boolean) {
        _state.value = _state.value.copy(
            autoSyncByBroker = _state.value.autoSyncByBroker + (brokerId to enabled),
        )
        viewModelScope.launch {
            portfolioSettings.putBoolean(portfolioId, settingKey, enabled)
        }
    }

    fun setAutoSyncOnStartup(portfolioId: Long, brokerId: String, enabled: Boolean) =
        setAutoSyncOnStartup(portfolioId, brokerId, "$brokerId.autoSyncOnStartup", enabled)

    /** Legacy overload retained for callers that only render TradeZero. */
    fun setAutoSyncOnStartup(portfolioId: Long, enabled: Boolean) =
        setAutoSyncOnStartup(portfolioId, "tradezero", enabled)

    fun selectParser(parser: TransactionParser?) {
        _state.value = _state.value.copy(
            selectedParser = parser,
            parsedTransactions = emptyList(),
            detectionSummary = null,
        )
    }

    fun parseFiles(files: List<ByteArray>, portfolioId: Long, market: Market) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = parseImportFiles(files, parsers, _state.value.selectedParser, portfolioId, market)
            _state.value = _state.value.copy(
                parsedTransactions = result.transactions,
                detectionSummary = buildSummary(result, files.size, market),
                status = ImportStatus.Idle,
            )
        }
    }

    private fun buildSummary(result: ImportParseResult, fileCount: Int, market: Market): String? {
        if (fileCount == 0) return null
        val parts = result.perParser.entries
            .filter { it.value > 0 }
            .map { "${it.key}: ${it.value}" }
            .toMutableList()
        if (result.skipped.nonTrade > 0) parts += "${result.skipped.nonTrade} non-trade skipped"
        if (result.skipped.options > 0) parts += "${result.skipped.options} options skipped"
        if (result.skipped.offMarket > 0) {
            // eToro mixes asset classes; rows for the other class can't go in this single-asset portfolio.
            val (kind, target) = if (market == Market.CRYPTO) "stock" to "a stocks portfolio"
                                 else "crypto" to "a Crypto portfolio"
            parts += "${result.skipped.offMarket} $kind rows skipped — import them into $target"
        }
        if (result.unrecognizedFiles > 0) parts += "${result.unrecognizedFiles} file(s) not recognized"
        if (parts.isEmpty()) return "No transactions found in the selected file(s)."
        return parts.joinToString(" · ")
    }

    fun import(portfolioId: Long, onSuccess: () -> Unit) {
        // Re-stamp to the portfolio selected *now* (the one the "Into:" pill shows). The parse-time
        // portfolioId is only a placeholder, so switching portfolios after parsing still imports into
        // the live selection. portfolioId never affects externalId, so dedup is unchanged.
        val transactions = _state.value.parsedTransactions.map { it.copy(portfolioId = portfolioId) }
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

    /** Discards the current parse preview, returning the Import screen to its drop-zone state. */
    fun clearParsed() {
        _state.value = _state.value.copy(
            parsedTransactions = emptyList(),
            detectionSummary = null,
            status = ImportStatus.Idle,
        )
    }
}
