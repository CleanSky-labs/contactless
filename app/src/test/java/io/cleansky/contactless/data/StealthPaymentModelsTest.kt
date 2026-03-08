package io.cleansky.contactless.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthPaymentModelsTest {
    @Test
    fun `PendingStealthPayment defaults createdAt and keeps payload`() {
        val before = System.currentTimeMillis()
        val pending =
            PendingStealthPayment(
                invoiceId = "inv-1",
                stealthAddress = "0xstealth",
                ephemeralPubKey = "0xpub",
                viewTag = 7,
                amount = "42",
                asset = "0xasset",
                chainId = 84532L,
            )
        val after = System.currentTimeMillis()

        assertEquals("inv-1", pending.invoiceId)
        assertEquals("0xstealth", pending.stealthAddress)
        assertEquals(7, pending.viewTag)
        assertTrue(pending.createdAt in before..after)
    }

    @Test
    fun `ClaimedStealthPayment copy updates tx hash only`() {
        val original =
            ClaimedStealthPayment(
                invoiceId = "inv-1",
                stealthAddress = "0xstealth",
                amount = "42",
                asset = "0xasset",
                chainId = 10L,
                claimedTxHash = "0xold",
                claimedAt = 123L,
            )

        val updated = original.copy(claimedTxHash = "0xnew")

        assertEquals("0xnew", updated.claimedTxHash)
        assertEquals(original.invoiceId, updated.invoiceId)
        assertEquals(original.claimedAt, updated.claimedAt)
    }

    @Test
    fun `EphemeralAccountRecord stores values as expected`() {
        val record =
            EphemeralAccountRecord(
                address = "0xabc",
                paymentIndex = 3L,
                invoiceId = "inv-3",
                merchantAddress = "0xmerchant",
                amount = "500",
                asset = "0xasset",
                chainId = 11155111L,
                txHash = "0xtx",
                createdAt = 999L,
            )

        assertEquals("0xabc", record.address)
        assertEquals(3L, record.paymentIndex)
        assertEquals("inv-3", record.invoiceId)
        assertEquals("0xtx", record.txHash)
    }
}
