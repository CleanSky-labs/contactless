package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.localizedErrorMessage
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Token

@Composable
internal fun ColumnScope.ConfirmContent(
    contact: Contact,
    amount: String,
    token: Token,
    chainName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(modifier = Modifier.weight(1f))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.send_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "$amount ${token.symbol}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.PayPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.send_to, ""),
                fontSize = 14.sp,
                color = Color.Gray,
            )
            Text(
                text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${contact.address.take(10)}...${contact.address.takeLast(6)}",
                fontSize = 12.sp,
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = chainName,
                    fontSize = 14.sp,
                    color = Color.Gray,
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
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onConfirm,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.PayPrimary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.send_confirm, amount, token.symbol),
            fontSize = 18.sp,
            color = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cancel),
            fontSize = 18.sp,
        )
    }
}

@Composable
internal fun ProcessingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = AppColors.PayPrimary,
            strokeWidth = 6.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.send_processing),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ColumnScope.SuccessContent(
    txHash: String,
    contact: Contact,
    amount: String,
    symbol: String,
    onNewTransfer: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(AppColors.CollectPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = AppColors.CollectPrimary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.send_success),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.send_success_desc, amount, symbol, contact.name.ifBlank { contact.address.take(10) + "..." }),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.tx_hash, "${txHash.take(10)}...${txHash.takeLast(6)}"),
            fontSize = 12.sp,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onNewTransfer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.send_new),
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.CollectPrimary,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.refund_done),
                fontSize = 18.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun ColumnScope.ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.Red,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.send_error),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = localizedErrorMessage(message),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onRetry,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.PayPrimary,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.retry),
                fontSize = 18.sp,
                color = Color.White,
            )
        }
    }
}
