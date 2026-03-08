package io.cleansky.contactless

import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.PaymasterConfig
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.RelayerConfig
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.service.TransactionExecutor
import org.web3j.crypto.Credentials

internal fun interface TokenAllowlistPolicy {
    suspend fun isTokenAllowed(
        asset: String,
        chainId: Long,
    ): Boolean
}

internal interface NoncePort {
    suspend fun consume(
        nonce: String,
        invoiceId: String,
        expiry: Long,
    ): Boolean

    suspend fun release(
        nonce: String,
        invoiceId: String,
    )
}

internal interface PaymentFeedbackPort {
    suspend fun onSuccess()

    suspend fun onError()
}

internal fun interface PaymentRecorder {
    suspend fun record(
        signedTx: SignedTransaction,
        txHash: String,
    )
}

internal fun interface WalletCredentialsProvider {
    suspend fun getUnsafeCredentials(): Credentials?
}

internal fun interface PaymentExecutor {
    suspend fun execute(signedTx: SignedTransaction): TransactionExecutor.ExecutionResult
}

internal interface ExecutorFactory {
    fun relayOnly(state: AppState): PaymentExecutor

    fun standard(
        state: AppState,
        credentials: Credentials,
    ): PaymentExecutor
}

internal object DefaultExecutorFactory : ExecutorFactory {
    override fun relayOnly(state: AppState): PaymentExecutor {
        val executor =
            TransactionExecutor.forRelayOnly(
                chainConfig = state.selectedChain,
                escrowOverride = state.receiveOnlyEscrow,
                relayerConfig = RelayerConfig.GELATO,
                apiKey = state.relayerApiKey,
            )
        return PaymentExecutor { signedTx -> executor.executePayment(signedTx) }
    }

    override fun standard(
        state: AppState,
        credentials: Credentials,
    ): PaymentExecutor {
        val executor =
            TransactionExecutor(
                chainConfig = state.selectedChain,
                credentials = credentials,
                executionMode = state.executionMode,
                relayerConfig = if (state.executionMode == ExecutionMode.RELAYER) RelayerConfig.GELATO else null,
                paymasterConfig = if (state.executionMode == ExecutionMode.ACCOUNT_ABSTRACTION) PaymasterConfig.PIMLICO else null,
                apiKey = state.relayerApiKey,
            )
        return PaymentExecutor { signedTx -> executor.executePayment(signedTx) }
    }
}

internal class AppStateMachine(
    private val tokenAllowlistPolicy: TokenAllowlistPolicy,
    private val noncePort: NoncePort,
    private val paymentFeedbackPort: PaymentFeedbackPort,
    private val paymentRecorder: PaymentRecorder,
    private val walletCredentialsProvider: WalletCredentialsProvider,
    private val executorFactory: ExecutorFactory,
) {
    suspend fun processIncomingPaymentRequest(
        currentState: AppState,
        request: PaymentRequest,
    ): AppState {
        if (currentState.mode != Mode.PAY) return currentState

        return when (val validation = request.validate()) {
            is PaymentRequest.ValidationResult.Valid -> {
                val isAllowed = tokenAllowlistPolicy.isTokenAllowed(request.asset, request.chainId)
                if (!isAllowed) {
                    currentState.copy(txStatus = TxStatus.Error("Token no permitido"))
                } else {
                    currentState.copy(pendingRequest = request)
                }
            }

            is PaymentRequest.ValidationResult.Expired -> {
                currentState.copy(txStatus = TxStatus.Error("Solicitud expirada"))
            }

            is PaymentRequest.ValidationResult.InsufficientTime -> {
                currentState.copy(txStatus = TxStatus.Error("Tiempo insuficiente para firmar"))
            }

            is PaymentRequest.ValidationResult.Invalid -> {
                currentState.copy(txStatus = TxStatus.Error("Solicitud invalida: ${validation.reason}"))
            }
        }
    }

    suspend fun processIncomingSignedTransaction(
        currentState: AppState,
        signedTx: SignedTransaction,
    ): AppState {
        if (currentState.mode != Mode.COLLECT && currentState.mode != Mode.RECEIVE_ONLY) {
            return currentState
        }

        val baseState =
            currentState.copy(
                txStatus = TxStatus.Executing,
                lastTxAmount = signedTx.amount,
                lastTxSymbol = currentState.selectedChain.symbol,
            )

        val nonceConsumed = noncePort.consume(signedTx.nonce, signedTx.invoiceId, signedTx.expiry)
        if (!nonceConsumed) {
            paymentFeedbackPort.onError()
            return baseState.copy(txStatus = TxStatus.Error("Replay detectado: nonce ya usado"))
        }

        if (baseState.mode == Mode.RECEIVE_ONLY) {
            if (baseState.relayerApiKey.isBlank()) {
                noncePort.release(signedTx.nonce, signedTx.invoiceId)
                paymentFeedbackPort.onError()
                return baseState.copy(txStatus = TxStatus.Error("API key required"))
            }

            val result = executorFactory.relayOnly(baseState).execute(signedTx)
            return baseState.copy(
                txStatus = handleCollectExecutionResult(result, signedTx),
            )
        }

        val credentials = walletCredentialsProvider.getUnsafeCredentials()
        if (credentials == null) {
            noncePort.release(signedTx.nonce, signedTx.invoiceId)
            paymentFeedbackPort.onError()
            return baseState.copy(txStatus = TxStatus.Error("Wallet no configurada"))
        }

        val result = executorFactory.standard(baseState, credentials).execute(signedTx)
        return baseState.copy(
            txStatus = handleCollectExecutionResult(result, signedTx),
        )
    }

    private suspend fun handleCollectExecutionResult(
        result: TransactionExecutor.ExecutionResult,
        signedTx: SignedTransaction,
    ): TxStatus {
        return when (result) {
            is TransactionExecutor.ExecutionResult.Success -> {
                paymentFeedbackPort.onSuccess()
                paymentRecorder.record(signedTx, result.txHash)
                TxStatus.Success(result.txHash)
            }

            is TransactionExecutor.ExecutionResult.Pending -> {
                paymentFeedbackPort.onSuccess()
                paymentRecorder.record(signedTx, result.taskId)
                TxStatus.Success(result.taskId)
            }

            is TransactionExecutor.ExecutionResult.Error -> {
                noncePort.release(signedTx.nonce, signedTx.invoiceId)
                paymentFeedbackPort.onError()
                TxStatus.Error(result.message)
            }
        }
    }
}
