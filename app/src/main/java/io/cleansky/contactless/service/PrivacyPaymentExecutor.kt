package io.cleansky.contactless.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.cleansky.contactless.crypto.EphemeralAccount
import io.cleansky.contactless.crypto.UserOperation
import io.cleansky.contactless.data.EphemeralAccountRecord
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.model.BundlerSelection
import io.cleansky.contactless.model.BundlerSelector
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.PaymasterConfig
import io.cleansky.contactless.model.PublicBundler
import io.cleansky.contactless.model.SignedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Privacy-Preserving Payment Executor (v0.5)
 *
 * Executes payments through ephemeral smart accounts for payer privacy.
 * The main wallet signs the UserOperation, but on-chain the payment appears
 * to come from the ephemeral account.
 *
 * Bundler Selection Priority:
 * 1. Custom bundler URL (if configured)
 * 2. API-key bundler (Pimlico/Stackup/Alchemy with key)
 * 3. Public bundler (testnets, no key required)
 *
 * Flow:
 * 1. Derive ephemeral account from main wallet
 * 2. Create UserOperation for payment
 * 3. Get Paymaster sponsorship (if available)
 * 4. Sign UserOperation with ephemeral owner key
 * 5. Submit to Bundler
 * 6. On-chain: ephemeral → merchant (main wallet hidden)
 */
