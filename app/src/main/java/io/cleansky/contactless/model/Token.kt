package io.cleansky.contactless.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class Token(
    val address: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val chainId: Long,
    val isNative: Boolean = false
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): Token? {
            return try {
                gson.fromJson(json, Token::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun listFromJson(json: String): List<Token> {
            return try {
                val type = object : TypeToken<List<Token>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun listToJson(tokens: List<Token>): String {
            return gson.toJson(tokens)
        }

        // Native token address placeholder
        const val NATIVE_ADDRESS = "0x0000000000000000000000000000000000000000"
    }

    fun toJson(): String = gson.toJson(this)

    fun matches(tokenAddress: String, tokenChainId: Long): Boolean {
        return address.equals(tokenAddress, ignoreCase = true) && chainId == tokenChainId
    }
}

data class TokenBalance(
    val token: Token,
    val balance: BigInteger,
    val balanceFormatted: String
) {
    companion object {
        fun create(token: Token, balance: BigInteger): TokenBalance {
            val formatted = NumberFormatter.formatBalance(balance, token.decimals)
            return TokenBalance(token, balance, formatted)
        }
    }

    fun isZero(): Boolean = balance == BigInteger.ZERO
}
