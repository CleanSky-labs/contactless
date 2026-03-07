package io.cleansky.contactless.model

/**
 * Formal state machine for payment transactions (Spec v0.2)
 *
 * State flow:
 * REQUEST_CREATED → REQUEST_SENT → AUTH_RECEIVED → TX_BROADCAST → TX_CONFIRMED/TX_FAILED
 */
enum class PaymentState {
    /**
     * Merchant has generated a valid PaymentRequest.
     * Timeout: N/A
     */
    REQUEST_CREATED,

    /**
     * PaymentRequest transmitted via NFC to payer.
     * Timeout: expiry timestamp
     */
    REQUEST_SENT,

    /**
     * Valid signature received from payer.
     * Timeout: 30 seconds
     */
    AUTH_RECEIVED,

    /**
     * Transaction submitted to mempool/bundler.
     * Timeout: 5 minutes
     */
    TX_BROADCAST,

    /**
     * Transaction confirmed on-chain.
     * Terminal state.
     */
    TX_CONFIRMED,

    /**
     * Transaction failed or timed out.
     * Terminal state.
     */
    TX_FAILED;

    fun isTerminal(): Boolean = this == TX_CONFIRMED || this == TX_FAILED

    fun canTransitionTo(next: PaymentState): Boolean {
        return when (this) {
            REQUEST_CREATED -> next == REQUEST_SENT || next == TX_FAILED
            REQUEST_SENT -> next == AUTH_RECEIVED || next == TX_FAILED
            AUTH_RECEIVED -> next == TX_BROADCAST || next == TX_FAILED
            TX_BROADCAST -> next == TX_CONFIRMED || next == TX_FAILED
            TX_CONFIRMED -> false
            TX_FAILED -> false
        }
    }

    companion object {
        const val AUTH_RECEIVED_TIMEOUT_MS = 30_000L
        const val TX_BROADCAST_TIMEOUT_MS = 300_000L // 5 minutes
    }
}

/**
 * Holds the current state of a payment with timestamps
 */
data class PaymentStateHolder(
    val invoiceId: String,
    val state: PaymentState,
    val enteredAt: Long = System.currentTimeMillis(),
    val request: PaymentRequest? = null,
    val signedTx: SignedTransaction? = null,
    val txHash: String? = null,
    val error: String? = null
) {
    fun transition(newState: PaymentState): PaymentStateHolder {
        require(state.canTransitionTo(newState)) {
            "Invalid state transition: $state -> $newState"
        }
        return copy(state = newState, enteredAt = System.currentTimeMillis())
    }

    fun isTimedOut(): Boolean {
        val elapsed = System.currentTimeMillis() - enteredAt
        return when (state) {
            PaymentState.REQUEST_SENT -> request?.isExpired() == true
            PaymentState.AUTH_RECEIVED -> elapsed > PaymentState.AUTH_RECEIVED_TIMEOUT_MS
            PaymentState.TX_BROADCAST -> elapsed > PaymentState.TX_BROADCAST_TIMEOUT_MS
            else -> false
        }
    }
}
