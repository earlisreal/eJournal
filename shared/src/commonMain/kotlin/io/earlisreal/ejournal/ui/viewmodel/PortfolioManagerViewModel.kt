package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PortfolioManagerState(
    val portfolios: List<Portfolio> = emptyList(),
    val pendingDelete: Portfolio? = null,
    val pendingDeleteCount: Long = 0,
)

/** CRUD over portfolios; [onChanged] notifies the shell to reload + reconcile the selection. */
class PortfolioManagerViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val onChanged: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioManagerState())
    val state: StateFlow<PortfolioManagerState> = _state.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(portfolios = portfolioRepository.getAll())
        }
    }

    fun create(name: String, market: Market) {
        viewModelScope.launch {
            portfolioRepository.insert(name.trim(), market)
            reload(); onChanged()
        }
    }

    fun update(id: Long, name: String, market: Market) {
        viewModelScope.launch {
            portfolioRepository.update(id, name.trim(), market)
            reload(); onChanged()
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
        viewModelScope.launch {
            transactionRepository.deleteByPortfolio(portfolio.id)
            // FK cascade isn't enforced (PRAGMA foreign_keys is off), so clear the portfolio's
            // settings explicitly — matching how transactions are removed above.
            portfolioSettings.clear(portfolio.id)
            portfolioRepository.delete(portfolio.id)
            _state.value = _state.value.copy(pendingDelete = null, pendingDeleteCount = 0)
            reload(); onChanged()
        }
    }
}
