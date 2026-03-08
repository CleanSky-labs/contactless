package io.cleansky.contactless.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.AppState
import io.cleansky.contactless.BalanceScreen
import io.cleansky.contactless.CollectScreen
import io.cleansky.contactless.HistoryScreen
import io.cleansky.contactless.Mode
import io.cleansky.contactless.PayScreen
import io.cleansky.contactless.R
import io.cleansky.contactless.RefundScreen
import io.cleansky.contactless.Screen
import io.cleansky.contactless.SettingsScreen
import io.cleansky.contactless.StealthWalletScreen
import io.cleansky.contactless.TxStatus
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.RefundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.AppScreenContent(
    state: AppState,
    walletAddress: String?,
    activity: FragmentActivity,
    nfcManager: NfcManager,
    walletManager: SecureWalletManager,
    paymentFeedback: PaymentFeedback,
    transactionRepository: TransactionRepository,
    refundService: RefundService,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    addressBookRepository: AddressBookRepository,
    appPreferences: AppPreferences,
    onStateChange: (AppState) -> Unit,
) {
    val scope = rememberCoroutineScope()

    if (state.screen == Screen.MAIN) {
        MainModeContent(
            state = state,
            activity = activity,
            nfcManager = nfcManager,
            walletManager = walletManager,
            paymentFeedback = paymentFeedback,
            tokenAllowlistRepository = tokenAllowlistRepository,
            tokenRepository = tokenRepository,
            stealthPaymentRepository = stealthPaymentRepository,
            privacyPayerRepository = privacyPayerRepository,
            appPreferences = appPreferences,
            scope = scope,
            onStateChange = onStateChange,
        )
        return
    }

    SecondaryScreenContent(
        state = state,
        walletAddress = walletAddress,
        activity = activity,
        nfcManager = nfcManager,
        walletManager = walletManager,
        paymentFeedback = paymentFeedback,
        transactionRepository = transactionRepository,
        refundService = refundService,
        tokenAllowlistRepository = tokenAllowlistRepository,
        tokenRepository = tokenRepository,
        stealthPaymentRepository = stealthPaymentRepository,
        privacyPayerRepository = privacyPayerRepository,
        addressBookRepository = addressBookRepository,
        appPreferences = appPreferences,
        onStateChange = onStateChange,
    )
}

