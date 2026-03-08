package io.cleansky.contactless.service

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class JsonRpcHttpTest {
    @Test
    fun `createPostConnection configures standard JSON RPC HTTP settings`() {
        val connection = JsonRpcHttp.createPostConnection("http://localhost:8080/rpc")

        assertEquals("POST", connection.requestMethod)
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertTrue(connection.doOutput)
        assertEquals(30000, connection.connectTimeout)
        assertEquals(30000, connection.readTimeout)
    }

    @Test
    fun `writeJsonBody writes serialized JSON payload to output stream`() {
        val connection = FakeHttpURLConnection(code = 200)
        val body =
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "eth_chainId")
            }

        JsonRpcHttp.writeJsonBody(connection, body)

        val payload = connection.outputAsString()
        assertTrue(payload.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(payload.contains("\"method\":\"eth_chainId\""))
    }

    @Test
    fun `readResponseBody returns input stream body for successful response`() {
        val connection =
            FakeHttpURLConnection(
                code = 200,
                inputBody = """{"result":"0x1"}""",
            )

        val response = JsonRpcHttp.readResponseBody(connection)

        assertEquals("""{"result":"0x1"}""", response)
    }

    @Test
    fun `readResponseBody returns error stream body for non-success response`() {
        val connection =
            FakeHttpURLConnection(
                code = 500,
                errorBody = """{"error":"boom"}""",
            )

        val response = JsonRpcHttp.readResponseBody(connection)

        assertEquals("""{"error":"boom"}""", response)
    }

    @Test
    fun `readResponseBody falls back to unknown error when error stream is missing`() {
        val connection = FakeHttpURLConnection(code = 503)

        val response = JsonRpcHttp.readResponseBody(connection)

        assertEquals("Unknown error", response)
    }

    private class FakeHttpURLConnection(
        private val code: Int,
        inputBody: String? = null,
        errorBody: String? = null,
    ) : HttpURLConnection(URL("http://localhost")) {
        private val output = ByteArrayOutputStream()
        private val input = inputBody?.let { ByteArrayInputStream(it.toByteArray()) }
        private val error = errorBody?.let { ByteArrayInputStream(it.toByteArray()) }

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getOutputStream() = output

        override fun getInputStream() = input ?: ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream() = error

        override fun getResponseCode(): Int = code

        fun outputAsString(): String = output.toString(Charsets.UTF_8.name())
    }
}
