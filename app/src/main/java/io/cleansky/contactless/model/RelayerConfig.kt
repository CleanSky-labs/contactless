package io.cleansky.contactless.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * - DIRECT: Merchant pays gas directly
 * - RELAYER: Meta-transactions through relayer (EIP-2771)
 */
enum class ExecutionMode {
    DIRECT, // Merchant pays gas directly
    RELAYER, // Meta-tx via relayer (Gelato, OpenZeppelin Defender, etc)
    ACCOUNT_ABSTRACTION, // ERC-4337 with paymaster
}

data class RelayerConfig(
    val name: String,
    val url: String,
    val apiKey: String = "",
    val chainIds: List<Long>,
) {
    companion object {
        val GELATO =
            RelayerConfig(
                name = "Gelato",
                url = "https://relay.gelato.digital",
                chainIds = listOf(1, 137, 8453, 42161, 10, 43114),
            )

        val BICONOMY =
            RelayerConfig(
                name = "Biconomy",
                url = "https://api.biconomy.io",
                chainIds = listOf(1, 137, 8453, 42161, 10, 56),
            )
    }
}

data class PaymasterConfig(
    val name: String,
    val paymasterUrl: String,
    val bundlerUrl: String,
    val entryPointAddress: String = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789",
    val chainIds: List<Long>,
    val requiresApiKey: Boolean = true,
) {
    companion object {
        val PIMLICO =
            PaymasterConfig(
                name = "Pimlico",
                paymasterUrl = "https://api.pimlico.io/v2",
                bundlerUrl = "https://api.pimlico.io/v2",
                chainIds = listOf(1, 137, 8453, 42161, 10, 84532),
                requiresApiKey = true,
            )

        val STACKUP =
            PaymasterConfig(
                name = "Stackup",
                paymasterUrl = "https://api.stackup.sh/v1/paymaster",
                bundlerUrl = "https://api.stackup.sh/v1/node",
                chainIds = listOf(1, 137, 8453, 42161, 10),
                requiresApiKey = true,
            )

        val ALCHEMY =
            PaymasterConfig(
                name = "Alchemy",
                paymasterUrl = "https://eth-mainnet.g.alchemy.com/v2",
                bundlerUrl = "https://eth-mainnet.g.alchemy.com/v2",
                chainIds = listOf(1, 137, 8453, 42161, 10, 11155111),
                requiresApiKey = true,
            )
    }
}

/**
 * Public Bundler configuration for ERC-4337
 *
 * These are bundler endpoints that can be used without API keys,
 * primarily for testnets and some mainnet free tiers.
 */
data class PublicBundler(
    val name: String,
    val urlTemplate: String,
    val chainIds: List<Long>,
    val requiresApiKey: Boolean = false,
    val rateLimited: Boolean = true,
    val supportsPaymaster: Boolean = false,
) {
    fun getUrl(
        chainId: Long,
        apiKey: String? = null,
    ): String {
        val baseUrl = urlTemplate.replace("{chainId}", chainId.toString())
        return if (apiKey != null && requiresApiKey) {
            "$baseUrl?apikey=$apiKey"
        } else {
            baseUrl
        }
    }

    companion object {
        // Public bundlers per chain - no API key required for testnets
        val PUBLIC_BUNDLERS =
            listOf(
                // Pimlico - Free tier for testnets, rate-limited mainnet
                PublicBundler(
                    name = "Pimlico Public",
                    urlTemplate = "https://api.pimlico.io/v2/{chainId}/rpc",
                    chainIds = listOf(84532, 11155111, 421614, 11155420),
                    requiresApiKey = false,
                    rateLimited = true,
                    supportsPaymaster = false,
                ),
                // Alchemy - Requires API key but has generous free tier
                PublicBundler(
                    name = "Alchemy",
                    urlTemplate = "https://arb-sepolia.g.alchemy.com/v2",
                    chainIds = listOf(421614),
                    requiresApiKey = true,
                    rateLimited = true,
                    supportsPaymaster = true,
                ),
                // Candide - Public bundler
                PublicBundler(
                    name = "Candide",
                    urlTemplate = "https://bundler.candide.dev/{chainId}/rpc",
                    chainIds = listOf(84532, 11155111),
                    requiresApiKey = false,
                    rateLimited = true,
                    supportsPaymaster = false,
                ),
                // Etherspot Skandha - Open source bundler
                PublicBundler(
                    name = "Etherspot",
                    urlTemplate = "https://sepolia-bundler.etherspot.io",
                    chainIds = listOf(11155111),
                    requiresApiKey = false,
                    rateLimited = true,
                    supportsPaymaster = false,
                ),
            )

        // Get available bundlers for a chain, prioritizing no-API-key options
        fun getBundlersForChain(chainId: Long): List<PublicBundler> {
            return PUBLIC_BUNDLERS
                .filter { chainId in it.chainIds }
                .sortedBy { it.requiresApiKey } // No-key first
        }

        // Get first available bundler URL for a chain
        fun getPublicBundlerUrl(chainId: Long): String? {
            return getBundlersForChain(chainId)
                .firstOrNull { !it.requiresApiKey }
                ?.getUrl(chainId)
        }

        // Check if chain has public (no-key) bundler
        fun hasPublicBundler(chainId: Long): Boolean {
            return PUBLIC_BUNDLERS.any { chainId in it.chainIds && !it.requiresApiKey }
        }
    }
}

