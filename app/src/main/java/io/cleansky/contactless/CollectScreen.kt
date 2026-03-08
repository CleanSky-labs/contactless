package io.cleansky.contactless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.DefaultTokens
import io.cleansky.contactless.model.DefaultTokens.UnderlyingCurrency
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.ui.ProcessingAnimation
import io.cleansky.contactless.ui.collect.AmountInputView
import io.cleansky.contactless.ui.collect.BroadcastingRequestView
import io.cleansky.contactless.ui.collect.CollectErrorView
import io.cleansky.contactless.ui.collect.CollectSuccessContent
import io.cleansky.contactless.ui.collect.CollectUiState
import io.cleansky.contactless.ui.collect.NoEscrowConfiguredView
import io.cleansky.contactless.ui.collect.NoWalletCollectView
import io.cleansky.contactless.ui.collect.createCollectPaymentRequest
import io.cleansky.contactless.ui.collect.resolveCollectUiState
import io.cleansky.contactless.util.AmountUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigInteger

@Composable
fun CollectScreen(
    selectedChain: ChainConfig,
    walletManager: SecureWalletManager,
    nfcManager: NfcManager,
    @Suppress("UNUSED_PARAMETER") paymentFeedback: PaymentFeedback,
    tokenAllowlistRepository: TokenAllowlistRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    appPreferences: AppPreferences,
    txStatus: TxStatus,
    lastTxAmount: String?,
    lastTxSymbol: String?,
    isBroadcasting: Boolean,
    currentRequest: PaymentRequest?,
    onTxStatusChange: (TxStatus) -> Unit,
    onBroadcastStateChange: (Boolean, PaymentRequest?) -> Unit,
    isReceiveOnly: Boolean = false,
    receiveOnlyEscrow: String = "",
    receiveOnlyMerchantId: String = "",
) {
    var amountText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)
    val stealthEnabled by stealthPaymentRepository.stealthEnabledFlow.collectAsState(initial = false)
    val stealthMetaAddress by stealthPaymentRepository.stealthMetaAddressFlow.collectAsState(initial = null)
    var selectedUnderlying by remember { mutableStateOf<UnderlyingCurrency?>(null) }
    var selectedToken by remember { mutableStateOf<Token?>(null) }

    val resetCollectFlow = {
        amountText = ""
        onBroadcastStateChange(false, null)
        nfcManager.stopBroadcast()
        onTxStatusChange(TxStatus.Idle)
    }

    LaunchedEffect(selectedChain.chainId) {
        tokenAllowlistRepository.initializeDefaultTokensIfNeeded(selectedChain.chainId)

        if (selectedUnderlying == null || selectedToken?.chainId != selectedChain.chainId) {
            val preferredCurrency = DefaultTokens.getPreferredCurrency()
            val availableUnderlyings = DefaultTokens.getAvailableUnderlyings(selectedChain.chainId)
            val defaultUnderlying =
                availableUnderlyings.find { it.code == preferredCurrency }
                    ?: availableUnderlyings.firstOrNull()

            defaultUnderlying?.let { underlying ->
                selectedUnderlying = underlying
                selectedToken = DefaultTokens.getTokensForUnderlying(selectedChain.chainId, underlying).firstOrNull()
            }
        }
    }

    val uiState =
        resolveCollectUiState(
            isReceiveOnly = isReceiveOnly,
            receiveOnlyEscrow = receiveOnlyEscrow,
            walletAddress = walletAddress,
            txStatus = txStatus,
            isBroadcasting = isBroadcasting,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CollectScreenContent(
            uiState = uiState,
            txStatus = txStatus,
            lastTxAmount = lastTxAmount,
            lastTxSymbol = lastTxSymbol,
            selectedChain = selectedChain,
            selectedUnderlying = selectedUnderlying,
            selectedToken = selectedToken,
            amountText = amountText,
            currentRequest = currentRequest,
            stealthEnabled = stealthEnabled,
            stealthMetaAddress = stealthMetaAddress,
            isReceiveOnly = isReceiveOnly,
            receiveOnlyMerchantId = receiveOnlyMerchantId,
            receiveOnlyEscrow = receiveOnlyEscrow,
            walletManager = walletManager,
            appPreferences = appPreferences,
            nfcManager = nfcManager,
            scope = scope,
            onAmountChange = { amountText = it },
            onCurrencySelected = { token, underlying ->
                selectedToken = token
                selectedUnderlying = underlying
            },
            onTxStatusChange = onTxStatusChange,
            onBroadcastStateChange = onBroadcastStateChange,
            onResetCollectFlow = resetCollectFlow,
        )
    }
}

