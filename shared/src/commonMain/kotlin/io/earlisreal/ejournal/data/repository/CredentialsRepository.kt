package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment

data class AlpacaCredentials(
    val keyId: String,
    val secretKey: String,
    val environment: AlpacaEnvironment = AlpacaEnvironment.PAPER,
)

data class TradeZeroCredentials(val keyId: String, val secretKey: String)

/** API keys only — kept separate from SettingsRepository so secret handling stays in one place. */
interface CredentialsRepository {
    fun getAlpacaCredentials(): AlpacaCredentials?
    fun setAlpacaCredentials(credentials: AlpacaCredentials)
    fun getTradeZeroCredentials(): TradeZeroCredentials?
    fun setTradeZeroCredentials(credentials: TradeZeroCredentials)
}
