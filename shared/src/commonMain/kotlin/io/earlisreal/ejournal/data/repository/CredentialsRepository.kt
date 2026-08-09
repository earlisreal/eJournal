package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Broker

data class AlpacaMarketDataCredentials(
    val keyId: String,
    val secretKey: String,
)

sealed interface PortfolioBrokerCredentials {
    val broker: Broker
    val keyId: String
    val secretKey: String
}

/** Stored per-portfolio Alpaca secret material. Environment belongs to Portfolio. */
data class AlpacaBrokerSecrets(
    override val keyId: String,
    override val secretKey: String,
) : PortfolioBrokerCredentials {
    override val broker: Broker = Broker.ALPACA
}

data class TradeZeroBrokerCredentials(
    override val keyId: String,
    override val secretKey: String,
) : PortfolioBrokerCredentials {
    override val broker: Broker = Broker.TRADEZERO
}

/** Global market-data keys and per-portfolio broker keys share one local secret store. */
interface CredentialsRepository {
    fun getAlpacaMarketDataCredentials(): AlpacaMarketDataCredentials?
    fun setAlpacaMarketDataCredentials(credentials: AlpacaMarketDataCredentials)

    fun getPortfolioBrokerCredentials(credentialRef: String): PortfolioBrokerCredentials?
    fun setPortfolioBrokerCredentials(credentialRef: String, credentials: PortfolioBrokerCredentials)
    fun deletePortfolioBrokerCredentials(credentialRef: String)
}
