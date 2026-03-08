package io.cleansky.contactless

import io.cleansky.contactless.service.ServiceErrorCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessageLocalizationTest {
    @Test
    fun `maps auth sentinel to localized auth string`() {
        val value = localizeErrorMessage("auth_error", ::fakeString)
        assertEquals("Authentication error", value)
    }

    @Test
    fun `maps spanish token not allowed to localized string`() {
        val value = localizeErrorMessage("Token no permitido", ::fakeString)
        assertEquals("Token not allowed", value)
    }

    @Test
    fun `maps invalid request with detail preserving suffix`() {
        val value = localizeErrorMessage("Solicitud invalida: bad signature", ::fakeString)
        assertEquals("Invalid request: bad signature", value)
    }

    @Test
    fun `maps replay detected prefix to replay string`() {
        val value = localizeErrorMessage("Replay detectado: nonce ya usado", ::fakeString)
        assertEquals("Replay detected: nonce already used", value)
    }

    @Test
    fun `maps relayer not configured from service catalog`() {
        val value = localizeErrorMessage(ServiceErrorCatalog.RELAYER_NOT_CONFIGURED, ::fakeString)
        assertEquals("Relayer not configured", value)
    }

    @Test
    fun `maps relayer error fragments to generic relayer string`() {
        val value = localizeErrorMessage("Gelato relayer error: timeout", ::fakeString)
        assertEquals("Relayer error", value)
    }

    @Test
    fun `maps unknown service error to unknown localized string`() {
        val value = localizeErrorMessage(ServiceErrorCatalog.UNKNOWN_ERROR, ::fakeString)
        assertEquals("Unknown error", value)
    }

    @Test
    fun `returns input message when mapping is not known`() {
        val raw = "custom remote failure"
        val value = localizeErrorMessage(raw, ::fakeString)
        assertEquals(raw, value)
    }

    private fun fakeString(resId: Int): String {
        return when (resId) {
            R.string.error_auth -> "Authentication error"
            R.string.error_token_not_allowed -> "Token not allowed"
            R.string.error_invalid_request -> "Invalid request"
            R.string.error_replay_detected -> "Replay detected: nonce already used"
            R.string.error_relayer_not_configured -> "Relayer not configured"
            R.string.error_relayer_generic -> "Relayer error"
            R.string.error_unknown -> "Unknown error"
            else -> "res:$resId"
        }
    }
}
