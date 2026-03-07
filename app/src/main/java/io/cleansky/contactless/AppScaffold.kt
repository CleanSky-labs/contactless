package io.cleansky.contactless

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.NonceRepository
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.ui.AddressBookScreen
import io.cleansky.contactless.ui.SendToContactScreen
import io.cleansky.contactless.ui.ShareContactScreen
import io.cleansky.contactless.model.*
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.RefundService
import io.cleansky.contactless.service.TransactionExecutor
import io.cleansky.contactless.ui.AppColors
import kotlinx.coroutines.launch

enum class Mode { PAY, COLLECT, RECEIVE_ONLY }
enum class Screen { MAIN, SETTINGS, HISTORY, REFUND, BALANCE, STEALTH_WALLET, ADDRESS_BOOK, SHARE_CONTACT, SEND }

data class AppState(
    val mode: Mode = Mode.PAY,
    val screen: Screen = Screen.MAIN,
    val selectedChain: ChainConfig = ChainConfig.CHAINS.first(),
    val pendingRequest: PaymentRequest? = null,
    val txStatus: TxStatus = TxStatus.Idle,
    val executionMode: ExecutionMode = ExecutionMode.DIRECT,
    val relayerApiKey: String = "",
    val lastTxAmount: String? = null,
    val lastTxSymbol: String? = null,
    val selectedTransaction: Transaction? = null,
    // Collect state - preserved across navigation
    val collectIsBroadcasting: Boolean = false,
    val collectCurrentRequest: PaymentRequest? = null,
    // Receive-only state
    val receiveOnlyEscrow: String = "",
    val receiveOnlyMerchantId: String = ""
)

sealed class TxStatus {
    object Idle : TxStatus()
    object Signing : TxStatus()
    object Broadcasting : TxStatus()
    object WaitingForSignature : TxStatus()
    object Executing : TxStatus()
    data class Success(val txHash: String) : TxStatus()
    data class Error(val message: String) : TxStatus()
}

