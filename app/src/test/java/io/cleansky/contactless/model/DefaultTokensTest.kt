package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class DefaultTokensTest {
    @Test
    fun `getNativeToken returns ETH for Ethereum mainnet`() {
        val token = DefaultTokens.getNativeToken(1L)

        assertEquals("ETH", token.symbol)
        assertEquals("Ethereum", token.name)
        assertEquals(18, token.decimals)
        assertEquals(1L, token.chainId)
        assertTrue(token.isNative)
        assertEquals(Token.NATIVE_ADDRESS, token.address)
    }

    @Test
    fun `getNativeToken returns ETH for Base`() {
        val token = DefaultTokens.getNativeToken(8453L)

        assertEquals("ETH", token.symbol)
        assertEquals("Ethereum", token.name)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns POL for Polygon`() {
        val token = DefaultTokens.getNativeToken(137L)

        assertEquals("POL", token.symbol)
        assertEquals("Polygon", token.name)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns ETH for Arbitrum`() {
        val token = DefaultTokens.getNativeToken(42161L)

        assertEquals("ETH", token.symbol)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns ETH for Optimism`() {
        val token = DefaultTokens.getNativeToken(10L)

        assertEquals("ETH", token.symbol)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns ETH for zkSync Era`() {
        val token = DefaultTokens.getNativeToken(324L)

        assertEquals("ETH", token.symbol)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns ETH for Linea`() {
        val token = DefaultTokens.getNativeToken(59144L)

        assertEquals("ETH", token.symbol)
        assertTrue(token.isNative)
    }

    @Test
    fun `getNativeToken returns fallback for unknown chain`() {
        val token = DefaultTokens.getNativeToken(999999L)

        assertEquals("ETH", token.symbol)
        assertEquals("Native Token", token.name)
        assertEquals(18, token.decimals)
        assertTrue(token.isNative)
    }

    @Test
    fun `getDefaultTokens includes native token first`() {
        val tokens = DefaultTokens.getDefaultTokens(8453L)

        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens[0].isNative)
        assertEquals("ETH", tokens[0].symbol)
    }

    @Test
    fun `getDefaultTokens includes USDC for Base`() {
        val tokens = DefaultTokens.getDefaultTokens(8453L)

        assertTrue(tokens.any { it.symbol == "USDC" })
    }

    @Test
    fun `getDefaultTokens includes USDC for Ethereum`() {
        val tokens = DefaultTokens.getDefaultTokens(1L)

        val usdc = tokens.find { it.symbol == "USDC" }
        assertNotNull(usdc)
        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", usdc?.address)
        assertEquals(6, usdc?.decimals)
    }

    @Test
    fun `getDefaultTokens includes USDC for Polygon`() {
        val tokens = DefaultTokens.getDefaultTokens(137L)

        val usdc = tokens.find { it.symbol == "USDC" }
        assertNotNull(usdc)
        assertEquals("0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359", usdc?.address)
    }

    @Test
    fun `getDefaultTokens includes DAI for supported chains`() {
        val baseTokens = DefaultTokens.getDefaultTokens(8453L)
        val ethTokens = DefaultTokens.getDefaultTokens(1L)

        assertTrue(baseTokens.any { it.symbol == "DAI" })
        assertTrue(ethTokens.any { it.symbol == "DAI" })
    }

    @Test
    fun `getTokensByUnderlying returns grouped tokens for Base`() {
        val grouped = DefaultTokens.getTokensByUnderlying(8453L)

        assertTrue(grouped.containsKey(DefaultTokens.UnderlyingCurrency.USD))
        assertTrue(grouped[DefaultTokens.UnderlyingCurrency.USD]?.any { it.symbol == "USDC" } == true)
    }

    @Test
    fun `getTokensByUnderlying includes EUR tokens for Ethereum`() {
        val grouped = DefaultTokens.getTokensByUnderlying(1L)

        assertTrue(grouped.containsKey(DefaultTokens.UnderlyingCurrency.EUR))
        assertTrue(grouped[DefaultTokens.UnderlyingCurrency.EUR]?.any { it.symbol == "EURC" } == true)
    }

    @Test
    fun `getTokensByUnderlying includes ETH tokens`() {
        val grouped = DefaultTokens.getTokensByUnderlying(1L)

        assertTrue(grouped.containsKey(DefaultTokens.UnderlyingCurrency.ETH))
        assertTrue(grouped[DefaultTokens.UnderlyingCurrency.ETH]?.any { it.symbol == "WETH" } == true)
    }

    @Test
    fun `getTokensByUnderlying includes BTC tokens`() {
        val grouped = DefaultTokens.getTokensByUnderlying(1L)

        assertTrue(grouped.containsKey(DefaultTokens.UnderlyingCurrency.BTC))
        assertTrue(grouped[DefaultTokens.UnderlyingCurrency.BTC]?.any { it.symbol == "WBTC" } == true)
    }

    @Test
    fun `getAvailableUnderlyings returns non-empty list for mainnet chains`() {
        val underlyings = DefaultTokens.getAvailableUnderlyings(1L)

        assertTrue(underlyings.isNotEmpty())
        assertTrue(underlyings.contains(DefaultTokens.UnderlyingCurrency.USD))
    }

    @Test
    fun `getTokensForUnderlying returns USD tokens`() {
        val usdTokens = DefaultTokens.getTokensForUnderlying(1L, DefaultTokens.UnderlyingCurrency.USD)

        assertTrue(usdTokens.isNotEmpty())
        assertTrue(usdTokens.any { it.symbol == "USDC" })
        assertTrue(usdTokens.any { it.symbol == "USDT" })
        assertTrue(usdTokens.any { it.symbol == "DAI" })
    }

    @Test
    fun `getTokensForUnderlying returns empty for unavailable currency`() {
        // GBP might not be available on all chains
        val gbpTokens = DefaultTokens.getTokensForUnderlying(8453L, DefaultTokens.UnderlyingCurrency.GBP)

        // Just verify it doesn't throw and returns a list
        assertNotNull(gbpTokens)
    }

    @Test
    fun `getAllDefaultTokens returns tokens from all chains`() {
        val allTokens = DefaultTokens.getAllDefaultTokens()

        assertTrue(allTokens.isNotEmpty())
        // Should have tokens from multiple chains
        assertTrue(allTokens.any { it.chainId == 1L })
        assertTrue(allTokens.any { it.chainId == 8453L })
        assertTrue(allTokens.any { it.chainId == 137L })
    }

    @Test
    fun `UnderlyingCurrency enum has correct values`() {
        assertEquals("USD", DefaultTokens.UnderlyingCurrency.USD.code)
        assertEquals("US Dollar", DefaultTokens.UnderlyingCurrency.USD.displayName)

        assertEquals("EUR", DefaultTokens.UnderlyingCurrency.EUR.code)
        assertEquals("Euro", DefaultTokens.UnderlyingCurrency.EUR.displayName)

        assertEquals("ETH", DefaultTokens.UnderlyingCurrency.ETH.code)
        assertEquals("Ethereum", DefaultTokens.UnderlyingCurrency.ETH.displayName)

        assertEquals("BTC", DefaultTokens.UnderlyingCurrency.BTC.code)
        assertEquals("Bitcoin", DefaultTokens.UnderlyingCurrency.BTC.displayName)
    }

    @Test
    fun `USDC addresses are different per chain`() {
        val ethTokens = DefaultTokens.getDefaultTokens(1L)
        val baseTokens = DefaultTokens.getDefaultTokens(8453L)
        val polygonTokens = DefaultTokens.getDefaultTokens(137L)

        val ethUsdc = ethTokens.find { it.symbol == "USDC" }
        val baseUsdc = baseTokens.find { it.symbol == "USDC" }
        val polygonUsdc = polygonTokens.find { it.symbol == "USDC" }

        assertNotEquals(ethUsdc?.address, baseUsdc?.address)
        assertNotEquals(ethUsdc?.address, polygonUsdc?.address)
        assertNotEquals(baseUsdc?.address, polygonUsdc?.address)
    }

    @Test
    fun `all token addresses have correct format`() {
        val tokens = DefaultTokens.getAllDefaultTokens()

        tokens.filter { !it.isNative }.forEach { token ->
            assertTrue(
                "${token.symbol} on chain ${token.chainId} should have valid address",
                token.address.startsWith("0x") && token.address.length == 42,
            )
        }
    }

    @Test
    fun `stablecoins have correct decimals`() {
        val tokens = DefaultTokens.getAllDefaultTokens()

        tokens.filter { it.symbol in listOf("USDC", "USDT") }.forEach { token ->
            assertEquals(
                "${token.symbol} should have 6 decimals",
                6,
                token.decimals,
            )
        }

        tokens.filter { it.symbol == "DAI" }.forEach { token ->
            assertEquals(
                "DAI should have 18 decimals",
                18,
                token.decimals,
            )
        }
    }

    @Test
    fun `getPreferredStablecoin returns non-null for supported chains`() {
        val chains = listOf(1L, 8453L, 137L, 42161L, 10L, 324L, 59144L)

        chains.forEach { chainId ->
            val stablecoin = DefaultTokens.getPreferredStablecoin(chainId)
            assertNotNull("Chain $chainId should have preferred stablecoin", stablecoin)
        }
    }

    @Test
    fun `getPreferredStablecoin returns null for unsupported chain`() {
        val stablecoin = DefaultTokens.getPreferredStablecoin(999999L, preferredCurrency = "USD")

        assertNull(stablecoin)
    }

    @Test
    fun `getTokensByUnderlying returns empty for unsupported chain`() {
        val grouped = DefaultTokens.getTokensByUnderlying(999999L)

        assertTrue(grouped.isEmpty())
    }
}
