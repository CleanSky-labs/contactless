package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.util.NumberFormatter
import kotlinx.coroutines.launch
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.Transfer
import org.web3j.utils.Convert
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

/**
 * Scam protection thresholds
 */
object ScamProtection {
    // Minimum amount in ETH (below this is likely dust attack)
    const val MIN_AMOUNT_ETH = 0.0001

    // Large amount threshold in ETH (requires extra confirmation)
    const val LARGE_AMOUNT_ETH = 0.5

    // Very large amount (strong warning)
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
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val contacts by addressBookRepository.contactsFlow.collectAsState(initial = emptyList())
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    var state by remember { mutableStateOf<SendState>(SendState.SelectContact) }
    var selectedToken by remember { mutableStateOf<Token?>(null) }
    var amount by remember { mutableStateOf("") }
    var tokenBalance by remember { mutableStateOf<BigInteger?>(null) }

    // Load native token balance when entering amount screen
    LaunchedEffect(state, walletAddress, selectedChain) {
        if (state is SendState.EnterAmount && walletAddress != null) {
            // Default to native token
            if (selectedToken == null) {
                selectedToken = Token(
                    address = "0x0000000000000000000000000000000000000000",
                    symbol = selectedChain.symbol,
                    decimals = 18,
                    name = selectedChain.name,
                    chainId = selectedChain.chainId
                )
            }
            // Load balance
            try {
                val web3j = Web3j.build(HttpService(selectedChain.rpcUrl))
                val balance = web3j.ethGetBalance(walletAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send()
                tokenBalance = balance.balance
            } catch (e: Exception) {
                tokenBalance = BigInteger.ZERO
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (val currentState = state) {
            SendState.SelectContact -> {
                ContactListContent(
                    contacts = contacts,
                    onContactSelected = { contact ->
                        state = SendState.EnterAmount(contact)
                    }
                )
            }
            is SendState.EnterAmount -> {
                EnterAmountContent(
                    contact = currentState.contact,
                    amount = amount,
                    onAmountChange = { amount = it },
                    token = selectedToken,
                    tokenBalance = tokenBalance,
                    chainSymbol = selectedChain.symbol,
                    onContinue = {
                        selectedToken?.let { token ->
                            val amountDecimal = try { BigDecimal(amount) } catch (e: Exception) { BigDecimal.ZERO }
                            // Check for scam protection thresholds
                            when {
                                ScamProtection.isAmountTooSmall(amountDecimal) -> {
                                    // Amount too small, show error
                                    state = SendState.Error(activity.getString(R.string.scam_amount_too_small))
                                }
                                ScamProtection.isLargeAmount(amountDecimal) -> {
                                    // Large amount, require extra confirmation
                                    state = SendState.LargeAmountWarning(currentState.contact, amount, token)
                                }
                                else -> {
                                    state = SendState.Confirming(currentState.contact, amount, token)
                                }
                            }
                        }
                    },
                    onChangeContact = {
                        state = SendState.SelectContact
                        amount = ""
                    }
                )
            }
            is SendState.LargeAmountWarning -> {
                LargeAmountWarningContent(
                    contact = currentState.contact,
                    amount = currentState.amount,
                    token = currentState.token,
                    isVeryLarge = ScamProtection.isVeryLargeAmount(BigDecimal(currentState.amount)),
                    onConfirm = {
                        state = SendState.Confirming(currentState.contact, currentState.amount, currentState.token)
                    },
                    onCancel = {
                        state = SendState.EnterAmount(currentState.contact)
                    }
                )
            }
            is SendState.Confirming -> {
                ConfirmContent(
                    contact = currentState.contact,
                    amount = currentState.amount,
                    token = currentState.token,
                    chainName = selectedChain.name,
                    onConfirm = {
                        state = SendState.Processing
                        scope.launch {
                            try {
                                val credResult = walletManager.getCredentials(activity)
                                when (credResult) {
                                    is SecureWalletManager.CredentialsResult.Success -> {
                                        val web3j = Web3j.build(HttpService(selectedChain.rpcUrl))
                                        val receipt = Transfer.sendFunds(
                                            web3j,
                                            credResult.credentials,
                                            currentState.contact.address,
                                            BigDecimal(currentState.amount),
                                            Convert.Unit.ETHER
                                        ).send()

                                        // Update last used
                                        addressBookRepository.updateLastUsed(currentState.contact.address)

                                        paymentFeedback.onPaymentSuccess()
                                        state = SendState.Success(
                                            txHash = receipt.transactionHash,
                                            contact = currentState.contact,
                                            amount = currentState.amount,
                                            symbol = currentState.token.symbol
                                        )
                                    }
                                    is SecureWalletManager.CredentialsResult.Cancelled -> {
                                        state = SendState.EnterAmount(currentState.contact)
                                    }
                                    else -> {
                                        paymentFeedback.onPaymentError()
                                        state = SendState.Error(activity.getString(R.string.error_auth))
                                    }
                                }
                            } catch (e: Exception) {
                                paymentFeedback.onPaymentError()
                                state = SendState.Error(e.message ?: activity.getString(R.string.error_unknown))
                            }
                        }
                    },
                    onCancel = {
                        state = SendState.EnterAmount(currentState.contact)
                    }
                )
            }
            SendState.Processing -> {
                ProcessingContent()
            }
            is SendState.Success -> {
                SuccessContent(
                    txHash = currentState.txHash,
                    contact = currentState.contact,
                    amount = currentState.amount,
                    symbol = currentState.symbol,
                    onNewTransfer = {
                        state = SendState.SelectContact
                        amount = ""
                        selectedToken = null
                    },
                    onDone = onBack
                )
            }
            is SendState.Error -> {
                ErrorContent(
                    message = currentState.message,
                    onRetry = {
                        // Go back to confirm state if possible
                        state = SendState.SelectContact
                        amount = ""
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactListContent(
    contacts: List<Contact>,
    onContactSelected: (Contact) -> Unit
) {
    if (contacts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.send_no_contacts),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.send_no_contacts_hint),
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Text(
            text = stringResource(R.string.send_select_contact),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(contacts) { contact ->
                ContactCard(
                    contact = contact,
                    onClick = { onContactSelected(contact) }
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase().ifEmpty { "#" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PayPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${contact.address.take(8)}...${contact.address.takeLast(6)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

@Composable
private fun ColumnScope.EnterAmountContent(
    contact: Contact,
    amount: String,
    onAmountChange: (String) -> Unit,
    token: Token?,
    tokenBalance: BigInteger?,
    chainSymbol: String,
    onContinue: () -> Unit,
    onChangeContact: () -> Unit
) {
    // Selected contact
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChangeContact() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = AppColors.PayPrimary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.send_to, contact.name.ifBlank { contact.address.take(10) + "..." }),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = contact.name.ifBlank { "${contact.address.take(10)}...${contact.address.takeLast(6)}" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = stringResource(R.string.settings_wallet_import),
                fontSize = 12.sp,
                color = AppColors.PayPrimary
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Amount input
    Text(
        text = stringResource(R.string.send_amount),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = amount,
        onValueChange = { newValue ->
            // Only allow valid decimal numbers
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                onAmountChange(newValue)
            }
        },
        placeholder = { Text(stringResource(R.string.send_amount_hint)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        trailingIcon = {
            Text(
                text = token?.symbol ?: chainSymbol,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = AppColors.PayPrimary,
            cursorColor = AppColors.PayPrimary
        )
    )

    // Balance
    tokenBalance?.let { balance ->
        Spacer(modifier = Modifier.height(8.dp))
        val formattedBalance = NumberFormatter.formatBalance(balance, 18, 6)
        Text(
            text = "${stringResource(R.string.stealth_wallet_available, formattedBalance, chainSymbol)}",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Continue button
    val isValidAmount = amount.isNotBlank() &&
        try { amount.toBigDecimal() > BigDecimal.ZERO } catch (e: Exception) { false }

    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = isValidAmount,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.PayPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = if (isValidAmount) {
                stringResource(R.string.send_confirm, amount, token?.symbol ?: chainSymbol)
            } else {
                stringResource(R.string.send_enter_amount)
            },
            fontSize = 18.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ColumnScope.LargeAmountWarningContent(
    contact: Contact,
    amount: String,
    token: Token,
    isVeryLarge: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))

    // Warning icon
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(if (isVeryLarge) Color.Red.copy(alpha = 0.1f) else Color(0xFFFF9800).copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = if (isVeryLarge) Color.Red else Color(0xFFFF9800)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.scam_amount_large_warning),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (isVeryLarge) Color.Red else Color(0xFFFF9800)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.scam_amount_large_desc, amount, token.symbol),
        fontSize = 16.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Show recipient
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Gray.copy(alpha = 0.1f),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.send_to, ""),
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${contact.address.take(10)}...${contact.address.takeLast(6)}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }

    if (isVeryLarge) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "⚠️ This is a very large amount. Triple check the recipient address.",
            fontSize = 14.sp,
            color = Color.Red,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Confirm button (with warning color)
    Button(
        onClick = onConfirm,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isVeryLarge) Color.Red else Color(0xFFFF9800)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringResource(R.string.scam_confirm_large),
            fontSize = 18.sp,
            color = Color.White
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Cancel button
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringResource(R.string.cancel),
            fontSize = 18.sp
        )
    }
}

@Composable
private fun ColumnScope.ConfirmContent(
    contact: Contact,
    amount: String,
    token: Token,
    chainName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Spacer(modifier = Modifier.weight(1f))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.send_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Amount
            Text(
                text = "$amount ${token.symbol}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.PayPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recipient
            Text(
                text = stringResource(R.string.send_to, ""),
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${contact.address.take(10)}...${contact.address.takeLast(6)}",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Network
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = chainName,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.refund_gas_notice),
        fontSize = 12.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.weight(1f))

    // Confirm button
    Button(
        onClick = onConfirm,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.PayPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = @Suppress("DEPRECATION") Icons.Default.Send,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.send_confirm, amount, token.symbol),
            fontSize = 18.sp,
            color = Color.White
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Cancel button
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringResource(R.string.cancel),
            fontSize = 18.sp
        )
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = AppColors.PayPrimary,
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.send_processing),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ColumnScope.SuccessContent(
    txHash: String,
    contact: Contact,
    amount: String,
    symbol: String,
    onNewTransfer: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Success icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppColors.CollectPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = AppColors.CollectPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.send_success),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.send_success_desc, amount, symbol, contact.name.ifBlank { contact.address.take(10) + "..." }),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TX Hash
        Text(
            text = stringResource(R.string.tx_hash, "${txHash.take(10)}...${txHash.takeLast(6)}"),
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.weight(1f))

        // New transfer button
        OutlinedButton(
            onClick = onNewTransfer,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.send_new),
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppColors.CollectPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.refund_done),
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ColumnScope.ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Error icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.Red
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.send_error),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Retry button
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppColors.PayPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.retry),
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}