@Composable
private fun CollectScreenContent(
    uiState: CollectUiState,
    txStatus: TxStatus,
    lastTxAmount: String?,
    lastTxSymbol: String?,
    selectedChain: ChainConfig,
    selectedUnderlying: UnderlyingCurrency?,
    selectedToken: Token?,
    amountText: String,
    currentRequest: PaymentRequest?,
    stealthEnabled: Boolean,
    stealthMetaAddress: String?,
    isReceiveOnly: Boolean,
    receiveOnlyMerchantId: String,
    receiveOnlyEscrow: String,
    walletManager: SecureWalletManager,
    appPreferences: AppPreferences,
    nfcManager: NfcManager,
    scope: CoroutineScope,
    onAmountChange: (String) -> Unit,
    onCurrencySelected: (Token, UnderlyingCurrency) -> Unit,
    onTxStatusChange: (TxStatus) -> Unit,
    onBroadcastStateChange: (Boolean, PaymentRequest?) -> Unit,
    onResetCollectFlow: () -> Unit,
) {
    if (uiState == CollectUiState.NO_ESCROW_CONFIGURED) {
        NoEscrowConfiguredView()
        return
    }
    if (uiState == CollectUiState.NO_WALLET) {
        NoWalletCollectView()
        return
    }
    if (uiState == CollectUiState.EXECUTING) {
        ProcessingAnimation(message = stringResource(R.string.collect_processing))
        return
    }
    if (uiState == CollectUiState.SUCCESS) {
        val successState = txStatus as TxStatus.Success
        CollectSuccessContent(
            successHash = successState.txHash,
            lastTxAmount = lastTxAmount,
            lastTxSymbol = lastTxSymbol,
            chain = selectedChain,
            onReset = onResetCollectFlow,
        )
        return
    }
    if (uiState == CollectUiState.ERROR) {
        val errorState = txStatus as TxStatus.Error
        CollectErrorView(
            message = errorState.message,
            onReset = onResetCollectFlow,
        )
        return
    }
    if (uiState == CollectUiState.BROADCASTING_REQUEST) {
        BroadcastingRequestView(
            request = currentRequest,
            selectedToken = selectedToken,
            onCancel = {
                nfcManager.stopBroadcast()
                onBroadcastStateChange(false, null)
                onTxStatusChange(TxStatus.Idle)
            },
        )
        return
    }
    if (uiState != CollectUiState.AMOUNT_INPUT) return

    AmountInputView(
        amountText = amountText,
        selectedUnderlying = selectedUnderlying,
        selectedToken = selectedToken,
        chainId = selectedChain.chainId,
        chainName = selectedChain.name,
        onAmountChange = onAmountChange,
        onCurrencySelected = onCurrencySelected,
        onCollect = {
            val token = selectedToken ?: return@AmountInputView
            val amount = AmountUtils.parseToUnitsOrZero(amountText, token.decimals)
            if (amount <= BigInteger.ZERO) return@AmountInputView

            scope.launch {
                val request =
                    createCollectPaymentRequest(
                        isReceiveOnly = isReceiveOnly,
                        receiveOnlyMerchantId = receiveOnlyMerchantId,
                        receiveOnlyEscrow = receiveOnlyEscrow,
                        walletManager = walletManager,
                        selectedChain = selectedChain,
                        appPreferences = appPreferences,
                        token = token,
                        amount = amount,
                        stealthEnabled = stealthEnabled,
                        stealthMetaAddress = stealthMetaAddress,
                    )
                nfcManager.preparePaymentRequest(request)
                onBroadcastStateChange(true, request)
                onTxStatusChange(TxStatus.WaitingForSignature)
            }
        },
        stealthEnabled = stealthEnabled,
    )
}
