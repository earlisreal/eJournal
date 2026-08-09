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
import io.earlisreal.ejournal.domain.moomoo.MoomooAccount
import io.earlisreal.ejournal.domain.moomoo.MoomooAccountEnvironment
import io.earlisreal.ejournal.domain.moomoo.MoomooAccountRole
import io.earlisreal.ejournal.domain.moomoo.MoomooClient
import io.earlisreal.ejournal.domain.moomoo.MoomooConnectionResult
import io.earlisreal.ejournal.domain.moomoo.MoomooMarket
import io.earlisreal.ejournal.domain.moomoo.MoomooPortfolioConfig
import io.earlisreal.ejournal.domain.moomoo.MoomooSettings
import io.earlisreal.ejournal.domain.moomoo.discoverEligibleAccounts
import io.earlisreal.ejournal.domain.moomoo.eligibleForUsStocks
import io.earlisreal.ejournal.domain.moomoo.getMoomooConfig
import io.earlisreal.ejournal.domain.moomoo.putMoomooConfig
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

    data class Moomoo(
        val port: String = MoomooSettings.DEFAULT_PORT.toString(),
        val account: MoomooAccount? = null,
    ) : BrokerCredentialDraft {
        override val keyId: String = ""
        override val secretKey: String = ""
    }
}

sealed interface BrokerConnectionTestResult {
    data class Alpaca(val result: AlpacaConnectionResult) : BrokerConnectionTestResult
    data class TradeZero(val result: TradeZeroConnectionResult) : BrokerConnectionTestResult
    data class Moomoo(val result: MoomooConnectionResult) : BrokerConnectionTestResult
}

data class PortfolioManagerState(
    val portfolios: List<Portfolio> = emptyList(),
    val pendingDelete: Portfolio? = null,
    val pendingDeleteCount: Long = 0,
    val error: String? = null,
    val connectionTest: BrokerConnectionTestResult? = null,
    val testingConnection: Boolean = false,
    val moomooAccounts: List<MoomooAccount> = emptyList(),
    val moomooConfigs: Map<Long, MoomooPortfolioConfig> = emptyMap(),
)

