package io.cleansky.contactless.crypto

import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.model.Token
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import java.math.BigInteger

object TransactionSigner {

    // EIP-712 Domain Separator
    private const val EIP712_DOMAIN_TYPEHASH = "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
    private const val DOMAIN_NAME = "PaymentEscrow"
    private const val DOMAIN_VERSION = "1"

    // Pay TypeHash
    private const val PAY_TYPEHASH = "Pay(bytes32 merchantId,bytes32 invoiceId,address asset,uint256 amount,bytes32 nonce,uint64 expiry,address escrow,uint256 chainId)"

    fun signPayment(
        request: PaymentRequest,
        credentials: Credentials
    ): SignedTransaction {
        require(parseAmountOrNull(request.amount)?.let { it > BigInteger.ZERO } == true) {
            "Invalid amount for signing: ${request.amount}"
        }

        val structHash = hashPayStruct(request)
        val domainSeparator = getDomainSeparator(request.chainId, request.escrow)
        val digest = getTypedDataHash(domainSeparator, structHash)

        val signature = Sign.signMessage(digest, credentials.ecKeyPair, false)
        val sigBytes = ByteArray(65)
        System.arraycopy(signature.r, 0, sigBytes, 0, 32)
        System.arraycopy(signature.s, 0, sigBytes, 32, 32)
        sigBytes[64] = signature.v[0]

        val sigHex = "0x" + sigBytes.joinToString("") { "%02x".format(it) }

        // Handle stealth address if present (v0.4)
        var stealthAddress: String? = null
        var ephemeralPubKey: String? = null
        var viewTag: Int? = null

        if (!request.stealthMetaAddress.isNullOrEmpty()) {
            val metaAddress = StealthAddress.StealthMetaAddress.decode(request.stealthMetaAddress)
            if (metaAddress != null) {
                val stealthData = StealthAddress.generateStealthPayment(metaAddress)
                stealthAddress = stealthData.stealthAddress
                ephemeralPubKey = "0x" + stealthData.ephemeralPubKey.joinToString("") { "%02x".format(it) }
                viewTag = stealthData.viewTag.toInt() and 0xFF
            }
        }

        return SignedTransaction.fromRequest(
            request = request,
            payer = credentials.address,
            signature = sigHex,
            stealthAddress = stealthAddress,
            ephemeralPubKey = ephemeralPubKey,
            viewTag = viewTag
        )
    }

    private fun getDomainSeparator(chainId: Long, escrow: String): ByteArray {
        val typeHash = keccak256(EIP712_DOMAIN_TYPEHASH.toByteArray())
        val nameHash = keccak256(DOMAIN_NAME.toByteArray())
        val versionHash = keccak256(DOMAIN_VERSION.toByteArray())
        val chainIdBytes = BigInteger.valueOf(chainId).toByteArray32()
        val escrowBytes = addressToBytes32(escrow)

        val encoded = typeHash + nameHash + versionHash + chainIdBytes + escrowBytes
        return keccak256(encoded)
    }

    private fun hashPayStruct(request: PaymentRequest): ByteArray {
        val typeHash = keccak256(PAY_TYPEHASH.toByteArray())
        val merchantIdBytes = flexibleBytes32(request.merchantId)
        val invoiceIdBytes = flexibleBytes32(request.invoiceId)
        val assetBytes = addressToBytes32(request.asset)
        val amountBytes = parseAmountOrNull(request.amount)!!.toByteArray32()
        val nonceBytes = flexibleBytes32(request.nonce)
        val expiryBytes = BigInteger.valueOf(request.expiry).toByteArray32()
        val escrowBytes = addressToBytes32(request.escrow)
        val chainIdBytes = BigInteger.valueOf(request.chainId).toByteArray32()

        val encoded = typeHash + merchantIdBytes + invoiceIdBytes + assetBytes +
                amountBytes + nonceBytes + expiryBytes + escrowBytes + chainIdBytes

        return keccak256(encoded)
    }

    private fun getTypedDataHash(domainSeparator: ByteArray, structHash: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x19, 0x01)
        return keccak256(prefix + domainSeparator + structHash)
    }

    private fun keccak256(input: ByteArray): ByteArray {
        return Hash.sha3(input)
    }

    /**
     * Accept either canonical hex (with/without 0x) or arbitrary text.
     * If input is not valid hex, hash UTF-8 bytes to keep a deterministic bytes32 value.
     */
    private fun flexibleBytes32(value: String): ByteArray {
        val cleanHex = value.removePrefix("0x")
        return if (isValidHex(cleanHex)) {
            hexToBytes(cleanHex).padTo32()
        } else {
            keccak256(value.toByteArray())
        }
    }

    private fun addressToBytes32(address: String): ByteArray {
        val normalizedAddress = when {
            address.equals("native", ignoreCase = true) || address.isBlank() -> Token.NATIVE_ADDRESS
            else -> address
        }
        val cleanAddress = normalizedAddress.removePrefix("0x")
        require(isValidHex(cleanAddress)) { "Invalid hex address: $address" }
        val bytes = hexToBytes(cleanAddress)
        // Address is 20 bytes, pad left to 32 bytes
        return ByteArray(32 - bytes.size) + bytes
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun isValidHex(value: String): Boolean {
        if (value.isEmpty() || value.length % 2 != 0) return false
        return value.all { it.lowercaseChar() in "0123456789abcdef" }
    }

    private fun parseAmountOrNull(value: String): BigInteger? {
        return try {
            BigInteger(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun BigInteger.toByteArray32(): ByteArray {
        val bytes = this.toByteArray()
        return when {
            bytes.size > 32 -> bytes.takeLast(32).toByteArray()
            bytes.size < 32 -> ByteArray(32 - bytes.size) + bytes
            else -> bytes
        }
    }

    private fun ByteArray.padTo32(): ByteArray {
        return when {
            this.size > 32 -> this.takeLast(32).toByteArray()
            this.size < 32 -> ByteArray(32 - this.size) + this
            else -> this
        }
    }
}
