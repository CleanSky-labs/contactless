package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class BundlerSelectorTest {
    // --- BundlerSelector priority ---

    @Test
    fun `custom URL has highest priority`() {
        val result =
            BundlerSelector.selectBundler(
                chainId = 84532,
                apiKey = "my-key",
                customBundlerUrl = "https://custom.bundler.io",
            )
        assertEquals("https://custom.bundler.io", result.url)
        assertEquals("Custom", result.name)
    }

    @Test
    fun `API key with preferred provider returns provider URL`() {
        val result =
            BundlerSelector.selectBundler(
                // Base Sepolia - supported by Pimlico
                chainId = 84532,
                apiKey = "my-key",
                customBundlerUrl = null,
                preferredProvider = PaymasterConfig.PIMLICO,
            )
        assertEquals("Pimlico", result.name)
        assertTrue(result.url!!.contains("my-key"))
        assertTrue(result.requiresApiKey)
        assertTrue(result.supportsPaymaster)
    }

    @Test
    fun `falls back to public bundler when no API key`() {
        val result =
            BundlerSelector.selectBundler(
                // Base Sepolia - has public bundler
                chainId = 84532,
                apiKey = null,
                customBundlerUrl = null,
            )
        assertNotNull(result.url)
        assertFalse(result.requiresApiKey)
    }

    @Test
    fun `falls back to any provider with API key when preferred does not support chain`() {
        // Use a chain supported by Pimlico/Stackup/Alchemy but not by preferred
        val result =
            BundlerSelector.selectBundler(
                // Mainnet - supported by Pimlico, Stackup, Alchemy
                chainId = 1,
                apiKey = "my-key",
                customBundlerUrl = null,
                // No preferred provider
                preferredProvider = null,
            )
        assertNotNull(result.url)
        assertTrue(result.requiresApiKey)
    }

    @Test
    fun `returns null URL when no bundler available`() {
        val result =
            BundlerSelector.selectBundler(
                // Unknown chain
                chainId = 99999L,
                apiKey = null,
                customBundlerUrl = null,
                preferredProvider = null,
            )
        assertNull(result.url)
        assertEquals("None", result.name)
    }

    @Test
    fun `blank custom URL is ignored`() {
        val result =
            BundlerSelector.selectBundler(
                chainId = 84532,
                apiKey = null,
                customBundlerUrl = "",
            )
        assertNotEquals("Custom", result.name)
    }

    // --- PublicBundler ---

    @Test
    fun `getUrl expands chainId template`() {
        val bundler =
            PublicBundler(
                name = "Test",
                urlTemplate = "https://bundler.example.com/{chainId}/rpc",
                chainIds = listOf(84532L),
            )
        assertEquals("https://bundler.example.com/84532/rpc", bundler.getUrl(84532))
    }

    @Test
    fun `getUrl appends API key when required`() {
        val bundler =
            PublicBundler(
                name = "Test",
                urlTemplate = "https://bundler.example.com/{chainId}",
                chainIds = listOf(1L),
                requiresApiKey = true,
            )
        assertEquals("https://bundler.example.com/1?apikey=abc", bundler.getUrl(1, "abc"))
    }

    @Test
    fun `getUrl ignores API key when not required`() {
        val bundler =
            PublicBundler(
                name = "Test",
                urlTemplate = "https://bundler.example.com/{chainId}",
                chainIds = listOf(1L),
                requiresApiKey = false,
            )
        assertEquals("https://bundler.example.com/1", bundler.getUrl(1, "abc"))
    }

    @Test
    fun `getBundlersForChain returns matching bundlers`() {
        val bundlers = PublicBundler.getBundlersForChain(84532) // Base Sepolia
        assertTrue(bundlers.isNotEmpty())
        bundlers.forEach { assertTrue(84532L in it.chainIds) }
    }

    @Test
    fun `getBundlersForChain returns empty for unknown chain`() {
        val bundlers = PublicBundler.getBundlersForChain(99999)
        assertTrue(bundlers.isEmpty())
    }

    @Test
    fun `getBundlersForChain sorts no-key-required first`() {
        val bundlers = PublicBundler.getBundlersForChain(84532) // Has both key and no-key bundlers
        if (bundlers.size > 1) {
            assertFalse(bundlers.first().requiresApiKey)
        }
    }

    @Test
    fun `hasPublicBundler returns true for supported testnet`() {
        assertTrue(PublicBundler.hasPublicBundler(84532)) // Base Sepolia
    }

    @Test
    fun `hasPublicBundler returns false for unsupported chain`() {
        assertFalse(PublicBundler.hasPublicBundler(99999))
    }
}
