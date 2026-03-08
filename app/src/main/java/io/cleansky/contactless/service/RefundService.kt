package io.cleansky.contactless.service

import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Refund execution service.
 *
 * Supports merchant-paid gas refunds.
 */
class RefundService(
    private val transactionRepository: TransactionRepository,
) {
    sealed class RefundResult {
        data class Success(val txHash: String, val amount: BigInteger) : RefundResult()

        data class Error(val message: String) : RefundResult()
    }

    /**
     * Processes a partial or full refund transaction.
     */
    suspend fun processRefund(
        originalTransaction: Transaction,
        refundAmount: BigInteger,
        credentials: Credentials,
        chainConfig: ChainConfig,
    ): RefundResult {
        return withContext(Dispatchers.IO) {
            try {
                val remainingRefundable = originalTransaction.getRemainingRefundable()
                if (refundAmount > remainingRefundable) {
                    return@withContext RefundResult.Error(
                        "Refund amount exceeds refundable balance: $remainingRefundable",
                    )
                }

                if (refundAmount <= BigInteger.ZERO) {
                    return@withContext RefundResult.Error("Refund amount must be greater than 0")
                }

                val web3j = Web3j.build(HttpService(chainConfig.rpcUrl))

                val nonce =
                    web3j.ethGetTransactionCount(
                        credentials.address,
                        DefaultBlockParameterName.PENDING,
                    ).send().transactionCount

                val transferFunction =
                    Function(
                        "transfer",
                        listOf(
                            Address(originalTransaction.counterparty),
                            Uint256(refundAmount),
                        ),
                        emptyList(),
                    )
                val encodedFunction = FunctionEncoder.encode(transferFunction)

                // Estimate gas
                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(100000) // Typical ERC-20 transfer uses ~65k

                val rawTransaction =
                    RawTransaction.createTransaction(
                        nonce,
                        gasPrice,
                        gasLimit,
                        originalTransaction.asset,
                        BigInteger.ZERO,
                        encodedFunction,
                    )

                // Sign
                val signedMessage =
                    TransactionEncoder.signMessage(
                        rawTransaction,
                        chainConfig.chainId,
                        credentials,
                    )
                val hexValue = Numeric.toHexString(signedMessage)

                // Send
                val response = web3j.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    return@withContext RefundResult.Error(response.error.message)
                }

                val txHash = response.transactionHash

                transactionRepository.recordRefundSent(
                    originalTxId = originalTransaction.id,
                    refundTxHash = txHash,
                    refundAmount = refundAmount,
                )

                RefundResult.Success(txHash, refundAmount)
            } catch (e: Exception) {
                RefundResult.Error(e.message ?: "Unknown error processing refund")
            }
        }
    }

    /**
     * Processes a full refund using all refundable balance.
     */
    suspend fun processFullRefund(
        originalTransaction: Transaction,
        credentials: Credentials,
        chainConfig: ChainConfig,
    ): RefundResult {
        val remainingAmount = originalTransaction.getRemainingRefundable()
        return processRefund(originalTransaction, remainingAmount, credentials, chainConfig)
    }

    /**
     * Estimates refund gas cost for the given chain.
     */
    suspend fun estimateRefundGas(chainConfig: ChainConfig): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = Web3j.build(HttpService(chainConfig.rpcUrl))
                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(100000)
                gasPrice.multiply(gasLimit)
            } catch (e: Exception) {
                BigInteger.ZERO
            }
        }
    }
}
