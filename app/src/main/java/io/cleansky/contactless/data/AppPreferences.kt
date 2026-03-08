package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * App-wide preferences for settings that aren't related to wallet security
 */
class AppPreferences(private val context: Context) {
    companion object {
        private val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
        private val CUSTOM_BUNDLER_URL = stringPreferencesKey("custom_bundler_url")
        private val CUSTOM_PAYMASTER_URL = stringPreferencesKey("custom_paymaster_url")
        private val USE_CUSTOM_BUNDLER = booleanPreferencesKey("use_custom_bundler")

        // Merchant identity (spec v0.2)
        private val MERCHANT_DISPLAY_NAME = stringPreferencesKey("merchant_display_name")
        private val MERCHANT_DOMAIN = stringPreferencesKey("merchant_domain")

        // Language preference
        private val APP_LANGUAGE = stringPreferencesKey("app_language")

        // Advanced mode (shows technical crypto options)
        private val ADVANCED_MODE = booleanPreferencesKey("advanced_mode")

        // Receive-only mode (no private key)
        private val RECEIVE_ONLY_ESCROW = stringPreferencesKey("receive_only_escrow")
        private val RECEIVE_ONLY_MERCHANT_ID = stringPreferencesKey("receive_only_merchant_id")
    }

    // ========== First Launch ==========

    suspend fun isFirstLaunch(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[FIRST_LAUNCH_COMPLETED] != true
    }

    suspend fun setFirstLaunchCompleted() {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[FIRST_LAUNCH_COMPLETED] = true
        }
    }

    // ========== Custom Bundler (ERC-4337) ==========

    val useCustomBundlerFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[USE_CUSTOM_BUNDLER] ?: false
        }

    val customBundlerUrlFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[CUSTOM_BUNDLER_URL] ?: ""
        }

    val customPaymasterUrlFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[CUSTOM_PAYMASTER_URL] ?: ""
        }

    suspend fun setUseCustomBundler(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[USE_CUSTOM_BUNDLER] = enabled
        }
    }

    suspend fun setCustomBundlerUrl(url: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[CUSTOM_BUNDLER_URL] = url
        }
    }

    suspend fun setCustomPaymasterUrl(url: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[CUSTOM_PAYMASTER_URL] = url
        }
    }

    suspend fun getCustomBundlerUrl(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[CUSTOM_BUNDLER_URL] ?: ""
    }

    suspend fun getCustomPaymasterUrl(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[CUSTOM_PAYMASTER_URL] ?: ""
    }

    suspend fun useCustomBundler(): Boolean {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[USE_CUSTOM_BUNDLER] ?: false
    }

    // ========== Merchant Identity (spec v0.2) ==========

    val merchantDisplayNameFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[MERCHANT_DISPLAY_NAME] ?: ""
        }

    val merchantDomainFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[MERCHANT_DOMAIN] ?: ""
        }

    suspend fun getMerchantDisplayName(): String? {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[MERCHANT_DISPLAY_NAME]?.takeIf { it.isNotBlank() }
    }

    suspend fun getMerchantDomain(): String? {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[MERCHANT_DOMAIN]?.takeIf { it.isNotBlank() }
    }

    suspend fun setMerchantDisplayName(name: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[MERCHANT_DISPLAY_NAME] = name
        }
    }

    suspend fun setMerchantDomain(domain: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[MERCHANT_DOMAIN] = domain
        }
    }

    // ========== Language ==========

    val appLanguageFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[APP_LANGUAGE] ?: "" // Empty = system default
        }

    suspend fun getAppLanguage(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[APP_LANGUAGE] ?: ""
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = languageCode
        }
    }

    // ========== Advanced Mode ==========

    val advancedModeFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[ADVANCED_MODE] ?: false
        }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[ADVANCED_MODE] = enabled
        }
    }

    // ========== Receive-Only Mode ==========

    val receiveOnlyEscrowFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[RECEIVE_ONLY_ESCROW] ?: ""
        }

    val receiveOnlyMerchantIdFlow: Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[RECEIVE_ONLY_MERCHANT_ID] ?: ""
        }

    suspend fun getReceiveOnlyEscrow(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[RECEIVE_ONLY_ESCROW] ?: ""
    }

    suspend fun getReceiveOnlyMerchantId(): String {
        val prefs = context.appPreferencesDataStore.data.first()
        return prefs[RECEIVE_ONLY_MERCHANT_ID] ?: ""
    }

    suspend fun setReceiveOnlyEscrow(address: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[RECEIVE_ONLY_ESCROW] = address
        }
    }

    suspend fun setReceiveOnlyMerchantId(merchantId: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[RECEIVE_ONLY_MERCHANT_ID] = merchantId
        }
    }
}
