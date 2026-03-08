package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.cleansky.contactless.crypto.StealthAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.stealthDataStore: DataStore<Preferences> by preferencesDataStore(name = "stealth_payments")

/**
 * Repository for managing stealth address functionality.
 *
 * Handles:
 * - Stealth mode enable/disable
 * - Stealth keys storage (encrypted via wallet manager)
 * - Pending stealth payments tracking
 * - Payment scanning and claiming
 */
class StealthPaymentRepository(private val context: Context) {
    companion object {
        private val STEALTH_ENABLED = booleanPreferencesKey("stealth_enabled")
        private val STEALTH_META_ADDRESS = stringPreferencesKey("stealth_meta_address")
        private val PENDING_PAYMENTS = stringPreferencesKey("pending_stealth_payments")
        private val CLAIMED_PAYMENTS = stringPreferencesKey("claimed_stealth_payments")
    }

    private val gson = Gson()

    /**
     * Flow indicating if stealth mode is enabled
     */
    val stealthEnabledFlow: Flow<Boolean> =
        context.stealthDataStore.data.map { prefs ->
            prefs[STEALTH_ENABLED] ?: false
        }

    /**
     * Flow of the stealth meta-address (if configured)
     */
    val stealthMetaAddressFlow: Flow<String?> =
        context.stealthDataStore.data.map { prefs ->
            prefs[STEALTH_META_ADDRESS]
        }

    /**
     * Check if stealth is enabled
     */
    suspend fun isStealthEnabled(): Boolean {
        return context.stealthDataStore.data.first()[STEALTH_ENABLED] ?: false
    }

    /**
     * Get the stealth meta-address
     */
    suspend fun getStealthMetaAddress(): StealthAddress.StealthMetaAddress? {
        val encoded = context.stealthDataStore.data.first()[STEALTH_META_ADDRESS] ?: return null
        return StealthAddress.StealthMetaAddress.decode(encoded)
    }

    /**
     * Enable stealth mode and store meta-address
     */
    suspend fun enableStealth(metaAddress: StealthAddress.StealthMetaAddress) {
        context.stealthDataStore.edit { prefs ->
            prefs[STEALTH_ENABLED] = true
            prefs[STEALTH_META_ADDRESS] = metaAddress.encode()
        }
    }

    /**
     * Disable stealth mode
     */
    suspend fun disableStealth() {
        context.stealthDataStore.edit { prefs ->
            prefs[STEALTH_ENABLED] = false
        }
    }

    /**
     * Record a pending stealth payment (merchant side)
     * Called when we broadcast a payment request with stealth
     */
    suspend fun recordPendingPayment(payment: PendingStealthPayment) {
        val current = getPendingPayments().toMutableList()
        current.add(payment)
        savePendingPayments(current)
    }

    /**
     * Get all pending stealth payments
     */
    suspend fun getPendingPayments(): List<PendingStealthPayment> {
        val json = context.stealthDataStore.data.first()[PENDING_PAYMENTS] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingStealthPayment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mark a payment as claimed
     */
    suspend fun markPaymentClaimed(
        invoiceId: String,
        txHash: String,
    ) {
        // Remove from pending
        val pending = getPendingPayments().toMutableList()
        val payment = pending.find { it.invoiceId == invoiceId }
        pending.removeAll { it.invoiceId == invoiceId }
        savePendingPayments(pending)

        // Add to claimed
        if (payment != null) {
            val claimed = getClaimedPayments().toMutableList()
            claimed.add(
                ClaimedStealthPayment(
                    invoiceId = invoiceId,
                    stealthAddress = payment.stealthAddress,
                    amount = payment.amount,
                    asset = payment.asset,
                    chainId = payment.chainId,
                    claimedTxHash = txHash,
                    claimedAt = System.currentTimeMillis(),
                ),
            )
            saveClaimedPayments(claimed)
        }
    }

    /**
     * Get claimed payments
     */
    suspend fun getClaimedPayments(): List<ClaimedStealthPayment> {
        val json = context.stealthDataStore.data.first()[CLAIMED_PAYMENTS] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ClaimedStealthPayment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun savePendingPayments(payments: List<PendingStealthPayment>) {
        context.stealthDataStore.edit { prefs ->
            prefs[PENDING_PAYMENTS] = gson.toJson(payments)
        }
    }

    private suspend fun saveClaimedPayments(payments: List<ClaimedStealthPayment>) {
        context.stealthDataStore.edit { prefs ->
            prefs[CLAIMED_PAYMENTS] = gson.toJson(payments)
        }
    }

    /**
     * Clean up expired pending payments (older than 24h)
     */
    suspend fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val pending = getPendingPayments().filter { it.createdAt > cutoff }
        savePendingPayments(pending)
    }
}

/**
 * A pending stealth payment waiting to be claimed
 */
data class PendingStealthPayment(
    val invoiceId: String,
    val stealthAddress: String,
    val ephemeralPubKey: String,
    val viewTag: Int,
    val amount: String,
    val asset: String,
    val chainId: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A claimed stealth payment
 */
data class ClaimedStealthPayment(
    val invoiceId: String,
    val stealthAddress: String,
    val amount: String,
    val asset: String,
    val chainId: Long,
    val claimedTxHash: String,
    val claimedAt: Long,
)
