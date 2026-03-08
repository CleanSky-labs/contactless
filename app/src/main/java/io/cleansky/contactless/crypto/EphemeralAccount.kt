package io.cleansky.contactless.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Ephemeral Account Manager for Payer Privacy (v0.5)
 *
 * Creates deterministic ephemeral smart accounts for privacy-preserving payments.
 * Each payment can use a unique ephemeral account, breaking the on-chain link
 * between the payer's main wallet and the merchant.
 *
 * Architecture:
 * ```
 *   (hidden)                          (visible on-chain)
 * ```
 *
 * The ephemeral account is a SimpleAccount (ERC-4337) where:
 * - Owner = derived ephemeral key (deterministic from main wallet + salt)
 * - Execution = via UserOperation signed by ephemeral key
 * - Gas = sponsored by Paymaster (no ETH needed in ephemeral)
 */
object EphemeralAccount {
    // SimpleAccount factory address (same across EVM chains)
    const val SIMPLE_ACCOUNT_FACTORY = "0x9406Cc6185a346906296840746125a0E44976454"

    // EntryPoint v0.6 address (standard across chains)
    const val ENTRY_POINT_V06 = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"

    // secp256k1 curve order
    private val SECP256K1_N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

    /**
     * Ephemeral account data
     */
    data class EphemeralAccountData(
        val address: String,
        val ownerCredentials: Credentials,
        val salt: BigInteger,
        val initCode: String,
    )

    /**
     * Derive an ephemeral account deterministically from main wallet
     *
     * @param mainCredentials Main wallet credentials
     * @param paymentIndex Index for this payment (allows multiple ephemeral accounts)
     * @return Ephemeral account data
     */
    fun deriveEphemeralAccount(
        mainCredentials: Credentials,
        paymentIndex: Long = System.currentTimeMillis(),
    ): EphemeralAccountData {
        // Derive ephemeral private key: hash(mainPrivateKey || "cleansky-ephemeral" || index)
        val derivationData =
            mainCredentials.ecKeyPair.privateKey.toByteArray() +
                "cleansky-ephemeral-payer".toByteArray() +
                BigInteger.valueOf(paymentIndex).toByteArray()

        val ephemeralPrivateKey =
            BigInteger(1, Hash.sha3(derivationData))
                .mod(SECP256K1_N)

        val ephemeralKeyPair = ECKeyPair.create(ephemeralPrivateKey)
        val ephemeralCredentials = Credentials.create(ephemeralKeyPair)

        // Calculate salt from ephemeral address
        val salt = BigInteger(1, Hash.sha3(ephemeralCredentials.address.toByteArray()))

        // Calculate counterfactual address
        val accountAddress = calculateAccountAddress(ephemeralCredentials.address, salt)

        // Generate init code (factory + createAccount calldata)
        val initCode = generateInitCode(ephemeralCredentials.address, salt)

        return EphemeralAccountData(
            address = accountAddress,
            ownerCredentials = ephemeralCredentials,
            salt = salt,
            initCode = initCode,
        )
    }

    /**
     * Calculate the counterfactual address of a SimpleAccount
     * Uses CREATE2 formula: keccak256(0xff ++ factory ++ salt ++ keccak256(initCode))
     */
    private fun calculateAccountAddress(
        owner: String,
        salt: BigInteger,
    ): String {
        // SimpleAccount creation code hash (from factory)
        // This is a simplified version - in production, query the factory
        val createAccountSelector = "0x5fbfb9cf" // createAccount(address,uint256)
        val ownerPadded = owner.removePrefix("0x").padStart(64, '0')
        val saltHex = salt.toString(16).padStart(64, '0')

        val initCodeHash =
            Hash.sha3(
                Numeric.hexStringToByteArray(createAccountSelector + ownerPadded + saltHex),
            )

        // CREATE2: keccak256(0xff ++ factory ++ salt ++ initCodeHash)
        val factoryBytes = Numeric.hexStringToByteArray(SIMPLE_ACCOUNT_FACTORY)
        val saltBytes = Numeric.hexStringToByteArray(saltHex)

        val preImage = byteArrayOf(0xff.toByte()) + factoryBytes + saltBytes + initCodeHash
        val addressHash = Hash.sha3(preImage)

        // Take last 20 bytes as address
        return "0x" + Numeric.toHexStringNoPrefix(addressHash).takeLast(40)
    }

