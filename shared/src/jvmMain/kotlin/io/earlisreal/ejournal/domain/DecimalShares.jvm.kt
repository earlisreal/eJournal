package io.earlisreal.ejournal.domain

import java.math.BigDecimal
import java.math.RoundingMode

internal actual class DecimalShares private constructor(private val amount: BigDecimal) : Comparable<DecimalShares> {
    actual constructor(shares: Double) : this(BigDecimal.valueOf(shares))

    override fun compareTo(other: DecimalShares): Int = amount.compareTo(other.amount)
    actual operator fun plus(other: DecimalShares): DecimalShares = DecimalShares(amount.add(other.amount))
    actual operator fun minus(other: DecimalShares): DecimalShares = DecimalShares(amount.subtract(other.amount))
    actual fun isPositive(): Boolean = amount.signum() > 0
    actual fun toDouble(): Double = amount.toDouble()

    actual fun toPlainString(maxDecimals: Int?): String {
        val displayed = if (maxDecimals == null) amount else amount.setScale(maxDecimals, RoundingMode.HALF_UP)
        return displayed.stripTrailingZeros().toPlainString()
    }
}
