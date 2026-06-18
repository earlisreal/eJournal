package io.earlisreal.ejournal.data.repository

data class AlpacaCredentials(val keyId: String, val secretKey: String)

data class TradeZeroCredentials(val keyId: String, val secretKey: String)

/** API keys only — kept separate from SettingsRepository so secret handling stays in one place. */
interface CredentialsRepository {
    fun getAlpacaCredentials(): AlpacaCredentials?
    fun setAlpacaCredentials(credentials: AlpacaCredentials)
    fun getTradeZeroCredentials(): TradeZeroCredentials?
    fun setTradeZeroCredentials(credentials: TradeZeroCredentials)
}
