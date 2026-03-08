package io.cleansky.contactless

import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.service.TransactionExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Credentials
import java.math.BigInteger

class AppStateMachineTest {
    @Test
    fun `processIncomingPaymentRequest sets pending request when token is allowed`() =
        runBlocking {
            val machine =
                testMachine(
                    tokenAllowlistPolicy = TokenAllowlistPolicy { _, _ -> true },
                )
            val request = validRequest()

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), request)

            assertEquals(request, result.pendingRequest)
            assertTrue(result.txStatus is TxStatus.Idle)
        }

    @Test
    fun `processIncomingPaymentRequest returns error when token is not allowed`() =
        runBlocking {
            val machine =
                testMachine(
                    tokenAllowlistPolicy = TokenAllowlistPolicy { _, _ -> false },
                )
            val request = validRequest()

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), request)

            assertTrue(result.txStatus is TxStatus.Error)
        }

    @Test
    fun `processIncomingPaymentRequest does nothing outside PAY mode`() =
        runBlocking {
            val machine = testMachine()
            val request = validRequest()
            val state = AppState(mode = Mode.COLLECT)

            val result = machine.processIncomingPaymentRequest(state, request)

            assertEquals(state, result)
        }

    @Test
    fun `processIncomingPaymentRequest returns expired error for stale request`() =
        runBlocking {
            val machine = testMachine()
            val request =
                validRequest().copy(
                    expiry = (System.currentTimeMillis() / 1000) - 1000,
                )

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), request)

            assertEquals(TxStatus.Error("Solicitud expirada"), result.txStatus)
        }

    @Test
    fun `processIncomingPaymentRequest returns invalid error for malformed request`() =
        runBlocking {
            val machine = testMachine()
            val malformed =
                validRequest().copy(
                    amount = "0",
                )

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), malformed)

            assertTrue(result.txStatus is TxStatus.Error)
            assertNull(result.pendingRequest)
        }

    @Test
    fun `processIncomingPaymentRequest returns invalid error when expiry is too far`() =
        runBlocking {
            val machine = testMachine()
            val malformed =
                validRequest().copy(
                    expiry = (System.currentTimeMillis() / 1000) + 10_000,
                )

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), malformed)

            assertTrue(result.txStatus is TxStatus.Error)
            assertNull(result.pendingRequest)
        }

    @Test
    fun `processIncomingPaymentRequest returns insufficient time error`() =
        runBlocking {
            val machine = testMachine()
            val shortLived =
                validRequest().copy(
                    expiry = (System.currentTimeMillis() / 1000) + 5,
                )

            val result = machine.processIncomingPaymentRequest(AppState(mode = Mode.PAY), shortLived)

            assertEquals(TxStatus.Error("Tiempo insuficiente para firmar"), result.txStatus)
        }

    @Test
    fun `processIncomingSignedTransaction does nothing outside collect modes`() =
        runBlocking {
            val machine = testMachine()
            val state = AppState(mode = Mode.PAY)

            val result = machine.processIncomingSignedTransaction(state, validSignedTx())

            assertEquals(state, result)
        }

    @Test
    fun `processIncomingSignedTransaction fails when nonce is already consumed`() =
        runBlocking {
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = FakeNoncePort(consumeResult = false),
                    feedbackPort = feedback,
                )

            val result = machine.processIncomingSignedTransaction(AppState(mode = Mode.COLLECT), validSignedTx())

            assertTrue(result.txStatus is TxStatus.Error)
            assertEquals(1, feedback.errorCalls)
        }

    @Test
    fun `processIncomingSignedTransaction receive only requires api key`() =
        runBlocking {
            val nonce = FakeNoncePort(consumeResult = true)
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = nonce,
                    feedbackPort = feedback,
                )

            val state = AppState(mode = Mode.RECEIVE_ONLY, relayerApiKey = "")
            val result = machine.processIncomingSignedTransaction(state, validSignedTx())

            assertTrue(result.txStatus is TxStatus.Error)
            assertEquals(1, nonce.releaseCalls)
            assertEquals(1, feedback.errorCalls)
        }

    @Test
    fun `processIncomingSignedTransaction collect requires wallet credentials`() =
        runBlocking {
            val nonce = FakeNoncePort(consumeResult = true)
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = nonce,
                    feedbackPort = feedback,
                    walletCredentialsProvider = WalletCredentialsProvider { null },
                )

            val result = machine.processIncomingSignedTransaction(AppState(mode = Mode.COLLECT), validSignedTx())

            assertTrue(result.txStatus is TxStatus.Error)
            assertEquals(1, nonce.releaseCalls)
            assertEquals(1, feedback.errorCalls)
        }

    @Test
    fun `processIncomingSignedTransaction records and succeeds on direct execution success`() =
        runBlocking {
            val recorder = FakeRecorder()
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = FakeNoncePort(consumeResult = true),
                    feedbackPort = feedback,
                    recorder = recorder,
                    executorFactory =
                        FakeExecutorFactory(
                            standardResult = TransactionExecutor.ExecutionResult.Success("0xabc"),
                        ),
                )

            val result = machine.processIncomingSignedTransaction(AppState(mode = Mode.COLLECT), validSignedTx())

            assertEquals(TxStatus.Success("0xabc"), result.txStatus)
            assertEquals(1, feedback.successCalls)
            assertEquals("0xabc", recorder.lastTxHash)
        }

    @Test
    fun `processIncomingSignedTransaction returns pending task id as success`() =
        runBlocking {
            val recorder = FakeRecorder()
            val machine =
                testMachine(
                    noncePort = FakeNoncePort(consumeResult = true),
                    recorder = recorder,
                    executorFactory =
                        FakeExecutorFactory(
                            standardResult = TransactionExecutor.ExecutionResult.Pending("task-1"),
                        ),
                )

            val result = machine.processIncomingSignedTransaction(AppState(mode = Mode.COLLECT), validSignedTx())

            assertEquals(TxStatus.Success("task-1"), result.txStatus)
            assertEquals("task-1", recorder.lastTxHash)
        }

    @Test
    fun `processIncomingSignedTransaction receive only executes relay path`() =
        runBlocking {
            val recorder = FakeRecorder()
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = FakeNoncePort(consumeResult = true),
                    feedbackPort = feedback,
                    recorder = recorder,
                    executorFactory =
                        FakeExecutorFactory(
                            relayResult = TransactionExecutor.ExecutionResult.Success("0xrelay"),
                        ),
                )

            val state = AppState(mode = Mode.RECEIVE_ONLY, relayerApiKey = "api-key")
            val result = machine.processIncomingSignedTransaction(state, validSignedTx())

            assertEquals(TxStatus.Success("0xrelay"), result.txStatus)
            assertEquals(1, feedback.successCalls)
            assertEquals("0xrelay", recorder.lastTxHash)
        }

    @Test
    fun `processIncomingSignedTransaction receive only pending maps to success task id`() =
        runBlocking {
            val recorder = FakeRecorder()
            val machine =
                testMachine(
                    noncePort = FakeNoncePort(consumeResult = true),
                    recorder = recorder,
                    executorFactory =
                        FakeExecutorFactory(
                            relayResult = TransactionExecutor.ExecutionResult.Pending("relay-task"),
                        ),
                )

            val state = AppState(mode = Mode.RECEIVE_ONLY, relayerApiKey = "api-key")
            val result = machine.processIncomingSignedTransaction(state, validSignedTx())

            assertEquals(TxStatus.Success("relay-task"), result.txStatus)
            assertEquals("relay-task", recorder.lastTxHash)
        }

    @Test
    fun `processIncomingSignedTransaction receive only error releases nonce`() =
        runBlocking {
            val nonce = FakeNoncePort(consumeResult = true)
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = nonce,
                    feedbackPort = feedback,
                    executorFactory =
                        FakeExecutorFactory(
                            relayResult = TransactionExecutor.ExecutionResult.Error("relay-boom"),
                        ),
                )

            val state = AppState(mode = Mode.RECEIVE_ONLY, relayerApiKey = "api-key")
            val result = machine.processIncomingSignedTransaction(state, validSignedTx())

            assertEquals(TxStatus.Error("relay-boom"), result.txStatus)
            assertEquals(1, nonce.releaseCalls)
            assertEquals(1, feedback.errorCalls)
        }

    @Test
    fun `processIncomingSignedTransaction releases nonce on execution error`() =
        runBlocking {
            val nonce = FakeNoncePort(consumeResult = true)
            val feedback = FakeFeedback()
            val machine =
                testMachine(
                    noncePort = nonce,
                    feedbackPort = feedback,
                    executorFactory =
                        FakeExecutorFactory(
                            standardResult = TransactionExecutor.ExecutionResult.Error("boom"),
                        ),
                )

            val result = machine.processIncomingSignedTransaction(AppState(mode = Mode.COLLECT), validSignedTx())

            assertEquals(TxStatus.Error("boom"), result.txStatus)
            assertEquals(1, nonce.releaseCalls)
            assertEquals(1, feedback.errorCalls)
        }

    @Test
    fun `default executor factory builds relay and standard executors`() {
        val baseState = AppState(mode = Mode.COLLECT, relayerApiKey = "api-key")
        val relayState = AppState(mode = Mode.RECEIVE_ONLY, relayerApiKey = "api-key", receiveOnlyEscrow = "0x" + "2".repeat(40))

        val relayExecutor = DefaultExecutorFactory.relayOnly(relayState)
        val standardExecutor = DefaultExecutorFactory.standard(baseState, Credentials.create("0x" + "1".repeat(64)))

        assertTrue(relayExecutor is PaymentExecutor)
        assertTrue(standardExecutor is PaymentExecutor)
    }

    private fun testMachine(
        tokenAllowlistPolicy: TokenAllowlistPolicy = TokenAllowlistPolicy { _, _ -> true },
        noncePort: FakeNoncePort = FakeNoncePort(consumeResult = true),
        feedbackPort: FakeFeedback = FakeFeedback(),
        recorder: FakeRecorder = FakeRecorder(),
        walletCredentialsProvider: WalletCredentialsProvider = WalletCredentialsProvider { Credentials.create("0x" + "1".repeat(64)) },
        executorFactory: ExecutorFactory = FakeExecutorFactory(),
    ): AppStateMachine {
        return AppStateMachine(
            tokenAllowlistPolicy = tokenAllowlistPolicy,
            noncePort = noncePort,
            paymentFeedbackPort = feedbackPort,
            paymentRecorder = recorder,
            walletCredentialsProvider = walletCredentialsProvider,
            executorFactory = executorFactory,
        )
    }

    private fun validRequest(): PaymentRequest {
        return PaymentRequest.create(
            merchantId = "0x" + "1".repeat(64),
            amount = BigInteger("1000000"),
            asset = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            chainId = 1L,
            escrow = "0x" + "2".repeat(40),
        )
    }

    private fun validSignedTx(): SignedTransaction {
        return SignedTransaction(
            merchantId = "0x" + "1".repeat(64),
            invoiceId = "0x" + "3".repeat(64),
            amount = "1000000",
            asset = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            chainId = 1L,
            escrow = "0x" + "2".repeat(40),
            nonce = "0x" + "4".repeat(64),
            expiry = System.currentTimeMillis() / 1000 + 120,
            payer = "0x" + "5".repeat(40),
            payerSig = "0x" + "6".repeat(130),
        )
    }

    private class FakeNoncePort(
        private val consumeResult: Boolean,
    ) : NoncePort {
        var releaseCalls: Int = 0

        override suspend fun consume(
            nonce: String,
            invoiceId: String,
            expiry: Long,
        ): Boolean = consumeResult

        override suspend fun release(
            nonce: String,
            invoiceId: String,
        ) {
            releaseCalls += 1
        }
    }

    private class FakeFeedback : PaymentFeedbackPort {
        var successCalls: Int = 0
        var errorCalls: Int = 0

        override suspend fun onSuccess() {
            successCalls += 1
        }

        override suspend fun onError() {
            errorCalls += 1
        }
    }

    private class FakeRecorder : PaymentRecorder {
        var lastTxHash: String? = null

        override suspend fun record(
            signedTx: SignedTransaction,
            txHash: String,
        ) {
            lastTxHash = txHash
        }
    }

    private class FakeExecutorFactory(
        private val relayResult: TransactionExecutor.ExecutionResult = TransactionExecutor.ExecutionResult.Success("0xrelay"),
        private val standardResult: TransactionExecutor.ExecutionResult = TransactionExecutor.ExecutionResult.Success("0xdirect"),
    ) : ExecutorFactory {
        override fun relayOnly(state: AppState): PaymentExecutor = PaymentExecutor { relayResult }

        override fun standard(
            state: AppState,
            credentials: Credentials,
        ): PaymentExecutor = PaymentExecutor { standardResult }
    }
}
