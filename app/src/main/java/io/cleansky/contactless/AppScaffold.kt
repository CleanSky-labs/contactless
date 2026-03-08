package io.cleansky.contactless

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
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
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.RefundService
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.AppScreenContent
import io.cleansky.contactless.ui.MainTopBarActions
import io.cleansky.contactless.ui.appBarTitle
import io.cleansky.contactless.ui.backNavigationState
import io.cleansky.contactless.ui.backgroundColorFor
import io.cleansky.contactless.ui.topBarColorFor

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
    val collectIsBroadcasting: Boolean = false,
    val collectCurrentRequest: PaymentRequest? = null,
    val receiveOnlyEscrow: String = "",
    val receiveOnlyMerchantId: String = "",
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
    onSignedTransactionConsumed: () -> Unit,
) {
    var state by remember { mutableStateOf(AppState()) }
    val stateMachine =
        remember(
            tokenAllowlistRepository,
            nonceRepository,
            paymentFeedback,
            transactionRepository,
            walletManager,
        ) {
            createAppStateMachine(
                tokenAllowlistRepository = tokenAllowlistRepository,
                nonceRepository = nonceRepository,
                paymentFeedback = paymentFeedback,
                transactionRepository = transactionRepository,
                walletManager = walletManager,
            )
        }
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    val receiveOnlyEscrow by appPreferences.receiveOnlyEscrowFlow.collectAsState(initial = "")
    val receiveOnlyMerchantId by appPreferences.receiveOnlyMerchantIdFlow.collectAsState(initial = "")
    LaunchedEffect(receiveOnlyEscrow, receiveOnlyMerchantId) {
        state =
            state.copy(
                receiveOnlyEscrow = receiveOnlyEscrow,
                receiveOnlyMerchantId = receiveOnlyMerchantId,
            )
    }

    LaunchedEffect(receivedPaymentRequest) {
        receivedPaymentRequest?.let { request ->
            state = stateMachine.processIncomingPaymentRequest(state, request)
            onPaymentRequestConsumed()
        }
    }

    LaunchedEffect(receivedSignedTransaction) {
        receivedSignedTransaction?.let { signedTx ->
            state = stateMachine.processIncomingSignedTransaction(state, signedTx)
            onSignedTransactionConsumed()
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = backgroundColorFor(state),
        label = "backgroundColor",
    )

    val topBarColor by animateColorAsState(
        targetValue = topBarColorFor(state),
        label = "topBarColor",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        appBarTitle(state),
                        color = if (state.screen != Screen.MAIN) AppColors.Black else AppColors.White,
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
                navigationIcon =
                    if (state.screen != Screen.MAIN) {
                        {
                            IconButton(onClick = {
                                state = backNavigationState(state)
                            }) {
                                Icon(
                                    imageVector = @Suppress("DEPRECATION") Icons.Default.ArrowBack,
                                    contentDescription = stringResource(R.string.nav_back),
                                    tint = AppColors.Black,
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
        },
        backgroundColor = backgroundColor,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            AppScreenContent(
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
                onStateChange = { state = it },
            )
        }
    }
}

private fun createAppStateMachine(
    tokenAllowlistRepository: TokenAllowlistRepository,
    nonceRepository: NonceRepository,
    paymentFeedback: PaymentFeedback,
    transactionRepository: TransactionRepository,
    walletManager: SecureWalletManager,
    executorFactory: ExecutorFactory = DefaultExecutorFactory,
): AppStateMachine {
    return AppStateMachine(
        tokenAllowlistPolicy = TokenAllowlistPolicy { asset, chainId -> tokenAllowlistRepository.isTokenAllowed(asset, chainId) },
        noncePort =
            object : NoncePort {
                override suspend fun consume(
                    nonce: String,
                    invoiceId: String,
                    expiry: Long,
                ): Boolean = nonceRepository.consumeNonce(nonce, invoiceId, expiry)

                override suspend fun release(
                    nonce: String,
                    invoiceId: String,
                ) {
                    nonceRepository.releaseNonce(nonce, invoiceId)
                }
            },
        paymentFeedbackPort =
            object : PaymentFeedbackPort {
                override suspend fun onSuccess() {
                    paymentFeedback.onPaymentSuccess()
                }

                override suspend fun onError() {
                    paymentFeedback.onPaymentError()
                }
            },
        paymentRecorder =
            PaymentRecorder { signedTx, txHash ->
                transactionRepository.recordPaymentReceived(
                    txHash = txHash,
                    amount = signedTx.amount,
                    asset = signedTx.asset,
                    chainId = signedTx.chainId,
                    payerAddress = signedTx.payer,
                    merchantId = signedTx.merchantId,
                    invoiceId = signedTx.invoiceId,
                )
            },
        walletCredentialsProvider = WalletCredentialsProvider { walletManager.getCredentialsUnsafe() },
        executorFactory = executorFactory,
    )
}
