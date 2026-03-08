package io.cleansky.contactless.service

internal object ServiceErrorCatalog {
    const val UNKNOWN_ERROR = "Unknown error"
    const val RELAYER_NOT_CONFIGURED = "Relayer not configured"
    const val RELAYER_ERROR = "Relayer error"
    const val PAYMASTER_NOT_CONFIGURED = "Paymaster not configured"
    const val ACCOUNT_ABSTRACTION_ERROR = "Account abstraction error"
    const val STATUS_CHECK_ERROR = "Status check error"
    const val TRANSACTION_FAILED = "Transaction failed"
    const val PRIVACY_PAYMENT_ERROR = "Privacy payment error"
    const val NO_BUNDLER_AVAILABLE = "No bundler available"
    const val UNKNOWN_BUNDLER_ERROR = "Unknown bundler error"
    const val USER_OPERATION_FAILED = "UserOperation failed on-chain"
    const val REFUND_AMOUNT_GT_ZERO = "Refund amount must be greater than 0"
    const val REFUND_PROCESSING_ERROR = "Unknown error processing refund"
    const val NO_BALANCE_TO_SPEND = "No balance to spend"

    fun fromException(
        error: Exception,
        fallback: String,
    ): String = error.message?.takeIf { it.isNotBlank() } ?: fallback
}
