package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.AlpacaBrokerSecrets
import io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioBrokerCredentials
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerClient
import io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerCredentials
import io.earlisreal.ejournal.domain.alpaca.AlpacaConnectionResult
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroConnectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BrokerCredentialDraft {
    val keyId: String
    val secretKey: String

    data class Alpaca(
        override val keyId: String = "",
        override val secretKey: String = "",
        val environment: AlpacaEnvironment = AlpacaEnvironment.PAPER,
    ) : BrokerCredentialDraft

    data class TradeZero(
        override val keyId: String = "",
        override val secretKey: String = "",
    ) : BrokerCredentialDraft
}

sealed interface BrokerConnectionTestResult {
    data class Alpaca(val result: AlpacaConnectionResult) : BrokerConnectionTestResult
    data class TradeZero(val result: TradeZeroConnectionResult) : BrokerConnectionTestResult
}

data class PortfolioManagerState(
    val portfolios: List<Portfolio> = emptyList(),
    val pendingDelete: Portfolio? = null,
    val pendingDeleteCount: Long = 0,
    val error: String? = null,
    val connectionTest: BrokerConnectionTestResult? = null,
    val testingConnection: Boolean = false,
)

/** CRUD over portfolios, including optional broker configuration and its local secret reference. */
class PortfolioManagerViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val alpacaBrokerClient: AlpacaBrokerClient,
    private val tradeZeroClient: TradeZeroClient,
    private val onChanged: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioManagerState())
    val state: StateFlow<PortfolioManagerState> = _state.asStateFlow()

    init { reload() }

    fun globalAlpacaCredentials(): AlpacaMarketDataCredentials? =
        credentialsRepository.getAlpacaMarketDataCredentials()

    fun draftFor(portfolio: Portfolio): BrokerCredentialDraft? =
        when (val credentials = credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef)) {
            is AlpacaBrokerSecrets -> BrokerCredentialDraft.Alpaca(
                credentials.keyId,
                credentials.secretKey,
                portfolio.alpacaEnvironment ?: AlpacaEnvironment.PAPER,
            )
            is TradeZeroBrokerCredentials -> BrokerCredentialDraft.TradeZero(credentials.keyId, credentials.secretKey)
            null -> if (portfolio.broker == Broker.ALPACA) {
                BrokerCredentialDraft.Alpaca(environment = portfolio.alpacaEnvironment ?: AlpacaEnvironment.PAPER)
            } else {
                null
            }
        }

    fun clearConnectionTest() {
        _state.value = _state.value.copy(connectionTest = null)
    }

    fun testConnection(draft: BrokerCredentialDraft?) {
        val stored = draft.toStoredCredentialsOrNull().getOrNull() ?: return
        _state.value = _state.value.copy(testingConnection = true, connectionTest = null, error = null)
        viewModelScope.launch {
            try {
                val result = when (draft) {
                    is BrokerCredentialDraft.Alpaca -> BrokerConnectionTestResult.Alpaca(
                        alpacaBrokerClient.testConnection(
                            AlpacaBrokerCredentials(stored.keyId, stored.secretKey, draft.environment),
                        ),
                    )
                    is BrokerCredentialDraft.TradeZero -> BrokerConnectionTestResult.TradeZero(
                        tradeZeroClient.testConnection(TradeZeroBrokerCredentials(stored.keyId, stored.secretKey)),
                    )
                    null -> return@launch
                }
                _state.value = _state.value.copy(connectionTest = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "Connection test failed")
            } finally {
                _state.value = _state.value.copy(testingConnection = false)
            }
        }
    }

    fun create(name: String, market: Market, broker: Broker?, draft: BrokerCredentialDraft?) {
        if (!validateBrokerMarket(market, broker)) return
        val credentials = draft.toStoredCredentialsOrNull()
        if (credentials.isFailure) {
            _state.value = _state.value.copy(error = credentials.exceptionOrNull()?.message)
            return
        }
        val resolved = credentials.getOrNull()
        if (resolved != null && resolved.broker != broker) {
            _state.value = _state.value.copy(error = "Broker credentials do not match the selected broker")
            return
        }
        val alpacaEnvironment = draft.alpacaEnvironmentFor(broker)
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val portfolio = portfolioRepository.insert(name.trim(), market, broker, alpacaEnvironment)
                try {
                    resolved?.let { credentialsRepository.setPortfolioBrokerCredentials(portfolio.credentialRef, it) }
                } catch (error: Throwable) {
                    credentialsRepository.deletePortfolioBrokerCredentials(portfolio.credentialRef)
                    portfolioRepository.delete(portfolio.id)
                    throw error
                }
            }.onSuccess {
                _state.value = _state.value.copy(error = null)
                reload()
                onChanged()
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message ?: "Could not create portfolio")
            }
        }
    }

    fun update(id: Long, name: String, market: Market, broker: Broker?, draft: BrokerCredentialDraft?) {
        if (!validateBrokerMarket(market, broker)) return
        val credentials = draft.toStoredCredentialsOrNull()
        if (credentials.isFailure) {
            _state.value = _state.value.copy(error = credentials.exceptionOrNull()?.message)
            return
        }
        val resolved = credentials.getOrNull()
        if (resolved != null && resolved.broker != broker) {
            _state.value = _state.value.copy(error = "Broker credentials do not match the selected broker")
            return
        }
        val alpacaEnvironment = draft.alpacaEnvironmentFor(broker)
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val old = portfolioRepository.getById(id) ?: error("Portfolio no longer exists")
                val oldCredentials = credentialsRepository.getPortfolioBrokerCredentials(old.credentialRef)
                val credentialsChanged = oldCredentials != resolved
                val changed = old.broker != broker || old.alpacaEnvironment != alpacaEnvironment || credentialsChanged
                try {
                    if (credentialsChanged) replaceCredentials(old.credentialRef, resolved)
                    portfolioRepository.update(id, name.trim(), market, broker, alpacaEnvironment)
                } catch (error: Throwable) {
                    if (credentialsChanged) {
                        runCatching { replaceCredentials(old.credentialRef, oldCredentials) }
                            .onFailure(error::addSuppressed)
                    }
                    throw error
                }
                if (changed) {
                    old.broker?.let { portfolioSettings.clearNamespace(id, "${it.id}.") }
                    broker?.let { portfolioSettings.clearNamespace(id, "${it.id}.") }
                }
            }.onSuccess {
                _state.value = _state.value.copy(error = null)
                reload()
                onChanged()
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message ?: "Could not update portfolio")
            }
        }
    }

    fun requestDelete(portfolio: Portfolio) {
        viewModelScope.launch {
            val count = transactionRepository.countByPortfolio(portfolio.id)
            _state.value = _state.value.copy(pendingDelete = portfolio, pendingDeleteCount = count)
        }
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pendingDelete = null, pendingDeleteCount = 0)
    }

    fun confirmDelete() {
        val portfolio = _state.value.pendingDelete ?: return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                transactionRepository.deleteByPortfolio(portfolio.id)
                portfolioSettings.clear(portfolio.id)
                credentialsRepository.deletePortfolioBrokerCredentials(portfolio.credentialRef)
                portfolioRepository.delete(portfolio.id)
            }.onSuccess {
                _state.value = _state.value.copy(pendingDelete = null, pendingDeleteCount = 0, error = null)
                reload()
                onChanged()
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message ?: "Could not delete portfolio")
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(portfolios = portfolioRepository.getAll())
        }
    }

    private fun validateBrokerMarket(market: Market, broker: Broker?): Boolean {
        if (broker != null && market != Market.US_STOCKS) {
            _state.value = _state.value.copy(error = "Broker connections are supported for US stock portfolios only")
            return false
        }
        return true
    }

    private fun replaceCredentials(
        credentialRef: String,
        credentials: PortfolioBrokerCredentials?,
    ) {
        if (credentials == null) {
            credentialsRepository.deletePortfolioBrokerCredentials(credentialRef)
        } else {
            credentialsRepository.setPortfolioBrokerCredentials(credentialRef, credentials)
        }
    }
}

private fun BrokerCredentialDraft?.toStoredCredentialsOrNull(): Result<PortfolioBrokerCredentials?> {
    if (this == null || (keyId.isBlank() && secretKey.isBlank())) return Result.success(null)
    if (keyId.isBlank() || secretKey.isBlank()) return Result.failure(IllegalArgumentException("Enter both broker credential fields or leave both blank"))
    return Result.success(
        when (this) {
            is BrokerCredentialDraft.Alpaca -> AlpacaBrokerSecrets(keyId.trim(), secretKey.trim())
            is BrokerCredentialDraft.TradeZero -> TradeZeroBrokerCredentials(keyId.trim(), secretKey.trim())
        },
    )
}

private fun BrokerCredentialDraft?.alpacaEnvironmentFor(broker: Broker?): AlpacaEnvironment? =
    if (broker == Broker.ALPACA) {
        (this as? BrokerCredentialDraft.Alpaca)?.environment ?: AlpacaEnvironment.PAPER
    } else {
        null
    }
