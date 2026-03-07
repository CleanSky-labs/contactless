package io.cleansky.contactless.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigInteger
import java.security.SecureRandom

/**
 * PaymentRequest v0.5 - Spec compliant
 *
 * Uses CBOR encoding (RFC 8949) for deterministic, compact serialization.
 * Includes merchant identity fields for anti-spoofing.
 */
data class PaymentRequest(
    // Required fields (MUST)
    val v: Int = PROTOCOL_VERSION,
    val merchantId: String,
    val invoiceId: String,
    val amount: String,
    val asset: String,
    val chainId: Long,
    val escrow: String,
    val nonce: String,
    val expiry: Long,

    // Merchant identity fields (SHOULD/MAY)
    val merchantDisplayName: String? = null,  // mdn - Human-readable name
    val merchantDomain: String? = null,       // mdo - Domain or ENS
    val merchantPubKey: String? = null,       // mpk - Public key for verification

    // Stealth address (v0.4 - EIP-5564)
    val stealthMetaAddress: String? = null,   // sma - Stealth meta-address if merchant uses privacy mode

    // Legacy field
    val merchantSig: String = ""
) {
    companion object {
        const val PROTOCOL_VERSION = 5

        // Timing constants (spec v0.5)
        const val MAX_TTL_SECONDS = 300L          // 5 minutes max
        const val DEFAULT_TTL_SECONDS = 180L     // 3 minutes default
        const val MIN_TTL_SECONDS = 30L          // 30 seconds minimum
        const val CLOCK_SKEW_TOLERANCE = 30L     // ±30 seconds

        private val random = SecureRandom()
        private val cborMapper = ObjectMapper(CBORFactory())
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun create(
            merchantId: String,
            amount: BigInteger,
            asset: String,
            chainId: Long,
            escrow: String,
            expirySeconds: Long = DEFAULT_TTL_SECONDS,
            merchantDisplayName: String? = null,
            merchantDomain: String? = null,
            merchantPubKey: String? = null,
            stealthMetaAddress: String? = null
        ): PaymentRequest {
            // Enforce max TTL
            val ttl = expirySeconds.coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)

            val invoiceId = generateRandomHex(32)
            val nonce = generateRandomHex(32)
            val expiry = System.currentTimeMillis() / 1000 + ttl

            return PaymentRequest(
                merchantId = merchantId,
                invoiceId = invoiceId,
                amount = amount.toString(),
                asset = asset,
                chainId = chainId,
                escrow = escrow,
                nonce = nonce,
                expiry = expiry,
                merchantDisplayName = merchantDisplayName,
                merchantDomain = merchantDomain,
                merchantPubKey = merchantPubKey,
                stealthMetaAddress = stealthMetaAddress
            )
        }

        /**
         * Parse from CBOR bytes
         */
        fun fromCbor(bytes: ByteArray): PaymentRequest? {
            return try {
                cborMapper.readValue<PaymentRequest>(bytes)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Alias for fromCbor - all protocol messages use CBOR
         */
        fun parse(data: ByteArray): PaymentRequest? = fromCbor(data)

        private fun generateRandomHex(bytes: Int): String {
            val buffer = ByteArray(bytes)
            random.nextBytes(buffer)
            return "0x" + buffer.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Encode as CBOR bytes (deterministic)
     */
    fun toCbor(): ByteArray = cborMapper.writeValueAsBytes(this)

    /**
     * Get formatted amount with decimals (locale-aware)
     */
    @JsonIgnore
    fun getAmountFormatted(decimals: Int = 6): String {
        val amountBig = BigInteger(amount)
        return NumberFormatter.formatBalance(amountBig, decimals)
    }

    /**
     * Check if request has expired (with clock skew tolerance)
     */
    @JsonIgnore
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis() / 1000
        return now > expiry + CLOCK_SKEW_TOLERANCE
    }

    /**
     * Check if there's enough time to complete the payment
     */
    @JsonIgnore
    fun hasEnoughTime(): Boolean {
        val now = System.currentTimeMillis() / 1000
        val remainingTime = expiry - now
        return remainingTime >= MIN_TTL_SECONDS
    }

    /**
     * Validate the request according to spec v0.2 rules
     */
    @JsonIgnore
    fun validate(): ValidationResult {
        val now = System.currentTimeMillis() / 1000

        validateVersion()?.let { return it }
        validateTiming(now)?.let { return it }
        validateRequiredFields()?.let { return it }
        return ValidationResult.Valid
    }

    private fun validateVersion(): ValidationResult.Invalid? {
        return if (v < 1 || v > PROTOCOL_VERSION) {
            ValidationResult.Invalid("Unsupported protocol version: $v")
        } else {
            null
        }
    }

    private fun validateTiming(now: Long): ValidationResult? {
        if (now > expiry + CLOCK_SKEW_TOLERANCE) {
            return ValidationResult.Expired
        }
        if (expiry > now + MAX_TTL_SECONDS + CLOCK_SKEW_TOLERANCE) {
            return ValidationResult.Invalid("Expiry too far in future")
        }
        if (!hasEnoughTime()) {
            return ValidationResult.InsufficientTime
        }
        return null
    }

    private fun validateRequiredFields(): ValidationResult.Invalid? {
        return when {
            merchantId.isBlank() -> ValidationResult.Invalid("Missing merchantId")
            invoiceId.isBlank() -> ValidationResult.Invalid("Missing invoiceId")
            amount.isBlank() || amount == "0" -> ValidationResult.Invalid("Invalid amount")
            asset.isBlank() -> ValidationResult.Invalid("Missing asset")
            escrow.isBlank() -> ValidationResult.Invalid("Missing escrow address")
            nonce.isBlank() -> ValidationResult.Invalid("Missing nonce")
            else -> null
        }
    }

    /**
     * Get display name for UI (falls back to truncated address)
     */
    @JsonIgnore
    fun getDisplayName(): String {
        return merchantDisplayName
            ?: merchantDomain
            ?: "${escrow.take(6)}...${escrow.takeLast(4)}"
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        object Expired : ValidationResult()
        object InsufficientTime : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()

        fun isValid(): Boolean = this is Valid
    }
}

/**
 * SignedTransaction v0.5 - Contains signed payment authorization
 */
data class SignedTransaction(
    val v: Int = PaymentRequest.PROTOCOL_VERSION,
    val merchantId: String,
    val invoiceId: String,
    val amount: String,
    val asset: String,
    val chainId: Long,
    val escrow: String,
    val nonce: String,
    val expiry: Long,
    val payer: String,
    val payerSig: String,

    // Merchant identity (copied from request)
    val merchantDisplayName: String? = null,
    val merchantDomain: String? = null,
    val merchantPubKey: String? = null,

    // Stealth address fields (v0.4 - filled by payer when merchant uses stealth)
    val stealthAddress: String? = null,       // One-time address to send funds to
    val ephemeralPubKey: String? = null,      // R - ephemeral public key (hex)
    val viewTag: Int? = null                  // First byte of shared secret for fast scanning
) {
    companion object {
        private val cborMapper = ObjectMapper(CBORFactory()).registerModule(KotlinModule.Builder().build())

        /**
         * Create from PaymentRequest and signature
         */
        fun fromRequest(
            request: PaymentRequest,
            payer: String,
            signature: String,
            stealthAddress: String? = null,
            ephemeralPubKey: String? = null,
            viewTag: Int? = null
        ): SignedTransaction {
            return SignedTransaction(
                v = request.v,
                merchantId = request.merchantId,
                invoiceId = request.invoiceId,
                amount = request.amount,
                asset = request.asset,
                chainId = request.chainId,
                escrow = request.escrow,
                nonce = request.nonce,
                expiry = request.expiry,
                payer = payer,
                payerSig = signature,
                merchantDisplayName = request.merchantDisplayName,
                merchantDomain = request.merchantDomain,
                merchantPubKey = request.merchantPubKey,
                stealthAddress = stealthAddress,
                ephemeralPubKey = ephemeralPubKey,
                viewTag = viewTag
            )
        }

        /**
         * Parse from CBOR bytes
         */
        fun fromCbor(bytes: ByteArray): SignedTransaction? {
            return try {
                cborMapper.readValue<SignedTransaction>(bytes)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Alias for fromCbor - all protocol messages use CBOR
         */
        fun parse(data: ByteArray): SignedTransaction? = fromCbor(data)
    }

    /**
     * Encode as CBOR bytes
     */
    fun toCbor(): ByteArray = cborMapper.writeValueAsBytes(this)
}
