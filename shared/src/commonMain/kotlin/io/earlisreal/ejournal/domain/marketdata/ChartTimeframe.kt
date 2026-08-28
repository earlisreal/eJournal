package io.earlisreal.ejournal.domain.marketdata

enum class ChartTimeframe(val label: String) {
    TEN_SEC("10s"),
    ONE_MIN("1m"),
    FIVE_MIN("5m"),
    FIFTEEN_MIN("15m"),
    DAILY("D"),
    WEEKLY("W"),
}