@Composable
fun AppScaffold(
    activity: FragmentActivity,
    nfcManager: NfcManager,
    walletManager: SecureWalletManager,
    paymentFeedback: PaymentFeedback,
    transactionRepository: TransactionRepository,
    refundService: RefundService,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    nonceRepository: NonceRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    addressBookRepository: AddressBookRepository,
    appPreferences: AppPreferences,
    receivedPaymentRequest: PaymentRequest?,
    receivedSignedTransaction: SignedTransaction?,
    onPaymentRequestConsumed: () -> Unit,
    onSignedTransactionConsumed: () -> Unit
) {
    var state by remember { mutableStateOf(AppState()) }
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    // Load receive-only preferences
    val receiveOnlyEscrow by appPreferences.receiveOnlyEscrowFlow.collectAsState(initial = "")
    val receiveOnlyMerchantId by appPreferences.receiveOnlyMerchantIdFlow.collectAsState(initial = "")
    LaunchedEffect(receiveOnlyEscrow, receiveOnlyMerchantId) {
        state = state.copy(
            receiveOnlyEscrow = receiveOnlyEscrow,
            receiveOnlyMerchantId = receiveOnlyMerchantId
        )
    }

    // Manejar request recibido (modo PAGAR) - Validation per spec v0.2
    LaunchedEffect(receivedPaymentRequest) {
        receivedPaymentRequest?.let { request ->
            if (state.mode == Mode.PAY) {
                // Validate request per spec v0.2
                when (val validation = request.validate()) {
                    is PaymentRequest.ValidationResult.Valid -> {
                        // Check token allowlist
                        val isAllowed = tokenAllowlistRepository.isTokenAllowed(request.asset, request.chainId)
                        if (!isAllowed) {
                            state = state.copy(txStatus = TxStatus.Error("Token no permitido"))
                        } else {
                            state = state.copy(pendingRequest = request)
                        }
                    }
                    is PaymentRequest.ValidationResult.Expired -> {
                        state = state.copy(txStatus = TxStatus.Error("Solicitud expirada"))
                    }
                    is PaymentRequest.ValidationResult.InsufficientTime -> {
                        state = state.copy(txStatus = TxStatus.Error("Tiempo insuficiente para firmar"))
                    }
                    is PaymentRequest.ValidationResult.Invalid -> {
                        state = state.copy(txStatus = TxStatus.Error("Solicitud inválida: ${validation.reason}"))
                    }
                }
            }
            onPaymentRequestConsumed()
        }
    }

    // Manejar transacción firmada recibida (modo COBRAR / RECEIVE_ONLY) - With nonce tracking (spec v0.2)
    LaunchedEffect(receivedSignedTransaction) {
        receivedSignedTransaction?.let { signedTx ->
            if (state.mode == Mode.COLLECT || state.mode == Mode.RECEIVE_ONLY) {
                state = state.copy(
                    txStatus = TxStatus.Executing,
                    lastTxAmount = signedTx.amount,
                    lastTxSymbol = state.selectedChain.symbol
                )

                scope.launch {
                    // Atomically consume nonce before broadcast (spec v0.2)
                    val nonceConsumed = nonceRepository.consumeNonce(
                        nonce = signedTx.nonce,
                        invoiceId = signedTx.invoiceId,
                        expiry = signedTx.expiry
                    )
                    if (!nonceConsumed) {
                        paymentFeedback.onPaymentError()
                        state = state.copy(txStatus = TxStatus.Error("Replay detectado: nonce ya usado"))
                        onSignedTransactionConsumed()
                        return@launch
                    }

                    if (state.mode == Mode.RECEIVE_ONLY) {
                        // Receive-only: use relayer without private key
                        if (state.relayerApiKey.isBlank()) {
                            nonceRepository.releaseNonce(signedTx.nonce, signedTx.invoiceId)
                            paymentFeedback.onPaymentError()
                            state = state.copy(txStatus = TxStatus.Error("API key required"))
                            onSignedTransactionConsumed()
                            return@launch
                        }

                        val executor = TransactionExecutor.forRelayOnly(
                            chainConfig = state.selectedChain,
                            escrowOverride = state.receiveOnlyEscrow,
                            relayerConfig = RelayerConfig.GELATO,
                            apiKey = state.relayerApiKey
                        )

                        when (val result = executor.executePayment(signedTx)) {
                            is TransactionExecutor.ExecutionResult.Success -> {
                                paymentFeedback.onPaymentSuccess()
                                transactionRepository.recordPaymentReceived(
                                    txHash = result.txHash,
                                    amount = signedTx.amount,
                                    asset = signedTx.asset,
                                    chainId = signedTx.chainId,
                                    payerAddress = signedTx.payer,
                                    merchantId = signedTx.merchantId,
                                    invoiceId = signedTx.invoiceId
                                )
                                state = state.copy(txStatus = TxStatus.Success(result.txHash))
                            }
                            is TransactionExecutor.ExecutionResult.Pending -> {
                                paymentFeedback.onPaymentSuccess()
                                transactionRepository.recordPaymentReceived(
                                    txHash = result.taskId,
                                    amount = signedTx.amount,
                                    asset = signedTx.asset,
                                    chainId = signedTx.chainId,
                                    payerAddress = signedTx.payer,
                                    merchantId = signedTx.merchantId,
                                    invoiceId = signedTx.invoiceId
                                )
                                state = state.copy(txStatus = TxStatus.Success(result.taskId))
                            }
                            is TransactionExecutor.ExecutionResult.Error -> {
                                nonceRepository.releaseNonce(signedTx.nonce, signedTx.invoiceId)
                                paymentFeedback.onPaymentError()
                                state = state.copy(txStatus = TxStatus.Error(result.message))
                            }
                        }
                    } else {
                        // Normal COLLECT mode: use wallet credentials
                        val credResult = walletManager.getCredentialsUnsafe()
                        if (credResult != null) {
                            val executor = TransactionExecutor(
                                chainConfig = state.selectedChain,
                                credentials = credResult,
                                executionMode = state.executionMode,
                                relayerConfig = if (state.executionMode == ExecutionMode.RELAYER) RelayerConfig.GELATO else null,
                                paymasterConfig = if (state.executionMode == ExecutionMode.ACCOUNT_ABSTRACTION) PaymasterConfig.PIMLICO else null,
                                apiKey = state.relayerApiKey
                            )

                            when (val result = executor.executePayment(signedTx)) {
                                is TransactionExecutor.ExecutionResult.Success -> {
                                    paymentFeedback.onPaymentSuccess()
                                    transactionRepository.recordPaymentReceived(
                                        txHash = result.txHash,
                                        amount = signedTx.amount,
                                        asset = signedTx.asset,
                                        chainId = signedTx.chainId,
                                        payerAddress = signedTx.payer,
                                        merchantId = signedTx.merchantId,
                                        invoiceId = signedTx.invoiceId
                                    )
                                    state = state.copy(txStatus = TxStatus.Success(result.txHash))
                                }
                                is TransactionExecutor.ExecutionResult.Pending -> {
                                    paymentFeedback.onPaymentSuccess()
                                    transactionRepository.recordPaymentReceived(
                                        txHash = result.taskId,
                                        amount = signedTx.amount,
                                        asset = signedTx.asset,
                                        chainId = signedTx.chainId,
                                        payerAddress = signedTx.payer,
                                        merchantId = signedTx.merchantId,
                                        invoiceId = signedTx.invoiceId
                                    )
                                    state = state.copy(txStatus = TxStatus.Success(result.taskId))
                                }
                                is TransactionExecutor.ExecutionResult.Error -> {
                                    nonceRepository.releaseNonce(signedTx.nonce, signedTx.invoiceId)
                                    paymentFeedback.onPaymentError()
                                    state = state.copy(txStatus = TxStatus.Error(result.message))
                                }
                            }
                        } else {
                            nonceRepository.releaseNonce(signedTx.nonce, signedTx.invoiceId)
                            paymentFeedback.onPaymentError()
                            state = state.copy(txStatus = TxStatus.Error("Wallet no configurada"))
                        }
                    }
                }
            }
            onSignedTransactionConsumed()
        }
    }

    // Colores animados según el modo
    val backgroundColor by animateColorAsState(
        targetValue = backgroundColorFor(state),
        label = "backgroundColor"
    )

    val topBarColor by animateColorAsState(
        targetValue = topBarColorFor(state),
        label = "topBarColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        appBarTitle(state),
                        color = if (state.screen != Screen.MAIN) AppColors.Black else AppColors.White
                    )
                },
                backgroundColor = topBarColor,
                elevation = 0.dp,
                actions = {
                    if (state.screen == Screen.MAIN) {
                        MainTopBarActions(state.mode) { screen ->
                            state = state.copy(screen = screen)
                        }
                    }
                },
                navigationIcon = if (state.screen != Screen.MAIN) {
                    {
                        IconButton(onClick = {
                            state = backNavigationState(state)
                        }) {
                            Icon(
                                imageVector = @Suppress("DEPRECATION") Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                                tint = AppColors.Black
                            )
                        }
                    }
                } else null
            )
        },
        backgroundColor = backgroundColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            when (state.screen) {
                Screen.SETTINGS -> {
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
                            state = state.copy(mode = mode, pendingRequest = null, txStatus = TxStatus.Idle)
                        },
                        onChainChange = { chain ->
                            state = state.copy(selectedChain = chain)
                        },
                        onExecutionModeChange = { mode ->
                            state = state.copy(executionMode = mode)
                        },
                        onApiKeyChange = { key ->
                            state = state.copy(relayerApiKey = key)
                        },
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.HISTORY -> {
                    HistoryScreen(
                        transactionRepository = transactionRepository,
                        addressBookRepository = addressBookRepository,
                        selectedChain = state.selectedChain,
                        onTransactionClick = { tx ->
                            if (tx.canRefund()) {
                                state = state.copy(screen = Screen.REFUND, selectedTransaction = tx)
                            }
                        },
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.REFUND -> {
                    state.selectedTransaction?.let { tx ->
                        RefundScreen(
                            transaction = tx,
                            chainConfig = state.selectedChain,
                            walletManager = walletManager,
                            refundService = refundService,
                            paymentFeedback = paymentFeedback,
                            activity = activity,
                            onBack = {
                                state = state.copy(screen = Screen.HISTORY, selectedTransaction = null)
                            },
                            onSuccess = {
                                state = state.copy(screen = Screen.HISTORY, selectedTransaction = null)
                            }
                        )
                    }
                }
                Screen.BALANCE -> {
                    BalanceScreen(
                        walletAddress = walletAddress,
                        selectedChain = state.selectedChain,
                        tokenRepository = tokenRepository,
                        onManageTokens = {
                            state = state.copy(screen = Screen.SETTINGS)
                        },
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.STEALTH_WALLET -> {
                    StealthWalletScreen(
                        chainConfig = state.selectedChain,
                        walletManager = walletManager,
                        stealthPaymentRepository = stealthPaymentRepository,
                        paymentFeedback = paymentFeedback,
                        activity = activity,
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.ADDRESS_BOOK -> {
                    AddressBookScreen(
                        addressBookRepository = addressBookRepository,
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.SHARE_CONTACT -> {
                    ShareContactScreen(
                        walletManager = walletManager,
                        nfcManager = nfcManager,
                        addressBookRepository = addressBookRepository,
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.SEND -> {
                    SendToContactScreen(
                        walletManager = walletManager,
                        addressBookRepository = addressBookRepository,
                        tokenRepository = tokenRepository,
                        selectedChain = state.selectedChain,
                        paymentFeedback = paymentFeedback,
                        activity = activity,
                        onBack = {
                            state = state.copy(screen = Screen.MAIN)
                        }
                    )
                }
                Screen.MAIN -> {
                    when (state.mode) {
                        Mode.PAY -> PayScreen(
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
                                state = state.copy(txStatus = TxStatus.Broadcasting)
                                nfcManager.prepareSignedTransaction(signedTx)
                            },
                            onCancel = {
                                state = state.copy(pendingRequest = null, txStatus = TxStatus.Idle)
                                nfcManager.stopBroadcast()
                            },
                            onReset = {
                                state = state.copy(pendingRequest = null, txStatus = TxStatus.Idle)
                                nfcManager.stopBroadcast()
                            },
                            onPrivacyPaymentComplete = { result ->
                                when (result) {
                                    is TxStatus.Success -> {
                                        scope.launch { paymentFeedback.onPaymentSuccess() }
                                        state = state.copy(txStatus = result)
                                    }
                                    is TxStatus.Error -> {
                                        scope.launch { paymentFeedback.onPaymentError() }
                                        state = state.copy(txStatus = result)
                                    }
                                    else -> state = state.copy(txStatus = result)
                                }
                            }
                        )
                        Mode.COLLECT -> CollectScreen(
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
                            onTxStatusChange = { status ->
                                state = state.copy(txStatus = status)
                            },
                            onBroadcastStateChange = { broadcasting, request ->
                                state = state.copy(
                                    collectIsBroadcasting = broadcasting,
                                    collectCurrentRequest = request
                                )
                            }
                        )
                        Mode.RECEIVE_ONLY -> CollectScreen(
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
                            onTxStatusChange = { status ->
                                state = state.copy(txStatus = status)
                            },
                            onBroadcastStateChange = { broadcasting, request ->
                                state = state.copy(
                                    collectIsBroadcasting = broadcasting,
                                    collectCurrentRequest = request
                                )
                            },
                            isReceiveOnly = true,
                            receiveOnlyEscrow = state.receiveOnlyEscrow,
                            receiveOnlyMerchantId = state.receiveOnlyMerchantId
                        )
                    }
                }
            }
        }
    }
}

private fun isSecondaryScreen(screen: Screen): Boolean {
    return screen == Screen.SETTINGS || screen == Screen.HISTORY || screen == Screen.REFUND ||
        screen == Screen.BALANCE || screen == Screen.STEALTH_WALLET || screen == Screen.ADDRESS_BOOK ||
        screen == Screen.SHARE_CONTACT || screen == Screen.SEND
}

private fun backgroundColorFor(state: AppState): Color {
    return when {
        isSecondaryScreen(state.screen) -> AppColors.White
        state.mode == Mode.PAY -> AppColors.PayBackground
        else -> AppColors.CollectBackground
    }
}

private fun topBarColorFor(state: AppState): Color {
    return when {
        isSecondaryScreen(state.screen) -> AppColors.White
        state.mode == Mode.PAY -> AppColors.PayPrimary
        else -> AppColors.CollectPrimary
    }
}

@Composable
private fun appBarTitle(state: AppState): String {
    return when (state.screen) {
        Screen.SETTINGS -> stringResource(R.string.nav_settings)
        Screen.HISTORY -> stringResource(R.string.nav_history)
        Screen.REFUND -> stringResource(R.string.nav_refund)
        Screen.BALANCE -> stringResource(R.string.nav_balance)
        Screen.STEALTH_WALLET -> stringResource(R.string.nav_stealth_wallet)
        Screen.ADDRESS_BOOK -> stringResource(R.string.address_book_title)
        Screen.SHARE_CONTACT -> stringResource(R.string.nav_share_contact)
        Screen.SEND -> stringResource(R.string.send_title)
        Screen.MAIN -> when (state.mode) {
            Mode.PAY -> stringResource(R.string.nav_pay)
            Mode.COLLECT -> stringResource(R.string.nav_collect)
            Mode.RECEIVE_ONLY -> stringResource(R.string.nav_receive_only)
        }
    }
}

@Composable
private fun MainTopBarActions(mode: Mode, onNavigate: (Screen) -> Unit) {
    if (mode == Mode.PAY) {
        IconButton(onClick = { onNavigate(Screen.BALANCE) }) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = stringResource(R.string.nav_balance),
                tint = AppColors.White
            )
        }
    }
    if (mode == Mode.COLLECT) {
        IconButton(onClick = { onNavigate(Screen.STEALTH_WALLET) }) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.nav_stealth_wallet),
                tint = AppColors.White
            )
        }
    }
    if (mode != Mode.RECEIVE_ONLY) {
        IconButton(onClick = { onNavigate(Screen.SHARE_CONTACT) }) {
            Icon(
                Icons.Default.ContactPhone,
                contentDescription = stringResource(R.string.nav_share_contact),
                tint = AppColors.White
            )
        }
        IconButton(onClick = { onNavigate(Screen.SEND) }) {
            Icon(
                @Suppress("DEPRECATION") Icons.Default.Send,
                contentDescription = stringResource(R.string.nav_send),
                tint = AppColors.White
            )
        }
        IconButton(onClick = { onNavigate(Screen.ADDRESS_BOOK) }) {
            Icon(
                Icons.Default.Contacts,
                contentDescription = stringResource(R.string.address_book_title),
                tint = AppColors.White
            )
        }
    }
    IconButton(onClick = { onNavigate(Screen.HISTORY) }) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = stringResource(R.string.nav_history),
            tint = AppColors.White
        )
    }
    IconButton(onClick = { onNavigate(Screen.SETTINGS) }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            tint = AppColors.White
        )
    }
}

private fun backNavigationState(state: AppState): AppState {
    return when (state.screen) {
        Screen.REFUND -> state.copy(screen = Screen.HISTORY, selectedTransaction = null)
        else -> state.copy(screen = Screen.MAIN)
    }
}
