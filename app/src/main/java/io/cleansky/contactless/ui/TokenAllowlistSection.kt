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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

/**
 * Token allowlist section for Settings.
 * Shows a card that opens a full-screen token manager.
 */
@Composable
fun TokenAllowlistSection(
    selectedChain: ChainConfig,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
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
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { showFullScreen = true },
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Token,
                        contentDescription = null,
                        tint = AppColors.Gray,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_tokens),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${allowedTokens.size} tokens",
                            fontSize = 12.sp,
                            color = AppColors.Gray,
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
            onDismiss = { showFullScreen = false },
        )
    }
}

@Composable
private fun TokenAllowlistFullScreen(
    selectedChain: ChainConfig,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    onDismiss: () -> Unit,
) {
    var allowedTokens by remember { mutableStateOf<List<Token>>(emptyList()) }
    var expandedUnderlying by remember { mutableStateOf<UnderlyingCurrency?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var tokenAddressInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val tokensByUnderlying =
        remember(selectedChain.chainId) {
            DefaultTokens.getTokensByUnderlying(selectedChain.chainId)
        }
    val availableUnderlyings =
        remember(selectedChain.chainId) {
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_tokens),
                            fontWeight = FontWeight.Medium,
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
                    elevation = 0.dp,
                )

                Divider()

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
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
                        val enabledCount =
                            tokens.count { token ->
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
                                },
                            )
                        }

                        if (isExpanded) {
                            tokens.forEach { token ->
                                item(key = "token_${token.address}") {
                                    val isEnabled =
                                        allowedTokens.any {
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
                                        },
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
                    },
                )
            }
        }
    }
}
