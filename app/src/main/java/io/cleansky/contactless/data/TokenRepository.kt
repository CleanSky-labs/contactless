package io.cleansky.contactless.data

import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.model.TokenBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class TokenRepository(
    private val tokenAllowlistRepository: TokenAllowlistRepository,
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val jsonMediaType = "application/json".toMediaType()

    sealed class BalanceResult {
        data class Success(val balances: List<TokenBalance>) : BalanceResult()

        data class Error(val message: String) : BalanceResult()
    }

    sealed class ValidationResult {
        data class Valid(val token: Token) : ValidationResult()

        data class Invalid(val message: String) : ValidationResult()
    }

    suspend fun getNativeBalance(
        walletAddress: String,
        chainConfig: ChainConfig,
    ): BigInteger? =
        withContext(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "eth_getBalance")
                        put(
                            "params",
                            JSONArray().apply {
                                put(walletAddress)
                                put("latest")
                            },
                        )
                        put("id", 1)
                    }

                val response = makeRpcCall(chainConfig.rpcUrl, payload)
                response?.let {
                    val resultHex = it.optString("result", "0x0")
                    parseHexToBigInteger(resultHex)
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getTokenBalance(
        walletAddress: String,
        tokenAddress: String,
        chainConfig: ChainConfig,
    ): BigInteger? =
        withContext(Dispatchers.IO) {
            try {
                // ERC20 balanceOf(address) selector: 0x70a08231
                val paddedAddress = walletAddress.removePrefix("0x").padStart(64, '0')
                val data = "0x70a08231$paddedAddress"

                val payload =
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "eth_call")
                        put(
                            "params",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("to", tokenAddress)
                                        put("data", data)
                                    },
                                )
                                put("latest")
                            },
                        )
                        put("id", 1)
                    }

                val response = makeRpcCall(chainConfig.rpcUrl, payload)
                response?.let {
                    val resultHex = it.optString("result", "0x0")
                    parseHexToBigInteger(resultHex)
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getAllBalances(
        walletAddress: String,
        chainConfig: ChainConfig,
    ): BalanceResult =
        withContext(Dispatchers.IO) {
            try {
                // Initialize default tokens if needed
                tokenAllowlistRepository.initializeDefaultTokensIfNeeded(chainConfig.chainId)

                val allowedTokens = tokenAllowlistRepository.getAllowedTokensForChain(chainConfig.chainId)
                val balances = mutableListOf<TokenBalance>()

                for (token in allowedTokens) {
                    val balance =
                        if (token.isNative) {
                            getNativeBalance(walletAddress, chainConfig)
                        } else {
                            getTokenBalance(walletAddress, token.address, chainConfig)
                        }

                    balance?.let {
                        balances.add(TokenBalance.create(token, it))
                    }
                }

                BalanceResult.Success(balances.sortedByDescending { !it.isZero() })
            } catch (e: Exception) {
                BalanceResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun validateToken(
        tokenAddress: String,
        chainConfig: ChainConfig,
    ): ValidationResult =
        withContext(Dispatchers.IO) {
            try {
                // Get symbol
                val symbolResult = callStringMethod(tokenAddress, "0x95d89b41", chainConfig.rpcUrl) // symbol()
                val symbol = symbolResult ?: return@withContext ValidationResult.Invalid("Could not read token symbol")

                // Get name
                val nameResult = callStringMethod(tokenAddress, "0x06fdde03", chainConfig.rpcUrl) // name()
                val name = nameResult ?: return@withContext ValidationResult.Invalid("Could not read token name")

                // Get decimals
                val decimalsResult = callUintMethod(tokenAddress, "0x313ce567", chainConfig.rpcUrl) // decimals()
                val decimals = decimalsResult?.toInt() ?: return@withContext ValidationResult.Invalid("Could not read token decimals")

                if (decimals !in 0..18) {
                    return@withContext ValidationResult.Invalid("Invalid decimals: $decimals")
                }

                ValidationResult.Valid(
                    Token(
                        address = tokenAddress,
                        symbol = symbol,
                        name = name,
                        decimals = decimals,
                        chainId = chainConfig.chainId,
                        isNative = false,
                    ),
                )
            } catch (e: Exception) {
                ValidationResult.Invalid(e.message ?: "Validation failed")
            }
        }

    private suspend fun callStringMethod(
        contractAddress: String,
        methodSelector: String,
        rpcUrl: String,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "eth_call")
                        put(
                            "params",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("to", contractAddress)
                                        put("data", methodSelector)
                                    },
                                )
                                put("latest")
                            },
                        )
                        put("id", 1)
                    }

                val response = makeRpcCall(rpcUrl, payload)
                response?.let {
                    val resultHex = it.optString("result", "")
                    if (resultHex.length > 2) {
                        decodeAbiString(resultHex)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun callUintMethod(
        contractAddress: String,
        methodSelector: String,
        rpcUrl: String,
    ): BigInteger? =
        withContext(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "eth_call")
                        put(
                            "params",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("to", contractAddress)
                                        put("data", methodSelector)
                                    },
                                )
                                put("latest")
                            },
                        )
                        put("id", 1)
                    }

                val response = makeRpcCall(rpcUrl, payload)
                response?.let {
                    val resultHex = it.optString("result", "0x0")
                    parseHexToBigInteger(resultHex)
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun makeRpcCall(
        rpcUrl: String,
        payload: JSONObject,
    ): JSONObject? {
        val request =
            Request.Builder()
                .url(rpcUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                return body?.let { JSONObject(it) }
            }
        }
        return null
    }

    private fun parseHexToBigInteger(hex: String): BigInteger {
        val cleaned = hex.removePrefix("0x")
        return if (cleaned.isEmpty() || cleaned == "0") {
            BigInteger.ZERO
        } else {
            BigInteger(cleaned, 16)
        }
    }

    private fun decodeAbiString(hex: String): String? {
        return try {
            val data = hex.removePrefix("0x")
            if (data.length < 128) return null

            // ABI encoded string: offset (32 bytes) + length (32 bytes) + data
            val offsetHex = data.substring(0, 64)
            val offset = BigInteger(offsetHex, 16).toInt() * 2

            if (offset + 64 > data.length) return null

            val lengthHex = data.substring(offset, offset + 64)
            val length = BigInteger(lengthHex, 16).toInt()

            if (offset + 64 + length * 2 > data.length) return null

            val stringHex = data.substring(offset + 64, offset + 64 + length * 2)
            val bytes = stringHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
