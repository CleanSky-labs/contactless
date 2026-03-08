package io.cleansky.contactless.service

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.cleansky.contactless.crypto.UserOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPaymentParserAdditionalTest {
    private val baseUserOp =
        UserOperation(
            sender = "0xsender",
            nonce = "0x1",
            initCode = "0x",
            callData = "0xcall",
            callGasLimit = "0x10",
            verificationGasLimit = "0x20",
            preVerificationGas = "0x30",
            maxFeePerGas = "0x40",
            maxPriorityFeePerGas = "0x50",
            paymasterAndData = "0x",
            signature = "0xsig",
        )

    @Test(expected = Exception::class)
    fun `parsePaymasterResponse throws when error is present`() {
        val response =
            JsonObject().apply {
                add("error", JsonObject().apply { addProperty("message", "sponsorship denied") })
            }

        PrivacyPaymentParser.parsePaymasterResponse(response, baseUserOp)
    }

    @Test(expected = Exception::class)
    fun `parsePaymasterResponse throws when result is missing`() {
        PrivacyPaymentParser.parsePaymasterResponse(JsonObject(), baseUserOp)
    }

    @Test
    fun `parsePaymasterResponse preserves existing values when result fields are missing`() {
        val response =
            JsonObject().apply {
                add("result", JsonObject())
            }

        val updated = PrivacyPaymentParser.parsePaymasterResponse(response, baseUserOp)

        assertEquals(baseUserOp.callGasLimit, updated.callGasLimit)
        assertEquals(baseUserOp.verificationGasLimit, updated.verificationGasLimit)
        assertEquals(baseUserOp.preVerificationGas, updated.preVerificationGas)
        assertEquals("0x", updated.paymasterAndData)
    }

    @Test
    fun `parseSendUserOperationResponse uses fallback error message when missing message`() {
        val response =
            JsonObject().apply {
                add("error", JsonObject())
            }

        val result = PrivacyPaymentParser.parseSendUserOperationResponse(response, "0xsender")

        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Error)
        assertEquals(
            "Unknown bundler error",
            (result as PrivacyPaymentExecutor.PrivacyExecutionResult.Error).message,
        )
    }

    @Test
    fun `parseUserOpStatusResponse returns error when success is false`() {
        val response =
            JsonObject().apply {
                add(
                    "result",
                    JsonObject().apply {
                        addProperty("success", false)
                        addProperty("sender", "0xsender")
                        add("receipt", JsonObject().apply { addProperty("transactionHash", "0xtx") })
                    },
                )
            }

        val result = PrivacyPaymentParser.parseUserOpStatusResponse(response, "0xhash")
        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Error)
    }

    @Test
    fun `parseUserOpStatusResponse returns pending when result field is json null`() {
        val response =
            JsonObject().apply {
                add("result", JsonNull.INSTANCE)
            }

        val result = PrivacyPaymentParser.parseUserOpStatusResponse(response, "0xpending")

        assertTrue(result is PrivacyPaymentExecutor.PrivacyExecutionResult.Pending)
        assertEquals(
            "0xpending",
            (result as PrivacyPaymentExecutor.PrivacyExecutionResult.Pending).userOpHash,
        )
    }
}
