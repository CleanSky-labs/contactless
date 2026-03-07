package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.StealthWalletService
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.BottomSheetContainer
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.TitledBottomSheet
import kotlinx.coroutines.launch
import java.math.BigInteger

/**
 * Stealth Wallet Screen - Unified view of merchant's private wallet.
 *
 * The merchant sees a single balance aggregated from all stealth addresses.
 * Spending is automatic - the system picks which addresses to use.
 * No manual stealth address management required.
 */
@Composable
fun StealthWalletScreen(
    chainConfig: ChainConfig,
    walletManager: SecureWalletManager,
    stealthPaymentRepository: StealthPaymentRepository,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    var isLoading by remember { mutableStateOf(true) }
    var balance by remember { mutableStateOf<StealthWalletService.StealthWalletBalance?>(null) }
    var showSpendDialog by remember { mutableStateOf(false) }
    var recipientAddress by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (success, message)
    var showConsolidateDialog by remember { mutableStateOf(false) }

    // Load balance
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
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.White)
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
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
                onOpenConsolidate = { showConsolidateDialog = true }
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
                    isSending = true
                    resultMessage = null

                    val creds = walletManager.getCredentials(activity)
                    when (creds) {
                        is SecureWalletManager.CredentialsResult.Success -> {
                            val service = StealthWalletService(chainConfig, stealthPaymentRepository, creds.credentials)
                            val amount = parseSpendAmount(amountText, balance?.totalBalance ?: BigInteger.ZERO)
                            when (val result = service.spend(recipientAddress, amount, "native")) {
                                is StealthWalletService.SpendResult.Success -> {
                                    paymentFeedback.onPaymentSuccess()
                                    resultMessage = true to activity.getString(R.string.stealth_spend_sent_tx, result.txHashes.first().take(10))
                                    showSpendDialog = false
                                    recipientAddress = ""
                                    amountText = ""
                                    loadBalance()
                                }
                                is StealthWalletService.SpendResult.Error -> {
                                    paymentFeedback.onPaymentError()
                                    resultMessage = false to result.message
                                }
                            }
                        }
                        else -> resultMessage = false to activity.getString(R.string.error_auth_cancelled)
                    }
                    isSending = false
                }
            }
        )
    }

    if (showConsolidateDialog) {
        ConsolidateStealthBottomSheet(
            walletManager = walletManager,
            isSending = isSending,
            onDismiss = { if (!isSending) showConsolidateDialog = false },
            onConfirm = {
                scope.launch {
                    isSending = true
                    resultMessage = null

                    val mainWallet = walletAddress
                    if (mainWallet == null) {
                        resultMessage = false to activity.getString(R.string.error_wallet_not_configured)
                        isSending = false
                        return@launch
                    }

                    val creds = walletManager.getCredentials(activity)
                    when (creds) {
                        is SecureWalletManager.CredentialsResult.Success -> {
                            val service = StealthWalletService(chainConfig, stealthPaymentRepository, creds.credentials)
                            when (val result = service.spendAll(mainWallet, "native")) {
                                is StealthWalletService.SpendResult.Success -> {
                                    paymentFeedback.onPaymentSuccess()
                                    resultMessage = true to activity.getString(R.string.stealth_wallet_consolidate_success)
                                    showConsolidateDialog = false
                                    loadBalance()
                                }
                                is StealthWalletService.SpendResult.Error -> {
                                    paymentFeedback.onPaymentError()
                                    resultMessage = false to result.message
                                }
                            }
                        }
                        else -> resultMessage = false to activity.getString(R.string.error_auth_cancelled)
                    }
                    isSending = false
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.StealthWalletMainContent(
    chainConfig: ChainConfig,
    balance: StealthWalletService.StealthWalletBalance?,
    isSending: Boolean,
    resultMessage: Pair<Boolean, String>?,
    onRefresh: () -> Unit,
    onOpenSpend: () -> Unit,
    onOpenConsolidate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.CollectPrimary,
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = AppColors.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_title),
                    color = AppColors.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = balance?.totalBalanceFormatted ?: "0",
                color = AppColors.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = chainConfig.symbol,
                color = AppColors.White.copy(alpha = 0.8f),
                fontSize = 20.sp
            )

            if ((balance?.addressCount ?: 0) > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_addresses, balance?.addressCount ?: 0),
                    color = AppColors.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.Success.copy(alpha = 0.1f),
        elevation = 0.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = AppColors.Success,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.stealth_wallet_privacy_title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = AppColors.Success
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_privacy_desc),
                    fontSize = 12.sp,
                    color = AppColors.Gray
                )
            }
        }
    }

    if ((balance?.addressCount ?: 0) > 1) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = AppColors.Warning.copy(alpha = 0.1f),
            elevation = 0.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = AppColors.Warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.stealth_wallet_gas_title),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = AppColors.Warning
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stealth_wallet_gas_desc, balance?.addressCount ?: 0),
                        fontSize = 12.sp,
                        color = AppColors.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onOpenConsolidate, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = stringResource(R.string.stealth_wallet_consolidate),
                            fontSize = 12.sp,
                            color = AppColors.Warning
                        )
                    }
                }
            }
        }
    }

    resultMessage?.let { (success, message) ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            backgroundColor = if (success) AppColors.Success.copy(alpha = 0.1f) else AppColors.Error.copy(alpha = 0.1f),
            elevation = 0.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (success) AppColors.Success else AppColors.Error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = if (success) AppColors.Success else AppColors.Error
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), enabled = !isSending) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.balance_refresh))
        }

        Button(
            onClick = onOpenSpend,
            modifier = Modifier.weight(1f),
            enabled = !isSending && (balance?.totalBalance ?: BigInteger.ZERO) > BigInteger.ZERO,
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary)
        ) {
            Icon(@Suppress("DEPRECATION") Icons.Default.Send, contentDescription = null, tint = AppColors.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.stealth_wallet_send), color = AppColors.White)
        }
    }
}

