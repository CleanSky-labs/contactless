package io.cleansky.contactless.ui.collect

import io.cleansky.contactless.TxStatus

internal enum class CollectUiState {
    NO_ESCROW_CONFIGURED,
    NO_WALLET,
    SUCCESS,
    EXECUTING,
    ERROR,
    AMOUNT_INPUT,
    BROADCASTING_REQUEST,
    UNKNOWN,
}

internal fun resolveCollectUiState(
    isReceiveOnly: Boolean,
    receiveOnlyEscrow: String,
    walletAddress: String?,
    txStatus: TxStatus,
    isBroadcasting: Boolean,
): CollectUiState {
    resolveCollectPrerequisiteState(
        isReceiveOnly = isReceiveOnly,
        receiveOnlyEscrow = receiveOnlyEscrow,
        walletAddress = walletAddress,
    )?.let { return it }

    return resolveCollectTransactionState(
        txStatus = txStatus,
        isBroadcasting = isBroadcasting,
    )
}

private fun resolveCollectPrerequisiteState(
    isReceiveOnly: Boolean,
    receiveOnlyEscrow: String,
    walletAddress: String?,
): CollectUiState? {
    if (isReceiveOnly && receiveOnlyEscrow.isBlank()) {
        return CollectUiState.NO_ESCROW_CONFIGURED
    }
    if (!isReceiveOnly && walletAddress == null) {
        return CollectUiState.NO_WALLET
    }
    return null
}

private fun resolveCollectTransactionState(
    txStatus: TxStatus,
    isBroadcasting: Boolean,
): CollectUiState {
    if (txStatus is TxStatus.Success) return CollectUiState.SUCCESS
    if (txStatus == TxStatus.Executing) return CollectUiState.EXECUTING
    if (txStatus is TxStatus.Error) return CollectUiState.ERROR
    if (!isBroadcasting && txStatus == TxStatus.Idle) return CollectUiState.AMOUNT_INPUT
    if (isBroadcasting && txStatus == TxStatus.WaitingForSignature) return CollectUiState.BROADCASTING_REQUEST
    return CollectUiState.UNKNOWN
}
