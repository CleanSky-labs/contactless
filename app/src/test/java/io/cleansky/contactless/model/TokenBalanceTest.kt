package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class TokenBalanceTest {
    private val usdcToken =
        Token(
            address = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            chainId = 8453L,
        )

    private val ethToken =
        Token(
            address = Token.NATIVE_ADDRESS,
            symbol = "ETH",
            name = "Ethereum",
            decimals = 18,
            chainId = 8453L,
            isNative = true,
        )

    @Test
    fun `TokenBalance create formats zero balance correctly`() {
        val balance = TokenBalance.create(usdcToken, BigInteger.ZERO)

        assertEquals("0", balance.balanceFormatted)
        assertTrue(balance.isZero())
    }

    @Test
    fun `TokenBalance create formats whole number correctly`() {
        // 100 USDC = 100_000_000 (6 decimals)
        val balance = TokenBalance.create(usdcToken, BigInteger("100000000"))

        assertEquals("100", balance.balanceFormatted)
        assertFalse(balance.isZero())
    }

    @Test
    fun `TokenBalance create formats decimal balance correctly`() {
        // 100.5 USDC = 100_500_000 (6 decimals)
        val balance = TokenBalance.create(usdcToken, BigInteger("100500000"))

        assertEquals("100.5", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance create formats small decimal balance correctly`() {
        // 0.000001 USDC = 1 (6 decimals)
        val balance = TokenBalance.create(usdcToken, BigInteger("1"))

        assertEquals("0.000001", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance create formats 18 decimal token correctly`() {
        // 1 ETH = 1_000_000_000_000_000_000 (18 decimals)
        val balance = TokenBalance.create(ethToken, BigInteger("1000000000000000000"))

        assertEquals("1", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance create formats fractional ETH correctly`() {
        // 1.5 ETH = 1_500_000_000_000_000_000 (18 decimals)
        val balance = TokenBalance.create(ethToken, BigInteger("1500000000000000000"))

        assertEquals("1.5", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance create truncates to 6 decimal places`() {
        // Very small amount with many decimals
        // 0.123456789 ETH - should truncate to 6 decimals
        val balance = TokenBalance.create(ethToken, BigInteger("123456789000000000"))

        // Should be "0.123456" (truncated, trailing zeros removed)
        assertTrue(balance.balanceFormatted.startsWith("0.123456"))
    }

    @Test
    fun `TokenBalance create trims trailing zeros`() {
        // 1.100000 USDC = 1_100_000 (6 decimals)
        val balance = TokenBalance.create(usdcToken, BigInteger("1100000"))

        assertEquals("1.1", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance isZero returns true for zero balance`() {
        val balance = TokenBalance.create(usdcToken, BigInteger.ZERO)

        assertTrue(balance.isZero())
    }

    @Test
    fun `TokenBalance isZero returns false for non-zero balance`() {
        val balance = TokenBalance.create(usdcToken, BigInteger.ONE)

        assertFalse(balance.isZero())
    }

    @Test
    fun `TokenBalance preserves token reference`() {
        val balance = TokenBalance.create(usdcToken, BigInteger("100000000"))

        assertEquals(usdcToken, balance.token)
        assertEquals("USDC", balance.token.symbol)
    }

    @Test
    fun `TokenBalance preserves raw balance value`() {
        val rawBalance = BigInteger("123456789")
        val balance = TokenBalance.create(usdcToken, rawBalance)

        assertEquals(rawBalance, balance.balance)
    }

    @Test
    fun `TokenBalance formats large balance correctly`() {
        // 1,000,000 USDC = 1_000_000_000_000 (6 decimals)
        val balance = TokenBalance.create(usdcToken, BigInteger("1000000000000"))

        // NumberFormatter uses locale-aware thousand separators
        val grpSep = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.getDefault()).groupingSeparator
        assertEquals("1${grpSep}000${grpSep}000", balance.balanceFormatted)
    }

    @Test
    fun `TokenBalance formats complex fractional correctly`() {
        // 123.456789 USDC = 123_456_789 (6 decimals) - but truncates to 6
        val balance = TokenBalance.create(usdcToken, BigInteger("123456789"))

        assertEquals("123.456789", balance.balanceFormatted)
    }
}
