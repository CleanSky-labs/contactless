package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test

class ChainConfigTest {

    @Test
    fun `getByChainId returns correct chain for Ethereum mainnet`() {
        val chain = ChainConfig.getByChainId(1L)

        assertNotNull(chain)
        assertEquals("Ethereum", chain?.name)
        assertEquals(1L, chain?.chainId)
    }

    @Test
    fun `getByChainId returns correct chain for Base`() {
        val chain = ChainConfig.getByChainId(8453L)

        assertNotNull(chain)
        assertEquals("Base", chain?.name)
        assertEquals(8453L, chain?.chainId)
    }

    @Test
    fun `getByChainId returns correct chain for Base Sepolia`() {
        val chain = ChainConfig.getByChainId(84532L)

        assertNotNull(chain)
        assertEquals("Base Sepolia", chain?.name)
    }

    @Test
    fun `getByChainId returns correct chain for Polygon`() {
        val chain = ChainConfig.getByChainId(137L)

        assertNotNull(chain)
        assertEquals("Polygon", chain?.name)
    }

    @Test
    fun `getByChainId returns correct chain for Arbitrum`() {
        val chain = ChainConfig.getByChainId(42161L)

        assertNotNull(chain)
        assertEquals("Arbitrum", chain?.name)
    }

    @Test
    fun `getByChainId returns correct chain for Optimism`() {
        val chain = ChainConfig.getByChainId(10L)

        assertNotNull(chain)
        assertEquals("Optimism", chain?.name)
    }

    @Test
    fun `getByChainId returns correct chain for Localhost`() {
        val chain = ChainConfig.getByChainId(31337L)

        assertNotNull(chain)
        assertEquals("Localhost", chain?.name)
        assertFalse(chain!!.supportsRelayer)
        assertFalse(chain.supportsAA)
    }

    @Test
    fun `getByChainId returns null for unknown chain`() {
        val chain = ChainConfig.getByChainId(999999L)

        assertNull(chain)
    }

    @Test
    fun `CHAINS list contains all expected chains`() {
        val chains = ChainConfig.CHAINS

        assertTrue(chains.any { it.chainId == 1L })       // Ethereum
        assertTrue(chains.any { it.chainId == 8453L })    // Base
        assertTrue(chains.any { it.chainId == 84532L })   // Base Sepolia
        assertTrue(chains.any { it.chainId == 137L })     // Polygon
        assertTrue(chains.any { it.chainId == 42161L })   // Arbitrum
        assertTrue(chains.any { it.chainId == 10L })      // Optimism
        assertTrue(chains.any { it.chainId == 31337L })   // Localhost
    }

    @Test
    fun `all mainnet chains support relayer`() {
        val mainnets = ChainConfig.CHAINS.filter { it.chainId != 31337L && it.chainId != 84532L }

        mainnets.forEach { chain ->
            assertTrue("${chain.name} should support relayer", chain.supportsRelayer)
        }
    }

    @Test
    fun `all mainnet chains support AA`() {
        val mainnets = ChainConfig.CHAINS.filter { it.chainId != 31337L }

        mainnets.forEach { chain ->
            assertTrue("${chain.name} should support AA", chain.supportsAA)
        }
    }

    @Test
    fun `all chains have valid USDC address format`() {
        ChainConfig.CHAINS.forEach { chain ->
            assertTrue(
                "${chain.name} USDC address should start with 0x",
                chain.usdcAddress.startsWith("0x")
            )
            assertEquals(
                "${chain.name} USDC address should be 42 chars",
                42,
                chain.usdcAddress.length
            )
        }
    }

    @Test
    fun `all chains have valid RPC URL`() {
        ChainConfig.CHAINS.forEach { chain ->
            assertTrue(
                "${chain.name} RPC should start with http",
                chain.rpcUrl.startsWith("http")
            )
        }
    }

    @Test
    fun `all mainnet chains have explorer URL`() {
        val mainnets = ChainConfig.CHAINS.filter { it.chainId != 31337L }

        mainnets.forEach { chain ->
            assertTrue(
                "${chain.name} should have explorer URL",
                chain.explorerUrl.isNotEmpty()
            )
        }
    }

    @Test
    fun `ChainConfig data class equality works`() {
        val chain1 = ChainConfig(
            chainId = 1L,
            name = "Test",
            rpcUrl = "https://test.com",
            escrowAddress = "0x123",
            usdcAddress = "0x456"
        )
        val chain2 = ChainConfig(
            chainId = 1L,
            name = "Test",
            rpcUrl = "https://test.com",
            escrowAddress = "0x123",
            usdcAddress = "0x456"
        )

        assertEquals(chain1, chain2)
    }

    @Test
    fun `ChainConfig has correct default values`() {
        val chain = ChainConfig(
            chainId = 999L,
            name = "Test",
            rpcUrl = "https://test.com",
            escrowAddress = "0x123",
            usdcAddress = "0x456"
        )

        assertEquals("USDC", chain.symbol)
        assertEquals(6, chain.decimals)
        assertEquals("", chain.explorerUrl)
        assertTrue(chain.supportsRelayer)
        assertTrue(chain.supportsAA)
        assertEquals("0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789", chain.entryPointAddress)
    }

    @Test
    fun `Ethereum mainnet has correct USDC address`() {
        val eth = ChainConfig.getByChainId(1L)

        assertEquals("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", eth?.usdcAddress)
    }

    @Test
    fun `Base has correct USDC address`() {
        val base = ChainConfig.getByChainId(8453L)

        assertEquals("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", base?.usdcAddress)
    }
}
