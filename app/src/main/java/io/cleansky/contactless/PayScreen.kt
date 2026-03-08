package io.cleansky.contactless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.PaymentSuccessAnimation
import io.cleansky.contactless.ui.ProcessingAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigInteger

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
    onPrivacyPaymentComplete: (TxStatus) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)
    var isAuthenticating by remember { mutableStateOf(false) }
    var tokenBalance by remember { mutableStateOf<BigInteger?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }
    val payerPrivacyEnabled by privacyPayerRepository.privacyEnabledFlow.collectAsState(initial = false)
    var isProcessingPrivatePayment by remember { mutableStateOf(false) }

    PayBalanceLoader(
        pendingRequest = pendingRequest,
        walletAddress = walletAddress,
        tokenRepository = tokenRepository,
        onLoadingBalanceChange = { isLoadingBalance = it },
        onBalanceChange = { tokenBalance = it },
    )

    val uiState =
        resolvePayUiState(
            walletAddress = walletAddress,
            pendingRequest = pendingRequest,
            txStatus = txStatus,
            isAuthenticating = isAuthenticating,
            isProcessingPrivatePayment = isProcessingPrivatePayment,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PayScreenContent(
            uiState = uiState,
            pendingRequest = pendingRequest,
            tokenBalance = tokenBalance,
            isLoadingBalance = isLoadingBalance,
            payerPrivacyEnabled = payerPrivacyEnabled,
            apiKey = apiKey,
            walletManager = walletManager,
            activity = activity,
            privacyPayerRepository = privacyPayerRepository,
            onSign = onSign,
            onCancel = onCancel,
            onReset = onReset,
            onPrivacyPaymentComplete = onPrivacyPaymentComplete,
            onAuthenticatingChange = { isAuthenticating = it },
            onProcessingPrivatePaymentChange = { isProcessingPrivatePayment = it },
            scope = scope,
        )
    }
}

private sealed interface PayUiState {
    data object NoWallet : PayUiState

    data object Authenticating : PayUiState

    data object WaitingForRequest : PayUiState

    data object RequestReady : PayUiState

    data object ProcessingPrivate : PayUiState

    data object Signing : PayUiState

    data object Broadcasting : PayUiState

    data class Success(val txHash: String) : PayUiState

    data class Error(val message: String) : PayUiState
}

@Composable
private fun PayBalanceLoader(
    pendingRequest: PaymentRequest?,
    walletAddress: String?,
    tokenRepository: TokenRepository,
    onLoadingBalanceChange: (Boolean) -> Unit,
    onBalanceChange: (BigInteger?) -> Unit,
) {
    LaunchedEffect(pendingRequest, walletAddress) {
        if (pendingRequest == null || walletAddress == null) {
            onBalanceChange(null)
            return@LaunchedEffect
        }

        onLoadingBalanceChange(true)
        val chainConfig = ChainConfig.getByChainId(pendingRequest.chainId)
        if (chainConfig != null) {
            val balance =
                if (pendingRequest.asset == io.cleansky.contactless.model.Token.NATIVE_ADDRESS) {
                    tokenRepository.getNativeBalance(walletAddress, chainConfig)
                } else {
                    tokenRepository.getTokenBalance(walletAddress, pendingRequest.asset, chainConfig)
                }
            onBalanceChange(balance)
        }
        onLoadingBalanceChange(false)
    }
}

private fun resolvePayUiState(
    walletAddress: String?,
    pendingRequest: PaymentRequest?,
    txStatus: TxStatus,
    isAuthenticating: Boolean,
    isProcessingPrivatePayment: Boolean,
): PayUiState {
    if (walletAddress == null) return PayUiState.NoWallet
    if (isAuthenticating) return PayUiState.Authenticating
    resolveIdlePayUiState(pendingRequest, txStatus)?.let { return it }
    if (isProcessingPrivatePayment) return PayUiState.ProcessingPrivate
    resolveTxDrivenPayUiState(txStatus)?.let { return it }
    return PayUiState.WaitingForRequest
}

