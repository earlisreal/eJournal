package io.earlisreal.ejournal.domain

/** Exact decimal arithmetic for quantities read from the Double ledger. */
internal expect class DecimalShares(shares: Double) : Comparable<DecimalShares> {
    operator fun plus(other: DecimalShares): DecimalShares
    operator fun minus(other: DecimalShares): DecimalShares
    fun isPositive(): Boolean
    fun toDouble(): Double
    fun toPlainString(maxDecimals: Int? = null): String
}
