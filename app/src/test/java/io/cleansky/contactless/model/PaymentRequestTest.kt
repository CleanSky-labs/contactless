package io.cleansky.contactless.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PaymentRequestTest {
    private fun createValidRequest(
        // seconds from now
        expiryOffset: Long = 120L,
        merchantId: String = "merchant-1",
        invoiceId: String = "0xinvoice",
        amount: String = "1000000",
        asset: String = "0xtoken",
        escrow: String = "0xescrow123456789abcdef",
        nonce: String = "0xnonce123",
    ) = PaymentRequest(
        merchantId = merchantId,
        invoiceId = invoiceId,
        amount = amount,
        asset = asset,
        chainId = 8453L,
        escrow = escrow,
        nonce = nonce,
        expiry = System.currentTimeMillis() / 1000 + expiryOffset,
    )

    // --- validate() ---

    @Test
    fun `validate returns Valid for correct request`() {
        val request = createValidRequest()
        val result = request.validate()
        assertTrue(result.isValid())
        assertTrue(result is PaymentRequest.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Expired for expired request`() {
        val request = createValidRequest(expiryOffset = -60L)
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Expired)
    }

    @Test
    fun `validate returns Invalid for blank merchantId`() {
        val request = createValidRequest(merchantId = "")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns Invalid for blank invoiceId`() {
        val request = createValidRequest(invoiceId = "")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns Invalid for zero amount`() {
        val request = createValidRequest(amount = "0")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns Invalid for blank asset`() {
        val request = createValidRequest(asset = "")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns Invalid for blank escrow`() {
        val request = createValidRequest(escrow = "")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns Invalid for blank nonce`() {
        val request = createValidRequest(nonce = "")
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
    }

    @Test
    fun `validate returns InsufficientTime when almost expired`() {
        // Expiry 10 seconds from now is less than MIN_TTL_SECONDS (30)
        val request = createValidRequest(expiryOffset = 10L)
        val result = request.validate()
        assertTrue(result is PaymentRequest.ValidationResult.InsufficientTime)
    }

    // --- isExpired() ---

    @Test
    fun `isExpired returns false for future expiry`() {
        val request = createValidRequest(expiryOffset = 120L)
        assertFalse(request.isExpired())
    }

    @Test
    fun `isExpired returns true for past expiry beyond clock skew`() {
        val request = createValidRequest(expiryOffset = -60L)
        assertTrue(request.isExpired())
    }

    @Test
    fun `isExpired tolerates clock skew within 30 seconds`() {
        // Expiry just passed (5 seconds ago), within 30s tolerance
        val request = createValidRequest(expiryOffset = -5L)
        assertFalse(request.isExpired())
    }

    // --- hasEnoughTime() ---

    @Test
    fun `hasEnoughTime returns true with plenty of time`() {
        val request = createValidRequest(expiryOffset = 120L)
        assertTrue(request.hasEnoughTime())
    }

    @Test
    fun `hasEnoughTime returns false with insufficient time`() {
        val request = createValidRequest(expiryOffset = 10L)
        assertFalse(request.hasEnoughTime())
    }

    // --- getDisplayName() ---

    @Test
    fun `getDisplayName returns merchantDisplayName when set`() {
        val request = createValidRequest().copy(merchantDisplayName = "Coffee Shop")
        assertEquals("Coffee Shop", request.getDisplayName())
    }

    @Test
    fun `getDisplayName falls back to merchantDomain`() {
        val request = createValidRequest().copy(merchantDomain = "shop.eth")
        assertEquals("shop.eth", request.getDisplayName())
    }

    @Test
    fun `getDisplayName falls back to truncated escrow address`() {
        val request = createValidRequest(escrow = "0xescrow123456789abcdef")
        assertEquals("0xescr...cdef", request.getDisplayName())
    }

    @Test
    fun `getDisplayName prefers displayName over domain`() {
        val request =
            createValidRequest().copy(
                merchantDisplayName = "My Shop",
                merchantDomain = "shop.eth",
            )
        assertEquals("My Shop", request.getDisplayName())
    }

    // --- getAmountFormatted() ---

    @Test
    fun `getAmountFormatted formats with default 6 decimals`() {
        // 1 USDC = 1_000_000
        val request = createValidRequest(amount = "1000000")
        assertEquals("1", request.getAmountFormatted())
    }

    @Test
    fun `getAmountFormatted formats with custom decimals`() {
        // 1 ETH = 10^18
        val request = createValidRequest(amount = "1000000000000000000")
        assertEquals("1", request.getAmountFormatted(18))
    }

    // --- create() + CBOR parsing ---

    @Test
    fun `create clamps expiry to minimum TTL`() {
        val now = System.currentTimeMillis() / 1000
        val request =
            PaymentRequest.create(
                merchantId = "merchant-min",
                amount = BigInteger("1"),
                asset = "0xasset",
                chainId = 8453L,
                escrow = "0x1111111111111111111111111111111111111111",
                // below minimum
                expirySeconds = 1L,
            )

        val ttl = request.expiry - now
        assertTrue(ttl >= PaymentRequest.MIN_TTL_SECONDS)
        assertTrue(ttl <= PaymentRequest.MIN_TTL_SECONDS + 2) // allow execution drift
    }

    @Test
    fun `create clamps expiry to maximum TTL`() {
        val now = System.currentTimeMillis() / 1000
        val request =
            PaymentRequest.create(
                merchantId = "merchant-max",
                amount = BigInteger("1"),
                asset = "0xasset",
                chainId = 8453L,
                escrow = "0x1111111111111111111111111111111111111111",
                // above maximum
                expirySeconds = 99999L,
            )

        val ttl = request.expiry - now
        assertTrue(ttl <= PaymentRequest.MAX_TTL_SECONDS + 2)
        assertTrue(ttl >= PaymentRequest.MAX_TTL_SECONDS - 2)
    }

    @Test
    fun `create generates non-empty ids and nonce with hex prefix`() {
        val request =
            PaymentRequest.create(
                merchantId = "merchant-random",
                amount = BigInteger("123"),
                asset = "0xasset",
                chainId = 8453L,
                escrow = "0x1111111111111111111111111111111111111111",
            )

        assertTrue(request.invoiceId.startsWith("0x"))
        assertTrue(request.nonce.startsWith("0x"))
        assertEquals(66, request.invoiceId.length) // 32 bytes hex + 0x
        assertEquals(66, request.nonce.length)
    }

    @Test
    fun `toCbor and fromCbor roundtrip preserves fields`() {
        val request =
            createValidRequest().copy(
                merchantDisplayName = "Cafe",
                merchantDomain = "cafe.eth",
                merchantPubKey = "0xpub",
                stealthMetaAddress = "st:eth:abcd",
            )

        val cbor = request.toCbor()
        val parsed = PaymentRequest.fromCbor(cbor)

        assertNotNull(parsed)
        assertEquals(request.merchantId, parsed!!.merchantId)
        assertEquals(request.invoiceId, parsed.invoiceId)
        assertEquals(request.amount, parsed.amount)
        assertEquals(request.stealthMetaAddress, parsed.stealthMetaAddress)
    }

    @Test
    fun `fromCbor and parse return null on invalid data`() {
        val invalid = byteArrayOf(0x00, 0x01, 0x02)
        assertNull(PaymentRequest.fromCbor(invalid))
        assertNull(PaymentRequest.parse(invalid))
    }

    @Test
    fun `SignedTransaction toCbor fromCbor parse roundtrip`() {
        val request = createValidRequest()
        val tx =
            SignedTransaction.fromRequest(
                request = request,
                payer = "0x2222222222222222222222222222222222222222",
                signature = "0xabc",
            )

        val cbor = tx.toCbor()
        val parsed1 = SignedTransaction.fromCbor(cbor)
        val parsed2 = SignedTransaction.parse(cbor)

        assertNotNull(parsed1)
        assertNotNull(parsed2)
        assertEquals(tx.invoiceId, parsed1!!.invoiceId)
        assertEquals(tx.payer, parsed2!!.payer)
        assertEquals(tx.payerSig, parsed2.payerSig)
    }

    @Test
    fun `SignedTransaction fromCbor returns null on invalid data`() {
        assertNull(SignedTransaction.fromCbor(byteArrayOf(0x01, 0x02)))
        assertNull(SignedTransaction.parse(byteArrayOf(0x01, 0x02)))
    }

    // --- extra validate branches ---

    @Test
    fun `validate returns invalid for protocol version below supported range`() {
        val request = createValidRequest().copy(v = 0)
        val result = request.validate()

        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
        assertTrue((result as PaymentRequest.ValidationResult.Invalid).reason.contains("Unsupported"))
    }

    @Test
    fun `validate returns invalid for protocol version above current`() {
        val request = createValidRequest().copy(v = PaymentRequest.PROTOCOL_VERSION + 1)
        val result = request.validate()

        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
        assertTrue((result as PaymentRequest.ValidationResult.Invalid).reason.contains("Unsupported"))
    }

    @Test
    fun `validate returns invalid when expiry is too far in future`() {
        val now = System.currentTimeMillis() / 1000
        val request =
            createValidRequest().copy(
                expiry = now + PaymentRequest.MAX_TTL_SECONDS + PaymentRequest.CLOCK_SKEW_TOLERANCE + 60,
            )
        val result = request.validate()

        assertTrue(result is PaymentRequest.ValidationResult.Invalid)
        assertTrue((result as PaymentRequest.ValidationResult.Invalid).reason.contains("future"))
    }
}