@Composable
private fun SecondaryScreenContent(
    state: AppState,
    walletAddress: String?,
    activity: FragmentActivity,
    nfcManager: NfcManager,
    walletManager: SecureWalletManager,
    paymentFeedback: PaymentFeedback,
    transactionRepository: TransactionRepository,
    refundService: RefundService,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    addressBookRepository: AddressBookRepository,
    appPreferences: AppPreferences,
    onStateChange: (AppState) -> Unit,
) {
    if (
        renderSettingsAndHistoryScreens(
            state = state,
            walletAddress = walletAddress,
            activity = activity,
            walletManager = walletManager,
            paymentFeedback = paymentFeedback,
            transactionRepository = transactionRepository,
            refundService = refundService,
            tokenAllowlistRepository = tokenAllowlistRepository,
            tokenRepository = tokenRepository,
            stealthPaymentRepository = stealthPaymentRepository,
            privacyPayerRepository = privacyPayerRepository,
            appPreferences = appPreferences,
            addressBookRepository = addressBookRepository,
            onStateChange = onStateChange,
        )
    ) {
        return
    }

    when (state.screen) {
        Screen.STEALTH_WALLET ->
            StealthWalletScreen(
                chainConfig = state.selectedChain,
                walletManager = walletManager,
                stealthPaymentRepository = stealthPaymentRepository,
                paymentFeedback = paymentFeedback,
                activity = activity,
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.ADDRESS_BOOK ->
            AddressBookScreen(
                addressBookRepository = addressBookRepository,
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.SHARE_CONTACT ->
            ShareContactScreen(
                walletManager = walletManager,
                nfcManager = nfcManager,
                addressBookRepository = addressBookRepository,
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.SEND ->
            SendToContactScreen(
                walletManager = walletManager,
                addressBookRepository = addressBookRepository,
                tokenRepository = tokenRepository,
                selectedChain = state.selectedChain,
                paymentFeedback = paymentFeedback,
                activity = activity,
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.SETTINGS, Screen.HISTORY, Screen.REFUND, Screen.BALANCE, Screen.MAIN -> Unit
    }
}

@Composable
private fun renderSettingsAndHistoryScreens(
    state: AppState,
    walletAddress: String?,
    activity: FragmentActivity,
    walletManager: SecureWalletManager,
    paymentFeedback: PaymentFeedback,
    transactionRepository: TransactionRepository,
    refundService: RefundService,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    appPreferences: AppPreferences,
    addressBookRepository: AddressBookRepository,
    onStateChange: (AppState) -> Unit,
): Boolean {
    when (state.screen) {
        Screen.SETTINGS ->
            SettingsScreen(
                currentMode = state.mode,
                selectedChain = state.selectedChain,
                walletAddress = walletAddress,
                walletManager = walletManager,
                executionMode = state.executionMode,
                relayerApiKey = state.relayerApiKey,
                activity = activity,
                tokenAllowlistRepository = tokenAllowlistRepository,
                tokenRepository = tokenRepository,
                stealthPaymentRepository = stealthPaymentRepository,
                privacyPayerRepository = privacyPayerRepository,
                appPreferences = appPreferences,
                onModeChange = { mode ->
                    onStateChange(state.copy(mode = mode, pendingRequest = null, txStatus = TxStatus.Idle))
                },
                onChainChange = { chain -> onStateChange(state.copy(selectedChain = chain)) },
                onExecutionModeChange = { mode -> onStateChange(state.copy(executionMode = mode)) },
                onApiKeyChange = { key -> onStateChange(state.copy(relayerApiKey = key)) },
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.HISTORY ->
            HistoryScreen(
                transactionRepository = transactionRepository,
                addressBookRepository = addressBookRepository,
                selectedChain = state.selectedChain,
                onTransactionClick = { tx ->
                    if (tx.canRefund()) {
                        onStateChange(state.copy(screen = Screen.REFUND, selectedTransaction = tx))
                    }
                },
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        Screen.REFUND ->
            state.selectedTransaction?.let { tx ->
                RefundScreen(
                    transaction = tx,
                    chainConfig = state.selectedChain,
                    walletManager = walletManager,
                    refundService = refundService,
                    paymentFeedback = paymentFeedback,
                    activity = activity,
                    onBack = { onStateChange(state.copy(screen = Screen.HISTORY, selectedTransaction = null)) },
                    onSuccess = { onStateChange(state.copy(screen = Screen.HISTORY, selectedTransaction = null)) },
                )
            }
        Screen.BALANCE ->
            BalanceScreen(
                walletAddress = walletAddress,
                selectedChain = state.selectedChain,
                tokenRepository = tokenRepository,
                onManageTokens = { onStateChange(state.copy(screen = Screen.SETTINGS)) },
                onBack = { onStateChange(state.copy(screen = Screen.MAIN)) },
            )
        else -> return false
    }
    return true
}

@Composable
private fun MainModeContent(
    state: AppState,
    activity: FragmentActivity,
    nfcManager: NfcManager,
    walletManager: SecureWalletManager,
    paymentFeedback: PaymentFeedback,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    appPreferences: AppPreferences,
    scope: CoroutineScope,
    onStateChange: (AppState) -> Unit,
) {
    when (state.mode) {
        Mode.PAY ->
            PayScreen(
                pendingRequest = state.pendingRequest,
                txStatus = state.txStatus,
                walletManager = walletManager,
                nfcManager = nfcManager,
                paymentFeedback = paymentFeedback,
                tokenRepository = tokenRepository,
                privacyPayerRepository = privacyPayerRepository,
                activity = activity,
                selectedChain = state.selectedChain,
                apiKey = state.relayerApiKey,
                onSign = { signedTx ->
                    onStateChange(state.copy(txStatus = TxStatus.Broadcasting))
                    nfcManager.prepareSignedTransaction(signedTx)
                },
                onCancel = {
                    onStateChange(state.copy(pendingRequest = null, txStatus = TxStatus.Idle))
                    nfcManager.stopBroadcast()
                },
                onReset = {
                    onStateChange(state.copy(pendingRequest = null, txStatus = TxStatus.Idle))
                    nfcManager.stopBroadcast()
                },
                onPrivacyPaymentComplete = { result ->
                    when (result) {
                        is TxStatus.Success -> {
                            scope.launch { paymentFeedback.onPaymentSuccess() }
                            onStateChange(state.copy(txStatus = result))
                        }
                        is TxStatus.Error -> {
                            scope.launch { paymentFeedback.onPaymentError() }
                            onStateChange(state.copy(txStatus = result))
                        }
                        else -> onStateChange(state.copy(txStatus = result))
                    }
                },
            )
        Mode.COLLECT ->
            CollectScreen(
                selectedChain = state.selectedChain,
                walletManager = walletManager,
                nfcManager = nfcManager,
                paymentFeedback = paymentFeedback,
                tokenAllowlistRepository = tokenAllowlistRepository,
                stealthPaymentRepository = stealthPaymentRepository,
                appPreferences = appPreferences,
                txStatus = state.txStatus,
                lastTxAmount = state.lastTxAmount,
                lastTxSymbol = state.lastTxSymbol,
                isBroadcasting = state.collectIsBroadcasting,
                currentRequest = state.collectCurrentRequest,
                onTxStatusChange = { status -> onStateChange(state.copy(txStatus = status)) },
                onBroadcastStateChange = { broadcasting, request ->
                    onStateChange(
                        state.copy(
                            collectIsBroadcasting = broadcasting,
                            collectCurrentRequest = request,
                        ),
                    )
                },
            )
        Mode.RECEIVE_ONLY ->
            CollectScreen(
                selectedChain = state.selectedChain,
                walletManager = walletManager,
                nfcManager = nfcManager,
                paymentFeedback = paymentFeedback,
                tokenAllowlistRepository = tokenAllowlistRepository,
                stealthPaymentRepository = stealthPaymentRepository,
                appPreferences = appPreferences,
                txStatus = state.txStatus,
                lastTxAmount = state.lastTxAmount,
                lastTxSymbol = state.lastTxSymbol,
                isBroadcasting = state.collectIsBroadcasting,
                currentRequest = state.collectCurrentRequest,
                onTxStatusChange = { status -> onStateChange(state.copy(txStatus = status)) },
                onBroadcastStateChange = { broadcasting, request ->
                    onStateChange(
                        state.copy(
                            collectIsBroadcasting = broadcasting,
                            collectCurrentRequest = request,
                        ),
                    )
                },
                isReceiveOnly = true,
                receiveOnlyEscrow = state.receiveOnlyEscrow,
                receiveOnlyMerchantId = state.receiveOnlyMerchantId,
            )
    }
}

internal fun backgroundColorFor(state: AppState): Color {
    return when {
        isSecondaryScreen(state.screen) -> AppColors.White
        state.mode == Mode.PAY -> AppColors.PayBackground
        else -> AppColors.CollectBackground
    }
}

internal fun topBarColorFor(state: AppState): Color {
    return when {
        isSecondaryScreen(state.screen) -> AppColors.White
        state.mode == Mode.PAY -> AppColors.PayPrimary
        else -> AppColors.CollectPrimary
    }
}

@Composable
internal fun appBarTitle(state: AppState): String {
    val secondaryTitleRes =
        when (state.screen) {
            Screen.SETTINGS -> R.string.nav_settings
            Screen.HISTORY -> R.string.nav_history
            Screen.REFUND -> R.string.nav_refund
            Screen.BALANCE -> R.string.nav_balance
            Screen.STEALTH_WALLET -> R.string.nav_stealth_wallet
            Screen.ADDRESS_BOOK -> R.string.address_book_title
            Screen.SHARE_CONTACT -> R.string.nav_share_contact
            Screen.SEND -> R.string.send_title
            Screen.MAIN -> null
        }

    if (secondaryTitleRes != null) {
        return stringResource(secondaryTitleRes)
    }
    return mainModeTitle(state.mode)
}

@Composable
private fun mainModeTitle(mode: Mode): String {
    val titleRes =
        when (mode) {
            Mode.PAY -> R.string.nav_pay
            Mode.COLLECT -> R.string.nav_collect
            Mode.RECEIVE_ONLY -> R.string.nav_receive_only
        }
    return stringResource(titleRes)
}

@Composable
internal fun MainTopBarActions(
    mode: Mode,
    onNavigate: (Screen) -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { onNavigate(Screen.HISTORY) }) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = stringResource(R.string.nav_history),
            tint = AppColors.White,
        )
    }
    IconButton(onClick = { onNavigate(Screen.SETTINGS) }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            tint = AppColors.White,
        )
    }
    IconButton(onClick = { showOverflowMenu = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null,
            tint = AppColors.White,
        )
    }
    DropdownMenu(
        expanded = showOverflowMenu,
        onDismissRequest = { showOverflowMenu = false },
    ) {
        if (mode == Mode.PAY) {
            DropdownMenuItem(onClick = {
                showOverflowMenu = false
                onNavigate(Screen.BALANCE)
            }) {
                Text(stringResource(R.string.nav_balance))
            }
        }
        if (mode == Mode.COLLECT) {
            DropdownMenuItem(onClick = {
                showOverflowMenu = false
                onNavigate(Screen.STEALTH_WALLET)
            }) {
                Text(stringResource(R.string.nav_stealth_wallet))
            }
        }
        if (mode != Mode.RECEIVE_ONLY) {
            DropdownMenuItem(onClick = {
                showOverflowMenu = false
                onNavigate(Screen.SHARE_CONTACT)
            }) {
                Text(stringResource(R.string.nav_share_contact))
            }
            DropdownMenuItem(onClick = {
                showOverflowMenu = false
                onNavigate(Screen.SEND)
            }) {
                Text(stringResource(R.string.nav_send))
            }
            DropdownMenuItem(onClick = {
                showOverflowMenu = false
                onNavigate(Screen.ADDRESS_BOOK)
            }) {
                Text(stringResource(R.string.address_book_title))
            }
        }
    }
}

internal fun backNavigationState(state: AppState): AppState {
    return when (state.screen) {
        Screen.REFUND -> state.copy(screen = Screen.HISTORY, selectedTransaction = null)
        else -> state.copy(screen = Screen.MAIN)
    }
}

private fun isSecondaryScreen(screen: Screen): Boolean {
    return screen == Screen.SETTINGS || screen == Screen.HISTORY || screen == Screen.REFUND ||
        screen == Screen.BALANCE || screen == Screen.STEALTH_WALLET || screen == Screen.ADDRESS_BOOK ||
        screen == Screen.SHARE_CONTACT || screen == Screen.SEND
}
