package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.service.PaymentFeedback
import io.cleansky.contactless.service.RefundService
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.PaymentSuccessAnimation
import io.cleansky.contactless.util.AmountUtils
import io.cleansky.contactless.util.DateTimeUtils
import io.cleansky.contactless.util.NumberFormatter
import kotlinx.coroutines.launch
import java.math.BigInteger

sealed class RefundState {
    object Idle : RefundState()

    object Processing : RefundState()

    data class Success(val txHash: String, val amount: BigInteger) : RefundState()

    data class Error(val message: String) : RefundState()
}

@Composable
fun RefundScreen(
    transaction: Transaction,
    chainConfig: ChainConfig,
    walletManager: SecureWalletManager,
    refundService: RefundService,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refundState by remember { mutableStateOf<RefundState>(RefundState.Idle) }
    var refundAmountText by remember { mutableStateOf("") }
    var isFullRefund by remember { mutableStateOf(true) }

    val remainingRefundable = transaction.getRemainingRefundable()
    val maxRefundFormatted = transaction.getFormattedRemainingRefundable(chainConfig.decimals)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        when (refundState) {
            is RefundState.Success -> {
                val state = refundState as RefundState.Success
                RefundSuccessView(
                    amount = NumberFormatter.formatCurrency(state.amount, chainConfig.decimals),
                    symbol = chainConfig.symbol,
                    txHash = state.txHash,
                    onDone = onSuccess,
                )
            }
            is RefundState.Error -> {
                RefundErrorView(
                    message = localizedErrorMessage((refundState as RefundState.Error).message),
                    onRetry = { refundState = RefundState.Idle },
                )
            }
            RefundState.Processing -> {
                ProcessingView()
            }
            RefundState.Idle -> {
                RefundIdleContent(
                    transaction = transaction,
                    chainConfig = chainConfig,
                    maxRefundFormatted = maxRefundFormatted,
                    remainingRefundable = remainingRefundable,
                    isFullRefund = isFullRefund,
                    refundAmountText = refundAmountText,
                    onFullRefundChange = { isFullRefund = it },
                    onRefundAmountChange = { refundAmountText = it },
                    onRefund = {
                        scope.launch {
                            refundState = RefundState.Processing
                            refundState =
                                executeRefund(
                                    isFullRefund = isFullRefund,
                                    refundAmountText = refundAmountText,
                                    remainingRefundable = remainingRefundable,
                                    transaction = transaction,
                                    chainConfig = chainConfig,
                                    walletManager = walletManager,
                                    refundService = refundService,
                                    paymentFeedback = paymentFeedback,
                                    activity = activity,
                                )
                        }
                    },
                    onBack = onBack,
                )
            }
        }
    }
}

private suspend fun executeRefund(
    isFullRefund: Boolean,
    refundAmountText: String,
    remainingRefundable: BigInteger,
    transaction: Transaction,
    chainConfig: ChainConfig,
    walletManager: SecureWalletManager,
    refundService: RefundService,
    paymentFeedback: PaymentFeedback,
    activity: FragmentActivity,
): RefundState {
    val credResult = walletManager.getCredentials(activity)
    return when (credResult) {
        is SecureWalletManager.CredentialsResult.Success -> {
            val refundAmount =
                if (isFullRefund) {
                    remainingRefundable
                } else {
                    AmountUtils.parseToUnitsOrZero(refundAmountText, chainConfig.decimals)
                }

            when (
                val result =
                    refundService.processRefund(
                        originalTransaction = transaction,
                        refundAmount = refundAmount,
                        credentials = credResult.credentials,
                        chainConfig = chainConfig,
                    )
            ) {
                is RefundService.RefundResult.Success -> {
                    paymentFeedback.onPaymentSuccess()
                    RefundState.Success(result.txHash, result.amount)
                }
                is RefundService.RefundResult.Error -> {
                    paymentFeedback.onPaymentError()
                    RefundState.Error(result.message)
                }
            }
        }
        is SecureWalletManager.CredentialsResult.Cancelled -> RefundState.Idle
        else -> RefundState.Error(activity.getString(R.string.error_auth))
    }
}

