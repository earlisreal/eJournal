package io.earlisreal.ejournal.data.repository

data class AlpacaCredentials(val keyId: String, val secretKey: String)

/** API keys only — kept separate from SettingsRepository so secret handling stays in one place. */
interface CredentialsRepository {
    fun getAlpacaCredentials(): AlpacaCredentials?   // null = not configured
    fun setAlpacaCredentials(credentials: AlpacaCredentials)
}
