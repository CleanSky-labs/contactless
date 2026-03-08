package io.cleansky.contactless.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.cleansky.contactless.crypto.UserOperation
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.PaymasterConfig
import io.cleansky.contactless.model.RelayerConfig
import io.cleansky.contactless.model.SignedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

class TransactionExecutor(
    private val chainConfig: ChainConfig,
    private val credentials: Credentials,
    private val executionMode: ExecutionMode = ExecutionMode.DIRECT,
    private val relayerConfig: RelayerConfig? = null,
    private val paymasterConfig: PaymasterConfig? = null,
    private val apiKey: String = "",
) {
    private val web3j: Web3j = Web3j.build(HttpService(chainConfig.rpcUrl))
    private val gson = Gson()

    companion object {
        fun forRelayOnly(
            chainConfig: ChainConfig,
            escrowOverride: String,
            relayerConfig: RelayerConfig,
            apiKey: String,
        ): TransactionExecutor {
            return TransactionExecutor(
                chainConfig = chainConfig.copy(escrowAddress = escrowOverride),
                credentials = Credentials.create("0x0000000000000000000000000000000000000000000000000000000000000001"),
                executionMode = ExecutionMode.RELAYER,
                relayerConfig = relayerConfig,
                apiKey = apiKey,
            )
        }
    }

    sealed class ExecutionResult {
        data class Success(val txHash: String) : ExecutionResult()

        data class Pending(val taskId: String) : ExecutionResult()

        data class Error(val message: String) : ExecutionResult()
    }

    suspend fun executePayment(signedTx: SignedTransaction): ExecutionResult {
        return when (executionMode) {
            ExecutionMode.DIRECT -> executeDirectly(signedTx)
            ExecutionMode.RELAYER -> executeViaRelayer(signedTx)
            ExecutionMode.ACCOUNT_ABSTRACTION -> executeViaAA(signedTx)
        }
    }

    private suspend fun executeDirectly(signedTx: SignedTransaction): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val callData = encodePayFunction(signedTx)
                val nonce =
                    web3j.ethGetTransactionCount(
                        credentials.address,
                        DefaultBlockParameterName.PENDING,
                    ).send().transactionCount

                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(200000)

                val rawTx =
                    RawTransaction.createTransaction(
                        nonce,
                        gasPrice,
                        gasLimit,
                        chainConfig.escrowAddress,
                        BigInteger.ZERO,
                        callData,
                    )

                val signedRawTx = TransactionEncoder.signMessage(rawTx, chainConfig.chainId, credentials)
                val hexValue = Numeric.toHexString(signedRawTx)

                val response = web3j.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    ExecutionResult.Error(response.error.message)
                } else {
                    ExecutionResult.Success(response.transactionHash)
                }
            } catch (e: Exception) {
                ExecutionResult.Error(ServiceErrorCatalog.fromException(e, ServiceErrorCatalog.UNKNOWN_ERROR))
            }
        }
    }

    private suspend fun executeViaRelayer(signedTx: SignedTransaction): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = relayerConfig ?: return@withContext ExecutionResult.Error(ServiceErrorCatalog.RELAYER_NOT_CONFIGURED)
                val callData = encodePayFunction(signedTx)

                when (config.name) {
                    "Gelato" -> executeGelatoRelay(callData)
                    "Biconomy" -> executeBiconomyRelay(callData)
                    else -> executeGenericRelay(config, callData)
                }
            } catch (e: Exception) {
                ExecutionResult.Error(ServiceErrorCatalog.fromException(e, ServiceErrorCatalog.RELAYER_ERROR))
            }
        }
    }

    private fun executeGelatoRelay(callData: String): ExecutionResult {
        val url = URL("${relayerConfig!!.url}/relays/v2/sponsored-call")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        if (apiKey.isNotEmpty()) {
            connection.setRequestProperty("X-API-Key", apiKey)
        }
        connection.doOutput = true

        val requestBody =
            JsonObject().apply {
                addProperty("chainId", chainConfig.chainId)
                addProperty("target", chainConfig.escrowAddress)
                addProperty("data", callData)
                addProperty("sponsorApiKey", apiKey)
            }

        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().readText()

        return if (responseCode == 200 || responseCode == 201) {
            val json = gson.fromJson(response, JsonObject::class.java)
            val taskId = json.get("taskId")?.asString ?: ""
            ExecutionResult.Pending(taskId)
        } else {
            ExecutionResult.Error("Gelato ${ServiceErrorCatalog.RELAYER_ERROR.lowercase()}: $response")
        }
    }

    private fun executeBiconomyRelay(callData: String): ExecutionResult {
        val url = URL("${relayerConfig!!.url}/api/v1/relay")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.doOutput = true

        val requestBody =
            JsonObject().apply {
                addProperty("to", chainConfig.escrowAddress)
                addProperty("data", callData)
                addProperty("chainId", chainConfig.chainId)
                add("metaTransactionType", gson.toJsonTree("EIP2771"))
            }

        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().readText()

        return if (responseCode == 200) {
            val json = gson.fromJson(response, JsonObject::class.java)
            val txHash = json.get("txHash")?.asString
            if (txHash != null) {
                ExecutionResult.Success(txHash)
            } else {
                val taskId = json.get("taskId")?.asString ?: ""
                ExecutionResult.Pending(taskId)
            }
        } else {
            ExecutionResult.Error("Biconomy ${ServiceErrorCatalog.RELAYER_ERROR.lowercase()}: $response")
        }
    }

    private fun executeGenericRelay(
        config: RelayerConfig,
        callData: String,
    ): ExecutionResult {
        val url = URL("${config.url}/relay")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        if (config.apiKey.isNotEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        connection.doOutput = true

        val requestBody =
            JsonObject().apply {
                addProperty("chainId", chainConfig.chainId)
                addProperty("to", chainConfig.escrowAddress)
                addProperty("data", callData)
            }

        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().readText()

        return if (responseCode in 200..299) {
            val json = gson.fromJson(response, JsonObject::class.java)
            val txHash = json.get("txHash")?.asString
            if (txHash != null) {
                ExecutionResult.Success(txHash)
            } else {
                ExecutionResult.Pending(json.get("taskId")?.asString ?: "unknown")
            }
        } else {
            ExecutionResult.Error("Relay ${ServiceErrorCatalog.RELAYER_ERROR.lowercase()}: $response")
        }
    }

    private suspend fun executeViaAA(signedTx: SignedTransaction): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = paymasterConfig ?: return@withContext ExecutionResult.Error(ServiceErrorCatalog.PAYMASTER_NOT_CONFIGURED)
                val callData = encodePayFunction(signedTx)

                // Build UserOperation
                val userOp = buildUserOperation(callData)

                val sponsoredUserOp = sponsorUserOperation(userOp, config)

                // Sign UserOperation
                val signedUserOp = signUserOperation(sponsoredUserOp)

                // Send to bundler
                sendUserOperation(signedUserOp, config)
            } catch (e: Exception) {
                ExecutionResult.Error(ServiceErrorCatalog.fromException(e, ServiceErrorCatalog.ACCOUNT_ABSTRACTION_ERROR))
            }
        }
    }

    private fun buildUserOperation(callData: String): UserOperation {
        val executeCallData = encodeExecuteFunction(chainConfig.escrowAddress, "0", callData)

        return UserOperation(
            sender = credentials.address,
            nonce = "0x0",
            callData = executeCallData,
            callGasLimit = "0x50000",
            verificationGasLimit = "0x50000",
            preVerificationGas = "0x10000",
            maxFeePerGas = "0x" + BigInteger.valueOf(30_000_000_000).toString(16),
            maxPriorityFeePerGas = "0x" + BigInteger.valueOf(1_000_000_000).toString(16),
        )
    }

    private fun sponsorUserOperation(
        userOp: UserOperation,
        config: PaymasterConfig,
    ): UserOperation {
        val connection =
            JsonRpcHttp.createPostConnection(
                "${config.paymasterUrl}/${chainConfig.chainId}/rpc?apikey=$apiKey",
            )

        val requestBody =
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "pm_sponsorUserOperation")
                add("params", gson.toJsonTree(listOf(userOp, config.entryPointAddress)))
            }

        JsonRpcHttp.writeJsonBody(connection, requestBody)
        val json = gson.fromJson(JsonRpcHttp.readResponseBody(connection), JsonObject::class.java)
        return PrivacyPaymentParser.parsePaymasterResponse(json, userOp)
    }

    private fun signUserOperation(userOp: UserOperation): UserOperation {
        val userOpHash = getUserOpHash(userOp)
        val signature = org.web3j.crypto.Sign.signMessage(userOpHash, credentials.ecKeyPair, false)

        val sigBytes = ByteArray(65)
        System.arraycopy(signature.r, 0, sigBytes, 0, 32)
        System.arraycopy(signature.s, 0, sigBytes, 32, 32)
        sigBytes[64] = signature.v[0]

        return userOp.copy(signature = "0x" + sigBytes.joinToString("") { "%02x".format(it) })
    }

    private fun getUserOpHash(userOp: UserOperation): ByteArray {
        val packed = userOp.sender + userOp.nonce + userOp.callData
        return org.web3j.crypto.Hash.sha3(packed.toByteArray())
    }

    private fun sendUserOperation(
        userOp: UserOperation,
        config: PaymasterConfig,
    ): ExecutionResult {
        val connection =
            JsonRpcHttp.createPostConnection(
                "${config.bundlerUrl}/${chainConfig.chainId}/rpc?apikey=$apiKey",
            )

        val requestBody =
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "eth_sendUserOperation")
                add("params", gson.toJsonTree(listOf(userOp, config.entryPointAddress)))
            }

        JsonRpcHttp.writeJsonBody(connection, requestBody)
        val json = gson.fromJson(JsonRpcHttp.readResponseBody(connection), JsonObject::class.java)

        return if (json.has("result")) {
            val userOpHash = json.get("result").asString
            ExecutionResult.Pending(userOpHash)
        } else {
            val error = json.getAsJsonObject("error")?.get("message")?.asString ?: ServiceErrorCatalog.UNKNOWN_ERROR
            ExecutionResult.Error(error)
        }
    }

    private fun encodePayFunction(signedTx: SignedTransaction): String {
        val function =
            Function(
                "pay",
                listOf(
                    Bytes32(Numeric.hexStringToByteArray(signedTx.merchantId)),
                    Bytes32(Numeric.hexStringToByteArray(signedTx.invoiceId)),
                    Address(signedTx.asset),
                    Uint256(BigInteger(signedTx.amount)),
                    Bytes32(Numeric.hexStringToByteArray(signedTx.nonce)),
                    Uint64(BigInteger.valueOf(signedTx.expiry)),
                    Address(signedTx.payer),
                    DynamicBytes(Numeric.hexStringToByteArray(signedTx.payerSig)),
                ),
                emptyList(),
            )
        return FunctionEncoder.encode(function)
    }

    private fun encodeExecuteFunction(
        to: String,
        value: String,
        data: String,
    ): String {
        val function =
            Function(
                "execute",
                listOf(
                    Address(to),
                    Uint256(BigInteger(value)),
                    DynamicBytes(Numeric.hexStringToByteArray(data)),
                ),
                emptyList(),
            )
        return FunctionEncoder.encode(function)
    }

    suspend fun checkRelayStatus(taskId: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                when (relayerConfig?.name) {
                    "Gelato" -> checkGelatoStatus(taskId)
                    else -> ExecutionResult.Pending(taskId)
                }
            } catch (e: Exception) {
                ExecutionResult.Error(ServiceErrorCatalog.fromException(e, ServiceErrorCatalog.STATUS_CHECK_ERROR))
            }
        }
    }

    private fun checkGelatoStatus(taskId: String): ExecutionResult {
        val url = URL("${relayerConfig!!.url}/tasks/status/$taskId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        val response = connection.inputStream.bufferedReader().readText()
        val json = gson.fromJson(response, JsonObject::class.java)
        val task = json.getAsJsonObject("task")

        return when (task?.get("taskState")?.asString) {
            "ExecSuccess" -> ExecutionResult.Success(task.get("transactionHash")?.asString ?: "")
            "ExecReverted", "Cancelled" -> ExecutionResult.Error(ServiceErrorCatalog.TRANSACTION_FAILED)
            else -> ExecutionResult.Pending(taskId)
        }
    }
}
