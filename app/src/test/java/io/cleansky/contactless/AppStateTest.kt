package io.cleansky.contactless

import io.cleansky.contactless.model.ChainConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    @Test
    fun `AppState default values are initialized consistently`() {
        val state = AppState()

        assertEquals(Mode.PAY, state.mode)
        assertEquals(Screen.MAIN, state.screen)
        assertEquals(ChainConfig.CHAINS.first(), state.selectedChain)
        assertTrue(state.txStatus is TxStatus.Idle)
        assertEquals("", state.relayerApiKey)
        assertNull(state.lastTxAmount)
        assertNull(state.lastTxSymbol)
        assertEquals("", state.receiveOnlyEscrow)
        assertEquals("", state.receiveOnlyMerchantId)
    }

    @Test
    fun `AppState copy updates selected fields keeping the rest`() {
        val original = AppState()
        val updated =
            original.copy(
                mode = Mode.COLLECT,
                screen = Screen.HISTORY,
                relayerApiKey = "api-key",
            )

        assertEquals(Mode.COLLECT, updated.mode)
        assertEquals(Screen.HISTORY, updated.screen)
        assertEquals("api-key", updated.relayerApiKey)
        assertEquals(original.selectedChain, updated.selectedChain)
        assertEquals(original.receiveOnlyEscrow, updated.receiveOnlyEscrow)
    }

    @Test
    fun `TxStatus success and error preserve payload`() {
        val success = TxStatus.Success("0xabc")
        val error = TxStatus.Error("boom")

        assertEquals("0xabc", success.txHash)
        assertEquals("boom", error.message)
    }
}
