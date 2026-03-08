package io.cleansky.contactless.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.service.PaymentFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.Transfer
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

@Composable
internal fun SendAmountBalanceLoader(
    state: SendState,
    walletAddress: String?,
    selectedChain: ChainConfig,
    selectedToken: Token?,
    onSelectedToken: (Token) -> Unit,
    onTokenBalance: (BigInteger) -> Unit,
) {
    LaunchedEffect(state, walletAddress, selectedChain) {
        if (state !is SendState.EnterAmount || walletAddress == null) {
            return@LaunchedEffect
        }

        if (selectedToken == null) {
            onSelectedToken(
                Token(
                    address = "0x0000000000000000000000000000000000000000",
                    symbol = selectedChain.symbol,
                    decimals = 18,
                    name = selectedChain.name,
                    chainId = selectedChain.chainId,
                ),
            )
        }

        try {
            val web3j = Web3j.build(HttpService(selectedChain.rpcUrl))
            val balance = web3j.ethGetBalance(walletAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send()
            onTokenBalance(balance.balance)
        } catch (e: Exception) {
            onTokenBalance(BigInteger.ZERO)
        }
    }
}

@Composable
internal fun ColumnScope.SendStateContent(
    state: SendState,
    contacts: List<Contact>,
    amount: String,
    selectedToken: Token?,
    tokenBalance: BigInteger?,
    selectedChain: ChainConfig,
    walletManager: SecureWalletManager,
    addressBookRepository: AddressBookRepository,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onStateChange: (SendState) -> Unit,
    onAmountChange: (String) -> Unit,
    onSelectedTokenChange: (Token?) -> Unit,
) {
    when (val currentState = state) {
        SendState.SelectContact -> {
            ContactListContent(
                contacts = contacts,
                onContactSelected = { contact ->
                    onStateChange(SendState.EnterAmount(contact))
                },
            )
        }
        is SendState.EnterAmount -> {
            EnterAmountContent(
                contact = currentState.contact,
                amount = amount,
                onAmountChange = onAmountChange,
                token = selectedToken,
                tokenBalance = tokenBalance,
                chainSymbol = selectedChain.symbol,
                onContinue = {
                    selectedToken?.let { token ->
                        onStateChange(
                            nextStateForEnteredAmount(
                                contact = currentState.contact,
                                amount = amount,
                                token = token,
                                activity = activity,
                            ),
                        )
                    }
                },
                onChangeContact = {
                    onStateChange(SendState.SelectContact)
                    onAmountChange("")
                },
            )
        }
        is SendState.LargeAmountWarning -> {
            LargeAmountWarningContent(
                contact = currentState.contact,
                amount = currentState.amount,
                token = currentState.token,
                isVeryLarge = ScamProtection.isVeryLargeAmount(BigDecimal(currentState.amount)),
                onConfirm = {
                    onStateChange(SendState.Confirming(currentState.contact, currentState.amount, currentState.token))
                },
                onCancel = {
                    onStateChange(SendState.EnterAmount(currentState.contact))
                },
            )
        }
        is SendState.Confirming -> {
            ConfirmContent(
                contact = currentState.contact,
                amount = currentState.amount,
                token = currentState.token,
                chainName = selectedChain.name,
                onConfirm = {
                    onStateChange(SendState.Processing)
                    scope.launch {
                        onStateChange(
                            executeContactTransfer(
                                walletManager = walletManager,
                                addressBookRepository = addressBookRepository,
                                paymentFeedback = paymentFeedback,
                                activity = activity,
                                selectedChain = selectedChain,
                                confirmingState = currentState,
                            ),
                        )
                    }
                },
                onCancel = {
                    onStateChange(SendState.EnterAmount(currentState.contact))
                },
            )
        }
        SendState.Processing -> ProcessingContent()
        is SendState.Success -> {
            SuccessContent(
                txHash = currentState.txHash,
                contact = currentState.contact,
                amount = currentState.amount,
                symbol = currentState.symbol,
                onNewTransfer = {
                    onStateChange(SendState.SelectContact)
                    onAmountChange("")
                    onSelectedTokenChange(null)
                },
                onDone = onBack,
            )
        }
        is SendState.Error -> {
            ErrorContent(
                message = currentState.message,
                onRetry = {
                    onStateChange(SendState.SelectContact)
                    onAmountChange("")
                },
            )
        }
    }
}

private fun nextStateForEnteredAmount(
    contact: Contact,
    amount: String,
    token: Token,
    activity: FragmentActivity,
): SendState {
    val amountDecimal = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    if (ScamProtection.isAmountTooSmall(amountDecimal)) {
        return SendState.Error(activity.getString(R.string.scam_amount_too_small))
    }
    if (ScamProtection.isLargeAmount(amountDecimal)) {
        return SendState.LargeAmountWarning(contact, amount, token)
    }
    return SendState.Confirming(contact, amount, token)
}

private suspend fun executeContactTransfer(
    walletManager: SecureWalletManager,
    addressBookRepository: AddressBookRepository,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    selectedChain: ChainConfig,
    confirmingState: SendState.Confirming,
): SendState {
    return try {
        when (val credResult = walletManager.getCredentials(activity)) {
            is SecureWalletManager.CredentialsResult.Success -> {
                val web3j = Web3j.build(HttpService(selectedChain.rpcUrl))
                val receipt =
                    Transfer.sendFunds(
                        web3j,
                        credResult.credentials,
                        confirmingState.contact.address,
                        BigDecimal(confirmingState.amount),
                        Convert.Unit.ETHER,
                    ).send()
                addressBookRepository.updateLastUsed(confirmingState.contact.address)
                paymentFeedback.onPaymentSuccess()
                SendState.Success(
                    txHash = receipt.transactionHash,
                    contact = confirmingState.contact,
                    amount = confirmingState.amount,
                    symbol = confirmingState.token.symbol,
                )
            }
            is SecureWalletManager.CredentialsResult.Cancelled -> {
                SendState.EnterAmount(confirmingState.contact)
            }
            else -> {
                paymentFeedback.onPaymentError()
                SendState.Error(activity.getString(R.string.error_auth))
            }
        }
    } catch (e: Exception) {
        paymentFeedback.onPaymentError()
        SendState.Error(e.message ?: activity.getString(R.string.error_unknown))
    }
}
