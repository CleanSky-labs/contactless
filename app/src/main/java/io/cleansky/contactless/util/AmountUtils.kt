package io.cleansky.contactless.util

import java.math.BigDecimal
import java.math.BigInteger

object AmountUtils {
    fun parseToUnitsOrZero(
        text: String,
        decimals: Int,
    ): BigInteger {
        if (decimals < 0) return BigInteger.ZERO
        val normalized = text.trim()
        if (normalized.isEmpty()) return BigInteger.ZERO

        return try {
            val decimal = BigDecimal(normalized)
            if (decimal.signum() < 0) return BigInteger.ZERO
            val multiplier = BigDecimal.TEN.pow(decimals)
            decimal.multiply(multiplier).toBigInteger()
        } catch (_: Exception) {
            BigInteger.ZERO
        }
    }

    fun formatRawAmountOrPlaceholder(
        amount: String?,
        decimals: Int,
    ): String {
        val units = amount?.toBigIntegerOrNull()
        return NumberFormatter.formatCurrency(units, decimals)
    }
}
