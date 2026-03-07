package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.crypto.StealthAddress
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.PendingStealthPayment
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import org.web3j.utils.Numeric
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.DefaultTokens
import io.cleansky.contactless.model.DefaultTokens.UnderlyingCurrency
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.nfc.NfcManager
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.CurrencyFullScreenSelector
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.PaymentSuccessAnimation
import io.cleansky.contactless.ui.ProcessingAnimation
import io.cleansky.contactless.util.NumberFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger

@Composable
fun CollectScreen(
    selectedChain: ChainConfig,
    walletManager: SecureWalletManager,
    nfcManager: NfcManager,
    @Suppress("UNUSED_PARAMETER") paymentFeedback: PaymentFeedback,
    tokenAllowlistRepository: TokenAllowlistRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    appPreferences: AppPreferences,
    txStatus: TxStatus,
    lastTxAmount: String?,
    lastTxSymbol: String?,
    isBroadcasting: Boolean,
    currentRequest: PaymentRequest?,
    onTxStatusChange: (TxStatus) -> Unit,
    onBroadcastStateChange: (Boolean, PaymentRequest?) -> Unit,
    isReceiveOnly: Boolean = false,
    receiveOnlyEscrow: String = "",
    receiveOnlyMerchantId: String = ""
) {
    var amountText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    // Stealth mode (v0.4)
    val stealthEnabled by stealthPaymentRepository.stealthEnabledFlow.collectAsState(initial = false)
    val stealthMetaAddress by stealthPaymentRepository.stealthMetaAddressFlow.collectAsState(initial = null)

    // Token selection state - unified
    var selectedUnderlying by remember { mutableStateOf<UnderlyingCurrency?>(null) }
    var selectedToken by remember { mutableStateOf<Token?>(null) }

    // Load defaults on chain change
    LaunchedEffect(selectedChain.chainId) {
        tokenAllowlistRepository.initializeDefaultTokensIfNeeded(selectedChain.chainId)

        // Set default based on region preference
        if (selectedUnderlying == null || selectedToken?.chainId != selectedChain.chainId) {
            val preferredCurrency = DefaultTokens.getPreferredCurrency()
            val availableUnderlyings = DefaultTokens.getAvailableUnderlyings(selectedChain.chainId)
            val defaultUnderlying = availableUnderlyings.find { it.code == preferredCurrency }
                ?: availableUnderlyings.firstOrNull()

            defaultUnderlying?.let { underlying ->
                selectedUnderlying = underlying
                selectedToken = DefaultTokens.getTokensForUnderlying(selectedChain.chainId, underlying).firstOrNull()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isReceiveOnly && receiveOnlyEscrow.isBlank() -> {
                NoEscrowConfiguredView()
            }
            !isReceiveOnly && walletAddress == null -> {
                NoWalletCollectView()
            }
            txStatus is TxStatus.Success -> {
                val formattedAmount = lastTxAmount?.let { formatAmount(it, selectedChain.decimals) } ?: "0"
                PaymentSuccessAnimation(
                    amount = formattedAmount,
                    symbol = lastTxSymbol ?: selectedChain.symbol
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "TX: ${txStatus.txHash.take(10)}...${txStatus.txHash.takeLast(8)}",
                    fontSize = 12.sp,
                    color = AppColors.Gray
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        amountText = ""
                        onBroadcastStateChange(false, null)
                        nfcManager.stopBroadcast()
                        onTxStatusChange(TxStatus.Idle)
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.collect_new), color = AppColors.White, fontSize = 18.sp)
                }
            }
            txStatus == TxStatus.Executing -> {
                ProcessingAnimation(message = stringResource(R.string.collect_processing))
            }
            txStatus is TxStatus.Error -> {
                CollectErrorView(
                    message = txStatus.message,
                    onReset = {
                        onBroadcastStateChange(false, null)
                        nfcManager.stopBroadcast()
                        onTxStatusChange(TxStatus.Idle)
                    }
                )
            }
            !isBroadcasting && txStatus == TxStatus.Idle -> {
                AmountInputView(
                    amountText = amountText,
                    selectedUnderlying = selectedUnderlying,
                    selectedToken = selectedToken,
                    chainId = selectedChain.chainId,
                    chainName = selectedChain.name,
                    onAmountChange = { amountText = it },
                    onCurrencySelected = { token, underlying ->
                        selectedToken = token
                        selectedUnderlying = underlying
                    },
                    onCobrar = {
                        val token = selectedToken ?: return@AmountInputView
                        val amount = parseAmount(amountText, token.decimals)
                        if (amount > BigInteger.ZERO) {
                            scope.launch {
                                val merchantId = if (isReceiveOnly) {
                                    receiveOnlyMerchantId.ifBlank { "0x" + "0".repeat(64) }
                                } else {
                                    val rawId = walletManager.getMerchantId()
                                    "0x" + rawId.toByteArray()
                                        .take(32)
                                        .joinToString("") { "%02x".format(it) }
                                        .padEnd(64, '0')
                                }
                                val escrow = if (isReceiveOnly) receiveOnlyEscrow else selectedChain.escrowAddress
                                // Get merchant identity from preferences (spec v0.2)
                                val merchantDisplayName = appPreferences.getMerchantDisplayName()
                                val merchantDomain = appPreferences.getMerchantDomain()

                                val request = PaymentRequest.create(
                                    merchantId = merchantId,
                                    amount = amount,
                                    asset = token.address,
                                    chainId = selectedChain.chainId,
                                    escrow = escrow,
                                    merchantDisplayName = merchantDisplayName,
                                    merchantDomain = merchantDomain,
                                    // Stealth disabled in receive-only mode
                                    stealthMetaAddress = if (!isReceiveOnly && stealthEnabled) stealthMetaAddress else null
                                )
                                nfcManager.preparePaymentRequest(request) // Uses CBOR by default
                                onBroadcastStateChange(true, request)
                                onTxStatusChange(TxStatus.WaitingForSignature)
                            }
                        }
                    },
                    stealthEnabled = stealthEnabled
                )
            }
            isBroadcasting && txStatus == TxStatus.WaitingForSignature -> {
                BroadcastingRequestView(
                    request = currentRequest,
                    selectedToken = selectedToken,
                    onCancel = {
                        nfcManager.stopBroadcast()
                        onBroadcastStateChange(false, null)
                        onTxStatusChange(TxStatus.Idle)
                    }
                )
            }
        }
    }

}

