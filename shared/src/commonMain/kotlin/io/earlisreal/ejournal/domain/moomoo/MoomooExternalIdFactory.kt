package io.earlisreal.ejournal.domain.moomoo

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime

object MoomooExternalIdFactory {
    fun normalizeSymbol(symbol: String): String =
        symbol.trim().removePrefixIgnoringCase("US.").trim()

    fun create(
        symbol: String,
        orderCreatedAt: LocalDateTime,
        action: Action,
        filledQuantity: Double,
    ): String {
        val atSeconds = buildString {
            append(orderCreatedAt.year.toString().padStart(4, '0'))
            append('-').append((orderCreatedAt.month.ordinal + 1).toString().padStart(2, '0'))
            append('-').append(orderCreatedAt.day.toString().padStart(2, '0'))
            append('T').append(orderCreatedAt.hour.toString().padStart(2, '0'))
            append(':').append(orderCreatedAt.minute.toString().padStart(2, '0'))
            append(':').append(orderCreatedAt.second.toString().padStart(2, '0'))
        }
        return "moomoo:${normalizeSymbol(symbol)}:$atSeconds:${action.name}:$filledQuantity"
    }

    private fun String.removePrefixIgnoringCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this
}
