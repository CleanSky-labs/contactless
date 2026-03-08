package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.StealthWalletService
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.stealthwallet.ConsolidateStealthBottomSheet
import io.cleansky.contactless.ui.stealthwallet.SpendStealthBottomSheet
import io.cleansky.contactless.ui.stealthwallet.StealthWalletActions
import io.cleansky.contactless.ui.stealthwallet.StealthWalletMainContent
import kotlinx.coroutines.launch
import java.math.BigInteger

@Composable
fun StealthWalletScreen(
    chainConfig: ChainConfig,
    walletManager: SecureWalletManager,
    stealthPaymentRepository: StealthPaymentRepository,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stealthActions =
        remember(walletManager, stealthPaymentRepository, paymentFeedback) {
            StealthWalletActions(
                walletManager = walletManager,
                stealthPaymentRepository = stealthPaymentRepository,
                paymentFeedback = paymentFeedback,
            )
        }
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    var isLoading by remember { mutableStateOf(true) }
    var balance by remember { mutableStateOf<StealthWalletService.StealthWalletBalance?>(null) }
    var showSpendDialog by remember { mutableStateOf(false) }
    var recipientAddress by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showConsolidateDialog by remember { mutableStateOf(false) }

    fun loadBalance() {
        scope.launch {
            isLoading = true
            val creds = walletManager.getCredentialsUnsafe()
            if (creds != null) {
                val service = StealthWalletService(chainConfig, stealthPaymentRepository, creds)
                balance = service.getWalletBalance()
            }
            isLoading = false
        }
    }

    LaunchedEffect(chainConfig) {
        loadBalance()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppColors.White)
                .padding(16.dp),
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LogoLoadingIndicator()
            }
        } else {
            StealthWalletMainContent(
                chainConfig = chainConfig,
                balance = balance,
                isSending = isSending,
                resultMessage = resultMessage,
                onRefresh = { loadBalance() },
                onOpenSpend = { showSpendDialog = true },
                onOpenConsolidate = { showConsolidateDialog = true },
            )
        }
    }

    if (showSpendDialog) {
        SpendStealthBottomSheet(
            isSending = isSending,
            balanceFormatted = balance?.totalBalanceFormatted ?: "0",
            symbol = chainConfig.symbol,
            recipientAddress = recipientAddress,
            amountText = amountText,
            onRecipientChange = { recipientAddress = it },
            onAmountChange = { amountText = it },
            onSetMax = { amountText = balance?.totalBalanceFormatted ?: "" },
            onDismiss = { if (!isSending) showSpendDialog = false },
            onSubmit = {
                scope.launch {
                    stealthActions.handleSpend(
                        activity = activity,
                        chainConfig = chainConfig,
                        recipientAddress = recipientAddress,
                        amountText = amountText,
                        currentBalance = balance?.totalBalance ?: BigInteger.ZERO,
                        onSendingChange = { isSending = it },
                        onResult = { resultMessage = it },
                        onSuccess = {
                            showSpendDialog = false
                            recipientAddress = ""
                            amountText = ""
                            loadBalance()
                        },
                    )
                }
            },
        )
    }

    if (showConsolidateDialog) {
        ConsolidateStealthBottomSheet(
            walletManager = walletManager,
            isSending = isSending,
            onDismiss = { if (!isSending) showConsolidateDialog = false },
            onConfirm = {
                scope.launch {
                    stealthActions.handleConsolidation(
                        activity = activity,
                        chainConfig = chainConfig,
                        mainWallet = walletAddress,
                        onSendingChange = { isSending = it },
                        onResult = { resultMessage = it },
                        onSuccess = {
                            showConsolidateDialog = false
                            loadBalance()
                        },
                    )
                }
            },
        )
    }
}
