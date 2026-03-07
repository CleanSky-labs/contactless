package io.cleansky.contactless.model

data class ChainConfig(
    val chainId: Long,
    val name: String,
    val rpcUrl: String,
    val escrowAddress: String,
    val usdcAddress: String,
    val symbol: String = "USDC",
    val decimals: Int = 6,
    val explorerUrl: String = "",
    // Soporte para diferentes modos de ejecución
    val supportsRelayer: Boolean = true,
    val supportsAA: Boolean = true,
    val entryPointAddress: String = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
) {
    companion object {
        val CHAINS = listOf(
            ChainConfig(
                chainId = 1,
                name = "Ethereum",
                rpcUrl = "https://eth.llamarpc.com",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                explorerUrl = "https://etherscan.io",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 8453,
                name = "Base",
                rpcUrl = "https://mainnet.base.org",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                explorerUrl = "https://basescan.org",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 84532,
                name = "Base Sepolia",
                rpcUrl = "https://sepolia.base.org",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
                explorerUrl = "https://sepolia.basescan.org",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 137,
                name = "Polygon",
                rpcUrl = "https://polygon-rpc.com",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
                explorerUrl = "https://polygonscan.com",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 42161,
                name = "Arbitrum",
                rpcUrl = "https://arb1.arbitrum.io/rpc",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0xaf88d065e77c8cC2239327C5EDb3A432268e5831",
                explorerUrl = "https://arbiscan.io",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 10,
                name = "Optimism",
                rpcUrl = "https://mainnet.optimism.io",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85",
                explorerUrl = "https://optimistic.etherscan.io",
                supportsRelayer = true,
                supportsAA = true
            ),
            ChainConfig(
                chainId = 324,
                name = "zkSync Era",
                rpcUrl = "https://mainnet.era.zksync.io",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x1d17CBcF0D6D143135aE902365D2E5e2A16538D4",
                explorerUrl = "https://explorer.zksync.io",
                supportsRelayer = false,
                supportsAA = false
            ),
            ChainConfig(
                chainId = 59144,
                name = "Linea",
                rpcUrl = "https://rpc.linea.build",
                escrowAddress = "0x0000000000000000000000000000000000000000",
                usdcAddress = "0x176211869cA2b568f2A7D4EE941E073a821EE1ff",
                explorerUrl = "https://lineascan.build",
                supportsRelayer = false,
                supportsAA = false
            ),
            ChainConfig(
                chainId = 31337,
                name = "Localhost",
                rpcUrl = "http://10.0.2.2:8545",
                escrowAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3",
                usdcAddress = "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512",
                explorerUrl = "",
                supportsRelayer = false,
                supportsAA = false
            )
        )

        fun getByChainId(chainId: Long): ChainConfig? = CHAINS.find { it.chainId == chainId }
    }
}
