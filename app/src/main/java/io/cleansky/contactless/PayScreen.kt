package io.cleansky.contactless

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.crypto.StealthAddress
import io.cleansky.contactless.crypto.TransactionSigner
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.service.PrivacyPaymentExecutor
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.PaymasterConfig
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.PublicBundler
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigDecimal
import java.math.BigInteger
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.PaymentSuccessAnimation
import io.cleansky.contactless.ui.ProcessingAnimation
import kotlinx.coroutines.launch

@Composable
fun PayScreen(
    pendingRequest: PaymentRequest?,
    txStatus: TxStatus,
    walletManager: SecureWalletManager,
    @Suppress("UNUSED_PARAMETER") nfcManager: NfcManager,
    @Suppress("UNUSED_PARAMETER") paymentFeedback: PaymentFeedback,
    tokenRepository: TokenRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    activity: FragmentActivity,
    @Suppress("UNUSED_PARAMETER") selectedChain: ChainConfig,
    apiKey: String,
    onSign: (SignedTransaction) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onPrivacyPaymentComplete: (TxStatus) -> Unit
) {
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)
    var isAuthenticating by remember { mutableStateOf(false) }
    var tokenBalance by remember { mutableStateOf<BigInteger?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }

    // Payer privacy (v0.5)
    val payerPrivacyEnabled by privacyPayerRepository.privacyEnabledFlow.collectAsState(initial = false)
    var isProcessingPrivatePayment by remember { mutableStateOf(false) }

    // Load balance when request arrives
    LaunchedEffect(pendingRequest, walletAddress) {
        if (pendingRequest != null && walletAddress != null) {
            isLoadingBalance = true
            val chainConfig = ChainConfig.getByChainId(pendingRequest.chainId)
            if (chainConfig != null) {
                tokenBalance = if (pendingRequest.asset == Token.NATIVE_ADDRESS) {
                    tokenRepository.getNativeBalance(walletAddress!!, chainConfig)
                } else {
                    tokenRepository.getTokenBalance(walletAddress!!, pendingRequest.asset, chainConfig)
                }
            }
            isLoadingBalance = false
        } else {
            tokenBalance = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            walletAddress == null -> {
                NoWalletView()
            }
            isAuthenticating -> {
                ProcessingAnimation(message = stringResource(R.string.pay_authenticating))
            }
            pendingRequest == null && txStatus == TxStatus.Idle -> {
                WaitingForRequestView()
            }
            pendingRequest != null && txStatus == TxStatus.Idle -> {
                PaymentRequestView(
                    request = pendingRequest,
                    balance = tokenBalance,
                    isLoadingBalance = isLoadingBalance,
                    payerPrivacyEnabled = payerPrivacyEnabled && apiKey.isNotEmpty(),
                    onConfirm = {
                        scope.launch {
                            isAuthenticating = true
                            when (val credResult = walletManager.getCredentials(activity)) {
                                is SecureWalletManager.CredentialsResult.Success -> {
                                    val signedTx = TransactionSigner.signPayment(
                                        pendingRequest,
                                        credResult.credentials
                                    )

                                    // Check if payer privacy mode is enabled
                                    // Privacy works with API key OR public bundler (testnets)
                                    val chainConfig = ChainConfig.getByChainId(pendingRequest.chainId)
                                    val hasPublicBundler = chainConfig != null &&
                                        PublicBundler.hasPublicBundler(chainConfig.chainId)
                                    val canUsePrivacy = payerPrivacyEnabled &&
                                        (apiKey.isNotEmpty() || hasPublicBundler)

                                    if (canUsePrivacy && chainConfig != null) {
                                        // Execute privacy payment directly
                                        isAuthenticating = false
                                        isProcessingPrivatePayment = true
                                        onPrivacyPaymentComplete(TxStatus.Executing)

                                        val privacyExecutor = PrivacyPaymentExecutor(
                                            chainConfig = chainConfig,
                                            mainCredentials = credResult.credentials,
                                            privacyPayerRepository = privacyPayerRepository,
                                            paymasterConfig = if (apiKey.isNotEmpty()) PaymasterConfig.PIMLICO else null,
                                            apiKey = apiKey.ifEmpty { null }
                                        )

                                        when (val result = privacyExecutor.executePrivatePayment(signedTx)) {
                                            is PrivacyPaymentExecutor.PrivacyExecutionResult.Success -> {
                                                isProcessingPrivatePayment = false
                                                onPrivacyPaymentComplete(TxStatus.Success(result.txHash))
                                            }
                                            is PrivacyPaymentExecutor.PrivacyExecutionResult.Pending -> {
                                                isProcessingPrivatePayment = false
                                                onPrivacyPaymentComplete(TxStatus.Success(result.userOpHash))
                                            }
                                            is PrivacyPaymentExecutor.PrivacyExecutionResult.Error -> {
                                                isProcessingPrivatePayment = false
                                                onPrivacyPaymentComplete(TxStatus.Error(result.message))
                                            }
                                        }
                                    } else {
                                        // Normal flow: sign and return via NFC
                                        isAuthenticating = false
                                        onSign(signedTx)
                                    }
                                }
                                is SecureWalletManager.CredentialsResult.Cancelled -> {
                                    isAuthenticating = false
                                }
                                else -> {
                                    isAuthenticating = false
                                }
                            }
                        }
                    },
                    onCancel = onCancel
                )
            }
            isProcessingPrivatePayment -> {
                ProcessingAnimation(message = stringResource(R.string.payer_privacy_processing))
            }
            txStatus == TxStatus.Signing -> {
                ProcessingAnimation(message = stringResource(R.string.pay_signing))
            }
            txStatus == TxStatus.Broadcasting -> {
                BroadcastingView(onCancel = onCancel)
            }
            txStatus is TxStatus.Success -> {
                val chainConfig = pendingRequest?.let { ChainConfig.getByChainId(it.chainId) }
                PaymentSuccessAnimation(
                    amount = pendingRequest?.getAmountFormatted() ?: "0",
                    symbol = chainConfig?.symbol ?: "USDC"
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "TX: ${txStatus.txHash.take(10)}...${txStatus.txHash.takeLast(8)}",
                    fontSize = 12.sp,
                    color = AppColors.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary)
                ) {
                    Text(stringResource(R.string.pay_new), color = AppColors.White)
                }
            }
            txStatus is TxStatus.Error -> {
                ErrorView(message = txStatus.message, onReset = onReset)
            }
        }
    }
}

