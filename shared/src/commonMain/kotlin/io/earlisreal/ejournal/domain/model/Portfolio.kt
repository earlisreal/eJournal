package io.earlisreal.ejournal.domain.model

import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment

data class Portfolio(
    val id: Long,
    val name: String,
    val market: Market,
    val broker: Broker?,
    val credentialRef: String,
    val alpacaEnvironment: AlpacaEnvironment? = null,
)
