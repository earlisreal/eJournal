package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
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
)

class SettingsViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val alpacaProvider: AlpacaProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun initialState(): SettingsState {
        val saved = credentialsRepository.getAlpacaCredentials()
        return SettingsState(
            keyId = saved?.keyId.orEmpty(),
            secretKey = saved?.secretKey.orEmpty(),
            hasSavedKeys = saved != null,
        )
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
}
