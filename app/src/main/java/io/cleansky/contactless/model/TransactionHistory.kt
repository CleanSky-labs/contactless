package io.cleansky.contactless.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigInteger

enum class TransactionType {
    PAYMENT_RECEIVED,  // Cobro recibido (merchant)
    PAYMENT_SENT,      // Pago enviado (payer)
    REFUND_SENT,       // Devolución enviada (merchant)
    REFUND_RECEIVED    // Devolución recibida (payer)
}

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}

data class Transaction(
    val id: String,                    // UUID único
    val txHash: String,                // Hash de la transacción on-chain
    val type: TransactionType,
    val status: TransactionStatus,
    val amount: String,                // Monto original en unidades menores
    val refundedAmount: String = "0",  // Monto ya devuelto
    val asset: String,                 // Dirección del token
    val chainId: Long,
    val counterparty: String,          // Dirección del otro participante
    val merchantId: String,
    val invoiceId: String,
    val timestamp: Long,               // Unix timestamp
    val note: String = "",             // Nota opcional
    val refundTxHashes: List<String> = emptyList() // Hashes de devoluciones
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

    private fun formatTokenAmount(amount: BigInteger, decimals: Int): String {
        return NumberFormatter.formatCurrency(amount, decimals)
    }

    fun withRefund(refundAmount: BigInteger, refundTxHash: String): Transaction {
        require(refundAmount > BigInteger.ZERO) { "Refund amount must be positive" }
        require(refundTxHash.isNotBlank()) { "Refund tx hash cannot be blank" }
        val newRefundedAmount = (getRefundedAmountBigInt() + refundAmount).coerceAtMost(getAmountBigInt())
        val newStatus = if (newRefundedAmount >= getAmountBigInt()) {
            TransactionStatus.REFUNDED
        } else {
            TransactionStatus.PARTIALLY_REFUNDED
        }
        return copy(
            refundedAmount = newRefundedAmount.toString(),
            status = newStatus,
            refundTxHashes = refundTxHashes + refundTxHash
        )
    }
}

data class RefundRequest(
    val originalTxId: String,
    val amount: String,          // Monto a devolver
    val recipientAddress: String,
    val asset: String,
    val chainId: Long
)
