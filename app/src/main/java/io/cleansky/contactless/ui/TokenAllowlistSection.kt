package io.cleansky.contactless.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.cleansky.contactless.R
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.DefaultTokens
import io.cleansky.contactless.model.DefaultTokens.UnderlyingCurrency
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.util.AddressUtils
import kotlinx.coroutines.launch

/**
 * Token allowlist section for Settings.
 * Shows a card that opens a full-screen token manager.
 */
@Composable
fun TokenAllowlistSection(
    selectedChain: ChainConfig,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository
) {
    var allowedTokens by remember { mutableStateOf<List<Token>>(emptyList()) }
    var showFullScreen by remember { mutableStateOf(false) }

    // Load allowed tokens for current chain
    LaunchedEffect(selectedChain.chainId) {
        tokenAllowlistRepository.initializeDefaultTokensIfNeeded(selectedChain.chainId)
        tokenAllowlistRepository.allowedTokensFlow.collect { tokens ->
            allowedTokens = tokens.filter { it.chainId == selectedChain.chainId }
        }
    }

    Column {
        Text(
            text = stringResource(R.string.settings_tokens),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showFullScreen = true },
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Token,
                        contentDescription = null,
                        tint = AppColors.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_tokens),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${allowedTokens.size} tokens",
                            fontSize = 12.sp,
                            color = AppColors.Gray
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
            }
        }
    }

    // Full screen token manager
    if (showFullScreen) {
        TokenAllowlistFullScreen(
            selectedChain = selectedChain,
            tokenAllowlistRepository = tokenAllowlistRepository,
            tokenRepository = tokenRepository,
            onDismiss = { showFullScreen = false }
        )
    }
}