    /**
     * Generate init code for account deployment
     * initCode = factory address + createAccount(owner, salt) calldata
     */
    private fun generateInitCode(
        owner: String,
        salt: BigInteger,
    ): String {
        val createAccountSelector = "5fbfb9cf" // createAccount(address,uint256)
        val ownerPadded = owner.removePrefix("0x").lowercase().padStart(64, '0')
        val saltHex = salt.toString(16).padStart(64, '0')

        return SIMPLE_ACCOUNT_FACTORY + createAccountSelector + ownerPadded + saltHex
    }

    /**
     * Create a UserOperation for a privacy-preserving payment
     *
     * @param ephemeralAccount The ephemeral account data
     * @param targetAddress Recipient address (merchant's stealth address)
     * @param tokenAddress Token contract address (0x0 for native)
     * @param amount Amount in smallest unit
     * @param isDeployed Whether the ephemeral account is already deployed
     * @return UserOperation ready for signing
     */
    fun createPaymentUserOp(
        ephemeralAccount: EphemeralAccountData,
        targetAddress: String,
        tokenAddress: String,
        amount: BigInteger,
        nonce: BigInteger = BigInteger.ZERO,
        isDeployed: Boolean = false,
    ): UserOperation {
        // Encode the transfer call
        val callData =
            if (tokenAddress == "0x0000000000000000000000000000000000000000" ||
                tokenAddress.equals("native", ignoreCase = true)
            ) {
                // Native transfer via execute(to, value, data)
                encodeExecute(targetAddress, amount, "0x")
            } else {
                // ERC20 transfer via execute(tokenAddress, 0, transfer(to, amount))
                val transferData = encodeERC20Transfer(targetAddress, amount)
                encodeExecute(tokenAddress, BigInteger.ZERO, transferData)
            }

        return UserOperation(
            sender = ephemeralAccount.address,
            nonce = "0x" + nonce.toString(16),
            initCode = if (isDeployed) "0x" else ephemeralAccount.initCode,
            callData = callData,
            callGasLimit = "0x30000",
            verificationGasLimit = if (isDeployed) "0x20000" else "0x60000",
            preVerificationGas = "0x10000",
            maxFeePerGas = "0x" + BigInteger.valueOf(30_000_000_000).toString(16),
            maxPriorityFeePerGas = "0x" + BigInteger.valueOf(1_500_000_000).toString(16),
            paymasterAndData = "0x",
            signature = "0x",
        )
    }

    /**
     * Sign a UserOperation with the ephemeral account owner key
     */
    fun signUserOperation(
        userOp: UserOperation,
        ephemeralCredentials: Credentials,
        chainId: Long,
    ): UserOperation {
        val userOpHash = calculateUserOpHash(userOp, ENTRY_POINT_V06, chainId)

        val signature =
            org.web3j.crypto.Sign.signMessage(
                userOpHash,
                ephemeralCredentials.ecKeyPair,
                false,
            )

        val sigBytes = ByteArray(65)
        System.arraycopy(signature.r, 0, sigBytes, 0, 32)
        System.arraycopy(signature.s, 0, sigBytes, 32, 32)
        sigBytes[64] = signature.v[0]

        return userOp.copy(signature = "0x" + sigBytes.joinToString("") { "%02x".format(it) })
    }