/**
 * Bundler selection strategy
 */
object BundlerSelector {
    /**
     * Get the best bundler URL for a chain, with fallback logic
     *
     * Priority:
     * 1. Custom bundler URL (if configured)
     * 2. API-key bundler (if key provided)
     * 3. Public bundler (if available for chain)
     * 4. null (no bundler available)
     */
    fun selectBundler(
        chainId: Long,
        apiKey: String?,
        customBundlerUrl: String?,
        preferredProvider: PaymasterConfig? = PaymasterConfig.PIMLICO,
    ): BundlerSelection {
        // 1. Custom bundler takes priority
        if (!customBundlerUrl.isNullOrBlank()) {
            return BundlerSelection(
                url = customBundlerUrl,
                name = "Custom",
                requiresApiKey = false,
                supportsPaymaster = false,
            )
        }

        // 2. If API key provided, use preferred provider
        if (!apiKey.isNullOrBlank() && preferredProvider != null) {
            if (chainId in preferredProvider.chainIds) {
                return BundlerSelection(
                    url = "${preferredProvider.bundlerUrl}/$chainId/rpc?apikey=$apiKey",
                    name = preferredProvider.name,
                    requiresApiKey = true,
                    supportsPaymaster = true,
                )
            }
        }

        // 3. Try public bundler for this chain
        val publicBundler =
            PublicBundler.getBundlersForChain(chainId)
                .firstOrNull { !it.requiresApiKey }

        if (publicBundler != null) {
            return BundlerSelection(
                url = publicBundler.getUrl(chainId),
                name = publicBundler.name,
                requiresApiKey = false,
                supportsPaymaster = publicBundler.supportsPaymaster,
            )
        }

        // 4. If API key provided, try any provider that supports this chain
        if (!apiKey.isNullOrBlank()) {
            val provider =
                listOf(PaymasterConfig.PIMLICO, PaymasterConfig.STACKUP, PaymasterConfig.ALCHEMY)
                    .firstOrNull { chainId in it.chainIds }
            if (provider != null) {
                return BundlerSelection(
                    url = "${provider.bundlerUrl}/$chainId/rpc?apikey=$apiKey",
                    name = provider.name,
                    requiresApiKey = true,
                    supportsPaymaster = true,
                )
            }
        }

        // 5. No bundler available
        return BundlerSelection(
            url = null,
            name = "None",
            requiresApiKey = false,
            supportsPaymaster = false,
        )
    }
}

data class BundlerSelection(
    val url: String?,
    val name: String,
    val requiresApiKey: Boolean,
    val supportsPaymaster: Boolean,
)

data class RelayResponse(
    val taskId: String?,
    val txHash: String?,
    val status: String,
    val error: String?,
)

data class GelatoRelayRequest(
    val chainId: Long,
    val target: String,
    val data: String,
    val user: String,
    @SerializedName("userDeadline") val deadline: Long,
    @SerializedName("userNonce") val nonce: String,
    @SerializedName("userSignature") val signature: String,
)

data class MetaTransaction(
    val from: String,
    val to: String,
    val value: String = "0",
    val data: String,
    val nonce: String,
    val deadline: Long,
    val signature: String,
) {
    fun toJson(): String = Gson().toJson(this)
}
