package io.cleansky.contactless.model

import io.cleansky.contactless.model.defaulttokens.DefaultTokenCatalog
import io.cleansky.contactless.model.defaulttokens.RegionalCurrencyResolver

object DefaultTokens {
    enum class UnderlyingCurrency(val code: String, val displayName: String) {
        USD("USD", "US Dollar"),
        EUR("EUR", "Euro"),
        GBP("GBP", "British Pound"),
        JPY("JPY", "Japanese Yen"),
        CHF("CHF", "Swiss Franc"),
        ETH("ETH", "Ethereum"),
        BTC("BTC", "Bitcoin"),
    }

    private val symbolsByUnderlying =
        mapOf(
            UnderlyingCurrency.USD to listOf("USDC", "USDT", "DAI"),
            UnderlyingCurrency.EUR to listOf("EURC", "agEUR", "EURS"),
            UnderlyingCurrency.GBP to listOf("GBPT", "TGBP"),
            UnderlyingCurrency.JPY to listOf("JPYC", "GYEN"),
            UnderlyingCurrency.CHF to listOf("XCHF"),
            UnderlyingCurrency.ETH to listOf("WETH", "wstETH", "stETH", "rETH", "cbETH"),
            UnderlyingCurrency.BTC to listOf("WBTC", "cbBTC", "tBTC"),
        )

    fun isEURegion(countryCode: String? = null): Boolean {
        return if (countryCode == null) RegionalCurrencyResolver.isEURegion() else RegionalCurrencyResolver.isEURegion(countryCode)
    }

    fun isCHFRegion(countryCode: String? = null): Boolean {
        return if (countryCode == null) RegionalCurrencyResolver.isCHFRegion() else RegionalCurrencyResolver.isCHFRegion(countryCode)
    }

    fun isGBPRegion(countryCode: String? = null): Boolean {
        return if (countryCode == null) RegionalCurrencyResolver.isGBPRegion() else RegionalCurrencyResolver.isGBPRegion(countryCode)
    }

    fun isJPYRegion(countryCode: String? = null): Boolean {
        return if (countryCode == null) RegionalCurrencyResolver.isJPYRegion() else RegionalCurrencyResolver.isJPYRegion(countryCode)
    }

    fun getPreferredCurrency(countryCode: String? = null): String {
        return if (countryCode == null) {
            RegionalCurrencyResolver.preferredCurrencyCode()
        } else {
            RegionalCurrencyResolver.preferredCurrencyCode(countryCode)
        }
    }

    fun getNativeToken(chainId: Long): Token = DefaultTokenCatalog.nativeToken(chainId)

    fun getTokensByUnderlying(chainId: Long): Map<UnderlyingCurrency, List<Token>> {
        return symbolsByUnderlying
            .mapValues { (_, symbols) -> symbols.mapNotNull { symbol -> DefaultTokenCatalog.token(symbol, chainId) } }
            .filterValues { it.isNotEmpty() }
    }

    fun getAvailableUnderlyings(chainId: Long): List<UnderlyingCurrency> {
        val preferred = getPreferredCurrency()
        return getTokensByUnderlying(chainId).keys.toList().sortedBy { underlying ->
            when {
                underlying.code == preferred -> 0
                underlying == UnderlyingCurrency.USD -> 1
                underlying == UnderlyingCurrency.EUR -> 2
                underlying == UnderlyingCurrency.ETH -> 3
                underlying == UnderlyingCurrency.BTC -> 4
                else -> 5
            }
        }
    }

    fun getTokensForUnderlying(
        chainId: Long,
        underlying: UnderlyingCurrency,
    ): List<Token> {
        return getTokensByUnderlying(chainId)[underlying] ?: emptyList()
    }

    fun getDefaultTokens(chainId: Long): List<Token> {
        val native = getNativeToken(chainId)
        val stablecoins = mutableListOf<Token>()
        addRegionPreferredStablecoins(chainId, stablecoins)

        val addedSymbols = stablecoins.map { it.symbol }.toMutableSet()
        if (chainId == 1L) addIfMissing(stablecoins, addedSymbols, token("GYEN", chainId))
        addIfMissing(stablecoins, addedSymbols, token("JPYC", chainId))
        if (chainId == 1L) addIfMissing(stablecoins, addedSymbols, token("TGBP", chainId))
        addIfMissing(stablecoins, addedSymbols, token("agEUR", chainId))
        addIfMissing(stablecoins, addedSymbols, token("EURS", chainId))
        addIfMissing(stablecoins, addedSymbols, token("USDT", chainId))
        addIfMissing(stablecoins, addedSymbols, token("DAI", chainId))

        return listOf(native) + stablecoins
    }

    fun getPreferredStablecoin(
        chainId: Long,
        preferredCurrency: String = getPreferredCurrency(),
    ): Token? {
        return preferredStablecoinSymbols(preferredCurrency, chainId)
            .asSequence()
            .mapNotNull { symbol -> token(symbol, chainId) }
            .firstOrNull()
    }

    fun getAllDefaultTokens(): List<Token> {
        return ChainConfig.CHAINS.flatMap { chain ->
            getDefaultTokens(chain.chainId)
        }
    }

    private fun token(
        symbol: String,
        chainId: Long,
    ): Token? = DefaultTokenCatalog.token(symbol, chainId)

    private fun preferredStablecoinSymbols(
        preferredCurrency: String,
        chainId: Long,
    ): List<String> {
        return when (preferredCurrency) {
            "JPY" -> if (chainId == 1L) listOf("GYEN", "JPYC", "USDC") else listOf("JPYC", "USDC")
            "CHF" -> listOf("XCHF", "EURC", "USDC")
            "GBP" -> listOf("GBPT", "TGBP", "USDC")
            "EUR" -> listOf("EURC", "agEUR", "USDC")
            else -> listOf("USDC")
        }
    }

    private fun addRegionPreferredStablecoins(
        chainId: Long,
        stablecoins: MutableList<Token>,
    ) {
        when (getPreferredCurrency()) {
            "JPY" ->
                addByPreference(
                    stablecoins,
                    listOf(
                        if (chainId == 1L) token("GYEN", chainId) else null,
                        token("JPYC", chainId),
                        token("USDC", chainId),
                        token("EURC", chainId),
                    ),
                )
            "CHF" ->
                addByPreference(
                    stablecoins,
                    listOf(
                        token("XCHF", chainId),
                        token("EURC", chainId),
                        token("USDC", chainId),
                    ),
                )
            "GBP" ->
                addByPreference(
                    stablecoins,
                    listOf(
                        token("GBPT", chainId),
                        if (chainId == 1L) token("TGBP", chainId) else null,
                        token("USDC", chainId),
                        token("EURC", chainId),
                    ),
                )
            "EUR" ->
                addByPreference(
                    stablecoins,
                    listOf(
                        token("EURC", chainId),
                        token("agEUR", chainId),
                        token("EURS", chainId),
                        token("USDC", chainId),
                    ),
                )
            else ->
                addByPreference(
                    stablecoins,
                    listOf(
                        token("USDC", chainId),
                        token("EURC", chainId),
                    ),
                )
        }
    }

    private fun addByPreference(
        stablecoins: MutableList<Token>,
        tokens: List<Token?>,
    ) {
        tokens.filterNotNull().forEach { stablecoins.add(it) }
    }

    private fun addIfMissing(
        stablecoins: MutableList<Token>,
        addedSymbols: MutableSet<String>,
        token: Token?,
    ) {
        if (token == null || token.symbol in addedSymbols) return
        stablecoins.add(token)
        addedSymbols.add(token.symbol)
    }
}
