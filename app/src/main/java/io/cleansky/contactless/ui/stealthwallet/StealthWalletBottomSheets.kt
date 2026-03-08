package io.cleansky.contactless.ui.stealthwallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.BottomSheetContainer
import io.cleansky.contactless.ui.TitledBottomSheet

@Composable
internal fun SpendStealthBottomSheet(
    isSending: Boolean,
    balanceFormatted: String,
    symbol: String,
    recipientAddress: String,
    amountText: String,
    onRecipientChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSetMax: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    TitledBottomSheet(
        title = stringResource(R.string.stealth_wallet_send_title),
        subtitle = stringResource(R.string.stealth_wallet_available, balanceFormatted, symbol),
        onDismiss = onDismiss,
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
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.CollectPrimary,
                        cursorColor = AppColors.CollectPrimary,
                    ),
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
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.CollectPrimary,
                        cursorColor = AppColors.CollectPrimary,
                    ),
                trailingIcon = {
                    TextButton(onClick = onSetMax, enabled = !isSending) {
                        Text("MAX", fontSize = 12.sp, color = AppColors.CollectPrimary)
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = !isSending && recipientAddress.startsWith("0x") && recipientAddress.length == 42 && amountText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        @Suppress("DEPRECATION") Icons.Default.Send,
                        contentDescription = null,
                        tint = AppColors.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.stealth_wallet_send), color = AppColors.White, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
            ) {
                Text(stringResource(R.string.pay_cancel), color = AppColors.Gray)
            }
        }
    }
}

@Composable
internal fun ConsolidateStealthBottomSheet(
    walletManager: SecureWalletManager,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    BottomSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.Warning,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_warning),
                fontSize = 14.sp,
                color = AppColors.Gray,
                textAlign = TextAlign.Center,
            )
            if (walletAddress == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.error_wallet_not_configured),
                    fontSize = 12.sp,
                    color = AppColors.Error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                backgroundColor = AppColors.Error.copy(alpha = 0.1f),
                elevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.stealth_wallet_consolidate_privacy_loss),
                    fontSize = 12.sp,
                    color = AppColors.Error,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stealth_wallet_consolidate_benefit),
                fontSize = 12.sp,
                color = AppColors.Gray,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Warning),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(R.string.stealth_wallet_consolidate_confirm),
                        color = AppColors.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
            ) {
                Text(stringResource(R.string.pay_cancel), color = AppColors.Gray)
            }
        }
    }
}
