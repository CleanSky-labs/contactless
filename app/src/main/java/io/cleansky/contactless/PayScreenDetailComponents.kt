package io.cleansky.contactless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigDecimal
import java.math.BigInteger

private fun formatBalance(
    amount: BigInteger?,
    decimals: Int,
): String {
    return NumberFormatter.formatCurrency(amount, decimals)
}

@Composable
internal fun BalanceHeader(
    isLoadingBalance: Boolean,
    balance: BigInteger?,
    decimals: Int,
    symbol: String,
    hasInsufficientBalance: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (hasInsufficientBalance) AppColors.Error.copy(alpha = 0.1f) else AppColors.PayPrimary.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pay_your_balance),
                fontSize = 12.sp,
                color = AppColors.Gray,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isLoadingBalance) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.dp,
                    color = AppColors.Gray,
                )
            } else {
                Text(
                    text = "${formatBalance(balance, decimals)} $symbol",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasInsufficientBalance) AppColors.Error else AppColors.PayPrimary,
                )
            }
        }
    }
}

@Composable
internal fun InsufficientBalanceWarning(
    missingAmount: BigInteger,
    decimals: Int,
    symbol: String,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Error.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.pay_insufficient_balance),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    stringResource(
                        R.string.pay_need_more,
                        formatBalance(missingAmount, decimals),
                        symbol,
                    ),
                fontSize = 14.sp,
                color = AppColors.Error,
            )
        }
    }
}

@Composable
internal fun ScamWarnings(
    amountInEth: BigDecimal,
    amountFormatted: String,
    symbol: String,
) {
    if (amountInEth < BigDecimal("0.0001") && amountInEth > BigDecimal.ZERO) {
        WarningCard(
            title = stringResource(R.string.scam_dust_attack_warning),
            description = stringResource(R.string.scam_dust_attack_desc, amountFormatted, symbol),
        )
    }

    if (amountInEth >= BigDecimal("5.0")) {
        WarningCard(
            title = stringResource(R.string.scam_receive_large_warning),
            description = stringResource(R.string.scam_receive_large_desc, amountFormatted, symbol),
        )
    }
}

@Composable
private fun WarningCard(
    title: String,
    description: String,
) {
    Spacer(modifier = Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Warning.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "⚠️ $title",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Warning,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = AppColors.Warning,
            )
        }
    }
}

@Composable
internal fun PrivacyIndicators(
    stealthEnabled: Boolean,
    payerPrivacyEnabled: Boolean,
) {
    if (stealthEnabled) {
        PrivacyBadge(
            text = stringResource(R.string.stealth_payment_note),
            background = AppColors.Success.copy(alpha = 0.15f),
            tint = AppColors.Success,
        )
    }
    if (payerPrivacyEnabled) {
        PrivacyBadge(
            text = stringResource(R.string.payer_privacy_note),
            background = AppColors.PayPrimary.copy(alpha = 0.15f),
            tint = AppColors.PayPrimary,
        )
    }
}

@Composable
private fun PrivacyBadge(
    text: String,
    background: Color,
    tint: Color,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = tint,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun PaymentActionButtons(
    isConfirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Error,
                ),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.pay_cancel))
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            enabled = isConfirmEnabled,
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.PayPrimary,
                ),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.pay_confirm), color = AppColors.White)
        }
    }
}

@Composable
internal fun BroadcastingView(onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = AppColors.PayPrimary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pay_tx_signed),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PayText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pay_tx_signed_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        LogoLoadingIndicator()
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel), color = AppColors.Error)
        }
    }
}

@Composable
internal fun ErrorView(
    message: String,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppColors.Error,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.error),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = localizedErrorMessage(message),
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
        ) {
            Text(stringResource(R.string.retry), color = AppColors.White)
        }
    }
}
