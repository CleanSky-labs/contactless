package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privacyPayerDataStore: DataStore<Preferences> by preferencesDataStore(name = "payer_privacy")

/**
 * Repository for managing payer privacy settings (v0.5)
 *
 * Handles:
 * - Payer privacy mode enable/disable
 * - Ephemeral account tracking
 * - Payment session management
 */
class PrivacyPayerRepository(private val context: Context) {
    companion object {
        private val PRIVACY_ENABLED = booleanPreferencesKey("payer_privacy_enabled")
        private val LAST_PAYMENT_INDEX = longPreferencesKey("last_payment_index")
        private val EPHEMERAL_ACCOUNTS = stringPreferencesKey("ephemeral_accounts")
    }

    private val gson = Gson()

    /**
     * Flow indicating if payer privacy mode is enabled
     */
    val privacyEnabledFlow: Flow<Boolean> =
        context.privacyPayerDataStore.data.map { prefs ->
            prefs[PRIVACY_ENABLED] ?: false
        }

    /**
     * Check if payer privacy is enabled
     */
    suspend fun isPrivacyEnabled(): Boolean {
        return context.privacyPayerDataStore.data.first()[PRIVACY_ENABLED] ?: false
    }

    /**
     * Enable payer privacy mode
     */
    suspend fun enablePrivacy() {
        context.privacyPayerDataStore.edit { prefs ->
            prefs[PRIVACY_ENABLED] = true
        }
    }

    /**
     * Disable payer privacy mode
     */
    suspend fun disablePrivacy() {
        context.privacyPayerDataStore.edit { prefs ->
            prefs[PRIVACY_ENABLED] = false
        }
    }

    /**
     * Get next payment index for ephemeral account derivation
     * Each payment uses a unique index to generate a unique ephemeral account
     */
    suspend fun getNextPaymentIndex(): Long {
        val current = context.privacyPayerDataStore.data.first()[LAST_PAYMENT_INDEX] ?: 0L
        val next = current + 1
        context.privacyPayerDataStore.edit { prefs ->
            prefs[LAST_PAYMENT_INDEX] = next
        }
        return next
    }

    /**
     * Record an ephemeral account used for a payment
     * Useful for tracking and potential fund recovery
     */
    suspend fun recordEphemeralAccount(account: EphemeralAccountRecord) {
        val accounts = getEphemeralAccounts().toMutableList()
        accounts.add(account)
        // Keep only last 100 accounts
        val trimmed = accounts.takeLast(100)
        saveEphemeralAccounts(trimmed)
    }

    /**
     * Get all recorded ephemeral accounts
     */
    suspend fun getEphemeralAccounts(): List<EphemeralAccountRecord> {
        val json = context.privacyPayerDataStore.data.first()[EPHEMERAL_ACCOUNTS] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<EphemeralAccountRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveEphemeralAccounts(accounts: List<EphemeralAccountRecord>) {
        context.privacyPayerDataStore.edit { prefs ->
            prefs[EPHEMERAL_ACCOUNTS] = gson.toJson(accounts)
        }
    }

    /**
     * Clean up old ephemeral account records (older than 30 days)
     */
    suspend fun cleanupOldRecords() {
        val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val accounts = getEphemeralAccounts().filter { it.createdAt > cutoff }
        saveEphemeralAccounts(accounts)
    }
}

/**
 * Record of an ephemeral account used for a payment
 */
data class EphemeralAccountRecord(
    val address: String,
    val paymentIndex: Long,
    val invoiceId: String,
    val merchantAddress: String,
    val amount: String,
    val asset: String,
    val chainId: Long,
    val txHash: String?,
    val createdAt: Long = System.currentTimeMillis(),
)