@Composable
private fun PayScreenContent(
    uiState: PayUiState,
    pendingRequest: PaymentRequest?,
    tokenBalance: BigInteger?,
    isLoadingBalance: Boolean,
    payerPrivacyEnabled: Boolean,
    apiKey: String,
    walletManager: SecureWalletManager,
    activity: FragmentActivity,
    privacyPayerRepository: PrivacyPayerRepository,
    onSign: (SignedTransaction) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onPrivacyPaymentComplete: (TxStatus) -> Unit,
    onAuthenticatingChange: (Boolean) -> Unit,
    onProcessingPrivatePaymentChange: (Boolean) -> Unit,
    scope: CoroutineScope,
) {
    processingMessageFor(uiState)?.let { message ->
        ProcessingAnimation(message = message)
        return
    }

    if (uiState == PayUiState.NoWallet) {
        NoWalletView()
        return
    }

    if (uiState == PayUiState.WaitingForRequest) {
        WaitingForRequestView()
        return
    }

    if (uiState == PayUiState.Broadcasting) {
        BroadcastingView(onCancel = onCancel)
        return
    }

    if (uiState is PayUiState.Success) {
        PaySuccessContent(
            txHash = uiState.txHash,
            pendingRequest = pendingRequest,
            onReset = onReset,
        )
        return
    }

    if (uiState is PayUiState.Error) {
        ErrorView(message = uiState.message, onReset = onReset)
        return
    }

    if (uiState == PayUiState.RequestReady) {
        pendingRequest?.let { request ->
            PayRequestReadyContent(
                request = request,
                tokenBalance = tokenBalance,
                isLoadingBalance = isLoadingBalance,
                payerPrivacyEnabled = payerPrivacyEnabled,
                apiKey = apiKey,
                walletManager = walletManager,
                activity = activity,
                privacyPayerRepository = privacyPayerRepository,
                onSign = onSign,
                onPrivacyPaymentComplete = onPrivacyPaymentComplete,
                onAuthenticatingChange = onAuthenticatingChange,
                onProcessingPrivatePaymentChange = onProcessingPrivatePaymentChange,
                onCancel = onCancel,
                scope = scope,
            )
        }
    }
}

@Composable
private fun PayRequestReadyContent(
    request: PaymentRequest,
    tokenBalance: BigInteger?,
    isLoadingBalance: Boolean,
    payerPrivacyEnabled: Boolean,
    apiKey: String,
    walletManager: SecureWalletManager,
    activity: FragmentActivity,
    privacyPayerRepository: PrivacyPayerRepository,
    onSign: (SignedTransaction) -> Unit,
    onPrivacyPaymentComplete: (TxStatus) -> Unit,
    onAuthenticatingChange: (Boolean) -> Unit,
    onProcessingPrivatePaymentChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    scope: CoroutineScope,
) {
    PaymentRequestView(
        request = request,
        balance = tokenBalance,
        isLoadingBalance = isLoadingBalance,
        payerPrivacyEnabled = payerPrivacyEnabled && apiKey.isNotEmpty(),
        onConfirm = {
            scope.launch {
                handleConfirmPayment(
                    request = request,
                    walletManager = walletManager,
                    activity = activity,
                    payerPrivacyEnabled = payerPrivacyEnabled,
                    apiKey = apiKey,
                    privacyPayerRepository = privacyPayerRepository,
                    onSign = onSign,
                    onPrivacyPaymentComplete = onPrivacyPaymentComplete,
                    onAuthenticatingChange = onAuthenticatingChange,
                    onProcessingPrivatePaymentChange = onProcessingPrivatePaymentChange,
                )
            }
        },
        onCancel = onCancel,
    )
}

@Composable
private fun PaySuccessContent(
    txHash: String,
    pendingRequest: PaymentRequest?,
    onReset: () -> Unit,
) {
    val chainConfig = pendingRequest?.let { ChainConfig.getByChainId(it.chainId) }
    PaymentSuccessAnimation(
        amount = pendingRequest?.getAmountFormatted() ?: "0",
        symbol = chainConfig?.symbol ?: "USDC",
    )
    Spacer(modifier = Modifier.height(32.dp))
    Text(
        text = "TX: ${txHash.take(10)}...${txHash.takeLast(8)}",
        fontSize = 12.sp,
        color = AppColors.Gray,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
    ) {
        Text(stringResource(R.string.pay_new), color = AppColors.White)
    }
}

private fun resolveIdlePayUiState(
    pendingRequest: PaymentRequest?,
    txStatus: TxStatus,
): PayUiState? {
    if (pendingRequest == null && txStatus == TxStatus.Idle) return PayUiState.WaitingForRequest
    if (pendingRequest != null && txStatus == TxStatus.Idle) return PayUiState.RequestReady
    return null
}

private fun resolveTxDrivenPayUiState(txStatus: TxStatus): PayUiState? =
    when (txStatus) {
        TxStatus.Signing -> PayUiState.Signing
        TxStatus.Broadcasting -> PayUiState.Broadcasting
        is TxStatus.Success -> PayUiState.Success(txStatus.txHash)
        is TxStatus.Error -> PayUiState.Error(txStatus.message)
        else -> null
    }

@Composable
private fun processingMessageFor(uiState: PayUiState): String? =
    when (uiState) {
        PayUiState.Authenticating -> stringResource(R.string.pay_authenticating)
        PayUiState.ProcessingPrivate -> stringResource(R.string.payer_privacy_processing)
        PayUiState.Signing -> stringResource(R.string.pay_signing)
        else -> null
    }
