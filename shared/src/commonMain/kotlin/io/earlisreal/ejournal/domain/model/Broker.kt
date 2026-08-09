package io.earlisreal.ejournal.domain.model

enum class Broker(
    val id: String,
    val label: String,
) {
    ALPACA("alpaca", "Alpaca"),
    TRADEZERO("tradezero", "TradeZero"),
}
