package io.cleansky.contactless.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RefundRequestTest {
    @Test
    fun `RefundRequest stores provided values`() {
        val request =
            RefundRequest(
                originalTxId = "tx-1",
                amount = "1000",
                recipientAddress = "0x1234",
                asset = "0xasset",
                chainId = 84532L,
            )

        assertEquals("tx-1", request.originalTxId)
        assertEquals("1000", request.amount)
        assertEquals("0x1234", request.recipientAddress)
        assertEquals("0xasset", request.asset)
        assertEquals(84532L, request.chainId)
    }
}