class PrivacyPaymentExecutor(
    private val chainConfig: ChainConfig,
    private val mainCredentials: Credentials,
    private val privacyPayerRepository: PrivacyPayerRepository,
    private val paymasterConfig: PaymasterConfig? = null,
    private val apiKey: String? = null,
    private val customBundlerUrl: String? = null
) {
    // Select bundler based on available options
    private val bundlerSelection: BundlerSelection = BundlerSelector.selectBundler(
        chainId = chainConfig.chainId,
        apiKey = apiKey,
        customBundlerUrl = customBundlerUrl,
        preferredProvider = paymasterConfig
    )
    private val web3j: Web3j = Web3j.build(HttpService(chainConfig.rpcUrl))
    private val gson = Gson()

    sealed class PrivacyExecutionResult {
        data class Success(
            val txHash: String,
            val ephemeralAddress: String
        ) : PrivacyExecutionResult()

        data class Pending(
            val userOpHash: String,
            val ephemeralAddress: String
        ) : PrivacyExecutionResult()

        data class Error(val message: String) : PrivacyExecutionResult()
    }

    /**
     * Execute a privacy-preserving payment
     *
     * @param signedTx The signed transaction (contains merchant address, amount, etc.)
     * @return Execution result with ephemeral account info
     */
    suspend fun executePrivatePayment(signedTx: SignedTransaction): PrivacyExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val unavailable = unavailableBundlerError()
                if (unavailable != null) return@withContext unavailable

                val context = buildExecutionContext(signedTx)
                var userOp = buildUnsignedUserOp(context, signedTx)
                userOp = maybeSponsorUserOperation(userOp)
                userOp = signUserOperation(userOp, context.ephemeralAccount.ownerCredentials)

                val result = sendUserOperation(userOp)
                recordEphemeralAccountIfNeeded(result, context, signedTx)
                result
            } catch (e: Exception) {
                PrivacyExecutionResult.Error(e.message ?: "Privacy payment error")
            }
        }
    }

    private fun unavailableBundlerError(): PrivacyExecutionResult.Error? {
        return if (bundlerSelection.url == null) {
            PrivacyExecutionResult.Error(
                "No bundler available for chain ${chainConfig.chainId}. " +
                    "Add API key or use a testnet with public bundlers."
            )
        } else {
            null
        }
    }

    private data class ExecutionContext(
        val paymentIndex: Long,
        val ephemeralAccount: EphemeralAccount.EphemeralAccountData,
        val targetAddress: String,
        val isDeployed: Boolean,
        val nonce: BigInteger
    )

    private suspend fun buildExecutionContext(signedTx: SignedTransaction): ExecutionContext {
        val paymentIndex = privacyPayerRepository.getNextPaymentIndex()
        val ephemeralAccount = EphemeralAccount.deriveEphemeralAccount(mainCredentials, paymentIndex)
        val targetAddress = signedTx.stealthAddress ?: signedTx.escrow
        val isDeployed = checkAccountDeployed(ephemeralAccount.address)
        val nonce = if (isDeployed) getAccountNonce(ephemeralAccount.address) else BigInteger.ZERO
        return ExecutionContext(paymentIndex, ephemeralAccount, targetAddress, isDeployed, nonce)
    }

    private fun buildUnsignedUserOp(context: ExecutionContext, signedTx: SignedTransaction): UserOperation {
        val amount = BigInteger(signedTx.amount)
        return EphemeralAccount.createPaymentUserOp(
            ephemeralAccount = context.ephemeralAccount,
            targetAddress = context.targetAddress,
            tokenAddress = signedTx.asset,
            amount = amount,
            nonce = context.nonce,
            isDeployed = context.isDeployed
        )
    }

    private fun maybeSponsorUserOperation(userOp: UserOperation): UserOperation {
        return if (bundlerSelection.supportsPaymaster && paymasterConfig != null && !apiKey.isNullOrBlank()) {
            sponsorUserOperation(userOp)
        } else {
            userOp
        }
    }

    private fun signUserOperation(userOp: UserOperation, credentials: Credentials): UserOperation {
        return EphemeralAccount.signUserOperation(userOp, credentials, chainConfig.chainId)
    }

    private suspend fun recordEphemeralAccountIfNeeded(
        result: PrivacyExecutionResult,
        context: ExecutionContext,
        signedTx: SignedTransaction
    ) {
        when (result) {
            is PrivacyExecutionResult.Success,
            is PrivacyExecutionResult.Pending -> {
                privacyPayerRepository.recordEphemeralAccount(
                    EphemeralAccountRecord(
                        address = context.ephemeralAccount.address,
                        paymentIndex = context.paymentIndex,
                        invoiceId = signedTx.invoiceId,
                        merchantAddress = context.targetAddress,
                        amount = signedTx.amount,
                        asset = signedTx.asset,
                        chainId = signedTx.chainId,
                        txHash = when (result) {
                            is PrivacyExecutionResult.Success -> result.txHash
                            is PrivacyExecutionResult.Pending -> result.userOpHash
                            else -> null
                        }
                    )
                )
            }
            else -> Unit
        }
    }

    /**
     * Check if privacy payments are available for this chain
     */
    fun isAvailable(): Boolean = bundlerSelection.url != null

    /**
     * Get bundler info for display
     */
    fun getBundlerInfo(): String = "${bundlerSelection.name} (${if (bundlerSelection.supportsPaymaster) "with paymaster" else "no paymaster"})"

    private suspend fun checkAccountDeployed(address: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val code = web3j.ethGetCode(address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST)
                    .send()
                    .code
                code != null && code != "0x" && code != "0x0"
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun getAccountNonce(address: String): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                // Call EntryPoint.getNonce(address, 0)
                val getNonceData = "0x35567e1a" + // getNonce(address,uint192)
                        address.removePrefix("0x").lowercase().padStart(64, '0') +
                        "0".padStart(64, '0')

                val result = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        null,
                        EphemeralAccount.ENTRY_POINT_V06,
                        getNonceData
                    ),
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send()

                if (result.value != null && result.value != "0x") {
                    BigInteger(result.value.removePrefix("0x"), 16)
                } else {
                    BigInteger.ZERO
                }
            } catch (e: Exception) {
                BigInteger.ZERO
            }
        }
    }

    private fun sponsorUserOperation(userOp: UserOperation): UserOperation {
        if (paymasterConfig == null || apiKey.isNullOrBlank()) {
            return userOp // No paymaster available
        }

        val paymasterUrl = "${paymasterConfig.paymasterUrl}/${chainConfig.chainId}/rpc?apikey=$apiKey"
        val connection = JsonRpcHttp.createPostConnection(paymasterUrl)

        // Pimlico-style sponsorship request
        val requestBody = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "pm_sponsorUserOperation")
            add("params", gson.toJsonTree(listOf(
                userOpMap(userOp, signature = "0x" + "00".repeat(65), paymasterAndData = "0x"),
                paymasterConfig.entryPointAddress,
                mapOf("sponsorshipPolicyId" to "sp_cleansky_privacy") // Optional policy
            )))
        }

        JsonRpcHttp.writeJsonBody(connection, requestBody)
        val json = gson.fromJson(JsonRpcHttp.readResponseBody(connection), JsonObject::class.java)
        return PrivacyPaymentParser.parsePaymasterResponse(json, userOp)
    }

    private fun sendUserOperation(userOp: UserOperation): PrivacyExecutionResult {
        val bundlerUrl = bundlerSelection.url
            ?: return PrivacyExecutionResult.Error("No bundler available")

        val connection = JsonRpcHttp.createPostConnection(bundlerUrl)

        val requestBody = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "eth_sendUserOperation")
            add("params", gson.toJsonTree(listOf(
                userOpMap(userOp, signature = userOp.signature, paymasterAndData = userOp.paymasterAndData),
                paymasterConfig?.entryPointAddress ?: EphemeralAccount.ENTRY_POINT_V06
            )))
        }

        JsonRpcHttp.writeJsonBody(connection, requestBody)
        val json = gson.fromJson(JsonRpcHttp.readResponseBody(connection), JsonObject::class.java)
        return PrivacyPaymentParser.parseSendUserOperationResponse(json, userOp.sender)
    }

    /**
     * Check the status of a pending UserOperation
     */
    suspend fun checkUserOpStatus(userOpHash: String): PrivacyExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val bundlerUrl = bundlerSelection.url
                    ?: return@withContext PrivacyExecutionResult.Error("No bundler available")

                val connection = JsonRpcHttp.createPostConnection(bundlerUrl)

                val requestBody = JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("id", 1)
                    addProperty("method", "eth_getUserOperationReceipt")
                    add("params", gson.toJsonTree(listOf(userOpHash)))
                }

                JsonRpcHttp.writeJsonBody(connection, requestBody)
                val json = gson.fromJson(JsonRpcHttp.readResponseBody(connection), JsonObject::class.java)
                PrivacyPaymentParser.parseUserOpStatusResponse(json, userOpHash)
            } catch (e: Exception) {
                PrivacyExecutionResult.Error(e.message ?: "Error checking status")
            }
        }
    }

    private fun userOpMap(
        userOp: UserOperation,
        signature: String,
        paymasterAndData: String
    ): Map<String, String> {
        return mapOf(
            "sender" to userOp.sender,
            "nonce" to userOp.nonce,
            "initCode" to userOp.initCode,
            "callData" to userOp.callData,
            "callGasLimit" to userOp.callGasLimit,
            "verificationGasLimit" to userOp.verificationGasLimit,
            "preVerificationGas" to userOp.preVerificationGas,
            "maxFeePerGas" to userOp.maxFeePerGas,
            "maxPriorityFeePerGas" to userOp.maxPriorityFeePerGas,
            "paymasterAndData" to paymasterAndData,
            "signature" to signature
        )
    }

}

