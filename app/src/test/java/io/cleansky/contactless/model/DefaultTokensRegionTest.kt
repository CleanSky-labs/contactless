package io.cleansky.contactless.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTokensRegionTest {
    @Test
    fun `region helpers detect EU country`() {
        assertTrue(DefaultTokens.isEURegion("ES"))
        assertFalse(DefaultTokens.isCHFRegion("ES"))
        assertFalse(DefaultTokens.isGBPRegion("ES"))
        assertFalse(DefaultTokens.isJPYRegion("ES"))
        assertEquals("EUR", DefaultTokens.getPreferredCurrency("ES"))
    }

    @Test
    fun `region helpers detect CHF country`() {
        assertTrue(DefaultTokens.isCHFRegion("CH"))
        assertFalse(DefaultTokens.isGBPRegion("CH"))
        assertFalse(DefaultTokens.isJPYRegion("CH"))
        assertEquals("CHF", DefaultTokens.getPreferredCurrency("CH"))
    }

    @Test
    fun `region helpers detect GBP country`() {
        assertTrue(DefaultTokens.isGBPRegion("GB"))
        assertFalse(DefaultTokens.isCHFRegion("GB"))
        assertFalse(DefaultTokens.isJPYRegion("GB"))
        assertEquals("GBP", DefaultTokens.getPreferredCurrency("GB"))
    }

    @Test
    fun `region helpers detect JPY country`() {
        assertTrue(DefaultTokens.isJPYRegion("JP"))
        assertFalse(DefaultTokens.isGBPRegion("JP"))
        assertFalse(DefaultTokens.isCHFRegion("JP"))
        assertEquals("JPY", DefaultTokens.getPreferredCurrency("JP"))
    }

    @Test
    fun `region helpers are case insensitive`() {
        assertTrue(DefaultTokens.isEURegion("es"))
        assertEquals("GBP", DefaultTokens.getPreferredCurrency("gb"))
    }

    @Test
    fun `region helpers fallback to USD for unknown country`() {
        assertEquals("USD", DefaultTokens.getPreferredCurrency("ZZ"))
    }

    @Test
    fun `preferred stablecoin follows EUR branch`() {
        val token = DefaultTokens.getPreferredStablecoin(1L, preferredCurrency = "EUR")
        assertNotNull(token)
        assertEquals("agEUR", token?.symbol)
    }

    @Test
    fun `preferred stablecoin follows GBP branch`() {
        val token = DefaultTokens.getPreferredStablecoin(1L, preferredCurrency = "GBP")
        assertNotNull(token)
        assertEquals("GBPT", token?.symbol)
    }

    @Test
    fun `preferred stablecoin follows JPY branch on L2 fallback`() {
        val token = DefaultTokens.getPreferredStablecoin(8453L, preferredCurrency = "JPY")
        assertNotNull(token)
        assertEquals("JPYC", token?.symbol)
    }

    @Test
    fun `preferred stablecoin follows CHF and default branches`() {
        val chfToken = DefaultTokens.getPreferredStablecoin(1L, preferredCurrency = "CHF")
        val defaultToken = DefaultTokens.getPreferredStablecoin(1L, preferredCurrency = "USD")

        assertEquals("XCHF", chfToken?.symbol)
        assertEquals("DAI", defaultToken?.symbol)
    }
}