private fun parseSpendAmount(amountText: String, fallback: BigInteger): BigInteger {
    return try {
        val parts = amountText.split(".")
        val whole = BigInteger(parts[0])
        val frac = if (parts.size > 1) {
            BigInteger(parts[1].padEnd(6, '0').take(6))
        } else {
            BigInteger.ZERO
        }
        whole.multiply(BigInteger.TEN.pow(6)).add(frac)
    } catch (e: Exception) {
        fallback
    }
}

@Composable
private fun SpendStealthBottomSheet(
    isSending: Boolean,
    balanceFormatted: String,
    symbol: String,
    recipientAddress: String,
    amountText: String,
    onRecipientChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSetMax: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    TitledBottomSheet(
        title = stringResource(R.string.stealth_wallet_send_title),
        subtitle = stringResource(R.string.stealth_wallet_available, balanceFormatted, symbol),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = recipientAddress,
                onValueChange = onRecipientChange,
                label = { Text(stringResource(R.string.stealth_spend_recipient)) },
                placeholder = { Text("0x...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSending,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.CollectPrimary,
                    cursorColor = AppColors.CollectPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.stealth_wallet_amount)) },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.CollectPrimary,
                    cursorColor = AppColors.CollectPrimary
                ),
                trailingIcon = {
                    TextButton(onClick = onSetMax, enabled = !isSending) {
                        Text("MAX", fontSize = 12.sp, color = AppColors.CollectPrimary)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isSending && recipientAddress.startsWith("0x") && recipientAddress.length == 42 && amountText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.White, strokeWidth = 2.dp)
                } else {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Send, contentDescription = null, tint = AppColors.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.stealth_wallet_send), color = AppColors.White, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending
            ) {
                Text(stringResource(R.string.pay_cancel), color = AppColors.Gray)
            }
        }
    }
}

@Composable
private fun ConsolidateStealthBottomSheet(
    walletManager: SecureWalletManager,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    BottomSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.Warning,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_warning),
                fontSize = 14.sp,
                color = AppColors.Gray,
                textAlign = TextAlign.Center
            )
            if (walletAddress == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.error_wallet_not_configured),
                    fontSize = 12.sp,
                    color = AppColors.Error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                backgroundColor = AppColors.Error.copy(alpha = 0.1f),
                elevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.stealth_wallet_consolidate_privacy_loss),
                    fontSize = 12.sp,
                    color = AppColors.Error,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_benefit),
                fontSize = 12.sp,
                color = AppColors.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Warning),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.White, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.stealth_wallet_consolidate_confirm), color = AppColors.White, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending
            ) {
                Text(stringResource(R.string.pay_cancel), color = AppColors.Gray)
            }
        }
    }
}
