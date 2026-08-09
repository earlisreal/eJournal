package io.earlisreal.ejournal.domain.alpaca

enum class AlpacaEnvironment(
    val label: String,
    val tradingBaseUrl: String,
) {
    PAPER("Paper", "https://paper-api.alpaca.markets"),
    LIVE("Live", "https://api.alpaca.markets"),
}
