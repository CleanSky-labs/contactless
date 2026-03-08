package io.cleansky.contactless.ui.stealthwallet

import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.StealthWalletService
import org.web3j.crypto.Credentials
import java.math.BigInteger

internal interface StealthWalletServiceFactory {
    fun create(
        chainConfig: ChainConfig,
        stealthPaymentRepository: StealthPaymentRepository,
        credentials: Credentials,
    ): StealthWalletService
}

internal object DefaultStealthWalletServiceFactory : StealthWalletServiceFactory {
    override fun create(
        chainConfig: ChainConfig,
        stealthPaymentRepository: StealthPaymentRepository,
        credentials: Credentials,
    ): StealthWalletService {
        return StealthWalletService(chainConfig, stealthPaymentRepository, credentials)
    }
}

internal class StealthWalletActions(
    private val walletManager: SecureWalletManager,
    private val stealthPaymentRepository: StealthPaymentRepository,
    private val paymentFeedback: PaymentFeedback,
    private val serviceFactory: StealthWalletServiceFactory = DefaultStealthWalletServiceFactory,
) {
    suspend fun handleSpend(
        activity: FragmentActivity,
        chainConfig: ChainConfig,
        recipientAddress: String,
        amountText: String,
        currentBalance: BigInteger,
        onSendingChange: (Boolean) -> Unit,
        onResult: (Pair<Boolean, String>) -> Unit,
        onSuccess: () -> Unit,
    ) {
        onSendingChange(true)

        val creds = walletManager.getCredentials(activity)
        when (creds) {
            is SecureWalletManager.CredentialsResult.Success -> {
                val service = serviceFactory.create(chainConfig, stealthPaymentRepository, creds.credentials)
                val amount = parseSpendAmount(amountText, currentBalance)
                when (val result = service.spend(recipientAddress, amount, "native")) {
                    is StealthWalletService.SpendResult.Success -> {
                        paymentFeedback.onPaymentSuccess()
                        onResult(true to activity.getString(R.string.stealth_spend_sent_tx, result.txHashes.first().take(10)))
                        onSuccess()
                    }

                    is StealthWalletService.SpendResult.Error -> {
                        paymentFeedback.onPaymentError()
                        onResult(false to result.message)
                    }
                }
            }

            else -> onResult(false to activity.getString(R.string.error_auth_cancelled))
        }

        onSendingChange(false)
    }

    suspend fun handleConsolidation(
        activity: FragmentActivity,
        chainConfig: ChainConfig,
        mainWallet: String?,
        onSendingChange: (Boolean) -> Unit,
        onResult: (Pair<Boolean, String>) -> Unit,
        onSuccess: () -> Unit,
    ) {
        onSendingChange(true)

        if (mainWallet == null) {
            onResult(false to activity.getString(R.string.error_wallet_not_configured))
            onSendingChange(false)
            return
        }

        val creds = walletManager.getCredentials(activity)
        when (creds) {
            is SecureWalletManager.CredentialsResult.Success -> {
                val service = serviceFactory.create(chainConfig, stealthPaymentRepository, creds.credentials)
                when (val result = service.spendAll(mainWallet, "native")) {
                    is StealthWalletService.SpendResult.Success -> {
                        paymentFeedback.onPaymentSuccess()
                        onResult(true to activity.getString(R.string.stealth_wallet_consolidate_success))
                        onSuccess()
                    }

                    is StealthWalletService.SpendResult.Error -> {
                        paymentFeedback.onPaymentError()
                        onResult(false to result.message)
                    }
                }
            }

            else -> onResult(false to activity.getString(R.string.error_auth_cancelled))
        }

        onSendingChange(false)
    }

    private fun parseSpendAmount(
        amountText: String,
        fallback: BigInteger,
    ): BigInteger {
        return try {
            val parts = amountText.split(".")
            val whole = BigInteger(parts[0])
            val frac =
                if (parts.size > 1) {
                    BigInteger(parts[1].padEnd(6, '0').take(6))
                } else {
                    BigInteger.ZERO
                }
            whole.multiply(BigInteger.TEN.pow(6)).add(frac)
        } catch (e: Exception) {
            fallback
        }
    }
}
