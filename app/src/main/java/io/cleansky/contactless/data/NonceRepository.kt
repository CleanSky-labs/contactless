package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val Context.nonceDataStore: DataStore<Preferences> by preferencesDataStore(name = "nonce_storage")

/**
 * Repository for tracking used nonces to prevent replay attacks.
 * Implements spec v0.2 requirements:
 * - Store used nonces for at least 24 hours after expiry
 * - Reject any request with previously-used (nonce, invoiceId) pair
 * - Atomic nonce consumption
 */
class NonceRepository(private val context: Context) {

    // In-memory cache for fast lookups
    private val usedNonces = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()
    private var initialized = false

    companion object {
        private const val NONCE_RETENTION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val CLEANUP_THRESHOLD = 1000 // Clean up after this many entries
        private val KEY_NONCE_DATA = stringPreferencesKey("nonce_data")
        private val KEY_LAST_CLEANUP = longPreferencesKey("last_cleanup")
    }

    /**
     * Initialize the repository by loading persisted nonces
     */
    suspend fun initialize() {
        if (initialized) return

        mutex.withLock {
            if (initialized) return

            val prefs = context.nonceDataStore.data.first()
            val nonceData = prefs[KEY_NONCE_DATA] ?: ""

            // Parse stored nonces: "nonce1:expiry1,nonce2:expiry2,..."
            if (nonceData.isNotEmpty()) {
                nonceData.split(",").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val nonce = parts[0]
                        val expiry = parts[1].toLongOrNull() ?: 0L
                        if (!isExpired(expiry)) {
                            usedNonces[nonce] = expiry
                        }
                    }
                }
            }

            initialized = true
        }
    }

    /**
     * Check if a nonce has been used before.
     * Returns true if nonce is valid (not used), false if already used.
     */
    suspend fun isNonceValid(nonce: String, invoiceId: String): Boolean {
        initialize()

        val key = "$nonce:$invoiceId"
        return !usedNonces.containsKey(key)
    }

    /**
     * Atomically consume a nonce. Must be called before broadcasting transaction.
     * Returns true if successful, false if nonce was already used.
     */
    suspend fun consumeNonce(nonce: String, invoiceId: String, expiry: Long): Boolean {
        initialize()

        val key = "$nonce:$invoiceId"

        mutex.withLock {
            // Double-check after acquiring lock
            if (usedNonces.containsKey(key)) {
                return false
            }

            // Mark as used
            usedNonces[key] = expiry

            // Persist
            persistNonces()

            // Clean up old entries if needed
            if (usedNonces.size > CLEANUP_THRESHOLD) {
                cleanupExpiredNonces()
            }

            return true
        }
    }

    /**
     * Release a nonce if transaction broadcast fails.
     * This allows retry with the same nonce.
     */
    suspend fun releaseNonce(nonce: String, invoiceId: String) {
        val key = "$nonce:$invoiceId"

        mutex.withLock {
            usedNonces.remove(key)
            persistNonces()
        }
    }

    private suspend fun persistNonces() {
        val data = usedNonces.entries.joinToString(",") { "${it.key}:${it.value}" }
        context.nonceDataStore.edit { prefs ->
            prefs[KEY_NONCE_DATA] = data
        }
    }

    private suspend fun cleanupExpiredNonces() {
        val now = System.currentTimeMillis() / 1000
        val toRemove = usedNonces.entries
            .filter { isExpired(it.value) }
            .map { it.key }

        toRemove.forEach { usedNonces.remove(it) }

        if (toRemove.isNotEmpty()) {
            persistNonces()
            context.nonceDataStore.edit { prefs ->
                prefs[KEY_LAST_CLEANUP] = now
            }
        }
    }

    private fun isExpired(expiry: Long): Boolean {
        val now = System.currentTimeMillis() / 1000
        // Keep nonces for 24 hours after their expiry
        return now > expiry + (NONCE_RETENTION_MS / 1000)
    }

    /**
     * Get count of stored nonces (for debugging)
     */
    fun getStoredNonceCount(): Int = usedNonces.size
}