    /**
     * Calculate UserOperation hash for signing
     * Following ERC-4337 specification
     */
    private fun calculateUserOpHash(
        userOp: UserOperation,
        entryPoint: String,
        chainId: Long,
    ): ByteArray {
        // Pack UserOp fields
        val packed = packUserOp(userOp)
        val userOpHash = Hash.sha3(packed)

        // Final hash includes entryPoint and chainId
        val entryPointBytes = Numeric.hexStringToByteArray(entryPoint.padStart(64, '0'))
        val chainIdBytes =
            BigInteger.valueOf(chainId).toByteArray().let {
                ByteArray(32 - it.size) + it
            }

        return Hash.sha3(userOpHash + entryPointBytes + chainIdBytes)
    }

    private fun packUserOp(userOp: UserOperation): ByteArray {
        // Simplified packing - in production use proper ABI encoding
        val sender = Numeric.hexStringToByteArray(userOp.sender.removePrefix("0x").padStart(64, '0'))
        val nonce = Numeric.hexStringToByteArray(userOp.nonce.removePrefix("0x").padStart(64, '0'))
        val initCodeHash = Hash.sha3(Numeric.hexStringToByteArray(userOp.initCode))
        val callDataHash = Hash.sha3(Numeric.hexStringToByteArray(userOp.callData))
        val callGasLimit = Numeric.hexStringToByteArray(userOp.callGasLimit.removePrefix("0x").padStart(64, '0'))
        val verificationGasLimit = Numeric.hexStringToByteArray(userOp.verificationGasLimit.removePrefix("0x").padStart(64, '0'))
        val preVerificationGas = Numeric.hexStringToByteArray(userOp.preVerificationGas.removePrefix("0x").padStart(64, '0'))
        val maxFeePerGas = Numeric.hexStringToByteArray(userOp.maxFeePerGas.removePrefix("0x").padStart(64, '0'))
        val maxPriorityFeePerGas = Numeric.hexStringToByteArray(userOp.maxPriorityFeePerGas.removePrefix("0x").padStart(64, '0'))
        val paymasterAndDataHash = Hash.sha3(Numeric.hexStringToByteArray(userOp.paymasterAndData))

        return sender + nonce + initCodeHash + callDataHash + callGasLimit +
            verificationGasLimit + preVerificationGas + maxFeePerGas +
            maxPriorityFeePerGas + paymasterAndDataHash
    }

    private fun encodeExecute(
        to: String,
        value: BigInteger,
        data: String,
    ): String {
        // execute(address dest, uint256 value, bytes calldata func)
        val selector = "b61d27f6"
        val toPadded = to.removePrefix("0x").lowercase().padStart(64, '0')
        val valuePadded = value.toString(16).padStart(64, '0')
        val dataOffset = "0000000000000000000000000000000000000000000000000000000000000060" // offset 96
        val dataBytes = Numeric.hexStringToByteArray(data.removePrefix("0x"))
        val dataLength = dataBytes.size.toString(16).padStart(64, '0')
        val dataPadded =
            Numeric.toHexStringNoPrefix(dataBytes).padEnd(
                (dataBytes.size + 31) / 32 * 64,
                '0',
            )

        return "0x$selector$toPadded$valuePadded$dataOffset$dataLength$dataPadded"
    }

    private fun encodeERC20Transfer(
        to: String,
        amount: BigInteger,
    ): String {
        // transfer(address to, uint256 amount)
        val selector = "a9059cbb"
        val toPadded = to.removePrefix("0x").lowercase().padStart(64, '0')
        val amountPadded = amount.toString(16).padStart(64, '0')

        return "0x$selector$toPadded$amountPadded"
    }
}

/**
 * ERC-4337 UserOperation structure
 */
data class UserOperation(
    val sender: String,
    val nonce: String,
    val initCode: String = "0x",
    val callData: String,
    val callGasLimit: String,
    val verificationGasLimit: String,
    val preVerificationGas: String,
    val maxFeePerGas: String,
    val maxPriorityFeePerGas: String,
    val paymasterAndData: String = "0x",
    val signature: String = "0x",
)
