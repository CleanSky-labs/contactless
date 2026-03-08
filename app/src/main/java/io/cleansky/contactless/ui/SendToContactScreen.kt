package io.cleansky.contactless.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.service.PaymentFeedback
import java.math.BigDecimal
import java.math.BigInteger

sealed class SendState {
    object SelectContact : SendState()

    data class EnterAmount(val contact: Contact) : SendState()

    data class LargeAmountWarning(val contact: Contact, val amount: String, val token: Token) : SendState()

    data class Confirming(val contact: Contact, val amount: String, val token: Token) : SendState()

    object Processing : SendState()

    data class Success(val txHash: String, val contact: Contact, val amount: String, val symbol: String) : SendState()

    data class Error(val message: String) : SendState()
}

object ScamProtection {
    const val MIN_AMOUNT_ETH = 0.0001
    const val LARGE_AMOUNT_ETH = 0.5
    const val VERY_LARGE_AMOUNT_ETH = 5.0

    fun isAmountTooSmall(amount: BigDecimal): Boolean = amount < BigDecimal(MIN_AMOUNT_ETH)

    fun isLargeAmount(amount: BigDecimal): Boolean = amount >= BigDecimal(LARGE_AMOUNT_ETH)

    fun isVeryLargeAmount(amount: BigDecimal): Boolean = amount >= BigDecimal(VERY_LARGE_AMOUNT_ETH)
}

@Composable
fun SendToContactScreen(
    walletManager: SecureWalletManager,
    addressBookRepository: AddressBookRepository,
    @Suppress("UNUSED_PARAMETER") tokenRepository: TokenRepository,
    selectedChain: ChainConfig,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val contacts by addressBookRepository.contactsFlow.collectAsState(initial = emptyList())
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    var state by remember { mutableStateOf<SendState>(SendState.SelectContact) }
    var selectedToken by remember { mutableStateOf<Token?>(null) }
    var amount by remember { mutableStateOf("") }
    var tokenBalance by remember { mutableStateOf<BigInteger?>(null) }

    SendAmountBalanceLoader(
        state = state,
        walletAddress = walletAddress,
        selectedChain = selectedChain,
        selectedToken = selectedToken,
        onSelectedToken = { selectedToken = it },
        onTokenBalance = { tokenBalance = it },
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        SendStateContent(
            state = state,
            contacts = contacts,
            amount = amount,
            selectedToken = selectedToken,
            tokenBalance = tokenBalance,
            selectedChain = selectedChain,
            walletManager = walletManager,
            addressBookRepository = addressBookRepository,
            paymentFeedback = paymentFeedback,
            activity = activity,
            scope = scope,
            onBack = onBack,
            onStateChange = { state = it },
            onAmountChange = { amount = it },
            onSelectedTokenChange = { selectedToken = it },
        )
    }
}
