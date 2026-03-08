package io.cleansky.contactless.ui.collect

import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.Token
import java.math.BigInteger

internal suspend fun createCollectPaymentRequest(
    isReceiveOnly: Boolean,
    receiveOnlyMerchantId: String,
    receiveOnlyEscrow: String,
    walletManager: SecureWalletManager,
    selectedChain: ChainConfig,
    appPreferences: AppPreferences,
    token: Token,
    amount: BigInteger,
    stealthEnabled: Boolean,
    stealthMetaAddress: String?,
): PaymentRequest {
    val merchantId =
        resolveMerchantId(
            isReceiveOnly = isReceiveOnly,
            receiveOnlyMerchantId = receiveOnlyMerchantId,
            walletManager = walletManager,
        )
    val escrow = if (isReceiveOnly) receiveOnlyEscrow else selectedChain.escrowAddress

    return PaymentRequest.create(
        merchantId = merchantId,
        amount = amount,
        asset = token.address,
        chainId = selectedChain.chainId,
        escrow = escrow,
        merchantDisplayName = appPreferences.getMerchantDisplayName(),
        merchantDomain = appPreferences.getMerchantDomain(),
        stealthMetaAddress = if (!isReceiveOnly && stealthEnabled) stealthMetaAddress else null,
    )
}

private suspend fun resolveMerchantId(
    isReceiveOnly: Boolean,
    receiveOnlyMerchantId: String,
    walletManager: SecureWalletManager,
): String {
    if (isReceiveOnly) {
        return receiveOnlyMerchantId.ifBlank { "0x" + "0".repeat(64) }
    }

    val rawId = walletManager.getMerchantId()
    return "0x" +
        rawId.toByteArray()
            .take(32)
            .joinToString("") { "%02x".format(it) }
            .padEnd(64, '0')
}
