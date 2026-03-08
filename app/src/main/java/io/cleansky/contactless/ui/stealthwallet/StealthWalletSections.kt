package io.cleansky.contactless.ui.stealthwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.localizedErrorMessage
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.service.StealthWalletService
import io.cleansky.contactless.ui.AppColors
import java.math.BigInteger

@Composable
internal fun StealthWalletMainContent(
    chainConfig: ChainConfig,
    balance: StealthWalletService.StealthWalletBalance?,
    isSending: Boolean,
    resultMessage: Pair<Boolean, String>?,
    onRefresh: () -> Unit,
    onOpenSpend: () -> Unit,
    onOpenConsolidate: () -> Unit,
) {
    val addressCount = balance?.addressCount ?: 0
    val hasConsolidationCandidate = addressCount > 1
    val canSend = !isSending && (balance?.totalBalance ?: BigInteger.ZERO) > BigInteger.ZERO

    StealthBalanceCard(
        totalBalanceFormatted = balance?.totalBalanceFormatted ?: "0",
        symbol = chainConfig.symbol,
        addressCount = addressCount,
    )

    Spacer(modifier = Modifier.height(24.dp))

    StealthPrivacyInfoCard()

    if (hasConsolidationCandidate) {
        Spacer(modifier = Modifier.height(12.dp))
        ConsolidationNoticeCard(
            addressCount = addressCount,
            onOpenConsolidate = onOpenConsolidate,
        )
    }

    resultMessage?.let { (success, message) ->
        ResultMessageCard(
            success = success,
            message = message,
        )
    }

    StealthWalletActionsRow(
        isSending = isSending,
        canSend = canSend,
        onRefresh = onRefresh,
        onOpenSpend = onOpenSpend,
    )
}

@Composable
private fun StealthBalanceCard(
    totalBalanceFormatted: String,
    symbol: String,
    addressCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.CollectPrimary,
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = AppColors.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_title),
                    color = AppColors.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = totalBalanceFormatted,
                color = AppColors.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = symbol,
                color = AppColors.White.copy(alpha = 0.8f),
                fontSize = 20.sp,
            )

            if (addressCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_addresses, addressCount),
                    color = AppColors.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StealthPrivacyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.Success.copy(alpha = 0.1f),
        elevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = AppColors.Success,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.stealth_wallet_privacy_title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = AppColors.Success,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_privacy_desc),
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                )
            }
        }
    }
}

@Composable
private fun ConsolidationNoticeCard(
    addressCount: Int,
    onOpenConsolidate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.Warning.copy(alpha = 0.1f),
        elevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.LocalGasStation,
                contentDescription = null,
                tint = AppColors.Warning,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.stealth_wallet_gas_title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = AppColors.Warning,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stealth_wallet_gas_desc, addressCount),
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenConsolidate, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = stringResource(R.string.stealth_wallet_consolidate),
                        fontSize = 12.sp,
                        color = AppColors.Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultMessageCard(
    success: Boolean,
    message: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        backgroundColor = if (success) AppColors.Success.copy(alpha = 0.1f) else AppColors.Error.copy(alpha = 0.1f),
        elevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (success) AppColors.Success else AppColors.Error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = localizedErrorMessage(message),
                fontSize = 13.sp,
                color = if (success) AppColors.Success else AppColors.Error,
            )
        }
    }
}

@Composable
private fun StealthWalletActionsRow(
    isSending: Boolean,
    canSend: Boolean,
    onRefresh: () -> Unit,
    onOpenSpend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), enabled = !isSending) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.balance_refresh))
        }

        Button(
            onClick = onOpenSpend,
            modifier = Modifier.weight(1f),
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
        ) {
            Icon(
                @Suppress("DEPRECATION") Icons.Default.Send,
                contentDescription = null,
                tint = AppColors.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.stealth_wallet_send), color = AppColors.White)
        }
    }
}
