package io.cleansky.contactless.model.defaulttokens

import io.cleansky.contactless.model.Token

internal object DefaultTokenCatalog {
    private data class TokenMeta(
        val name: String,
        val decimals: Int,
    )

    private val nativeSymbols =
        mapOf(
            1L to "ETH",
            8453L to "ETH",
            84532L to "ETH",
            137L to "POL",
            42161L to "ETH",
            10L to "ETH",
            324L to "ETH",
            59144L to "ETH",
            31337L to "ETH",
        )

    private val nativeNames =
        mapOf(
            1L to "Ethereum",
            8453L to "Ethereum",
            84532L to "Ethereum",
            137L to "Polygon",
            42161L to "Ethereum",
            10L to "Ethereum",
            324L to "Ethereum",
            59144L to "Ethereum",
            31337L to "Ethereum",
        )

    private val tokenMetaBySymbol =
        mapOf(
            "USDC" to TokenMeta("USD Coin", 6),
            "USDT" to TokenMeta("Tether USD", 6),
            "DAI" to TokenMeta("Dai Stablecoin", 18),
            "EURC" to TokenMeta("Euro Coin", 6),
            "JPYC" to TokenMeta("JPY Coin", 18),
            "GYEN" to TokenMeta("GMO JPY", 6),
            "XCHF" to TokenMeta("CryptoFranc", 18),
            "EURS" to TokenMeta("STASIS EURO", 2),
            "GBPT" to TokenMeta("Poundtoken", 18),
            "TGBP" to TokenMeta("TrueGBP", 18),
            "agEUR" to TokenMeta("Angle Euro", 18),
            "WETH" to TokenMeta("Wrapped Ether", 18),
            "WBTC" to TokenMeta("Wrapped Bitcoin", 8),
            "cbBTC" to TokenMeta("Coinbase BTC", 8),
            "tBTC" to TokenMeta("Threshold BTC", 18),
            "stETH" to TokenMeta("Lido Staked ETH", 18),
            "wstETH" to TokenMeta("Wrapped stETH", 18),
            "rETH" to TokenMeta("Rocket Pool ETH", 18),
            "cbETH" to TokenMeta("Coinbase Staked ETH", 18),
        )

