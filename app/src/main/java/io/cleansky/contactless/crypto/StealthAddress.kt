package io.cleansky.contactless.crypto

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SecureRandom
import java.security.Security

/**
 * Stealth Address implementation based on EIP-5564 concepts.
 *
 * Allows merchants to receive payments to one-time addresses that only they can spend.
 * Provides on-chain unlinkability - observers can't link payments to the same merchant.
 *
 * Flow:
 * 1. Merchant generates stealth meta-address (spending pubkey + viewing pubkey)
 * 2. Payer generates ephemeral keypair
 * 3. Payer derives one-time address using ECDH
 * 4. Payment goes to one-time address
 * 5. Merchant scans using viewing key, derives spending key
 */
object StealthAddress {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
    private val random = SecureRandom()

    /**
     * Stealth meta-address: contains keys needed to receive stealth payments
     */
    data class StealthMetaAddress(
        val spendingPubKey: ByteArray,
        val viewingPubKey: ByteArray,
    ) {
        /**
         * Encode as hex string: spendingPubKey || viewingPubKey
         */
        fun encode(): String {
            return "st:eth:" + Numeric.toHexStringNoPrefix(spendingPubKey) +
                Numeric.toHexStringNoPrefix(viewingPubKey)
        }

        companion object {
            /**
             * Decode from hex string
             */
            fun decode(encoded: String): StealthMetaAddress? {
                return try {
                    val hex = encoded.removePrefix("st:eth:")

                    fun keyHexLen(prefix: String): Int =
                        when (prefix.lowercase()) {
                            "04" -> 130 // uncompressed secp256k1 public key
                            "02", "03" -> 66 // compressed secp256k1 public key
                            else -> throw IllegalArgumentException("Invalid public key prefix")
                        }

                    require(hex.length >= 68) { "Invalid length" } // At least two compressed keys
                    val firstLen = keyHexLen(hex.substring(0, 2))
                    require(hex.length > firstLen) { "Missing second key" }
                    val secondLen = keyHexLen(hex.substring(firstLen, firstLen + 2))
                    require(hex.length == firstLen + secondLen) { "Invalid concatenated key length" }

                    val spending = Numeric.hexStringToByteArray(hex.substring(0, firstLen))
                    val viewing = Numeric.hexStringToByteArray(hex.substring(firstLen))
                    StealthMetaAddress(spending, viewing)
                } catch (e: Exception) {
                    null
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StealthMetaAddress) return false
            return spendingPubKey.contentEquals(other.spendingPubKey) &&
                viewingPubKey.contentEquals(other.viewingPubKey)
        }

        override fun hashCode(): Int {
            var result = spendingPubKey.contentHashCode()
            result = 31 * result + viewingPubKey.contentHashCode()
            return result
        }
    }

    /**
     * Stealth keys: private keys for a stealth-enabled wallet
     */
    data class StealthKeys(
        val spendingKey: BigInteger,
        val viewingKey: BigInteger,
    ) {
        fun getMetaAddress(): StealthMetaAddress {
            val spendingPub = ecSpec.g.multiply(spendingKey).getEncoded(false)
            val viewingPub = ecSpec.g.multiply(viewingKey).getEncoded(false)
            return StealthMetaAddress(spendingPub, viewingPub)
        }
    }

    /**
     * Data needed by payer to send to stealth address
     */
    data class StealthPaymentData(
        val stealthAddress: String,
        val ephemeralPubKey: ByteArray,
        val viewTag: Byte,
    )

    /**
     * Generate new stealth keys for a merchant
     */
    fun generateStealthKeys(): StealthKeys {
        val spendingKey = BigInteger(256, random).mod(ecSpec.n)
        val viewingKey = BigInteger(256, random).mod(ecSpec.n)
        return StealthKeys(spendingKey, viewingKey)
    }

    /**
     * Derive stealth keys from existing wallet (deterministic)
     * Uses the wallet private key to derive spending and viewing keys
     */
    fun deriveStealthKeys(credentials: Credentials): StealthKeys {
        val privateKey = credentials.ecKeyPair.privateKey

        // Derive spending key: hash(privateKey || "spending")
        val spendingHash =
            Hash.sha3(
                privateKey.toByteArray() + "cleansky-stealth-spending".toByteArray(),
            )
        val spendingKey = BigInteger(1, spendingHash).mod(ecSpec.n)

        // Derive viewing key: hash(privateKey || "viewing")
        val viewingHash =
            Hash.sha3(
                privateKey.toByteArray() + "cleansky-stealth-viewing".toByteArray(),
            )
        val viewingKey = BigInteger(1, viewingHash).mod(ecSpec.n)

        return StealthKeys(spendingKey, viewingKey)
    }

    /**
     * Generate stealth payment data (called by PAYER)
     *
     * @param metaAddress Merchant's stealth meta-address
     * @return Data needed to complete the stealth payment
     */
    fun generateStealthPayment(metaAddress: StealthMetaAddress): StealthPaymentData {
        // Generate ephemeral keypair (r, R)
        val ephemeralPrivate = BigInteger(256, random).mod(ecSpec.n)
        val ephemeralPublic = ecSpec.g.multiply(ephemeralPrivate)

        // Parse merchant's viewing public key
        val viewingPubPoint = ecSpec.curve.decodePoint(metaAddress.viewingPubKey)

        // Compute shared secret: S = r * K_v (ECDH)
        val sharedSecretPoint = viewingPubPoint.multiply(ephemeralPrivate)
        val sharedSecret = Hash.sha3(sharedSecretPoint.getEncoded(true))

        // View tag is first byte of shared secret (for fast scanning)
        val viewTag = sharedSecret[0]

        // Parse merchant's spending public key
        val spendingPubPoint = ecSpec.curve.decodePoint(metaAddress.spendingPubKey)

        // Compute stealth public key: P = K_s + hash(S) * G
        val hashScalar = BigInteger(1, sharedSecret).mod(ecSpec.n)
        val stealthPubPoint = spendingPubPoint.add(ecSpec.g.multiply(hashScalar))

        // Derive address from stealth public key
        val stealthPubBytes = stealthPubPoint.getEncoded(false)
        val stealthAddress =
            "0x" +
                Keys.getAddress(
                    Numeric.toHexStringNoPrefix(stealthPubBytes.drop(1).toByteArray()),
                )

        return StealthPaymentData(
            stealthAddress = stealthAddress,
            ephemeralPubKey = ephemeralPublic.getEncoded(false),
            viewTag = viewTag,
        )
    }

    /**
     * Scan for stealth payment and derive spending key (called by MERCHANT)
     *
     * @param stealthKeys Merchant's stealth keys
     * @param ephemeralPubKey The R value from the payment
     * @param expectedAddress The address that received funds (to verify)
     * @return The private key to spend from this address, or null if not ours
     */
    fun scanAndDerive(
        stealthKeys: StealthKeys,
        ephemeralPubKey: ByteArray,
        expectedAddress: String? = null,
    ): Credentials? {
        return try {
            // Parse ephemeral public key R
            val ephemeralPoint = ecSpec.curve.decodePoint(ephemeralPubKey)

            // Compute shared secret: S = k_v * R (ECDH with viewing key)
            val sharedSecretPoint = ephemeralPoint.multiply(stealthKeys.viewingKey)
            val sharedSecret = Hash.sha3(sharedSecretPoint.getEncoded(true))

            // Compute stealth private key: p = k_s + hash(S)
            val hashScalar = BigInteger(1, sharedSecret).mod(ecSpec.n)
            val stealthPrivate = stealthKeys.spendingKey.add(hashScalar).mod(ecSpec.n)

            // Create credentials
            val keyPair = ECKeyPair.create(stealthPrivate)
            val credentials = Credentials.create(keyPair)

            // Verify address if provided
            if (expectedAddress != null) {
                if (!credentials.address.equals(expectedAddress, ignoreCase = true)) {
                    return null
                }
            }

            credentials
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Quick check using view tag (optimization for scanning many payments)
     */
    fun checkViewTag(
        viewingKey: BigInteger,
        ephemeralPubKey: ByteArray,
        expectedViewTag: Byte,
    ): Boolean {
        return try {
            val ephemeralPoint = ecSpec.curve.decodePoint(ephemeralPubKey)
            val sharedSecretPoint = ephemeralPoint.multiply(viewingKey)
            val sharedSecret = Hash.sha3(sharedSecretPoint.getEncoded(true))
            sharedSecret[0] == expectedViewTag
        } catch (e: Exception) {
            false
        }
    }
}
