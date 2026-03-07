package io.cleansky.contactless.model

import java.util.Locale

/**
 * Default tokens for each supported chain.
 * These are verified token addresses from official sources.
 *
 * Stablecoin priority:
 * - EU region: EURC first, then USDC
 * - Other regions: USDC first
 */
object DefaultTokens {

    // EU country codes (ISO 3166-1 alpha-2)
    private val EU_COUNTRIES = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        // EEA countries
        "IS", "LI", "NO",
        // Other European countries likely to use EUR
        "AD", "MC", "SM", "VA", "ME", "XK"
    )

    // Countries using Swiss Franc
    private val CHF_COUNTRIES = setOf("CH", "LI")

    // Countries using British Pound
    private val GBP_COUNTRIES = setOf("GB", "IM", "JE", "GG", "GI", "FK", "SH")

    // Countries using Japanese Yen
    private val JPY_COUNTRIES = setOf("JP")

    // Native token symbols per chain
    private val nativeSymbols = mapOf(
        1L to "ETH",         // Ethereum Mainnet
        8453L to "ETH",      // Base
        84532L to "ETH",     // Base Sepolia
        137L to "POL",       // Polygon (renamed from MATIC)
        42161L to "ETH",     // Arbitrum
        10L to "ETH",        // Optimism
        324L to "ETH",       // zkSync Era
        59144L to "ETH",     // Linea
        31337L to "ETH"      // Localhost
    )

    // Native token names per chain
    private val nativeNames = mapOf(
        1L to "Ethereum",    // Ethereum Mainnet
        8453L to "Ethereum",
        84532L to "Ethereum",
        137L to "Polygon",
        42161L to "Ethereum",
        10L to "Ethereum",
        324L to "Ethereum",
        59144L to "Ethereum",
        31337L to "Ethereum"
    )

    /**
     * Check if device is in EU region based on locale
     */
    fun isEURegion(): Boolean {
        val country = Locale.getDefault().country.uppercase()
        return country in EU_COUNTRIES
    }

    fun isCHFRegion(): Boolean {
        val country = Locale.getDefault().country.uppercase()
        return country in CHF_COUNTRIES
    }

    fun isGBPRegion(): Boolean {
        val country = Locale.getDefault().country.uppercase()
        return country in GBP_COUNTRIES
    }

    fun isJPYRegion(): Boolean {
        val country = Locale.getDefault().country.uppercase()
        return country in JPY_COUNTRIES
    }

    /**
     * Get user's preferred currency based on region
     */
    fun getPreferredCurrency(): String {
        return when {
            isJPYRegion() -> "JPY"
            isCHFRegion() -> "CHF"
            isGBPRegion() -> "GBP"
            isEURegion() -> "EUR"
            else -> "USD"
        }
    }

    fun getNativeToken(chainId: Long): Token {
        return Token(
            address = Token.NATIVE_ADDRESS,
            symbol = nativeSymbols[chainId] ?: "ETH",
            name = nativeNames[chainId] ?: "Native Token",
            decimals = 18,
            chainId = chainId,
            isNative = true
        )
    }

    /**
     * Get EURC token for a chain (if available)
     */
    private fun getEURC(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x1aBaEA1f7C830bD89Acc67eC4af516284b1bC33c"      // Ethereum Mainnet
            8453L -> "0x60a3E35Cc302bFA44Cb288Bc5a4F316Fdb1adb42"   // Base
            137L -> "0x820802Fa8a99901F52e39acD21177b0BE6EE2974"    // Polygon
            42161L -> "0x820802Fa8a99901F52e39acD21177b0BE6EE2974"  // Arbitrum
            10L -> "0x820802Fa8a99901F52e39acD21177b0BE6EE2974"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "EURC",
                name = "Euro Coin",
                decimals = 6,
                chainId = chainId
            )
        }
    }

    /**
     * Get USDC token for a chain
     */
    private fun getUSDC(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"      // Ethereum Mainnet
            8453L -> "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"   // Base
            84532L -> "0x036CbD53842c5426634e7929541eC2318f3dCF7e"  // Base Sepolia
            137L -> "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359"    // Polygon
            42161L -> "0xaf88d065e77c8cC2239327C5EDb3A432268e5831"  // Arbitrum
            10L -> "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85"     // Optimism
            324L -> "0x1d17CBcF0D6D143135aE902365D2E5e2A16538D4"    // zkSync Era
            59144L -> "0x176211869cA2b568f2A7D4EE941E073a821EE1ff"  // Linea
            31337L -> "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512"  // Localhost
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "USDC",
                name = "USD Coin",
                decimals = 6,
                chainId = chainId
            )
        }
    }

    /**
     * Get USDT token for a chain
     */
    private fun getUSDT(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xdAC17F958D2ee523a2206206994597C13D831ec7"      // Ethereum Mainnet
            137L -> "0xc2132D05D31c914a87C6611C10748AEb04B58e8F"    // Polygon
            42161L -> "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9"  // Arbitrum
            10L -> "0x94b008aA00579c1307B0EF2c499aD98a8ce58e58"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "USDT",
                name = "Tether USD",
                decimals = 6,
                chainId = chainId
            )
        }
    }

    /**
     * Get DAI token for a chain
     */
    private fun getDAI(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x6B175474E89094C44Da98b954EedeAC495271d0F"       // Ethereum Mainnet
            8453L -> "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb"   // Base
            137L -> "0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063"    // Polygon
            42161L -> "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1"  // Arbitrum
            10L -> "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "DAI",
                name = "Dai Stablecoin",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get JPYC (Japanese Yen stablecoin) for a chain
     */
    private fun getJPYC(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB"      // Ethereum Mainnet
            137L -> "0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB"    // Polygon
            42161L -> "0xa83575490D7df4E2F47b7D38ef351a2722cA45b9"  // Arbitrum
            10L -> "0x8912389b8d0E902cf77d04E2aD09e5247A6C144A"     // Optimism
            8453L -> "0x8912389b8d0E902cf77d04E2aD09e5247A6C144A"   // Base
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "JPYC",
                name = "JPY Coin",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get GYEN (GMO Trust Japanese Yen) - primarily on Ethereum L1
     */
    private fun getGYEN(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xC08512927D12348F6620a698105e1BAac6EcD911"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "GYEN",
                name = "GMO JPY",
                decimals = 6,
                chainId = chainId
            )
        }
    }

    /**
     * Get XCHF (Swiss Franc stablecoin - CryptoFranc) for a chain
     */
    private fun getXCHF(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xB4272071eCAdd69d933AdcD19cA99fe80664fc08"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "XCHF",
                name = "CryptoFranc",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get EURS (Stasis Euro - alternative EUR stablecoin with wider availability)
     */
    private fun getEURS(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xdB25f211AB05b1c97D595516F45794528a807ad8"      // Ethereum Mainnet
            137L -> "0xE111178A87A3BFf0c8d18DECBa5798827539Ae99"    // Polygon
            42161L -> "0xD22a58f79e9481D1a88e00c343885A588b34b68B"  // Arbitrum
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "EURS",
                name = "STASIS EURO",
                decimals = 2,
                chainId = chainId
            )
        }
    }

    /**
     * Get GBPT (Poundtoken - GBP stablecoin)
     */
    private fun getGBPT(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x86B4dBE5D203e634a12364C0e428fa242A3FbA98"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "GBPT",
                name = "Poundtoken",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get TGBP (TrueGBP - GBP stablecoin by TrustToken)
     */
    private fun getTGBP(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x00000000441378008EA67F4284A57932B1c000a5"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "TGBP",
                name = "TrueGBP",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get agEUR (Angle Protocol Euro) - widely available
     */
    private fun getAgEUR(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x1a7e4e63778B4f12a199C062f3eFdD288afCBce8"      // Ethereum Mainnet
            137L -> "0xE0B52e49357Fd4DAf2c15e02058DCE6BC0057db4"    // Polygon
            42161L -> "0xFA5Ed56A203466CbBC2430a43c66b9D8723528E7"  // Arbitrum
            10L -> "0x9485aca5bbBE1667AD97c7fE7C4531a624C8b1ED"     // Optimism
            8453L -> "0xA61BeB4A3d02decb01039e378237032B351125B4"   // Base
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "agEUR",
                name = "Angle Euro",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get WETH (Wrapped Ether)
     */
    private fun getWETH(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"      // Ethereum Mainnet
            8453L -> "0x4200000000000000000000000000000000000006"   // Base
            137L -> "0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619"    // Polygon
            42161L -> "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1"  // Arbitrum
            10L -> "0x4200000000000000000000000000000000000006"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "WETH",
                name = "Wrapped Ether",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get WBTC (Wrapped Bitcoin)
     */
    private fun getWBTC(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599"      // Ethereum Mainnet
            137L -> "0x1BFD67037B42Cf73acF2047067bd4F2C47D9BfD6"    // Polygon
            42161L -> "0x2f2a2543B76A4166549F7aaB2e75Bef0aefC5B0f"  // Arbitrum
            10L -> "0x68f180fcCe6836688e9084f035309E29Bf0A2095"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "WBTC",
                name = "Wrapped Bitcoin",
                decimals = 8,
                chainId = chainId
            )
        }
    }

    /**
     * Get cbBTC (Coinbase Wrapped Bitcoin)
     */
    private fun getCbBTC(chainId: Long): Token? {
        val address = when (chainId) {
            8453L -> "0xcbB7C0000aB88B473b1f5aFd9ef808440eed33Bf"   // Base
            1L -> "0xcbB7C0000aB88B473b1f5aFd9ef808440eed33Bf"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "cbBTC",
                name = "Coinbase BTC",
                decimals = 8,
                chainId = chainId
            )
        }
    }

    /**
     * Get tBTC (Threshold Bitcoin)
     */
    private fun getTBTC(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x18084fbA666a33d37592fA2633fD49a74DD93a88"      // Ethereum Mainnet
            137L -> "0x236aa50979D5f3De3Bd1Eeb40E81137F22ab794b"    // Polygon
            42161L -> "0x6c84a8f1c29108F47a79964b5Fe888D4f4D0dE40"  // Arbitrum
            10L -> "0x6c84a8f1c29108F47a79964b5Fe888D4f4D0dE40"     // Optimism
            8453L -> "0x236aa50979D5f3De3Bd1Eeb40E81137F22ab794b"   // Base
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "tBTC",
                name = "Threshold BTC",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get stETH (Lido Staked Ether)
     */
    private fun getStETH(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xae7ab96520DE3A18E5e111B5EaAb095312D7fE84"      // Ethereum Mainnet
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "stETH",
                name = "Lido Staked ETH",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get wstETH (Wrapped stETH - available on L2s)
     */
    private fun getWstETH(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0x7f39C581F595B53c5cb19bD0b3f8dA6c935E2Ca0"      // Ethereum Mainnet
            137L -> "0x03b54A6e9a984069379fae1a4fC4dBAE93B3bCCD"    // Polygon
            42161L -> "0x5979D7b546E38E414F7E9822514be443A4800529"  // Arbitrum
            10L -> "0x1F32b1c2345538c0c6f582fCB022739c4A194Ebb"     // Optimism
            8453L -> "0xc1CBa3fCea344f92D9239c08C0568f6F2F0ee452"   // Base
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "wstETH",
                name = "Wrapped stETH",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get rETH (Rocket Pool ETH)
     */
    private fun getRETH(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xae78736Cd615f374D3085123A210448E74Fc6393"      // Ethereum Mainnet
            137L -> "0x0266F4F08D82372CF0FcbCCc0Ff74309089c74d1"    // Polygon
            42161L -> "0xEC70Dcb4A1EFa46b8F2D97C310C9c4790ba5ffA8"  // Arbitrum
            10L -> "0x9Bcef72be871e61ED4fBbc7630889beE758eb81D"     // Optimism
            8453L -> "0xB6fe221Fe9EeF5aBa221c348bA20A1Bf5e73624c"   // Base
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "rETH",
                name = "Rocket Pool ETH",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Get cbETH (Coinbase Staked ETH)
     */
    private fun getCbETH(chainId: Long): Token? {
        val address = when (chainId) {
            1L -> "0xBe9895146f7AF43049ca1c1AE358B0541Ea49704"      // Ethereum Mainnet
            8453L -> "0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22"   // Base
            137L -> "0x4b4327dB1600B8B1440163F667e199CEf35385f5"    // Polygon
            42161L -> "0x1DEBd73E752bEaF79865Fd6446b0c970EaE7732f"  // Arbitrum
            10L -> "0xadDb6A0412DE1BA0F936DCaeb8Aaa24578dcF3B2"     // Optimism
            else -> null
        }
        return address?.let {
            Token(
                address = it,
                symbol = "cbETH",
                name = "Coinbase Staked ETH",
                decimals = 18,
                chainId = chainId
            )
        }
    }

    /**
     * Underlying currency types for grouping tokens
     */
    enum class UnderlyingCurrency(val code: String, val displayName: String) {
        USD("USD", "US Dollar"),
        EUR("EUR", "Euro"),
        GBP("GBP", "British Pound"),
        JPY("JPY", "Japanese Yen"),
        CHF("CHF", "Swiss Franc"),
        ETH("ETH", "Ethereum"),
        BTC("BTC", "Bitcoin")
    }

    /**
     * Get tokens grouped by underlying currency
     */
    fun getTokensByUnderlying(chainId: Long): Map<UnderlyingCurrency, List<Token>> {
        val result = mutableMapOf<UnderlyingCurrency, MutableList<Token>>()

        // Initialize all categories
        UnderlyingCurrency.entries.forEach { result[it] = mutableListOf() }

        // USD tokens
        getUSDC(chainId)?.let { result[UnderlyingCurrency.USD]?.add(it) }
        getUSDT(chainId)?.let { result[UnderlyingCurrency.USD]?.add(it) }
        getDAI(chainId)?.let { result[UnderlyingCurrency.USD]?.add(it) }

        // EUR tokens
        getEURC(chainId)?.let { result[UnderlyingCurrency.EUR]?.add(it) }
        getAgEUR(chainId)?.let { result[UnderlyingCurrency.EUR]?.add(it) }
        getEURS(chainId)?.let { result[UnderlyingCurrency.EUR]?.add(it) }

        // GBP tokens
        getGBPT(chainId)?.let { result[UnderlyingCurrency.GBP]?.add(it) }
        getTGBP(chainId)?.let { result[UnderlyingCurrency.GBP]?.add(it) }

        // JPY tokens
        getJPYC(chainId)?.let { result[UnderlyingCurrency.JPY]?.add(it) }
        getGYEN(chainId)?.let { result[UnderlyingCurrency.JPY]?.add(it) }

        // CHF tokens
        getXCHF(chainId)?.let { result[UnderlyingCurrency.CHF]?.add(it) }

        // ETH tokens (wrapped and staked versions)
        getWETH(chainId)?.let { result[UnderlyingCurrency.ETH]?.add(it) }
        getWstETH(chainId)?.let { result[UnderlyingCurrency.ETH]?.add(it) }
        getStETH(chainId)?.let { result[UnderlyingCurrency.ETH]?.add(it) }
        getRETH(chainId)?.let { result[UnderlyingCurrency.ETH]?.add(it) }
        getCbETH(chainId)?.let { result[UnderlyingCurrency.ETH]?.add(it) }

        // BTC tokens (wrapped versions)
        getWBTC(chainId)?.let { result[UnderlyingCurrency.BTC]?.add(it) }
        getCbBTC(chainId)?.let { result[UnderlyingCurrency.BTC]?.add(it) }
        getTBTC(chainId)?.let { result[UnderlyingCurrency.BTC]?.add(it) }

        // Remove empty categories
        return result.filterValues { it.isNotEmpty() }
    }

    /**
     * Get available underlying currencies for a chain
     */
    fun getAvailableUnderlyings(chainId: Long): List<UnderlyingCurrency> {
        return getTokensByUnderlying(chainId).keys.toList().sortedBy { underlying ->
            // Sort by preference: user's region first, then USD, then others
            val preferred = getPreferredCurrency()
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

    /**
     * Get tokens for a specific underlying currency
     */
    fun getTokensForUnderlying(chainId: Long, underlying: UnderlyingCurrency): List<Token> {
        return getTokensByUnderlying(chainId)[underlying] ?: emptyList()
    }

    /**
     * Get default tokens for a chain, ordered by region preference.
     * Priority based on user's region:
     * - Japan: JPYC, USDC, others
     * - Switzerland: XCHF, EURC, USDC, others
     * - UK: GBPT, USDC, EURC, others
     * - EU: EURC, USDC, others
     * - Others: USDC, EURC, others
     */
    fun getDefaultTokens(chainId: Long): List<Token> {
        val native = getNativeToken(chainId)
        val stablecoins = mutableListOf<Token>()
        addRegionPreferredStablecoins(chainId, stablecoins)

        // Add regional stablecoins that might be useful globally
        // (only if not already added)
        val addedSymbols = stablecoins.map { it.symbol }.toMutableSet()

        // Add JPY options for Ethereum L1
        if (chainId == 1L) addIfMissing(stablecoins, addedSymbols, getGYEN(chainId))
        addIfMissing(stablecoins, addedSymbols, getJPYC(chainId))

        // Add GBP options for Ethereum L1
        if (chainId == 1L) addIfMissing(stablecoins, addedSymbols, getTGBP(chainId))

        // Add EUR options
        addIfMissing(stablecoins, addedSymbols, getAgEUR(chainId))
        addIfMissing(stablecoins, addedSymbols, getEURS(chainId))

        // Add other common stablecoins (USDT, DAI)
        addIfMissing(stablecoins, addedSymbols, getUSDT(chainId))
        addIfMissing(stablecoins, addedSymbols, getDAI(chainId))

        return listOf(native) + stablecoins
    }

    private fun addRegionPreferredStablecoins(chainId: Long, stablecoins: MutableList<Token>) {
        when (getPreferredCurrency()) {
            "JPY" -> addByPreference(stablecoins, listOf(
                if (chainId == 1L) getGYEN(chainId) else null,
                getJPYC(chainId),
                getUSDC(chainId),
                getEURC(chainId)
            ))
            "CHF" -> addByPreference(stablecoins, listOf(
                getXCHF(chainId),
                getEURC(chainId),
                getUSDC(chainId)
            ))
            "GBP" -> addByPreference(stablecoins, listOf(
                getGBPT(chainId),
                if (chainId == 1L) getTGBP(chainId) else null,
                getUSDC(chainId),
                getEURC(chainId)
            ))
            "EUR" -> addByPreference(stablecoins, listOf(
                getEURC(chainId),
                getAgEUR(chainId),
                getEURS(chainId),
                getUSDC(chainId)
            ))
            else -> addByPreference(stablecoins, listOf(
                getUSDC(chainId),
                getEURC(chainId)
            ))
        }
    }

    private fun addByPreference(stablecoins: MutableList<Token>, tokens: List<Token?>) {
        tokens.filterNotNull().forEach { stablecoins.add(it) }
    }

    private fun addIfMissing(stablecoins: MutableList<Token>, addedSymbols: MutableSet<String>, token: Token?) {
        if (token == null || token.symbol in addedSymbols) return
        stablecoins.add(token)
        addedSymbols.add(token.symbol)
    }

    /**
     * Get the preferred stablecoin for collecting payments based on region
     */
    fun getPreferredStablecoin(chainId: Long): Token? {
        return when (getPreferredCurrency()) {
            "JPY" -> {
                if (chainId == 1L) getGYEN(chainId) ?: getJPYC(chainId) ?: getUSDC(chainId)
                else getJPYC(chainId) ?: getUSDC(chainId)
            }
            "CHF" -> getXCHF(chainId) ?: getEURC(chainId) ?: getUSDC(chainId)
            "GBP" -> getGBPT(chainId) ?: getTGBP(chainId) ?: getUSDC(chainId)
            "EUR" -> getEURC(chainId) ?: getAgEUR(chainId) ?: getUSDC(chainId)
            else -> getUSDC(chainId)
        }
    }

    fun getAllDefaultTokens(): List<Token> {
        return ChainConfig.CHAINS.flatMap { chain ->
            getDefaultTokens(chain.chainId)
        }
    }
}