    private val tokenAddressBySymbol =
        mapOf(
            "EURC" to
                mapOf(
                    1L to "0x1aBaEA1f7C830bD89Acc67eC4af516284b1bC33c",
                    8453L to "0x60a3E35Cc302bFA44Cb288Bc5a4F316Fdb1adb42",
                    137L to "0x820802Fa8a99901F52e39acD21177b0BE6EE2974",
                    42161L to "0x820802Fa8a99901F52e39acD21177b0BE6EE2974",
                    10L to "0x820802Fa8a99901F52e39acD21177b0BE6EE2974",
                ),
            "USDC" to
                mapOf(
                    1L to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                    8453L to "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                    84532L to "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
                    137L to "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
                    42161L to "0xaf88d065e77c8cC2239327C5EDb3A432268e5831",
                    10L to "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85",
                    324L to "0x1d17CBcF0D6D143135aE902365D2E5e2A16538D4",
                    59144L to "0x176211869cA2b568f2A7D4EE941E073a821EE1ff",
                    31337L to "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512",
                ),
            "USDT" to
                mapOf(
                    1L to "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                    137L to "0xc2132D05D31c914a87C6611C10748AEb04B58e8F",
                    42161L to "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9",
                    10L to "0x94b008aA00579c1307B0EF2c499aD98a8ce58e58",
                ),
            "DAI" to
                mapOf(
                    1L to "0x6B175474E89094C44Da98b954EedeAC495271d0F",
                    8453L to "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb",
                    137L to "0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063",
                    42161L to "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
                    10L to "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
                ),
            "JPYC" to
                mapOf(
                    1L to "0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB",
                    137L to "0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB",
                    42161L to "0xa83575490D7df4E2F47b7D38ef351a2722cA45b9",
                    10L to "0x8912389b8d0E902cf77d04E2aD09e5247A6C144A",
                    8453L to "0x8912389b8d0E902cf77d04E2aD09e5247A6C144A",
                ),
            "GYEN" to mapOf(1L to "0xC08512927D12348F6620a698105e1BAac6EcD911"),
            "XCHF" to mapOf(1L to "0xB4272071eCAdd69d933AdcD19cA99fe80664fc08"),
            "EURS" to
                mapOf(
                    1L to "0xdB25f211AB05b1c97D595516F45794528a807ad8",
                    137L to "0xE111178A87A3BFf0c8d18DECBa5798827539Ae99",
                    42161L to "0xD22a58f79e9481D1a88e00c343885A588b34b68B",
                ),
            "GBPT" to mapOf(1L to "0x86B4dBE5D203e634a12364C0e428fa242A3FbA98"),
            "TGBP" to mapOf(1L to "0x00000000441378008EA67F4284A57932B1c000a5"),
            "agEUR" to
                mapOf(
                    1L to "0x1a7e4e63778B4f12a199C062f3eFdD288afCBce8",
                    137L to "0xE0B52e49357Fd4DAf2c15e02058DCE6BC0057db4",
                    42161L to "0xFA5Ed56A203466CbBC2430a43c66b9D8723528E7",
                    10L to "0x9485aca5bbBE1667AD97c7fE7C4531a624C8b1ED",
                    8453L to "0xA61BeB4A3d02decb01039e378237032B351125B4",
                ),
            "WETH" to
                mapOf(
                    1L to "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
                    8453L to "0x4200000000000000000000000000000000000006",
                    137L to "0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619",
                    42161L to "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1",
                    10L to "0x4200000000000000000000000000000000000006",
                ),
            "WBTC" to
                mapOf(
                    1L to "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599",
                    137L to "0x1BFD67037B42Cf73acF2047067bd4F2C47D9BfD6",
                    42161L to "0x2f2a2543B76A4166549F7aaB2e75Bef0aefC5B0f",
                    10L to "0x68f180fcCe6836688e9084f035309E29Bf0A2095",
                ),
            "cbBTC" to
                mapOf(
                    8453L to "0xcbB7C0000aB88B473b1f5aFd9ef808440eed33Bf",
                    1L to "0xcbB7C0000aB88B473b1f5aFd9ef808440eed33Bf",
                ),
            "tBTC" to
                mapOf(
                    1L to "0x18084fbA666a33d37592fA2633fD49a74DD93a88",
                    137L to "0x236aa50979D5f3De3Bd1Eeb40E81137F22ab794b",
                    42161L to "0x6c84a8f1c29108F47a79964b5Fe888D4f4D0dE40",
                    10L to "0x6c84a8f1c29108F47a79964b5Fe888D4f4D0dE40",
                    8453L to "0x236aa50979D5f3De3Bd1Eeb40E81137F22ab794b",
                ),
            "stETH" to mapOf(1L to "0xae7ab96520DE3A18E5e111B5EaAb095312D7fE84"),
            "wstETH" to
                mapOf(
                    1L to "0x7f39C581F595B53c5cb19bD0b3f8dA6c935E2Ca0",
                    137L to "0x03b54A6e9a984069379fae1a4fC4dBAE93B3bCCD",
                    42161L to "0x5979D7b546E38E414F7E9822514be443A4800529",
                    10L to "0x1F32b1c2345538c0c6f582fCB022739c4A194Ebb",
                    8453L to "0xc1CBa3fCea344f92D9239c08C0568f6F2F0ee452",
                ),
            "rETH" to
                mapOf(
                    1L to "0xae78736Cd615f374D3085123A210448E74Fc6393",
                    137L to "0x0266F4F08D82372CF0FcbCCc0Ff74309089c74d1",
                    42161L to "0xEC70Dcb4A1EFa46b8F2D97C310C9c4790ba5ffA8",
                    10L to "0x9Bcef72be871e61ED4fBbc7630889beE758eb81D",
                    8453L to "0xB6fe221Fe9EeF5aBa221c348bA20A1Bf5e73624c",
                ),
            "cbETH" to
                mapOf(
                    1L to "0xBe9895146f7AF43049ca1c1AE358B0541Ea49704",
                    8453L to "0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22",
                    137L to "0x4b4327dB1600B8B1440163F667e199CEf35385f5",
                    42161L to "0x1DEBd73E752bEaF79865Fd6446b0c970EaE7732f",
                    10L to "0xadDb6A0412DE1BA0F936DCaeb8Aaa24578dcF3B2",
                ),
        )

    internal fun nativeToken(chainId: Long): Token {
        return Token(
            address = Token.NATIVE_ADDRESS,
            symbol = nativeSymbols[chainId] ?: "ETH",
            name = nativeNames[chainId] ?: "Native Token",
            decimals = 18,
            chainId = chainId,
            isNative = true,
        )
    }

    internal fun token(
        symbol: String,
        chainId: Long,
    ): Token? {
        val address = tokenAddressBySymbol[symbol]?.get(chainId) ?: return null
        val meta = tokenMetaBySymbol[symbol] ?: return null
        return Token(
            address = address,
            symbol = symbol,
            name = meta.name,
            decimals = meta.decimals,
            chainId = chainId,
        )
    }
}
