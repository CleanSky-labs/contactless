package io.cleansky.contactless.service

import io.cleansky.contactless.crypto.UserOperation
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.RelayerConfig
import io.cleansky.contactless.model.SignedTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Credentials

class TransactionExecutorFlowTest {
    private val chain =
        ChainConfig(
            chainId = 84532L,
            name = "Base Sepolia",
            rpcUrl = "https://sepolia.base.org",
            escrowAddress = "0x0000000000000000000000000000000000000001",
            usdcAddress = "0x0000000000000000000000000000000000000002",
        )

    private val credentials =
        Credentials
            .create("0x0000000000000000000000000000000000000000000000000000000000000001")

    private val signedTx =
        SignedTransaction(
            merchantId = "0x" + "11".repeat(32),
            invoiceId = "0x" + "22".repeat(32),
            amount = "1",
            asset = "0x0000000000000000000000000000000000000003",
            chainId = chain.chainId,
            escrow = chain.escrowAddress,
            nonce = "0x" + "33".repeat(32),
            expiry = System.currentTimeMillis() / 1000 + 300,
            payer = credentials.address,
            payerSig = "0x" + "44".repeat(65),
        )

    private val invalidRpcChain = chain.copy(rpcUrl = "http://127.0.0.1:1")

    @Test
    fun `executePayment in RELAYER mode fails fast without relayer config`() =
        runBlocking {
            val executor =
                TransactionExecutor(
                    chainConfig = chain,
                    credentials = credentials,
                    executionMode = ExecutionMode.RELAYER,
                    relayerConfig = null,
                    apiKey = "",
                )

            val result = executor.executePayment(signedTx)

            assertTrue(result is TransactionExecutor.ExecutionResult.Error)
            assertEquals(
                "Relayer not configured",
                (result as TransactionExecutor.ExecutionResult.Error).message,
            )
        }

    @Test
    fun `executePayment in DIRECT mode returns error when RPC is unreachable`() =
        runBlocking {
            val executor =
                TransactionExecutor(
                    chainConfig = invalidRpcChain,
                    credentials = credentials,
                    executionMode = ExecutionMode.DIRECT,
                )

            val result = executor.executePayment(signedTx)

            assertTrue(result is TransactionExecutor.ExecutionResult.Error)
        }

    @Test
    fun `executePayment in ACCOUNT_ABSTRACTION mode fails fast without paymaster config`() =
        runBlocking {
            val executor =
                TransactionExecutor(
                    chainConfig = chain,
                    credentials = credentials,
                    executionMode = ExecutionMode.ACCOUNT_ABSTRACTION,
                    paymasterConfig = null,
                    apiKey = "",
                )

            val result = executor.executePayment(signedTx)

            assertTrue(result is TransactionExecutor.ExecutionResult.Error)
            assertEquals(
                "Paymaster not configured",
                (result as TransactionExecutor.ExecutionResult.Error).message,
            )
        }

    @Test
    fun `executePayment in RELAYER mode returns error for malformed relayer URL`() =
        runBlocking {
            val badRelayer =
                RelayerConfig(
                    name = "Gelato",
                    url = "http://:",
                    chainIds = listOf(chain.chainId),
                )
            val executor =
                TransactionExecutor(
                    chainConfig = chain,
                    credentials = credentials,
                    executionMode = ExecutionMode.RELAYER,
                    relayerConfig = badRelayer,
                    apiKey = "k",
                )

            val result = executor.executePayment(signedTx)

            assertTrue(result is TransactionExecutor.ExecutionResult.Error)
        }

    @Test
    fun `checkRelayStatus returns error for malformed Gelato status URL`() =
        runBlocking {
            val badRelayer =
                RelayerConfig(
                    name = "Gelato",
                    url = "http://:",
                    chainIds = listOf(chain.chainId),
                )
            val executor =
                TransactionExecutor(
                    chainConfig = chain,
                    credentials = credentials,
                    executionMode = ExecutionMode.RELAYER,
                    relayerConfig = badRelayer,
                    apiKey = "k",
                )

            val result = executor.checkRelayStatus("task-1")

            assertTrue(result is TransactionExecutor.ExecutionResult.Error)
        }

    @Test
    fun `checkRelayStatus returns pending when relayer is not Gelato`() =
        runBlocking {
            val executor =
                TransactionExecutor(
                    chainConfig = chain,
                    credentials = credentials,
                    executionMode = ExecutionMode.RELAYER,
                    relayerConfig = null,
                    apiKey = "",
                )

            val result = executor.checkRelayStatus("task-1")

            assertTrue(result is TransactionExecutor.ExecutionResult.Pending)
            assertEquals("task-1", (result as TransactionExecutor.ExecutionResult.Pending).taskId)
        }

    @Test
    fun `encode helpers and signing produce expected formats`() {
        val executor =
            TransactionExecutor(
                chainConfig = chain,
                credentials = credentials,
                executionMode = ExecutionMode.RELAYER,
                relayerConfig = null,
                apiKey = "",
            )

        val encodedPay = callPrivate<String>(executor, "encodePayFunction", signedTx)
        val encodedExecute =
            callPrivate<String>(
                executor,
                "encodeExecuteFunction",
                chain.escrowAddress,
                "0",
                "0x",
            )
        val userOp =
            UserOperation(
                sender = credentials.address,
                nonce = "0x1",
                callData = encodedExecute,
                callGasLimit = "0x50000",
                verificationGasLimit = "0x50000",
                preVerificationGas = "0x10000",
                maxFeePerGas = "0x6fc23ac00",
                maxPriorityFeePerGas = "0x3b9aca00",
            )
        val hashA = callPrivate<ByteArray>(executor, "getUserOpHash", userOp)
        val hashB =
            callPrivate<ByteArray>(
                executor,
                "getUserOpHash",
                userOp.copy(nonce = "0x2"),
            )
        val signed = callPrivate<UserOperation>(executor, "signUserOperation", userOp)

        assertTrue(encodedPay.startsWith("0x"))
        assertTrue(encodedExecute.startsWith("0x"))
        assertEquals(32, hashA.size)
        assertNotEquals(hashA.toList(), hashB.toList())
        assertTrue(signed.signature.startsWith("0x"))
        assertEquals(132, signed.signature.length)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> callPrivate(
        instance: Any,
        name: String,
        vararg args: Any,
    ): T {
        val method =
            instance::class.java.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size
            }
        method.isAccessible = true
        return method.invoke(instance, *args) as T
    }
}
