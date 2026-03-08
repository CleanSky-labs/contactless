package io.cleansky.contactless

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.crypto.TransactionSigner
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.PaymasterConfig
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.PublicBundler
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.service.PrivacyPaymentExecutor
import io.cleansky.contactless.ui.AppColors
import java.math.BigDecimal
import java.math.BigInteger

@Composable
internal fun NoWalletView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.pay_no_wallet),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_no_wallet_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun WaitingForRequestView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = AppColors.PayPrimary.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.pay_ready),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_ready_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PaymentRequestView(
    request: PaymentRequest,
    balance: BigInteger?,
    isLoadingBalance: Boolean,
    payerPrivacyEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val chainConfig = ChainConfig.getByChainId(request.chainId)
    val chainName = chainConfig?.name ?: "Chain ${request.chainId}"
    val symbol = chainConfig?.symbol ?: "TOKEN"
    val decimals = chainConfig?.decimals ?: 6

    val requestAmount = parseBigIntegerOrZero(request.amount)
    val hasInsufficientBalance = balance != null && balance < requestAmount
    val missingAmount = if (hasInsufficientBalance) requestAmount - balance!! else BigInteger.ZERO
    val amountInEth = parseAmountInEth(request.amount)
    val isExpired = request.isExpired()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BalanceHeader(
                isLoadingBalance = isLoadingBalance,
                balance = balance,
                decimals = decimals,
                symbol = symbol,
                hasInsufficientBalance = hasInsufficientBalance,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.pay_request_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Gray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = request.getAmountFormatted(),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayText,
            )

            Text(
                text = symbol,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayPrimary,
            )

            if (hasInsufficientBalance) {
                InsufficientBalanceWarning(
                    missingAmount = missingAmount,
                    decimals = decimals,
                    symbol = symbol,
                )
            }

            ScamWarnings(
                amountInEth = amountInEth,
                amountFormatted = request.getAmountFormatted(),
                symbol = symbol,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.pay_network, chainName),
                fontSize = 14.sp,
                color = AppColors.Gray,
            )

            PrivacyIndicators(
                stealthEnabled = !request.stealthMetaAddress.isNullOrEmpty(),
                payerPrivacyEnabled = payerPrivacyEnabled,
            )

            if (isExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pay_expired),
                    fontSize = 14.sp,
                    color = AppColors.Error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PaymentActionButtons(
                isConfirmEnabled = !isExpired && !hasInsufficientBalance,
                onCancel = onCancel,
                onConfirm = onConfirm,
            )
        }
    }
}

private fun parseBigIntegerOrZero(value: String): BigInteger {
    return try {
        BigInteger(value)
    } catch (_: Exception) {
        BigInteger.ZERO
    }
}

private fun parseAmountInEth(rawAmount: String): BigDecimal {
    return try {
        BigDecimal(rawAmount).divide(BigDecimal("1000000000000000000"))
    } catch (_: Exception) {
        BigDecimal.ZERO
    }
}

internal suspend fun handleConfirmPayment(
    request: PaymentRequest,
    walletManager: SecureWalletManager,
    activity: FragmentActivity,
    payerPrivacyEnabled: Boolean,
    apiKey: String,
    privacyPayerRepository: PrivacyPayerRepository,
    onSign: (SignedTransaction) -> Unit,
    onPrivacyPaymentComplete: (TxStatus) -> Unit,
    onAuthenticatingChange: (Boolean) -> Unit,
    onProcessingPrivatePaymentChange: (Boolean) -> Unit,
) {
    onAuthenticatingChange(true)
    when (val credResult = walletManager.getCredentials(activity)) {
        is SecureWalletManager.CredentialsResult.Success -> {
            val signedTx = TransactionSigner.signPayment(request, credResult.credentials)
            val chainConfig = ChainConfig.getByChainId(request.chainId)

            if (shouldUsePrivacyPayment(payerPrivacyEnabled, apiKey, chainConfig)) {
                onAuthenticatingChange(false)
                onProcessingPrivatePaymentChange(true)
                onPrivacyPaymentComplete(TxStatus.Executing)

                val privacyExecutor =
                    PrivacyPaymentExecutor(
                        chainConfig = chainConfig!!,
                        mainCredentials = credResult.credentials,
                        privacyPayerRepository = privacyPayerRepository,
                        paymasterConfig = if (apiKey.isNotEmpty()) PaymasterConfig.PIMLICO else null,
                        apiKey = apiKey.ifEmpty { null },
                    )

                val privacyResult = privacyExecutor.executePrivatePayment(signedTx)
                onProcessingPrivatePaymentChange(false)
                onPrivacyPaymentComplete(mapPrivacyExecutionResult(privacyResult))
            } else {
                onAuthenticatingChange(false)
                onSign(signedTx)
            }
        }

        is SecureWalletManager.CredentialsResult.Cancelled -> {
            onAuthenticatingChange(false)
        }

        else -> {
            onAuthenticatingChange(false)
        }
    }
}

private fun shouldUsePrivacyPayment(
    payerPrivacyEnabled: Boolean,
    apiKey: String,
    chainConfig: ChainConfig?,
): Boolean {
    if (!payerPrivacyEnabled || chainConfig == null) return false
    val hasPublicBundler = PublicBundler.hasPublicBundler(chainConfig.chainId)
    return apiKey.isNotEmpty() || hasPublicBundler
}

private fun mapPrivacyExecutionResult(result: PrivacyPaymentExecutor.PrivacyExecutionResult): TxStatus {
    return when (result) {
        is PrivacyPaymentExecutor.PrivacyExecutionResult.Success -> TxStatus.Success(result.txHash)
        is PrivacyPaymentExecutor.PrivacyExecutionResult.Pending -> TxStatus.Success(result.userOpHash)
        is PrivacyPaymentExecutor.PrivacyExecutionResult.Error -> TxStatus.Error(result.message)
    }
}
