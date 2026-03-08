package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.model.TransactionStatus
import io.cleansky.contactless.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.util.UUID

private val Context.transactionDataStore: DataStore<Preferences> by preferencesDataStore(name = "transactions")

class TransactionRepository(private val context: Context) {
    companion object {
        private val TRANSACTIONS_KEY = stringPreferencesKey("transactions_list")
    }

    val transactionsFlow: Flow<List<Transaction>> =
        context.transactionDataStore.data.map { prefs ->
            val json = prefs[TRANSACTIONS_KEY] ?: "[]"
            Transaction.listFromJson(json).sortedByDescending { it.timestamp }
        }

    suspend fun getTransactions(): List<Transaction> {
        return transactionsFlow.first()
    }

    suspend fun getTransaction(id: String): Transaction? {
        return getTransactions().find { it.id == id }
    }

    suspend fun getTransactionByTxHash(txHash: String): Transaction? {
        return getTransactions().find { it.txHash == txHash }
    }

    suspend fun addTransaction(transaction: Transaction) {
        context.transactionDataStore.edit { prefs ->
            val currentList = Transaction.listFromJson(prefs[TRANSACTIONS_KEY] ?: "[]").toMutableList()
            currentList.add(transaction)
            prefs[TRANSACTIONS_KEY] = Transaction.listToJson(currentList)
        }
    }

    suspend fun updateTransaction(transaction: Transaction) {
        context.transactionDataStore.edit { prefs ->
            val currentList = Transaction.listFromJson(prefs[TRANSACTIONS_KEY] ?: "[]").toMutableList()
            val index = currentList.indexOfFirst { it.id == transaction.id }
            if (index >= 0) {
                currentList[index] = transaction
                prefs[TRANSACTIONS_KEY] = Transaction.listToJson(currentList)
            }
        }
    }

    suspend fun deleteTransaction(id: String) {
        context.transactionDataStore.edit { prefs ->
            val currentList = Transaction.listFromJson(prefs[TRANSACTIONS_KEY] ?: "[]").toMutableList()
            currentList.removeAll { it.id == id }
            prefs[TRANSACTIONS_KEY] = Transaction.listToJson(currentList)
        }
    }

    suspend fun recordPaymentReceived(
        txHash: String,
        amount: String,
        asset: String,
        chainId: Long,
        payerAddress: String,
        merchantId: String,
        invoiceId: String,
    ): Transaction {
        val tx =
            Transaction(
                id = UUID.randomUUID().toString(),
                txHash = txHash,
                type = TransactionType.PAYMENT_RECEIVED,
                status = TransactionStatus.CONFIRMED,
                amount = amount,
                asset = asset,
                chainId = chainId,
                counterparty = payerAddress,
                merchantId = merchantId,
                invoiceId = invoiceId,
                timestamp = System.currentTimeMillis(),
            )
        addTransaction(tx)
        return tx
    }

    suspend fun recordPaymentSent(
        txHash: String,
        amount: String,
        asset: String,
        chainId: Long,
        merchantAddress: String,
        merchantId: String,
        invoiceId: String,
    ): Transaction {
        val tx =
            Transaction(
                id = UUID.randomUUID().toString(),
                txHash = txHash,
                type = TransactionType.PAYMENT_SENT,
                status = TransactionStatus.CONFIRMED,
                amount = amount,
                asset = asset,
                chainId = chainId,
                counterparty = merchantAddress,
                merchantId = merchantId,
                invoiceId = invoiceId,
                timestamp = System.currentTimeMillis(),
            )
        addTransaction(tx)
        return tx
    }

    suspend fun recordRefundSent(
        originalTxId: String,
        refundTxHash: String,
        refundAmount: BigInteger,
    ) {
        val originalTx = getTransaction(originalTxId) ?: return
        val updatedTx = originalTx.withRefund(refundAmount, refundTxHash)
        updateTransaction(updatedTx)

        val refundTx =
            Transaction(
                id = UUID.randomUUID().toString(),
                txHash = refundTxHash,
                type = TransactionType.REFUND_SENT,
                status = TransactionStatus.CONFIRMED,
                amount = refundAmount.toString(),
                asset = originalTx.asset,
                chainId = originalTx.chainId,
                counterparty = originalTx.counterparty,
                merchantId = originalTx.merchantId,
                invoiceId = originalTx.invoiceId,
                timestamp = System.currentTimeMillis(),
                note = "Devolucion de tx ${originalTx.txHash.take(10)}...",
            )
        addTransaction(refundTx)
    }

    suspend fun getRefundableTransactions(): List<Transaction> {
        return getTransactions().filter { it.canRefund() }
    }

    suspend fun getTotalReceived(chainId: Long? = null): BigInteger {
        return getTransactions()
            .filter { it.type == TransactionType.PAYMENT_RECEIVED }
            .filter { chainId == null || it.chainId == chainId }
            .fold(BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }
    }

    suspend fun getTotalRefunded(chainId: Long? = null): BigInteger {
        return getTransactions()
            .filter { it.type == TransactionType.REFUND_SENT }
            .filter { chainId == null || it.chainId == chainId }
            .fold(BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }
    }

    suspend fun clearAll() {
        context.transactionDataStore.edit { prefs ->
            prefs[TRANSACTIONS_KEY] = "[]"
        }
    }
}
