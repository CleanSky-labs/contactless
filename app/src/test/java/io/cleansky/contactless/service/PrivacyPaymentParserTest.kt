package io.cleansky.contactless.service

import com.google.gson.JsonObject
import io.cleansky.contactless.crypto.UserOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPaymentParserTest {
    private val baseUserOp =
        UserOperation(
            sender = "0xsender",
            nonce = "0x1",
            initCode = "0x",
            callData = "0xdata",
            callGasLimit = "0x100",
            verificationGasLimit = "0x200",
            preVerificationGas = "0x300",
            maxFeePerGas = "0x400",
            maxPriorityFeePerGas = "0x500",
            paymasterAndData = "0x",
            signature = "0xsig",
        )

    @Test
    fun `parsePaymasterResponse applies sponsored gas fields and paymaster data`() {
        // Given
        val response =
            JsonObject().apply {
                add(
                    "result",
                    JsonObject().apply {
                        addProperty("paymasterAndData", "0xpm")
                        addProperty("callGasLimit", "0x111")
                        addProperty("verificationGasLimit", "0x222")
                        addProperty("preVerificationGas", "0x333")
                    },
                )
            }

        // When
        val sponsored = PrivacyPaymentParser.parsePaymasterResponse(response, baseUserOp)

        // Then
        assertEquals("0xpm", sponsored.paymasterAndData)
        assertEquals("0x111", sponsored.callGasLimit)
        assertEquals("0x222", sponsored.verificationGasLimit)
        assertEquals("0x333", sponsored.preVerificationGas)
    }

    @Test
    fun `parseSendUserOperationResponse returns pending when result hash exists`() {
        // Given
        val response = JsonObject().apply { addProperty("result", "0xuserop") }

        // When
        val result = PrivacyPaymentParser.parseSendUserOperationResponse(response, "0xabc")

        // Then
        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Pending)
        val pending = result as PrivacyPaymentExecutor.PrivacyExecutionResult.Pending
        assertEquals("0xuserop", pending.userOpHash)
        assertEquals("0xabc", pending.ephemeralAddress)
    }

    @Test
    fun `parseSendUserOperationResponse returns error message when error is present`() {
        // Given
        val response =
            JsonObject().apply {
                add("error", JsonObject().apply { addProperty("message", "bundler rejected op") })
            }

        // When
        val result = PrivacyPaymentParser.parseSendUserOperationResponse(response, "0xabc")

        // Then
        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Error)
        assertEquals(
            "bundler rejected op",
            (result as PrivacyPaymentExecutor.PrivacyExecutionResult.Error).message,
        )
    }

    @Test
    fun `parseUserOpStatusResponse returns success when receipt has tx hash and success true`() {
        // Given
        val response =
            JsonObject().apply {
                add(
                    "result",
                    JsonObject().apply {
                        addProperty("success", true)
                        addProperty("sender", "0xsender")
                        add("receipt", JsonObject().apply { addProperty("transactionHash", "0xtxhash") })
                    },
                )
            }

        // When
        val result = PrivacyPaymentParser.parseUserOpStatusResponse(response, "0xuserop")

        // Then
        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Success)
        val success = result as PrivacyPaymentExecutor.PrivacyExecutionResult.Success
        assertEquals("0xtxhash", success.txHash)
        assertEquals("0xsender", success.ephemeralAddress)
    }

    @Test
    fun `parseUserOpStatusResponse returns pending when result is null`() {
        // Given
        val response = JsonObject().apply { add("result", null) }

        // When
        val result = PrivacyPaymentParser.parseUserOpStatusResponse(response, "0xpending")

        // Then
        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Pending)
        assertEquals(
            "0xpending",
            (result as PrivacyPaymentExecutor.PrivacyExecutionResult.Pending).userOpHash,
        )
    }
}
