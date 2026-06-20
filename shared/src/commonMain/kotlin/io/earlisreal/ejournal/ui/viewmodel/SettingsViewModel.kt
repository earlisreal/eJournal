package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val keyId: String = "",
    val secretKey: String = "",
    val hasSavedKeys: Boolean = false,
    val justSaved: Boolean = false,
    val testing: Boolean = false,
    val connectionResult: ConnectionResult? = null,
    val tradeZeroKeyId: String = "",
    val tradeZeroSecretKey: String = "",
    val hasSavedTradeZeroCredentials: Boolean = false,
    val tradeZeroJustSaved: Boolean = false,
    val tradeZeroTesting: Boolean = false,
    val tradeZeroConnectionResult: ConnectionResult? = null,
    val autoSyncTradeZeroOnStartup: Boolean = true,
)

class SettingsViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val alpacaProvider: AlpacaProvider,
    private val tradeZeroClient: TradeZeroClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun initialState(): SettingsState {
        val savedAlpaca = credentialsRepository.getAlpacaCredentials()
        val savedTz     = credentialsRepository.getTradeZeroCredentials()
        return SettingsState(
            keyId        = savedAlpaca?.keyId.orEmpty(),
            secretKey    = savedAlpaca?.secretKey.orEmpty(),
            hasSavedKeys = savedAlpaca != null,
            tradeZeroKeyId               = savedTz?.keyId.orEmpty(),
            tradeZeroSecretKey           = savedTz?.secretKey.orEmpty(),
            hasSavedTradeZeroCredentials = savedTz != null,
            autoSyncTradeZeroOnStartup   = settingsRepository.getAutoSyncTradeZeroOnStartup(),
        )
    }

    fun setAutoSyncTradeZeroOnStartup(enabled: Boolean) {
        settingsRepository.setAutoSyncTradeZeroOnStartup(enabled)
        _state.value = _state.value.copy(autoSyncTradeZeroOnStartup = enabled)
    }

    fun updateKeyId(value: String) {
        _state.value = _state.value.copy(keyId = value, justSaved = false, connectionResult = null)
    }

    fun updateSecretKey(value: String) {
        _state.value = _state.value.copy(secretKey = value, justSaved = false, connectionResult = null)
    }

    fun save() {
        val current = _state.value
        if (current.keyId.isBlank() || current.secretKey.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            credentialsRepository.setAlpacaCredentials(AlpacaCredentials(current.keyId.trim(), current.secretKey.trim()))
            _state.value = _state.value.copy(hasSavedKeys = true, justSaved = true)
        }
    }

    fun testConnection() {
        if (_state.value.testing) return
        _state.value = _state.value.copy(testing = true, connectionResult = null)
        viewModelScope.launch {
            val result = alpacaProvider.testConnection()
            _state.value = _state.value.copy(testing = false, connectionResult = result)
        }
    }

    fun updateTradeZeroKeyId(value: String) {
        _state.value = _state.value.copy(tradeZeroKeyId = value, tradeZeroJustSaved = false, tradeZeroConnectionResult = null)
    }

    fun updateTradeZeroSecretKey(value: String) {
        _state.value = _state.value.copy(tradeZeroSecretKey = value, tradeZeroJustSaved = false, tradeZeroConnectionResult = null)
    }

    fun saveTradeZero() {
        val current = _state.value
        if (current.tradeZeroKeyId.isBlank() || current.tradeZeroSecretKey.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            credentialsRepository.setTradeZeroCredentials(
                TradeZeroCredentials(current.tradeZeroKeyId.trim(), current.tradeZeroSecretKey.trim())
            )
            _state.value = _state.value.copy(hasSavedTradeZeroCredentials = true, tradeZeroJustSaved = true)
        }
    }

    fun testTradeZeroConnection() {
        if (_state.value.tradeZeroTesting) return
        _state.value = _state.value.copy(tradeZeroTesting = true, tradeZeroConnectionResult = null)
        viewModelScope.launch {
            val result = tradeZeroClient.testConnection()
            _state.value = _state.value.copy(tradeZeroTesting = false, tradeZeroConnectionResult = result)
        }
    }
}