internal object PrivacyPaymentParser {
    fun parsePaymasterResponse(json: JsonObject, userOp: UserOperation): UserOperation {
        if (json.has("error")) {
            throw Exception("Paymaster error: ${json.getAsJsonObject("error")?.get("message")?.asString}")
        }

        val result = json.getAsJsonObject("result")
            ?: throw Exception("No result from Paymaster")

        return userOp.copy(
            paymasterAndData = result.get("paymasterAndData")?.asString ?: "0x",
            callGasLimit = result.get("callGasLimit")?.asString ?: userOp.callGasLimit,
            verificationGasLimit = result.get("verificationGasLimit")?.asString ?: userOp.verificationGasLimit,
            preVerificationGas = result.get("preVerificationGas")?.asString ?: userOp.preVerificationGas
        )
    }

    fun parseSendUserOperationResponse(
        json: JsonObject,
        sender: String
    ): PrivacyPaymentExecutor.PrivacyExecutionResult {
        if (json.has("result")) {
            return PrivacyPaymentExecutor.PrivacyExecutionResult.Pending(json.get("result").asString, sender)
        }
        val error = json.getAsJsonObject("error")?.get("message")?.asString ?: "Unknown bundler error"
        return PrivacyPaymentExecutor.PrivacyExecutionResult.Error(error)
    }

    fun parseUserOpStatusResponse(
        json: JsonObject,
        userOpHash: String
    ): PrivacyPaymentExecutor.PrivacyExecutionResult {
        if (!json.has("result") || json.get("result").isJsonNull) {
            return PrivacyPaymentExecutor.PrivacyExecutionResult.Pending(userOpHash, "")
        }

        val receipt = json.getAsJsonObject("result")
        val txHash = receipt.get("receipt")?.asJsonObject?.get("transactionHash")?.asString
        val success = receipt.get("success")?.asBoolean ?: false
        return if (success && txHash != null) {
            PrivacyPaymentExecutor.PrivacyExecutionResult.Success(txHash, receipt.get("sender")?.asString ?: "")
        } else {
            PrivacyPaymentExecutor.PrivacyExecutionResult.Error("UserOperation failed on-chain")
        }
    }
}
