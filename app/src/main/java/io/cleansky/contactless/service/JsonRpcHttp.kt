package io.cleansky.contactless.service

import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URL

internal object JsonRpcHttp {
    fun createPostConnection(rawUrl: String): HttpURLConnection {
        val connection = (URL(rawUrl).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        return connection
    }

    fun writeJsonBody(
        connection: HttpURLConnection,
        body: JsonObject,
    ) {
        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray())
        }
    }

    fun readResponseBody(connection: HttpURLConnection): String {
        return if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText() ?: ServiceErrorCatalog.UNKNOWN_ERROR
        }
    }
}
