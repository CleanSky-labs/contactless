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
    onBack: () -> Unit,
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

    val uiState = resolveBalanceUiState(walletAddress, isLoading, balances, errorMessage)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        BalanceHeaderCard(selectedChain = selectedChain, walletAddress = walletAddress)

        Spacer(modifier = Modifier.height(16.dp))

        BalanceToolbar(
            isLoading = isLoading,
            onRefresh = { loadBalances() },
            onManageTokens = onManageTokens,
        )

        Spacer(modifier = Modifier.height(8.dp))

        BalanceMainContent(
            uiState = uiState,
            balances = balances,
            errorMessage = errorMessage,
            onRetry = { loadBalances() },
            onManageTokens = onManageTokens,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.nav_back), color = AppColors.White, fontSize = 16.sp)
        }
    }
}

private enum class BalanceUiState {
    NO_WALLET,
    INITIAL_LOADING,
    ERROR,
    EMPTY,
    LIST,
}

private fun resolveBalanceUiState(
    walletAddress: String?,
    isLoading: Boolean,
    balances: List<TokenBalance>,
    errorMessage: String?,
): BalanceUiState {
    if (walletAddress == null) return BalanceUiState.NO_WALLET
    if (isLoading && balances.isEmpty()) return BalanceUiState.INITIAL_LOADING
    if (errorMessage != null && balances.isEmpty()) return BalanceUiState.ERROR
    if (balances.isEmpty()) return BalanceUiState.EMPTY
    return BalanceUiState.LIST
}

@Composable
private fun BalanceHeaderCard(
    selectedChain: ChainConfig,
    walletAddress: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = AppColors.PayPrimary,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = selectedChain.name,
                color = AppColors.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (walletAddress != null) {
                Text(
                    text = "${walletAddress.take(8)}...${walletAddress.takeLast(6)}",
                    color = AppColors.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = stringResource(R.string.pay_no_wallet),
                    color = AppColors.White,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun BalanceToolbar(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onManageTokens: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.balance_tokens),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Row {
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.balance_refresh),
                        tint = AppColors.PayPrimary,
                    )
                }
            }
            IconButton(onClick = onManageTokens) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.balance_manage),
                    tint = AppColors.Gray,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.BalanceMainContent(
    uiState: BalanceUiState,
    balances: List<TokenBalance>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onManageTokens: () -> Unit,
) {
    when (uiState) {
        BalanceUiState.NO_WALLET -> BalanceNoWalletContent()
        BalanceUiState.INITIAL_LOADING -> BalanceLoadingContent()
        BalanceUiState.ERROR -> BalanceErrorContent(errorMessage = errorMessage, onRetry = onRetry)
        BalanceUiState.EMPTY -> BalanceEmptyContent(onManageTokens = onManageTokens)
        BalanceUiState.LIST ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(balances) { tokenBalance ->
                    TokenBalanceCard(tokenBalance = tokenBalance)
                }
            }
    }
}

@Composable
private fun ColumnScope.BalanceNoWalletContent() {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = AppColors.Gray,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.pay_no_wallet),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.balance_no_wallet_desc),
                color = AppColors.Gray,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ColumnScope.BalanceLoadingContent() {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.balance_loading),
                color = AppColors.Gray,
            )
        }
    }
}

@Composable
private fun ColumnScope.BalanceErrorContent(
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppColors.Error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage ?: stringResource(R.string.error_unknown),
                color = AppColors.Error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun ColumnScope.BalanceEmptyContent(onManageTokens: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Token,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppColors.Gray,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.balance_empty),
                fontSize = 16.sp,
                color = AppColors.Gray,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onManageTokens) {
                Text(stringResource(R.string.settings_add_token))
            }
        }
    }
}

@Composable
private fun TokenBalanceCard(tokenBalance: TokenBalance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (tokenBalance.token.isNative) {
                                AppColors.PayPrimary.copy(alpha = 0.1f)
                            } else {
                                AppColors.CollectPrimary.copy(alpha = 0.1f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tokenBalance.token.symbol.take(2),
                    fontWeight = FontWeight.Bold,
                    color = if (tokenBalance.token.isNative) AppColors.PayPrimary else AppColors.CollectPrimary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tokenBalance.token.symbol,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Text(
                    text = tokenBalance.token.name,
                    color = AppColors.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = tokenBalance.balanceFormatted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                if (tokenBalance.isZero()) {
                    Text(
                        text = "0",
                        color = AppColors.Gray,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
