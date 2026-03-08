package io.cleansky.contactless

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cleansky.contactless.service.ServiceErrorCatalog

@Composable
internal fun localizedErrorMessage(message: String): String {
    val mapped = mapErrorMessage(message) ?: return message
    val base = stringResource(mapped.resId)
    return if (mapped.detail == null) base else "$base: ${mapped.detail}"
}

internal fun localizeErrorMessage(
    message: String,
    getString: (Int) -> String,
): String {
    val mapped = mapErrorMessage(message) ?: return message
    val base = getString(mapped.resId)
    return if (mapped.detail == null) base else "$base: ${mapped.detail}"
}

private data class MappedErrorMessage(
    val resId: Int,
    val detail: String? = null,
)

private val exactErrorMappings =
    mapOf(
        "auth_error" to R.string.error_auth,
        "wallet no configurada" to R.string.error_wallet_not_configured,
        "token no permitido" to R.string.error_token_not_allowed,
        "solicitud expirada" to R.string.error_request_expired,
        "tiempo insuficiente para firmar" to R.string.error_insufficient_time_to_sign,
        "api key required" to R.string.error_api_key_required,
        ServiceErrorCatalog.RELAYER_NOT_CONFIGURED.lowercase() to R.string.error_relayer_not_configured,
        ServiceErrorCatalog.PAYMASTER_NOT_CONFIGURED.lowercase() to R.string.error_paymaster_not_configured,
        ServiceErrorCatalog.RELAYER_ERROR.lowercase() to R.string.error_relayer_generic,
        ServiceErrorCatalog.ACCOUNT_ABSTRACTION_ERROR.lowercase() to R.string.error_account_abstraction,
        ServiceErrorCatalog.STATUS_CHECK_ERROR.lowercase() to R.string.error_status_check,
        ServiceErrorCatalog.TRANSACTION_FAILED.lowercase() to R.string.error_transaction_failed,
        ServiceErrorCatalog.NO_BALANCE_TO_SPEND.lowercase() to R.string.error_no_balance_to_spend,
        ServiceErrorCatalog.REFUND_AMOUNT_GT_ZERO.lowercase() to R.string.error_refund_amount_invalid,
        ServiceErrorCatalog.UNKNOWN_ERROR.lowercase() to R.string.error_unknown,
    )

private fun mapErrorMessage(message: String): MappedErrorMessage? {
    val normalized = message.trim()
    val lower = normalized.lowercase()

    exactErrorMappings[lower]?.let { resId ->
        return MappedErrorMessage(resId)
    }

    if (lower.startsWith("solicitud invalida:")) {
        val detail = normalized.substringAfter(':', "").trim()
        return MappedErrorMessage(
            R.string.error_invalid_request,
            detail.takeIf { it.isNotEmpty() },
        )
    }

    if (lower.startsWith("replay detectado")) {
        return MappedErrorMessage(R.string.error_replay_detected)
    }

    return if (lower.contains("relayer error")) {
        MappedErrorMessage(R.string.error_relayer_generic)
    } else {
        null
    }
}