@Composable
private fun TokenAllowlistFullScreen(
    selectedChain: ChainConfig,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    onDismiss: () -> Unit
) {
    var allowedTokens by remember { mutableStateOf<List<Token>>(emptyList()) }
    var expandedUnderlying by remember { mutableStateOf<UnderlyingCurrency?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var tokenAddressInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tokensByUnderlying = remember(selectedChain.chainId) {
        DefaultTokens.getTokensByUnderlying(selectedChain.chainId)
    }
    val availableUnderlyings = remember(selectedChain.chainId) {
        DefaultTokens.getAvailableUnderlyings(selectedChain.chainId)
    }

    // Load allowed tokens for current chain
    LaunchedEffect(selectedChain.chainId) {
        tokenAllowlistRepository.allowedTokensFlow.collect { tokens ->
            allowedTokens = tokens.filter { it.chainId == selectedChain.chainId }
        }
    }

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
                        stringResource(R.string.settings_tokens),
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCustomDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_token))
                    }
                },
                backgroundColor = Color.White,
                elevation = 0.dp
            )

            Divider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Native token (always shown first, always enabled)
                item(key = "native") {
                    val nativeToken = DefaultTokens.getNativeToken(selectedChain.chainId)
                    NativeTokenRow(token = nativeToken)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Underlying categories
                availableUnderlyings.forEach { underlying ->
                    val tokens = tokensByUnderlying[underlying] ?: emptyList()
                    val isExpanded = expandedUnderlying == underlying
                    val enabledCount = tokens.count { token ->
                        allowedTokens.any { it.address.equals(token.address, ignoreCase = true) }
                    }

                    item(key = "header_${underlying.code}") {
                        UnderlyingCategoryHeader(
                            underlying = underlying,
                            tokenCount = tokens.size,
                            enabledCount = enabledCount,
                            isExpanded = isExpanded,
                            onClick = {
                                expandedUnderlying = if (isExpanded) null else underlying
                            }
                        )
                    }

                    if (isExpanded) {
                        tokens.forEach { token ->
                            item(key = "token_${token.address}") {
                                val isEnabled = allowedTokens.any {
                                    it.address.equals(token.address, ignoreCase = true)
                                }
                                TokenToggleRow(
                                    token = token,
                                    isEnabled = isEnabled,
                                    onToggle = { enabled ->
                                        scope.launch {
                                            if (enabled) {
                                                tokenAllowlistRepository.addToken(token)
                                            } else {
                                                tokenAllowlistRepository.removeToken(token.address, token.chainId)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item(key = "divider_${underlying.code}") {
                        if (underlying != availableUnderlyings.last()) {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // Add custom token dialog
        if (showAddCustomDialog) {
            AddCustomTokenDialog(
            tokenAddressInput = tokenAddressInput,
            onAddressChange = {
                tokenAddressInput = it
                validationError = null
            },
            isValidating = isValidating,
            validationError = validationError,
            allowedTokens = allowedTokens,
            selectedChain = selectedChain,
            tokenRepository = tokenRepository,
            tokenAllowlistRepository = tokenAllowlistRepository,
            onValidatingChange = { isValidating = it },
            onValidationError = { validationError = it },
            onDismiss = {
                showAddCustomDialog = false
                tokenAddressInput = ""
                validationError = null
            },
            onSuccess = {
                showAddCustomDialog = false
                tokenAddressInput = ""
            }
            )
        }
        }
    }
}

@Composable
private fun NativeTokenRow(token: Token) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.PayPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ξ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.PayPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = token.symbol,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AppColors.PayPrimary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = stringResource(R.string.token_native),
                        fontSize = 10.sp,
                        color = AppColors.PayPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = token.name,
                color = AppColors.Gray,
                fontSize = 12.sp
            )
        }

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AppColors.Success,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun UnderlyingCategoryHeader(
    underlying: UnderlyingCurrency,
    tokenCount: Int,
    enabledCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(getUnderlyingColor(underlying).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = getUnderlyingSymbol(underlying),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = getUnderlyingColor(underlying)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = underlying.displayName,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(
                text = "$enabledCount / $tokenCount tokens",
                fontSize = 12.sp,
                color = AppColors.Gray
            )
        }

        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.Gray
        )
    }
}

@Composable
private fun TokenToggleRow(
    token: Token,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = token.symbol,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = if (isEnabled) AppColors.Black else AppColors.Gray
            )
            Text(
                text = token.name,
                fontSize = 11.sp,
                color = AppColors.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.Success,
                checkedTrackColor = AppColors.Success.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun AddCustomTokenDialog(
    tokenAddressInput: String,
    onAddressChange: (String) -> Unit,
    isValidating: Boolean,
    validationError: String?,
    allowedTokens: List<Token>,
    selectedChain: ChainConfig,
    tokenRepository: TokenRepository,
    tokenAllowlistRepository: TokenAllowlistRepository,
    onValidatingChange: (Boolean) -> Unit,
    onValidationError: (String?) -> Unit,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    TitledBottomSheet(
        title = stringResource(R.string.settings_add_token),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.add_token_prompt),
                fontSize = 14.sp,
                color = AppColors.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = tokenAddressInput,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0x...") },
                singleLine = true,
                isError = validationError != null,
                enabled = !isValidating,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.Success,
                    cursorColor = AppColors.Success
                )
            )
            validationError?.let {
                Text(
                    text = it,
                    color = AppColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (isValidating) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Success
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_token_validating),
                        fontSize = 12.sp,
                        color = AppColors.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val address = tokenAddressInput.trim()
                    if (!AddressUtils.isValidEvmAddress(address)) {
                        onValidationError(context.getString(R.string.error_invalid_address_format))
                        return@Button
                    }

                    if (allowedTokens.any { it.address.equals(address, ignoreCase = true) }) {
                        onValidationError(context.getString(R.string.error_token_already_added))
                        return@Button
                    }

                    onValidatingChange(true)
                    onValidationError(null)

                    scope.launch {
                        when (val result = tokenRepository.validateToken(address, selectedChain)) {
                            is TokenRepository.ValidationResult.Valid -> {
                                tokenAllowlistRepository.addToken(result.token)
                                onSuccess()
                            }
                            is TokenRepository.ValidationResult.Invalid -> {
                                onValidationError(result.message)
                            }
                        }
                        onValidatingChange(false)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = tokenAddressInput.isNotBlank() && !isValidating,
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Success),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(stringResource(R.string.add_token_button), color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
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