@Composable
private fun NoWalletView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.pay_no_wallet),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_no_wallet_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WaitingForRequestView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = AppColors.PayPrimary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.pay_ready),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_ready_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PaymentRequestView(
    request: PaymentRequest,
    balance: BigInteger?,
    isLoadingBalance: Boolean,
    payerPrivacyEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val chainConfig = ChainConfig.getByChainId(request.chainId)
    val chainName = chainConfig?.name ?: "Chain ${request.chainId}"
    val symbol = chainConfig?.symbol ?: "TOKEN"
    val decimals = chainConfig?.decimals ?: 6

    val requestAmount = try { BigInteger(request.amount) } catch (e: Exception) { BigInteger.ZERO }
    val hasInsufficientBalance = balance != null && balance < requestAmount
    val missingAmount = if (hasInsufficientBalance) requestAmount - balance!! else BigInteger.ZERO

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Balance display at top
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hasInsufficientBalance) AppColors.Error.copy(alpha = 0.1f) else AppColors.PayPrimary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.pay_your_balance),
                        fontSize = 12.sp,
                        color = AppColors.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isLoadingBalance) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = AppColors.Gray
                        )
                    } else {
                        Text(
                            text = "${formatBalance(balance, decimals)} $symbol",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.pay_request_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount to pay (large)
            Text(
                text = request.getAmountFormatted(),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayText
            )

            Text(
                text = symbol,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayPrimary
            )

            // Insufficient balance warning
            if (hasInsufficientBalance) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.Error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.pay_insufficient_balance),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.pay_need_more,
                                formatBalance(missingAmount, decimals),
                                symbol
                            ),
                            fontSize = 14.sp,
                            color = AppColors.Error
                        )
                    }
                }
            }

            // Scam protection warnings
            val amountInEth = try {
                BigDecimal(request.amount).divide(BigDecimal("1000000000000000000"))
            } catch (e: Exception) { BigDecimal.ZERO }

            // Dust attack warning (very small amount)
            if (amountInEth < BigDecimal("0.0001") && amountInEth > BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.Warning.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ " + stringResource(R.string.scam_dust_attack_warning),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Warning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.scam_dust_attack_desc, request.getAmountFormatted(), symbol),
                            fontSize = 12.sp,
                            color = AppColors.Warning
                        )
                    }
                }
            }

            // Large amount warning
            if (amountInEth >= BigDecimal("5.0")) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.Warning.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ " + stringResource(R.string.scam_receive_large_warning),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Warning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.scam_receive_large_desc, request.getAmountFormatted(), symbol),
                            fontSize = 12.sp,
                            color = AppColors.Warning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.pay_network, chainName),
                fontSize = 14.sp,
                color = AppColors.Gray
            )

            // Stealth payment indicator (v0.4)
            if (!request.stealthMetaAddress.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.Success.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.stealth_payment_note),
                            fontSize = 12.sp,
                            color = AppColors.Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Payer privacy indicator (v0.5)
            if (payerPrivacyEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.PayPrimary.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AppColors.PayPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.payer_privacy_note),
                            fontSize = 12.sp,
                            color = AppColors.PayPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (request.isExpired()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pay_expired),
                    fontSize = 14.sp,
                    color = AppColors.Error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pay_cancel))
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !request.isExpired() && !hasInsufficientBalance,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppColors.PayPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pay_confirm), color = AppColors.White)
                }
            }
        }
    }
}

private fun formatBalance(amount: BigInteger?, decimals: Int): String {
    return NumberFormatter.formatCurrency(amount, decimals)
}

@Composable
private fun BroadcastingView(onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = AppColors.PayPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pay_tx_signed),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_tx_signed_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        LogoLoadingIndicator()
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel), color = AppColors.Error)
        }
    }
}

@Composable
private fun ErrorView(message: String, onReset: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppColors.Error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.error),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary)
        ) {
            Text(stringResource(R.string.retry), color = AppColors.White)
        }
    }
}
