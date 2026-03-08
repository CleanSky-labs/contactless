package io.cleansky.contactless.crypto

import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.Credentials

class StealthAddressTest {
    private val mainCredentials =
        Credentials.create(
            "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f0e6c58f6e8f7a66f5",
        )

    @Test
    fun `deriveStealthKeys is deterministic for same wallet`() {
        val keys1 = StealthAddress.deriveStealthKeys(mainCredentials)
        val keys2 = StealthAddress.deriveStealthKeys(mainCredentials)

        assertEquals(keys1.spendingKey, keys2.spendingKey)
        assertEquals(keys1.viewingKey, keys2.viewingKey)
    }

    @Test
    fun `meta address encode and decode roundtrip`() {
        val keys = StealthAddress.deriveStealthKeys(mainCredentials)
        val meta = keys.getMetaAddress()
        val encoded = meta.encode()
        val decoded = StealthAddress.StealthMetaAddress.decode(encoded)

        assertNotNull(decoded)
        assertArrayEquals(meta.spendingPubKey, decoded!!.spendingPubKey)
        assertArrayEquals(meta.viewingPubKey, decoded.viewingPubKey)
    }

    @Test
    fun `meta address decode returns null for invalid format`() {
        assertNull(StealthAddress.StealthMetaAddress.decode("invalid"))

        val keys = StealthAddress.deriveStealthKeys(mainCredentials)
        val invalidLength = keys.getMetaAddress().encode().dropLast(2)
        assertNull(StealthAddress.StealthMetaAddress.decode(invalidLength))
    }

    @Test
    fun `generate stealth payment and scan derive recover spend credentials`() {
        val keys = StealthAddress.deriveStealthKeys(mainCredentials)
        val meta = keys.getMetaAddress()

        val payment = StealthAddress.generateStealthPayment(meta)
        val derived =
            StealthAddress.scanAndDerive(
                stealthKeys = keys,
                ephemeralPubKey = payment.ephemeralPubKey,
                expectedAddress = payment.stealthAddress,
            )

        assertNotNull(derived)
        assertEquals(payment.stealthAddress.lowercase(), derived!!.address.lowercase())
        assertTrue(payment.stealthAddress.startsWith("0x"))
        assertEquals(42, payment.stealthAddress.length)
    }

    @Test
    fun `scanAndDerive returns null when expected address does not match`() {
        val keys = StealthAddress.deriveStealthKeys(mainCredentials)
        val payment = StealthAddress.generateStealthPayment(keys.getMetaAddress())

        val derived =
            StealthAddress.scanAndDerive(
                stealthKeys = keys,
                ephemeralPubKey = payment.ephemeralPubKey,
                expectedAddress = "0x0000000000000000000000000000000000000001",
            )

        assertNull(derived)
    }

    @Test
    fun `checkViewTag validates correct tag and rejects wrong tag`() {
        val keys = StealthAddress.deriveStealthKeys(mainCredentials)
        val payment = StealthAddress.generateStealthPayment(keys.getMetaAddress())

        val matches =
            StealthAddress.checkViewTag(
                viewingKey = keys.viewingKey,
                ephemeralPubKey = payment.ephemeralPubKey,
                expectedViewTag = payment.viewTag,
            )
        val wrongTag = (payment.viewTag.toInt() xor 0x01).toByte()
        val notMatches =
            StealthAddress.checkViewTag(
                viewingKey = keys.viewingKey,
                ephemeralPubKey = payment.ephemeralPubKey,
                expectedViewTag = wrongTag,
            )

        assertTrue(matches)
        assertFalse(notMatches)
    }
}