@Composable
private fun NoEscrowConfiguredView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AppColors.CollectPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.receive_only_no_escrow),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.receive_only_no_escrow_desc),
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoWalletCollectView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.collect_no_wallet),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.collect_no_wallet_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AmountInputView(
    amountText: String,
    selectedUnderlying: UnderlyingCurrency?,
    selectedToken: Token?,
    chainId: Long,
    chainName: String,
    stealthEnabled: Boolean,
    onAmountChange: (String) -> Unit,
    onCurrencySelected: (Token, UnderlyingCurrency) -> Unit,
    onCobrar: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        var showCurrencySelector by remember { mutableStateOf(false) }

        Text(
            text = stringResource(R.string.collect_amount),
            fontSize = 20.sp,
            color = AppColors.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                        onAmountChange(value)
                    }
                },
                modifier = Modifier.width(180.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = AppColors.CollectText
                ),
                placeholder = {
                    Text(
                        "0.00",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.CollectPrimary,
                    unfocusedBorderColor = AppColors.LightGray
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Token badge - clickable to open full screen selector
            Surface(
                modifier = Modifier.clickable { showCurrencySelector = true },
                shape = RoundedCornerShape(8.dp),
                color = AppColors.CollectPrimary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedToken?.symbol ?: "---",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.CollectPrimary
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppColors.CollectPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Full screen currency selector
        if (showCurrencySelector) {
            CurrencyFullScreenSelector(
                chainId = chainId,
                selectedUnderlying = selectedUnderlying,
                selectedToken = selectedToken,
                onTokenSelected = { token, underlying ->
                    onCurrencySelected(token, underlying)
                    showCurrencySelector = false
                },
                onDismiss = { showCurrencySelector = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.pay_network, chainName),
            fontSize = 14.sp,
            color = AppColors.Gray
        )

        // Stealth mode indicator (v0.4)
        if (stealthEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppColors.Success.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = AppColors.Success,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stealth_address_label),
                        fontSize = 12.sp,
                        color = AppColors.Success,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (stealthEnabled) 32.dp else 48.dp))

        Button(
            onClick = onCobrar,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = amountText.isNotEmpty() && amountText.toDoubleOrNull() != null && amountText.toDouble() > 0 && selectedToken != null,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppColors.CollectPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = AppColors.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.collect_button),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White
            )
        }
    }
}

@Composable
private fun BroadcastingRequestView(
    request: PaymentRequest?,
    selectedToken: Token?,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = AppColors.CollectPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.collect_waiting),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText
        )

        Spacer(modifier = Modifier.height(16.dp))

        request?.let {
            val decimals = selectedToken?.decimals ?: 6
            Text(
                text = "${it.getAmountFormatted(decimals)} ${selectedToken?.symbol ?: ""}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.CollectPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.collect_waiting_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        LogoLoadingIndicator()

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel), color = AppColors.Error, fontSize = 16.sp)
        }
    }
}


@Composable
private fun CollectErrorView(message: String, onReset: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppColors.Error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.error),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary)
        ) {
            Text(stringResource(R.string.retry), color = AppColors.White)
        }
    }
}

private fun parseAmount(text: String, decimals: Int): BigInteger {
    return try {
        val decimal = BigDecimal(text)
        val multiplier = BigDecimal.TEN.pow(decimals)
        decimal.multiply(multiplier).toBigInteger()
    } catch (e: Exception) {
        BigInteger.ZERO
    }
}

private fun formatAmount(amountStr: String, decimals: Int): String {
    return try {
        val amount = BigInteger(amountStr)
        NumberFormatter.formatCurrency(amount, decimals)
    } catch (e: Exception) {
        NumberFormatter.formatCurrency(null, decimals)
    }
}
