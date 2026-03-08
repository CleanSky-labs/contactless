package io.cleansky.contactless.crypto

import io.cleansky.contactless.model.PaymentRequest
import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.Credentials

class TransactionSignerTest {
    private val payerCredentials =
        Credentials.create(
            "0x6c3699283bda56ad74f6b855546325b68d482e983852a7a89d6d77fd26f6a6f3",
        )

    private val merchantCredentials =
        Credentials.create(
            "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f0e6c58f6e8f7a66f5",
        )

    private fun baseRequest(stealthMetaAddress: String? = null) =
        PaymentRequest(
            merchantId = "0x" + "33".repeat(32),
            invoiceId = "0x" + "11".repeat(32),
            amount = "1000000",
            asset = "0x0000000000000000000000000000000000000000",
            chainId = 84532L,
            escrow = "0x1111111111111111111111111111111111111111",
            nonce = "0x" + "22".repeat(32),
            expiry = System.currentTimeMillis() / 1000 + 120L,
            merchantDisplayName = "Shop",
            merchantDomain = "shop.eth",
            merchantPubKey = "0xpub",
            stealthMetaAddress = stealthMetaAddress,
        )

    @Test
    fun `signPayment fills signed transaction fields and signature format`() {
        // Given
        val request = baseRequest()

        // When
        val signed = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertEquals(payerCredentials.address.lowercase(), signed.payer.lowercase())
        assertEquals("0x" + "33".repeat(32), signed.merchantId)
        assertEquals("1000000", signed.amount)
        assertTrue(signed.payerSig.startsWith("0x"))
        assertEquals(132, signed.payerSig.length) // 65-byte signature hex + 0x
    }

    @Test
    fun `signPayment is deterministic for same request and key`() {
        // Given
        val request = baseRequest()

        // When
        val signed1 = TransactionSigner.signPayment(request, payerCredentials)
        val signed2 = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertEquals(signed1.payerSig, signed2.payerSig)
    }

    @Test
    fun `signPayment signature changes with different chain`() {
        // Given
        val reqBase = baseRequest().copy(chainId = 84532L)
        val reqEth = baseRequest().copy(chainId = 1L)

        // When
        val sigBase = TransactionSigner.signPayment(reqBase, payerCredentials).payerSig
        val sigEth = TransactionSigner.signPayment(reqEth, payerCredentials).payerSig

        // Then
        assertNotEquals(sigBase, sigEth)
    }

    @Test
    fun `signPayment without stealth leaves stealth fields null`() {
        // Given
        val request = baseRequest(stealthMetaAddress = null)

        // When
        val signed = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertNull(signed.stealthAddress)
        assertNull(signed.ephemeralPubKey)
        assertNull(signed.viewTag)
    }

    @Test
    fun `signPayment with valid stealth meta fills stealth fields`() {
        // Given
        val stealthMeta =
            StealthAddress
                .deriveStealthKeys(merchantCredentials)
                .getMetaAddress()
                .encode()
        val request = baseRequest(stealthMetaAddress = stealthMeta)

        // When
        val signed = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertNotNull(signed.stealthAddress)
        assertNotNull(signed.ephemeralPubKey)
        assertNotNull(signed.viewTag)
        assertTrue(signed.stealthAddress!!.startsWith("0x"))
        assertEquals(42, signed.stealthAddress!!.length)
        assertTrue(signed.ephemeralPubKey!!.startsWith("0x"))
    }

    @Test
    fun `signPayment with invalid stealth meta does not break signing`() {
        // Given
        val request = baseRequest(stealthMetaAddress = "st:eth:invalid")

        // When
        val signed =
            TransactionSigner.signPayment(
                request,
                payerCredentials,
            )

        // Then
        assertTrue(signed.payerSig.startsWith("0x"))
        assertNull(signed.stealthAddress)
        assertNull(signed.ephemeralPubKey)
        assertNull(signed.viewTag)
    }

    @Test
    fun `signPayment accepts native alias as asset`() {
        // Given
        val request = baseRequest().copy(asset = "native")

        // When
        val signed = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertTrue(signed.payerSig.startsWith("0x"))
        assertEquals("native", signed.asset)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `signPayment rejects non-numeric amount`() {
        // Given
        val request = baseRequest().copy(amount = "one-usdc")

        // When
        TransactionSigner.signPayment(request, payerCredentials)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `signPayment rejects non-positive amount`() {
        // Given
        val request = baseRequest().copy(amount = "0")

        // When
        TransactionSigner.signPayment(request, payerCredentials)
    }

    @Test
    fun `signPayment accepts non-hex identifiers using deterministic fallback hashing`() {
        // Given
        val request =
            baseRequest().copy(
                merchantId = "merchant-store-42",
                invoiceId = "invoice-2026-03-06",
                nonce = "nonce:abc-123",
            )

        // When
        val signed1 = TransactionSigner.signPayment(request, payerCredentials)
        val signed2 = TransactionSigner.signPayment(request, payerCredentials)

        // Then
        assertTrue(signed1.payerSig.startsWith("0x"))
        assertEquals(signed1.payerSig, signed2.payerSig)
    }
}
