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
 * Servicio para procesar devoluciones (refunds).
 *
 * Soporta:
 * - Devolución total
 * - Devolución parcial
 * - El merchant paga el gas
 */
class RefundService(
    private val transactionRepository: TransactionRepository
) {
    sealed class RefundResult {
        data class Success(val txHash: String, val amount: BigInteger) : RefundResult()
        data class Error(val message: String) : RefundResult()
    }

    /**
     * Procesa una devolución de una transacción.
     *
     * @param originalTransaction La transacción original a devolver
     * @param refundAmount Monto a devolver (en unidades menores del token)
     * @param credentials Credenciales del merchant para firmar
     * @param chainConfig Configuración de la red
     */
    suspend fun processRefund(
        originalTransaction: Transaction,
        refundAmount: BigInteger,
        credentials: Credentials,
        chainConfig: ChainConfig
    ): RefundResult {
        return withContext(Dispatchers.IO) {
            try {
                // Validar que se puede hacer la devolución
                val remainingRefundable = originalTransaction.getRemainingRefundable()
                if (refundAmount > remainingRefundable) {
                    return@withContext RefundResult.Error(
                        "Monto excede lo disponible para devolver: ${remainingRefundable}"
                    )
                }

                if (refundAmount <= BigInteger.ZERO) {
                    return@withContext RefundResult.Error("El monto debe ser mayor a 0")
                }

                // Conectar a la red
                val web3j = Web3j.build(HttpService(chainConfig.rpcUrl))

                // Obtener nonce
                val nonce = web3j.ethGetTransactionCount(
                    credentials.address,
                    DefaultBlockParameterName.PENDING
                ).send().transactionCount

                // Crear calldata para transfer ERC20
                val transferFunction = Function(
                    "transfer",
                    listOf(
                        Address(originalTransaction.counterparty),
                        Uint256(refundAmount)
                    ),
                    emptyList()
                )
                val encodedFunction = FunctionEncoder.encode(transferFunction)

                // Estimar gas
                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(100000) // ERC20 transfer típicamente usa ~65k

                // Crear transacción
                val rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    originalTransaction.asset, // Token contract
                    BigInteger.ZERO,
                    encodedFunction
                )

                // Firmar
                val signedMessage = TransactionEncoder.signMessage(
                    rawTransaction,
                    chainConfig.chainId,
                    credentials
                )
                val hexValue = Numeric.toHexString(signedMessage)

                // Enviar
                val response = web3j.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    return@withContext RefundResult.Error(response.error.message)
                }

                val txHash = response.transactionHash

                // Registrar la devolución
                transactionRepository.recordRefundSent(
                    originalTxId = originalTransaction.id,
                    refundTxHash = txHash,
                    refundAmount = refundAmount
                )

                RefundResult.Success(txHash, refundAmount)
            } catch (e: Exception) {
                RefundResult.Error(e.message ?: "Unknown error processing refund")
            }
        }
    }

    /**
     * Devolución total - devuelve todo lo que queda por devolver
     */
    suspend fun processFullRefund(
        originalTransaction: Transaction,
        credentials: Credentials,
        chainConfig: ChainConfig
    ): RefundResult {
        val remainingAmount = originalTransaction.getRemainingRefundable()
        return processRefund(originalTransaction, remainingAmount, credentials, chainConfig)
    }

    /**
     * Estima el costo de gas para una devolución
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
