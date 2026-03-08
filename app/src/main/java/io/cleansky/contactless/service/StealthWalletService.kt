package io.cleansky.contactless.service

import io.cleansky.contactless.crypto.StealthAddress
import io.cleansky.contactless.data.PendingStealthPayment
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Stealth Wallet Service - Unified view of all stealth addresses as a single wallet.
 *
 * The merchant sees ONE balance and can spend freely.
 * The system automatically selects which stealth address(es) to use.
 *
 * Privacy model:
 * - All stealth addresses are ephemeral (used once to receive)
 * - Combining them for spending doesn't leak merchant identity
 * - The "cluster" of stealth addresses is anonymous
 * - Main wallet is NEVER connected to stealth cluster
 */
class StealthWalletService(
    private val chainConfig: ChainConfig,
    private val stealthPaymentRepository: StealthPaymentRepository,
    private val merchantCredentials: Credentials,
) {
    private val web3j: Web3j = Web3j.build(HttpService(chainConfig.rpcUrl))
    private val stealthKeys = StealthAddress.deriveStealthKeys(merchantCredentials)

    sealed class SpendResult {
        data class Success(
            val txHashes: List<String>,
            val totalSpent: BigInteger,
        ) : SpendResult()

        data class Error(val message: String) : SpendResult()
    }

    data class StealthWalletBalance(
        val totalBalance: BigInteger,
        val totalBalanceFormatted: String,
        val addressCount: Int,
        val addresses: List<StealthAddressBalance>,
    )

    data class StealthAddressBalance(
        val payment: PendingStealthPayment,
        val balance: BigInteger,
    )

    private data class AddressProcessResult(
        val derivationFailed: Boolean,
        val txHash: String?,
    )

    /**
     * Get unified balance across all stealth addresses
     */
    suspend fun getWalletBalance(): StealthWalletBalance {
        return withContext(Dispatchers.IO) {
            val pending =
                stealthPaymentRepository.getPendingPayments()
                    .filter { it.chainId == chainConfig.chainId }

            val addressBalances =
                pending.mapNotNull { payment ->
                    try {
                        val balance = getBalance(payment.stealthAddress, payment.asset)
                        if (balance > BigInteger.ZERO) {
                            StealthAddressBalance(payment, balance)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.balance } // Largest first for optimal selection

            val total = addressBalances.sumOf { it.balance }

            StealthWalletBalance(
                totalBalance = total,
                totalBalanceFormatted = formatBalance(total, 6),
                addressCount = addressBalances.size,
                addresses = addressBalances,
            )
        }
    }

    /**
     * Spend from stealth wallet - system automatically selects addresses
     *
     * @param recipientAddress Where to send funds
     * @param amount Total amount to send
     * @param asset Token address
     */
    suspend fun spend(
        recipientAddress: String,
        amount: BigInteger,
        asset: String,
    ): SpendResult {
        return withContext(Dispatchers.IO) {
            try {
                val walletBalance = getWalletBalance()

                if (walletBalance.totalBalance < amount) {
                    return@withContext SpendResult.Error(
                        "Insufficient balance: have ${walletBalance.totalBalanceFormatted}, need ${formatBalance(amount, 6)}",
                    )
                }

                // Select addresses to spend from (greedy: largest first)
                val selectedAddresses = selectAddressesForAmount(walletBalance.addresses, amount)

                // Execute transactions
                val txHashes = mutableListOf<String>()
                var remaining = amount

                for ((index, addrBalance) in selectedAddresses.withIndex()) {
                    val amountFromThis = takeAmountFromAddress(remaining, addrBalance.balance)
                    val processResult =
                        processSelectedAddress(
                            addrBalance = addrBalance,
                            amountFromThis = amountFromThis,
                            recipientAddress = recipientAddress,
                            asset = asset,
                            isLastAddress = index == selectedAddresses.lastIndex,
                        )
                    if (processResult.derivationFailed) {
                        return@withContext SpendResult.Error("Failed to derive key for ${addrBalance.payment.stealthAddress}")
                    }
                    processResult.txHash?.let { txHashes.add(it) }

                    remaining -= amountFromThis
                    if (remaining <= BigInteger.ZERO) break
                }

                SpendResult.Success(txHashes, amount - remaining)
            } catch (e: Exception) {
                SpendResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun takeAmountFromAddress(
        remaining: BigInteger,
        addressBalance: BigInteger,
    ): BigInteger {
        return if (remaining >= addressBalance) addressBalance else remaining
    }

    private suspend fun processSelectedAddress(
        addrBalance: StealthAddressBalance,
        amountFromThis: BigInteger,
        recipientAddress: String,
        asset: String,
        isLastAddress: Boolean,
    ): AddressProcessResult {
        val spendingCreds =
            StealthAddress.scanAndDerive(
                stealthKeys = stealthKeys,
                ephemeralPubKey = Numeric.hexStringToByteArray(addrBalance.payment.ephemeralPubKey),
                expectedAddress = addrBalance.payment.stealthAddress,
            ) ?: return AddressProcessResult(derivationFailed = true, txHash = null)

        val actualAmount = computeSpendAmount(amountFromThis, asset, isLastAddress)
        if (actualAmount <= BigInteger.ZERO) {
            return AddressProcessResult(derivationFailed = false, txHash = null)
        }

        val txHash =
            sendTransaction(
                credentials = spendingCreds,
                to = recipientAddress,
                amount = actualAmount,
                asset = asset,
            )
        stealthPaymentRepository.markPaymentClaimed(addrBalance.payment.invoiceId, txHash)
        return AddressProcessResult(derivationFailed = false, txHash = txHash)
    }

    private suspend fun computeSpendAmount(
        amountFromThis: BigInteger,
        asset: String,
        isLastAddress: Boolean,
    ): BigInteger {
        if (!isNativeAsset(asset) || !isLastAddress) return amountFromThis
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasCost = gasPrice.multiply(BigInteger.valueOf(21000))
        return (amountFromThis - gasCost).coerceAtLeast(BigInteger.ZERO)
    }

    /**
     * Spend entire stealth wallet balance
     */
    suspend fun spendAll(
        recipientAddress: String,
        asset: String,
    ): SpendResult {
        val balance = getWalletBalance()
        return if (balance.totalBalance > BigInteger.ZERO) {
            spend(recipientAddress, balance.totalBalance, asset)
        } else {
            SpendResult.Error("No balance to spend")
        }
    }

    private fun selectAddressesForAmount(
        addresses: List<StealthAddressBalance>,
        targetAmount: BigInteger,
    ): List<StealthAddressBalance> {
        // First, try to find a single address that covers the amount
        val singleMatch = addresses.find { it.balance >= targetAmount }
        if (singleMatch != null) {
            return listOf(singleMatch)
        }

        // Otherwise, greedily select addresses until we have enough
        val selected = mutableListOf<StealthAddressBalance>()
        var accumulated = BigInteger.ZERO

        for (addr in addresses) {
            selected.add(addr)
            accumulated += addr.balance
            if (accumulated >= targetAmount) break
        }

        return selected
    }

    private suspend fun sendTransaction(
        credentials: Credentials,
        to: String,
        amount: BigInteger,
        asset: String,
    ): String {
        val nonce =
            web3j.ethGetTransactionCount(
                credentials.address,
                DefaultBlockParameterName.PENDING,
            ).send().transactionCount

        val gasPrice = web3j.ethGasPrice().send().gasPrice

        return if (isNativeAsset(asset)) {
            val rawTx =
                RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    BigInteger.valueOf(21000),
                    to,
                    amount,
                )
            val signedTx = TransactionEncoder.signMessage(rawTx, chainConfig.chainId, credentials)
            val response = web3j.ethSendRawTransaction(Numeric.toHexString(signedTx)).send()
            if (response.hasError()) throw Exception(response.error.message)
            response.transactionHash
        } else {
            val transferData =
                "0xa9059cbb" +
                    to.removePrefix("0x").lowercase().padStart(64, '0') +
                    amount.toString(16).padStart(64, '0')

            val rawTx =
                RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    BigInteger.valueOf(100000),
                    asset,
                    BigInteger.ZERO,
                    transferData,
                )
            val signedTx = TransactionEncoder.signMessage(rawTx, chainConfig.chainId, credentials)
            val response = web3j.ethSendRawTransaction(Numeric.toHexString(signedTx)).send()
            if (response.hasError()) throw Exception(response.error.message)
            response.transactionHash
        }
    }

    private suspend fun getBalance(
        address: String,
        asset: String,
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            if (isNativeAsset(asset)) {
                web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().balance
            } else {
                val data = "0x70a08231" + address.removePrefix("0x").lowercase().padStart(64, '0')
                val result =
                    web3j.ethCall(
                        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null, asset, data),
                        DefaultBlockParameterName.LATEST,
                    ).send()
                if (result.value != null && result.value.length > 2) {
                    BigInteger(result.value.removePrefix("0x"), 16)
                } else {
                    BigInteger.ZERO
                }
            }
        }
    }

    private fun isNativeAsset(asset: String) =
        asset == "0x0000000000000000000000000000000000000000" ||
            asset.equals("native", ignoreCase = true) ||
            asset.isBlank()

    private fun formatBalance(
        balance: BigInteger,
        decimals: Int,
    ): String {
        return NumberFormatter.formatBalance(balance, decimals)
    }
}
