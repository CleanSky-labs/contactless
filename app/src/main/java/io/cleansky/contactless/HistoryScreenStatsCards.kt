package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.model.TransactionStatus
import io.cleansky.contactless.model.TransactionType
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.util.DateTimeUtils
import io.cleansky.contactless.util.NumberFormatter

@Composable
internal fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AppColors.PayPrimary else AppColors.LightGray,
        elevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) Color.White else AppColors.Gray,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                color = if (selected) Color.White else AppColors.Black,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun StatsHeader(
    transactions: List<Transaction>,
    symbol: String,
    decimals: Int,
) {
    val totalReceived =
        transactions
            .filter { it.type == TransactionType.PAYMENT_RECEIVED }
            .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalSent =
        transactions
            .filter { it.type == TransactionType.PAYMENT_SENT }
            .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalRefundsSent =
        transactions
            .filter { it.type == TransactionType.REFUND_SENT }
            .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalRefundsReceived =
        transactions
            .filter { it.type == TransactionType.REFUND_RECEIVED }
            .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val hasReceived = totalReceived > java.math.BigInteger.ZERO
    val hasSent = totalSent > java.math.BigInteger.ZERO

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                stringResource(R.string.history_summary),
                fontSize = 14.sp,
                color = AppColors.Gray,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (hasReceived || (!hasReceived && !hasSent)) {
                    StatItem(
                        label = stringResource(R.string.history_received),
                        amount = NumberFormatter.formatCurrency(totalReceived, decimals),
                        symbol = symbol,
                        color = AppColors.Success,
                    )
                }
                if (hasSent) {
                    StatItem(
                        label = stringResource(R.string.history_sent),
                        amount = NumberFormatter.formatCurrency(totalSent, decimals),
                        symbol = symbol,
                        color = AppColors.PayPrimary,
                    )
                }
                if (totalRefundsSent > java.math.BigInteger.ZERO || totalRefundsReceived > java.math.BigInteger.ZERO) {
                    StatItem(
                        label = stringResource(R.string.history_refunded),
                        amount = NumberFormatter.formatCurrency(totalRefundsSent + totalRefundsReceived, decimals),
                        symbol = symbol,
                        color = AppColors.Error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    amount: String,
    symbol: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = AppColors.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            amount,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(symbol, fontSize = 12.sp, color = AppColors.Gray)
    }
}

@Composable
internal fun TransactionCard(
    transaction: Transaction,
    contactName: String?,
    symbol: String,
    decimals: Int,
    onClick: () -> Unit,
) {
    val style = transaction.type.style()
    val title = contactName ?: stringResource(style.titleResId)
    val signedAmount = "${style.signPrefix}${transaction.getFormattedAmount(decimals)}"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(style.badgeColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.badgeColor,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    DateTimeUtils.formatLocalDateTime(transaction.timestamp),
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                )
                if (contactName == null) {
                    Text(
                        "${transaction.counterparty.take(6)}...${transaction.counterparty.takeLast(4)}",
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedAmount,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = style.amountColor,
                )
                Text(symbol, fontSize = 12.sp, color = AppColors.Gray)

                if (transaction.status != TransactionStatus.CONFIRMED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(transaction.status)
                }
            }
        }
    }
}

private data class TransactionTypeStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeColor: Color,
    val amountColor: Color,
    val signPrefix: String,
    val titleResId: Int,
)

private fun TransactionType.style(): TransactionTypeStyle =
    when (this) {
        TransactionType.PAYMENT_RECEIVED ->
            TransactionTypeStyle(
                icon = Icons.Default.ArrowDownward,
                badgeColor = AppColors.Success,
                amountColor = AppColors.Success,
                signPrefix = "+",
                titleResId = R.string.history_payment_received,
            )
        TransactionType.PAYMENT_SENT ->
            TransactionTypeStyle(
                icon = Icons.Default.ArrowUpward,
                badgeColor = AppColors.PayPrimary,
                amountColor = AppColors.Error,
                signPrefix = "-",
                titleResId = R.string.history_payment_sent,
            )
        TransactionType.REFUND_SENT ->
            TransactionTypeStyle(
                icon = Icons.AutoMirrored.Filled.Undo,
                badgeColor = AppColors.Error,
                amountColor = AppColors.Error,
                signPrefix = "-",
                titleResId = R.string.history_refund_sent,
            )
        TransactionType.REFUND_RECEIVED ->
            TransactionTypeStyle(
                icon = Icons.AutoMirrored.Filled.Redo,
                badgeColor = AppColors.Success,
                amountColor = AppColors.Success,
                signPrefix = "+",
                titleResId = R.string.history_refund_received,
            )
    }

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val (text, color) =
        when (status) {
            TransactionStatus.PENDING -> stringResource(R.string.history_status_pending) to AppColors.Gray
            TransactionStatus.CONFIRMED -> stringResource(R.string.history_status_confirmed) to AppColors.Success
            TransactionStatus.FAILED -> stringResource(R.string.history_status_failed) to AppColors.Error
            TransactionStatus.REFUNDED -> stringResource(R.string.history_status_refunded) to AppColors.Error
            TransactionStatus.PARTIALLY_REFUNDED -> stringResource(R.string.history_status_partial) to AppColors.Error
        }

    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}
