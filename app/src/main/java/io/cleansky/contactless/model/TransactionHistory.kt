package io.cleansky.contactless.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigInteger

enum class TransactionType {
    PAYMENT_RECEIVED,
    PAYMENT_SENT,
    REFUND_SENT,
    REFUND_RECEIVED,
}

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
}

data class Transaction(
    val id: String,
    val txHash: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val amount: String,
    val refundedAmount: String = "0",
    val asset: String,
    val chainId: Long,
    val counterparty: String,
    val merchantId: String,
    val invoiceId: String,
    val timestamp: Long,
    val note: String = "",
    val refundTxHashes: List<String> = emptyList(),
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): Transaction? {
            return try {
                gson.fromJson(json, Transaction::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun listFromJson(json: String): List<Transaction> {
            return try {
                val type = object : TypeToken<List<Transaction>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun listToJson(list: List<Transaction>): String {
            return gson.toJson(list)
        }
    }

    fun toJson(): String = gson.toJson(this)

    fun getAmountBigInt(): BigInteger = BigInteger(amount)

    fun getRefundedAmountBigInt(): BigInteger = BigInteger(refundedAmount)

    fun getRemainingRefundable(): BigInteger {
        return (getAmountBigInt() - getRefundedAmountBigInt()).coerceAtLeast(BigInteger.ZERO)
    }

    fun canRefund(): Boolean {
        return (type == TransactionType.PAYMENT_RECEIVED || type == TransactionType.PAYMENT_SENT) &&
            status != TransactionStatus.REFUNDED &&
            getRemainingRefundable() > BigInteger.ZERO
    }

    fun getFormattedAmount(decimals: Int = 6): String {
        return formatTokenAmount(getAmountBigInt(), decimals)
    }

    fun getFormattedRefundedAmount(decimals: Int = 6): String {
        return formatTokenAmount(getRefundedAmountBigInt(), decimals)
    }

    fun getFormattedRemainingRefundable(decimals: Int = 6): String {
        return formatTokenAmount(getRemainingRefundable(), decimals)
    }

    private fun formatTokenAmount(
        amount: BigInteger,
        decimals: Int,
    ): String {
        return NumberFormatter.formatCurrency(amount, decimals)
    }

    fun withRefund(
        refundAmount: BigInteger,
        refundTxHash: String,
    ): Transaction {
        require(refundAmount > BigInteger.ZERO) { "Refund amount must be positive" }
        require(refundTxHash.isNotBlank()) { "Refund tx hash cannot be blank" }
        val newRefundedAmount = (getRefundedAmountBigInt() + refundAmount).coerceAtMost(getAmountBigInt())
        val newStatus =
            if (newRefundedAmount >= getAmountBigInt()) {
                TransactionStatus.REFUNDED
            } else {
                TransactionStatus.PARTIALLY_REFUNDED
            }
        return copy(
            refundedAmount = newRefundedAmount.toString(),
            status = newStatus,
            refundTxHashes = refundTxHashes + refundTxHash,
        )
    }
}

data class RefundRequest(
    val originalTxId: String,
    val amount: String,
    val recipientAddress: String,
    val asset: String,
    val chainId: Long,
)
