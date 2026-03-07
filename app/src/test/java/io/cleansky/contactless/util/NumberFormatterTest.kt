package io.cleansky.contactless.util

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.text.DecimalFormatSymbols
import java.util.Locale

class NumberFormatterTest {

    private val decSep = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    private val grpSep = DecimalFormatSymbols.getInstance(Locale.getDefault()).groupingSeparator

    // --- formatBalance: null and zero ---

    @Test
    fun `formatBalance returns dots for null`() {
        assertEquals("...", NumberFormatter.formatBalance(null, 6))
    }

    @Test
    fun `formatBalance returns 0 for zero`() {
        assertEquals("0", NumberFormatter.formatBalance(BigInteger.ZERO, 6))
    }

    // --- formatBalance: integers ---

    @Test
    fun `formatBalance formats whole number without decimals`() {
        // 1 USDC = 1_000_000 units with 6 decimals
        val result = NumberFormatter.formatBalance(BigInteger("1000000"), 6)
        assertEquals("1", result)
    }

    @Test
    fun `formatBalance formats large whole number with thousand separators`() {
        // 1,234 USDC = 1_234_000_000 units with 6 decimals
        val result = NumberFormatter.formatBalance(BigInteger("1234000000"), 6)
        assertEquals("1${grpSep}234", result)
    }

    // --- formatBalance: decimals ---

    @Test
    fun `formatBalance formats fractional amount`() {
        // 1.5 USDC = 1_500_000 units with 6 decimals
        val result = NumberFormatter.formatBalance(BigInteger("1500000"), 6)
        assertEquals("1${decSep}5", result)
    }

    @Test
    fun `formatBalance removes trailing zeros`() {
        // 1.10 → "1.1"
        val result = NumberFormatter.formatBalance(BigInteger("1100000"), 6)
        assertEquals("1${decSep}1", result)
    }

    @Test
    fun `formatBalance shows small fractional values`() {
        // 0.001 USDC = 1_000 units with 6 decimals
        val result = NumberFormatter.formatBalance(BigInteger("1000"), 6)
        assertEquals("0${decSep}001", result)
    }

    // --- formatBalance: maxDisplayDecimals ---

    @Test
    fun `formatBalance truncates to maxDisplayDecimals`() {
        // 0.123456789 with 9 decimals, maxDisplay=4 → truncated
        val result = NumberFormatter.formatBalance(BigInteger("123456789"), 9, maxDisplayDecimals = 4)
        assertEquals("0${decSep}1234", result)
    }

    @Test
    fun `formatBalance with maxDisplayDecimals 2 truncates`() {
        // 1.123456 USDC with 6 decimals, maxDisplay=2
        val result = NumberFormatter.formatBalance(BigInteger("1123456"), 6, maxDisplayDecimals = 2)
        assertEquals("1${decSep}12", result)
    }

    // --- formatBalance: ETH (18 decimals) ---

    @Test
    fun `formatBalance handles 18 decimal token like ETH`() {
        // 1 ETH = 10^18 wei
        val oneEth = BigInteger("1000000000000000000")
        val result = NumberFormatter.formatBalance(oneEth, 18)
        assertEquals("1", result)
    }

    @Test
    fun `formatBalance handles fractional ETH`() {
        // 0.5 ETH
        val halfEth = BigInteger("500000000000000000")
        val result = NumberFormatter.formatBalance(halfEth, 18)
        assertEquals("0${decSep}5", result)
    }

    // --- formatCurrency ---

    @Test
    fun `formatCurrency returns dots for null`() {
        assertEquals("...", NumberFormatter.formatCurrency(null, 6))
    }

    @Test
    fun `formatCurrency returns zero with 2 decimals for zero`() {
        val result = NumberFormatter.formatCurrency(BigInteger.ZERO, 6)
        assertEquals("0${decSep}00", result)
    }

    @Test
    fun `formatCurrency formats with exactly 2 decimal places`() {
        // 1.50 USDC
        val result = NumberFormatter.formatCurrency(BigInteger("1500000"), 6)
        assertEquals("1${decSep}50", result)
    }

    @Test
    fun `formatCurrency formats whole amount with 2 decimal places`() {
        // 10 USDC
        val result = NumberFormatter.formatCurrency(BigInteger("10000000"), 6)
        assertEquals("10${decSep}00", result)
    }

    @Test
    fun `formatCurrency formats large amount`() {
        // 1,234.56 USDC
        val result = NumberFormatter.formatCurrency(BigInteger("1234560000"), 6)
        assertEquals("1${grpSep}234${decSep}56", result)
    }
}