/** CRUD over portfolios, including optional broker configuration and its local secret reference. */
class PortfolioManagerViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val alpacaBrokerClient: AlpacaBrokerClient,
    private val tradeZeroClient: TradeZeroClient,
    private val moomooClient: MoomooClient? = null,
    private val onChanged: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioManagerState())
    val state: StateFlow<PortfolioManagerState> = _state.asStateFlow()

    init { reload() }

    fun globalAlpacaCredentials(): AlpacaMarketDataCredentials? =
        credentialsRepository.getAlpacaMarketDataCredentials()

    fun draftFor(portfolio: Portfolio): BrokerCredentialDraft? {
        if (portfolio.broker == Broker.MOOMOO) {
            val config = _state.value.moomooConfigs[portfolio.id]
            return BrokerCredentialDraft.Moomoo(
                port = (config?.port ?: MoomooSettings.DEFAULT_PORT).toString(),
                account = config?.toAccount(),
            )
        }
        return when (val credentials = credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef)) {
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
    }

    fun clearConnectionTest() {
        _state.value = _state.value.copy(connectionTest = null, moomooAccounts = emptyList())
    }

    fun testConnection(draft: BrokerCredentialDraft?) {
        if (draft is BrokerCredentialDraft.Moomoo) {
            val port = draft.port.toIntOrNull()?.takeIf { it in 1..65535 }
            if (port == null) {
                _state.value = _state.value.copy(error = "OpenD port must be between 1 and 65535")
                return
            }
            val client = moomooClient
            if (client == null) {
                _state.value = _state.value.copy(error = "Moomoo OpenD client is unavailable")
                return
            }
            _state.value = _state.value.copy(
                testingConnection = true,
                connectionTest = null,
                error = null,
                moomooAccounts = emptyList(),
            )
            viewModelScope.launch {
                try {
                    val result = client.discoverEligibleAccounts(port)
                    _state.value = _state.value.copy(
                        connectionTest = BrokerConnectionTestResult.Moomoo(result),
                        moomooAccounts = (result as? MoomooConnectionResult.Connected)?.accounts.orEmpty(),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.value = _state.value.copy(error = error.message ?: "OpenD connection test failed")
                } finally {
                    _state.value = _state.value.copy(testingConnection = false)
                }
            }
            return
        }
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
                    is BrokerCredentialDraft.Moomoo -> error("handled above")
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
        val moomooConfig = validateMoomooDraft(broker, draft) ?: if (broker == Broker.MOOMOO) return else null
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
                moomooConfig?.let { ensureMoomooBindingAvailable(null, it.source) }
                val portfolio = portfolioRepository.insert(name.trim(), market, broker, alpacaEnvironment)
                try {
                    resolved?.let { credentialsRepository.setPortfolioBrokerCredentials(portfolio.credentialRef, it) }
                    moomooConfig?.let { portfolioSettings.putMoomooConfig(portfolio.id, it) }
                } catch (error: Throwable) {
                    portfolioSettings.clear(portfolio.id)
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
        val moomooConfig = validateMoomooDraft(broker, draft) ?: if (broker == Broker.MOOMOO) return else null
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
                moomooConfig?.let { ensureMoomooBindingAvailable(id, it.source) }
                val oldCredentials = credentialsRepository.getPortfolioBrokerCredentials(old.credentialRef)
                val oldMoomooConfig = portfolioSettings.getMoomooConfig(id)
                val credentialsChanged = oldCredentials != resolved
                val changed = old.broker != broker || old.alpacaEnvironment != alpacaEnvironment ||
                    credentialsChanged || oldMoomooConfig != moomooConfig
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
                moomooConfig?.let { portfolioSettings.putMoomooConfig(id, it) }
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
            val portfolios = portfolioRepository.getAll()
            val configs = buildMap {
                portfolios.filter { it.broker == Broker.MOOMOO }.forEach { portfolio ->
                    portfolioSettings.getMoomooConfig(portfolio.id)?.let { put(portfolio.id, it) }
                }
            }
            _state.value = _state.value.copy(portfolios = portfolios, moomooConfigs = configs)
        }
    }

    private fun validateMoomooDraft(broker: Broker?, draft: BrokerCredentialDraft?): MoomooPortfolioConfig? {
        if (broker != Broker.MOOMOO) return null
        val value = draft as? BrokerCredentialDraft.Moomoo
        val port = value?.port?.toIntOrNull()?.takeIf { it in 1..65535 }
        val account = value?.account
        val message = when {
            port == null -> "OpenD port must be between 1 and 65535"
            account == null || listOf(account).eligibleForUsStocks().isEmpty() ->
                "Connect to OpenD and select an eligible live US account"
            else -> null
        }
        if (message != null) {
            _state.value = _state.value.copy(error = message)
            return null
        }
        return MoomooPortfolioConfig(port!!, account!!.id, account.label, account.securityFirm)
    }

    private suspend fun ensureMoomooBindingAvailable(exceptPortfolioId: Long?, source: String) {
        val conflict = portfolioRepository.getAll().firstOrNull { portfolio ->
            portfolio.id != exceptPortfolioId &&
                (portfolioSettings.getString(portfolio.id, MoomooSettings.ACCOUNT_SOURCE) == source ||
                    portfolioSettings.getString(portfolio.id, MoomooSettings.LAST_SYNCED_SOURCE) == source)
        }
        require(conflict == null) { "This Moomoo account is already linked to portfolio \"${conflict?.name}\"" }
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
    if (this is BrokerCredentialDraft.Moomoo) return Result.success(null)
    if (this == null || (keyId.isBlank() && secretKey.isBlank())) return Result.success(null)
    if (keyId.isBlank() || secretKey.isBlank()) return Result.failure(IllegalArgumentException("Enter both broker credential fields or leave both blank"))
    return Result.success(
        when (this) {
            is BrokerCredentialDraft.Alpaca -> AlpacaBrokerSecrets(keyId.trim(), secretKey.trim())
            is BrokerCredentialDraft.TradeZero -> TradeZeroBrokerCredentials(keyId.trim(), secretKey.trim())
            is BrokerCredentialDraft.Moomoo -> error("handled above")
        },
    )
}

private fun BrokerCredentialDraft?.alpacaEnvironmentFor(broker: Broker?): AlpacaEnvironment? =
    if (broker == Broker.ALPACA) {
        (this as? BrokerCredentialDraft.Alpaca)?.environment ?: AlpacaEnvironment.PAPER
    } else {
        null
    }

private fun MoomooPortfolioConfig.toAccount() = MoomooAccount(
    id = accountId,
    label = accountLabel,
    securityFirm = securityFirm,
    environment = MoomooAccountEnvironment.REAL,
    role = MoomooAccountRole.NORMAL,
    authorizedMarkets = setOf(MoomooMarket.US),
    active = true,
)