@Composable
private fun RefundIdleContent(
    transaction: Transaction,
    chainConfig: ChainConfig,
    maxRefundFormatted: String,
    remainingRefundable: BigInteger,
    isFullRefund: Boolean,
    refundAmountText: String,
    onFullRefundChange: (Boolean) -> Unit,
    onRefundAmountChange: (String) -> Unit,
    onRefund: () -> Unit,
    onBack: () -> Unit,
) {
    TransactionInfoCard(transaction, chainConfig)

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        stringResource(R.string.refund_type),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.Gray,
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RefundTypeCard(
            title = stringResource(R.string.refund_full),
            description = "$maxRefundFormatted ${chainConfig.symbol}",
            isSelected = isFullRefund,
            modifier = Modifier.weight(1f),
            onClick = { onFullRefundChange(true) },
        )
        RefundTypeCard(
            title = stringResource(R.string.refund_partial),
            description = stringResource(R.string.refund_partial_desc),
            isSelected = !isFullRefund,
            modifier = Modifier.weight(1f),
            onClick = { onFullRefundChange(false) },
        )
    }

    if (!isFullRefund) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = refundAmountText,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onRefundAmountChange(value)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.refund_amount_label, chainConfig.symbol)) },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors =
                TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.CollectPrimary,
                ),
        )

        Text(
            stringResource(R.string.refund_max, maxRefundFormatted, chainConfig.symbol),
            fontSize = 12.sp,
            color = AppColors.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.PayBackground,
        elevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = AppColors.PayPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.refund_gas_notice),
                fontSize = 14.sp,
                color = AppColors.PayText,
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onRefund,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        enabled =
            isFullRefund ||
                (
                    refundAmountText.isNotEmpty() &&
                        isValidAmount(refundAmountText, remainingRefundable, chainConfig.decimals)
                ),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.Error,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(@Suppress("DEPRECATION") Icons.Default.Undo, contentDescription = null, tint = AppColors.White)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(
                R.string.refund_button,
                if (isFullRefund) maxRefundFormatted else refundAmountText.ifEmpty { "0" },
                chainConfig.symbol,
            ),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.White,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.cancel), color = AppColors.Gray)
    }
}

@Composable
private fun TransactionInfoCard(
    transaction: Transaction,
    chainConfig: ChainConfig,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.refund_original), fontSize = 14.sp, color = AppColors.Gray)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.refund_amount), color = AppColors.Gray)
                Text(
                    "${transaction.getFormattedAmount(chainConfig.decimals)} ${chainConfig.symbol}",
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.refund_already), color = AppColors.Gray)
                Text(
                    "${transaction.getFormattedRefundedAmount(chainConfig.decimals)} ${chainConfig.symbol}",
                    fontWeight = FontWeight.Medium,
                    color = if (transaction.getRefundedAmountBigInt() > BigInteger.ZERO) AppColors.Error else AppColors.Black,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.refund_available), color = AppColors.Gray)
                Text(
                    "${transaction.getFormattedRemainingRefundable(chainConfig.decimals)} ${chainConfig.symbol}",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Success,
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.refund_recipient), color = AppColors.Gray)
                Text(
                    "${transaction.counterparty.take(8)}...${transaction.counterparty.takeLast(6)}",
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.refund_date), color = AppColors.Gray)
                Text(DateTimeUtils.formatLocalDateTime(transaction.timestamp), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RefundTypeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            modifier
                .clickable(onClick = onClick),
        elevation = if (isSelected) 4.dp else 1.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isSelected) AppColors.Error.copy(alpha = 0.1f) else AppColors.White,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AppColors.Error else AppColors.Black,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = AppColors.Gray,
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = AppColors.Error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ProcessingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoLoadingIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.refund_processing), fontSize = 18.sp)
        }
    }
}

@Composable
private fun RefundSuccessView(
    amount: String,
    symbol: String,
    txHash: String,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        PaymentSuccessAnimation(
            amount = amount,
            symbol = symbol,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(R.string.refund_success),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Success,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.tx_hash, "${txHash.take(10)}...${txHash.takeLast(8)}"),
            fontSize = 12.sp,
            color = AppColors.Gray,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Success),
        ) {
            Text(stringResource(R.string.refund_done), color = AppColors.White)
        }
    }
}

@Composable
private fun RefundErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    val displayMessage =
        when (message) {
            "auth_error" -> stringResource(R.string.error_auth)
            else -> message
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppColors.Error,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.error), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Error)

        Spacer(modifier = Modifier.height(16.dp))

        Text(displayMessage, textAlign = TextAlign.Center, color = AppColors.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

private fun isValidAmount(
    text: String,
    maxAmount: BigInteger,
    decimals: Int,
): Boolean {
    val amount = AmountUtils.parseToUnitsOrZero(text, decimals)
    return amount > BigInteger.ZERO && amount <= maxAmount
}
