package io.cleansky.contactless.ui.collect

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.localizedErrorMessage
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.DefaultTokens.UnderlyingCurrency
import io.cleansky.contactless.model.PaymentRequest
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.CurrencyFullScreenSelector
import io.cleansky.contactless.ui.LogoLoadingIndicator
import io.cleansky.contactless.ui.PaymentSuccessAnimation
import io.cleansky.contactless.util.AmountUtils

@Composable
internal fun CollectSuccessContent(
    successHash: String,
    lastTxAmount: String?,
    lastTxSymbol: String?,
    chain: ChainConfig,
    onReset: () -> Unit,
) {
    val formattedAmount = AmountUtils.formatRawAmountOrPlaceholder(lastTxAmount, chain.decimals)
    PaymentSuccessAnimation(
        amount = formattedAmount,
        symbol = lastTxSymbol ?: chain.symbol,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text =
            stringResource(
                R.string.tx_hash,
                "${successHash.take(10)}...${successHash.takeLast(8)}",
            ),
        fontSize = 12.sp,
        color = AppColors.Gray,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(stringResource(R.string.collect_new), color = AppColors.White, fontSize = 18.sp)
    }
}

@Composable
internal fun NoEscrowConfiguredView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AppColors.CollectPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.receive_only_no_escrow),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.receive_only_no_escrow_desc),
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun NoWalletCollectView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.collect_no_wallet),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.collect_no_wallet_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AmountInputView(
    amountText: String,
    selectedUnderlying: UnderlyingCurrency?,
    selectedToken: Token?,
    chainId: Long,
    chainName: String,
    stealthEnabled: Boolean,
    onAmountChange: (String) -> Unit,
    onCurrencySelected: (Token, UnderlyingCurrency) -> Unit,
    onCollect: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        var showCurrencySelector by remember { mutableStateOf(false) }

        Text(
            text = stringResource(R.string.collect_amount),
            fontSize = 20.sp,
            color = AppColors.Gray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                        onAmountChange(value)
                    }
                },
                modifier = Modifier.width(180.dp),
                textStyle =
                    LocalTextStyle.current.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = AppColors.CollectText,
                    ),
                placeholder = {
                    Text(
                        "0.00",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.CollectPrimary,
                        unfocusedBorderColor = AppColors.LightGray,
                    ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                modifier = Modifier.clickable { showCurrencySelector = true },
                shape = RoundedCornerShape(8.dp),
                color = AppColors.CollectPrimary.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedToken?.symbol ?: "---",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.CollectPrimary,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppColors.CollectPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (showCurrencySelector) {
            CurrencyFullScreenSelector(
                chainId = chainId,
                selectedUnderlying = selectedUnderlying,
                selectedToken = selectedToken,
                onTokenSelected = { token, underlying ->
                    onCurrencySelected(token, underlying)
                    showCurrencySelector = false
                },
                onDismiss = { showCurrencySelector = false },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.pay_network, chainName),
            fontSize = 14.sp,
            color = AppColors.Gray,
        )

        if (stealthEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppColors.Success.copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = AppColors.Success,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stealth_address_label),
                        fontSize = 12.sp,
                        color = AppColors.Success,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (stealthEnabled) 32.dp else 48.dp))

        Button(
            onClick = onCollect,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            enabled = amountText.isNotEmpty() && amountText.toDoubleOrNull() != null && amountText.toDouble() > 0 && selectedToken != null,
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = AppColors.CollectPrimary,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = AppColors.White,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.collect_button),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
            )
        }
    }
}

@Composable
internal fun BroadcastingRequestView(
    request: PaymentRequest?,
    selectedToken: Token?,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = AppColors.CollectPrimary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.collect_waiting),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.CollectText,
        )

        Spacer(modifier = Modifier.height(16.dp))

        request?.let {
            val decimals = selectedToken?.decimals ?: 6
            Text(
                text = "${it.getAmountFormatted(decimals)} ${selectedToken?.symbol ?: ""}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.CollectPrimary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.collect_waiting_desc),
            fontSize = 18.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
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
internal fun CollectErrorView(
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
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.CollectPrimary),
        ) {
            Text(stringResource(R.string.retry), color = AppColors.White)
        }
    }
}
