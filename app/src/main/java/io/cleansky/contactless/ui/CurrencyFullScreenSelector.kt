package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.cleansky.contactless.R
import io.cleansky.contactless.model.DefaultTokens
import io.cleansky.contactless.model.DefaultTokens.UnderlyingCurrency
import io.cleansky.contactless.model.Token

/**
 * Full screen currency selector.
 * Shows underlying currencies (USD, EUR, BTC, ETH...) with expandable token lists.
 * Used in CollectScreen and SettingsScreen.
 */
@Composable
fun CurrencyFullScreenSelector(
    chainId: Long,
    selectedUnderlying: UnderlyingCurrency?,
    selectedToken: Token?,
    onTokenSelected: (Token, UnderlyingCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    val tokensByUnderlying = remember(chainId) {
        DefaultTokens.getTokensByUnderlying(chainId)
    }
    val availableUnderlyings = remember(chainId) {
        DefaultTokens.getAvailableUnderlyings(chainId)
    }

    var expandedUnderlying by remember {
        mutableStateOf(selectedUnderlying ?: availableUnderlyings.firstOrNull())
    }

    // Full screen dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.collect_select_currency),
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                backgroundColor = Color.White,
                elevation = 0.dp
            )

            Divider()

            // Currency list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                availableUnderlyings.forEach { underlying ->
                    val tokens = tokensByUnderlying[underlying] ?: emptyList()
                    val isExpanded = expandedUnderlying == underlying

                    // Underlying header
                    item(key = "header_${underlying.code}") {
                        UnderlyingRow(
                            underlying = underlying,
                            isExpanded = isExpanded,
                            isSelected = selectedUnderlying == underlying,
                            tokenCount = tokens.size,
                            onClick = {
                                expandedUnderlying = if (isExpanded) null else underlying
                            }
                        )
                    }

                    // Tokens (if expanded)
                    if (isExpanded) {
                        tokens.forEach { token ->
                            item(key = "token_${token.address}") {
                                TokenRow(
                                    token = token,
                                    isSelected = selectedToken?.address == token.address,
                                    onClick = { onTokenSelected(token, underlying) }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun UnderlyingRow(
    underlying: UnderlyingCurrency,
    isExpanded: Boolean,
    isSelected: Boolean,
    tokenCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Symbol circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(getUnderlyingColor(underlying).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getUnderlyingSymbol(underlying),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = getUnderlyingColor(underlying)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = underlying.displayName,
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.Black
                )
                Text(
                    text = "$tokenCount tokens",
                    fontSize = 13.sp,
                    color = AppColors.Gray
                )
            }
        }

        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.Gray
        )
    }
}

@Composable
private fun TokenRow(
    token: Token,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AppColors.CollectPrimary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = token.symbol,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) AppColors.CollectPrimary else AppColors.Black
            )
            Text(
                text = token.name,
                fontSize = 13.sp,
                color = AppColors.Gray
            )
        }

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = AppColors.CollectPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getUnderlyingSymbol(underlying: UnderlyingCurrency): String {
    return when (underlying) {
        UnderlyingCurrency.USD -> "$"
        UnderlyingCurrency.EUR -> "€"
        UnderlyingCurrency.GBP -> "£"
        UnderlyingCurrency.JPY -> "¥"
        UnderlyingCurrency.CHF -> "₣"
        UnderlyingCurrency.ETH -> "Ξ"
        UnderlyingCurrency.BTC -> "₿"
    }
}

private fun getUnderlyingColor(underlying: UnderlyingCurrency): Color {
    return when (underlying) {
        UnderlyingCurrency.USD -> Color(0xFF2E7D32)
        UnderlyingCurrency.EUR -> Color(0xFF1565C0)
        UnderlyingCurrency.GBP -> Color(0xFF7B1FA2)
        UnderlyingCurrency.JPY -> Color(0xFFD32F2F)
        UnderlyingCurrency.CHF -> Color(0xFFE65100)
        UnderlyingCurrency.ETH -> Color(0xFF5C6BC0)
        UnderlyingCurrency.BTC -> Color(0xFFF57C00)
    }
}
