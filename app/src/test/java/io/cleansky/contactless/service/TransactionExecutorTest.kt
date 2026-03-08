package io.cleansky.contactless.service

import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.RelayerConfig
import org.junit.Assert.*
import org.junit.Test

class TransactionExecutorTest {
    private val testChain =
        ChainConfig(
            chainId = 84532L,
            name = "Base Sepolia",
            rpcUrl = "https://sepolia.base.org",
            escrowAddress = "0x0000000000000000000000000000000000000000",
            usdcAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
        )

    private val customEscrow = "0x1234567890abcdef1234567890abcdef12345678"

    @Test
    fun `forRelayOnly configures RELAYER execution mode`() {
        // Given + When
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = testChain,
                escrowOverride = customEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = "test-api-key",
            )

        // Then
        assertEquals(ExecutionMode.RELAYER, readPrivateField<ExecutionMode>(executor, "executionMode"))
    }

    @Test
    fun `forRelayOnly applies escrow override to internal chain config`() {
        // Given + When
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = testChain,
                escrowOverride = customEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = "test-api-key",
            )

        // Then
        val internalChain = readPrivateField<ChainConfig>(executor, "chainConfig")
        assertEquals(customEscrow, internalChain.escrowAddress)
        assertEquals(testChain.chainId, internalChain.chainId)
    }

    @Test
    fun `forRelayOnly stores provided relayer config`() {
        // Given + When
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = testChain,
                escrowOverride = customEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = "gelato-key-123",
            )

        // Then
        val internalRelayer = readPrivateField<RelayerConfig?>(executor, "relayerConfig")
        assertNotNull(internalRelayer)
        assertEquals("Gelato", internalRelayer!!.name)
        assertEquals(RelayerConfig.GELATO.url, internalRelayer.url)
    }

    @Test
    fun `forRelayOnly stores API key as provided`() {
        // Given + When
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = testChain,
                escrowOverride = customEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = "my-api-key",
            )

        // Then
        assertEquals("my-api-key", readPrivateField<String>(executor, "apiKey"))
    }

    @Test
    fun `forRelayOnly uses deterministic dummy credentials for relay-only mode`() {
        // Given + When
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = testChain,
                escrowOverride = customEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = "x",
            )

        // Then
        val credentials = readPrivateField<org.web3j.crypto.Credentials>(executor, "credentials")
        val expected =
            org.web3j.crypto.Credentials
                .create("0x0000000000000000000000000000000000000000000000000000000000000001")
                .address
        assertEquals(expected.lowercase(), credentials.address.lowercase())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readPrivateField(
        instance: Any,
        fieldName: String,
    ): T {
        val field = instance::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance) as T
    }
}
