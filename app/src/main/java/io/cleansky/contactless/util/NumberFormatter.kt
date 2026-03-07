package io.cleansky.contactless.util

import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Locale-aware number formatting utility for crypto balances.
 * Respects user's locale for decimal/thousand separators.
 */
object NumberFormatter {

    /**
     * Formats a BigInteger balance with the appropriate decimals for display.
     * Uses locale-aware decimal and thousand separators.
     *
     * @param amount The raw amount in smallest units (wei, satoshi, etc.)
     * @param decimals The number of decimal places for the token
     * @param maxDisplayDecimals Maximum decimal places to show (default 6)
     * @return Formatted string respecting user's locale
     */
    fun formatBalance(
        amount: BigInteger?,
        decimals: Int,
        maxDisplayDecimals: Int = 6
    ): String {
        if (amount == null) return "..."
        if (amount == BigInteger.ZERO) return "0"

        return try {
            val divisor = BigInteger.TEN.pow(decimals)
            val wholePart = amount.divide(divisor)
            val fractionalPart = amount.remainder(divisor).abs()

            if (fractionalPart == BigInteger.ZERO) {
                formatWholePart(wholePart)
            } else {
                val fractionalStr = fractionalPart.toString().padStart(decimals, '0')
                val trimmed = fractionalStr.trimEnd('0')
                val displayDecimals = minOf(trimmed.length, maxDisplayDecimals)
                val truncated = fractionalStr.take(displayDecimals).trimEnd('0')

                if (truncated.isEmpty()) {
                    formatWholePart(wholePart)
                } else {
                    formatWithFraction(wholePart, truncated)
                }
            }
        } catch (e: Exception) {
            "0"
        }
    }

    /**
     * Formats a balance with exactly 2 decimal places (for currency display).
     */
    fun formatCurrency(amount: BigInteger?, decimals: Int): String {
        if (amount == null) return "..."
        if (amount == BigInteger.ZERO) return formatZeroCurrency()

        return try {
            val divisor = BigInteger.TEN.pow(decimals)
            val wholePart = amount.divide(divisor)
            val fractionalPart = amount.remainder(divisor).abs()
            val fractionalStr = fractionalPart.toString().padStart(decimals, '0').take(2)

            val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
            val formatter = DecimalFormat("#,##0.00", symbols)
            val value = wholePart.toDouble() + fractionalStr.toDouble() / 100
            formatter.format(value)
        } catch (e: Exception) {
            formatZeroCurrency()
        }
    }

    private fun formatWholePart(wholePart: BigInteger): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val formatter = DecimalFormat("#,##0", symbols)
        return formatter.format(wholePart.toLong())
    }

    private fun formatWithFraction(wholePart: BigInteger, fraction: String): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val wholeFormatted = if (wholePart >= BigInteger.valueOf(1000)) {
            val formatter = DecimalFormat("#,##0", symbols)
            formatter.format(wholePart.toLong())
        } else {
            wholePart.toString()
        }
        return "$wholeFormatted${symbols.decimalSeparator}$fraction"
    }

    private fun formatZeroCurrency(): String {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        return "0${symbols.decimalSeparator}00"
    }
}
