package io.cleansky.contactless

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.NonceRepository
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.SignedTransaction
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.RefundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var nfcManager: NfcManager
    private lateinit var walletManager: SecureWalletManager
    private lateinit var paymentFeedback: PaymentFeedback
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var refundService: RefundService
    private lateinit var tokenAllowlistRepository: TokenAllowlistRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var nonceRepository: NonceRepository
    private lateinit var stealthPaymentRepository: StealthPaymentRepository
    private lateinit var privacyPayerRepository: PrivacyPayerRepository
    private lateinit var addressBookRepository: AddressBookRepository

    // Estados observables
    private var receivedPaymentRequest by mutableStateOf<PaymentRequest?>(null)
    private var receivedSignedTransaction by mutableStateOf<SignedTransaction?>(null)
    private var isInitialized by mutableStateOf(false)
    private var showWelcome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen visible while initializing
        splashScreen.setKeepOnScreenCondition { !isInitialized }

        nfcManager = NfcManager(this)
        walletManager = SecureWalletManager(this)
        paymentFeedback = PaymentFeedback(this)
        transactionRepository = TransactionRepository(this)
        refundService = RefundService(transactionRepository)
        tokenAllowlistRepository = TokenAllowlistRepository(this)
        tokenRepository = TokenRepository(tokenAllowlistRepository)
        appPreferences = AppPreferences(this)
        nonceRepository = NonceRepository(this)
        stealthPaymentRepository = StealthPaymentRepository(this)
        privacyPayerRepository = PrivacyPayerRepository(this)
        addressBookRepository = AddressBookRepository(this)

        // Inicializar el sistema de seguridad
        lifecycleScope.launch {
            // Check if first launch
            val isFirstLaunch = appPreferences.isFirstLaunch()
            showWelcome = isFirstLaunch

            val initResult = walletManager.initialize()
            when (initResult) {
                SecureWalletManager.InitResult.WalletExists -> {
                    isInitialized = true
                }
                SecureWalletManager.InitResult.NoWallet -> {
                    isInitialized = true
                }
                SecureWalletManager.InitResult.KeystoreError -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_device_security),
                        Toast.LENGTH_LONG
                    ).show()
                    val retryResult = walletManager.initialize()
                    if (retryResult == SecureWalletManager.InitResult.KeystoreError) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.error_keystore_unavailable),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    isInitialized = true
                }
            }
        }

        // Configurar callbacks NFC
        nfcManager.setOnPaymentRequestReceived { request ->
            paymentFeedback.onPaymentRequestReceived()
            receivedPaymentRequest = request
        }

        nfcManager.setOnSignedTransactionReceived { signedTx ->
            paymentFeedback.onNfcDetected()
            receivedSignedTransaction = signedTx
        }

        setContent {
            MaterialTheme {
                if (isInitialized) {
                    if (showWelcome) {
                        WelcomeScreen(
                            onAccept = {
                                lifecycleScope.launch {
                                    appPreferences.setFirstLaunchCompleted()
                                    showWelcome = false
                                }
                            }
                        )
                    } else {
                        AppScaffold(
                            activity = this,
                            nfcManager = nfcManager,
                            walletManager = walletManager,
                            paymentFeedback = paymentFeedback,
                            transactionRepository = transactionRepository,
                            refundService = refundService,
                            tokenAllowlistRepository = tokenAllowlistRepository,
                            tokenRepository = tokenRepository,
                            nonceRepository = nonceRepository,
                            stealthPaymentRepository = stealthPaymentRepository,
                            privacyPayerRepository = privacyPayerRepository,
                            addressBookRepository = addressBookRepository,
                            appPreferences = appPreferences,
                            receivedPaymentRequest = receivedPaymentRequest,
                            receivedSignedTransaction = receivedSignedTransaction,
                            onPaymentRequestConsumed = { receivedPaymentRequest = null },
                            onSignedTransactionConsumed = { receivedSignedTransaction = null }
                        )
                    }
                }
            }
        }

        // Manejar intent inicial si viene de NFC
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcManager.enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentFeedback.release()
    }

    private fun handleIntent(intent: Intent) {
        nfcManager.handleIntent(intent)
    }
}
