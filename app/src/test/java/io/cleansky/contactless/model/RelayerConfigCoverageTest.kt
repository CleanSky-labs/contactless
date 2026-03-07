package io.cleansky.contactless.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class RelayerConfigCoverageTest {

    @Test
    fun `relayer presets include expected networks`() {
        assertTrue(8453L in RelayerConfig.GELATO.chainIds)    // Base
        assertTrue(137L in RelayerConfig.GELATO.chainIds)     // Polygon
        assertTrue(8453L in RelayerConfig.BICONOMY.chainIds)  // Base
        assertTrue(56L in RelayerConfig.BICONOMY.chainIds)    // BNB chain
    }

    @Test
    fun `paymaster presets require API key`() {
        assertTrue(PaymasterConfig.PIMLICO.requiresApiKey)
        assertTrue(PaymasterConfig.STACKUP.requiresApiKey)
        assertTrue(PaymasterConfig.ALCHEMY.requiresApiKey)
    }

    @Test
    fun `public bundler URL exists for supported testnet and not for unknown chain`() {
        assertNotNull(PublicBundler.getPublicBundlerUrl(84532L)) // Base Sepolia
        assertNull(PublicBundler.getPublicBundlerUrl(999999L))
    }

    @Test
    fun `meta transaction serializes with defaults`() {
        val tx = MetaTransaction(
            from = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            to = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            data = "0x1234",
            nonce = "1",
            deadline = 1234567890L,
            signature = "0xdeadbeef"
        )

        val json = tx.toJson()
        assertTrue(json.contains("\"from\""))
        assertTrue(json.contains("\"to\""))
        assertTrue(json.contains("\"data\""))
        assertTrue(json.contains("\"value\":\"0\""))
    }

    @Test
    fun `gelato relay request uses serialized names`() {
        val request = GelatoRelayRequest(
            chainId = 84532L,
            target = "0xcccccccccccccccccccccccccccccccccccccccc",
            data = "0x00",
            user = "0xdddddddddddddddddddddddddddddddddddddddd",
            deadline = 1000L,
            nonce = "0x1",
            signature = "0xabc"
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"userDeadline\""))
        assertTrue(json.contains("\"userNonce\""))
        assertTrue(json.contains("\"userSignature\""))
        assertFalse(json.contains("\"deadline\""))
        assertFalse(json.contains("\"nonce\""))
        assertFalse(json.contains("\"signature\""))
    }

    @Test
    fun `relay response data class keeps values`() {
        val response = RelayResponse(
            taskId = "task-1",
            txHash = "0xhash",
            status = "pending",
            error = null
        )

        assertEquals("task-1", response.taskId)
        assertEquals("0xhash", response.txHash)
        assertEquals("pending", response.status)
        assertNull(response.error)
    }
}
