package io.cleansky.contactless.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.cleansky.contactless.model.DefaultTokens
import io.cleansky.contactless.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tokenAllowlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "token_allowlist")

class TokenAllowlistRepository(private val context: Context) {
    companion object {
        private val ALLOWED_TOKENS_KEY = stringPreferencesKey("allowed_tokens")
        private val INITIALIZED_CHAINS_KEY = stringPreferencesKey("initialized_chains")
    }

    val allowedTokensFlow: Flow<List<Token>> =
        context.tokenAllowlistDataStore.data.map { prefs ->
            val json = prefs[ALLOWED_TOKENS_KEY] ?: "[]"
            Token.listFromJson(json)
        }

    suspend fun getAllowedTokens(): List<Token> {
        return allowedTokensFlow.first()
    }

    suspend fun getAllowedTokensForChain(chainId: Long): List<Token> {
        return getAllowedTokens().filter { it.chainId == chainId }
    }

    suspend fun addToken(token: Token) {
        context.tokenAllowlistDataStore.edit { prefs ->
            val currentList = Token.listFromJson(prefs[ALLOWED_TOKENS_KEY] ?: "[]").toMutableList()
            // Avoid duplicates
            if (!currentList.any { it.matches(token.address, token.chainId) }) {
                currentList.add(token)
                prefs[ALLOWED_TOKENS_KEY] = Token.listToJson(currentList)
            }
        }
    }

    suspend fun removeToken(
        tokenAddress: String,
        chainId: Long,
    ) {
        context.tokenAllowlistDataStore.edit { prefs ->
            val currentList = Token.listFromJson(prefs[ALLOWED_TOKENS_KEY] ?: "[]").toMutableList()
            currentList.removeAll { it.matches(tokenAddress, chainId) }
            prefs[ALLOWED_TOKENS_KEY] = Token.listToJson(currentList)
        }
    }

    suspend fun isTokenAllowed(
        tokenAddress: String,
        chainId: Long,
    ): Boolean {
        val tokens = getAllowedTokens()
        return tokens.any { it.matches(tokenAddress, chainId) }
    }

    suspend fun initializeDefaultTokensIfNeeded(chainId: Long) {
        val initializedChains = getInitializedChains()
        if (chainId !in initializedChains) {
            val defaultTokens = DefaultTokens.getDefaultTokens(chainId)
            defaultTokens.forEach { token ->
                addToken(token)
            }
            markChainInitialized(chainId)
        }
    }

    private suspend fun getInitializedChains(): Set<Long> {
        return context.tokenAllowlistDataStore.data.first().let { prefs ->
            val json = prefs[INITIALIZED_CHAINS_KEY] ?: ""
            if (json.isEmpty()) {
                emptySet()
            } else {
                json.split(",").mapNotNull { it.toLongOrNull() }.toSet()
            }
        }
    }

    private suspend fun markChainInitialized(chainId: Long) {
        context.tokenAllowlistDataStore.edit { prefs ->
            val currentChains =
                (prefs[INITIALIZED_CHAINS_KEY] ?: "")
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { it.toLongOrNull() }
                    .toMutableSet()
            currentChains.add(chainId)
            prefs[INITIALIZED_CHAINS_KEY] = currentChains.joinToString(",")
        }
    }

    suspend fun clearAll() {
        context.tokenAllowlistDataStore.edit { prefs ->
            prefs[ALLOWED_TOKENS_KEY] = "[]"
            prefs[INITIALIZED_CHAINS_KEY] = ""
        }
    }
}
