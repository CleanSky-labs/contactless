package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.TokenBalance
import io.cleansky.contactless.ui.AppColors
import kotlinx.coroutines.launch

@Composable
fun BalanceScreen(
    walletAddress: String?,
    selectedChain: ChainConfig,
    tokenRepository: TokenRepository,
    onManageTokens: () -> Unit,
    onBack: () -> Unit
) {
    var balances by remember { mutableStateOf<List<TokenBalance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadBalances() {
        if (walletAddress == null) {
            isLoading = false
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            when (val result = tokenRepository.getAllBalances(walletAddress, selectedChain)) {
                is TokenRepository.BalanceResult.Success -> {
                    balances = result.balances
                    isLoading = false
                }
                is TokenRepository.BalanceResult.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(walletAddress, selectedChain.chainId) {
        loadBalances()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            backgroundColor = AppColors.PayPrimary
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedChain.name,
                    color = AppColors.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (walletAddress != null) {
                    Text(
                        text = "${walletAddress.take(8)}...${walletAddress.takeLast(6)}",
                        color = AppColors.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pay_no_wallet),
                        color = AppColors.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title row with refresh and manage buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.balance_tokens),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { loadBalances() }, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.balance_refresh),
                            tint = AppColors.PayPrimary
                        )
                    }
                }
                IconButton(onClick = onManageTokens) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.balance_manage),
                        tint = AppColors.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        when {
            walletAddress == null -> {
                // No wallet state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = AppColors.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.pay_no_wallet),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.balance_no_wallet_desc),
                            color = AppColors.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            isLoading && balances.isEmpty() -> {
                // Initial loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.balance_loading),
                            color = AppColors.Gray
                        )
                    }
                }
            }
            errorMessage != null && balances.isEmpty() -> {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AppColors.Error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: stringResource(R.string.error_unknown),
                            color = AppColors.Error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadBalances() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            balances.isEmpty() -> {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Token,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AppColors.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.balance_empty),
                            fontSize = 16.sp,
                            color = AppColors.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onManageTokens) {
                            Text(stringResource(R.string.settings_add_token))
                        }
                    }
                }
            }
            else -> {
                // Balances list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(balances) { tokenBalance ->
                        TokenBalanceCard(tokenBalance = tokenBalance)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back button
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.nav_back), color = AppColors.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun TokenBalanceCard(tokenBalance: TokenBalance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token icon placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (tokenBalance.token.isNative) AppColors.PayPrimary.copy(alpha = 0.1f)
                        else AppColors.CollectPrimary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tokenBalance.token.symbol.take(2),
                    fontWeight = FontWeight.Bold,
                    color = if (tokenBalance.token.isNative) AppColors.PayPrimary else AppColors.CollectPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Token info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tokenBalance.token.symbol,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = tokenBalance.token.name,
                    color = AppColors.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Balance
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = tokenBalance.balanceFormatted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                if (tokenBalance.isZero()) {
                    Text(
                        text = "0",
                        color = AppColors.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
