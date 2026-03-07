package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.crypto.StealthAddress
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.StealthPaymentRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.PublicBundler
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.BottomSheetContainer
import io.cleansky.contactless.ui.ConfirmationBottomSheet
import io.cleansky.contactless.ui.InputBottomSheet
import io.cleansky.contactless.ui.LanguageSelector
import io.cleansky.contactless.ui.SecuritySettingsSection
import io.cleansky.contactless.ui.TitledBottomSheet
import io.cleansky.contactless.ui.TokenAllowlistSection
import kotlinx.coroutines.launch

private enum class ImportMode { PRIVATE_KEY, SEED_PHRASE }

@Composable
fun SettingsScreen(
    currentMode: Mode,
    selectedChain: ChainConfig,
    walletAddress: String?,
    walletManager: SecureWalletManager,
    executionMode: ExecutionMode,
    relayerApiKey: String,
    activity: FragmentActivity,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    stealthPaymentRepository: StealthPaymentRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    appPreferences: AppPreferences,
    onModeChange: (Mode) -> Unit,
    onChainChange: (ChainConfig) -> Unit,
    onExecutionModeChange: (ExecutionMode) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showChainDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExecutionModeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var privateKeyInput by remember { mutableStateOf("") }
    var seedPhraseInput by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf(ImportMode.PRIVATE_KEY) }
    var apiKeyInput by remember { mutableStateOf(relayerApiKey) }
    var exportedKey by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isCreatingWallet by remember { mutableStateOf(false) }
    var createWalletError by remember { mutableStateOf<String?>(null) }

    // Merchant identity (spec v0.2)
    val merchantDisplayName by appPreferences.merchantDisplayNameFlow.collectAsState(initial = "")
    val merchantDomain by appPreferences.merchantDomainFlow.collectAsState(initial = "")

    // Receive-only mode
    val receiveOnlyEscrow by appPreferences.receiveOnlyEscrowFlow.collectAsState(initial = "")
    val receiveOnlyMerchantId by appPreferences.receiveOnlyMerchantIdFlow.collectAsState(initial = "")

    // Stealth mode (v0.4)
    val stealthEnabled by stealthPaymentRepository.stealthEnabledFlow.collectAsState(initial = false)
    var isInitializingStealth by remember { mutableStateOf(false) }

    // Payer privacy (v0.5)
    val payerPrivacyEnabled by privacyPayerRepository.privacyEnabledFlow.collectAsState(initial = false)

    // Advanced mode
    val advancedMode by appPreferences.advancedModeFlow.collectAsState(initial = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Seccion: Modo
        SettingsSection(title = stringResource(R.string.settings_mode)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeCard(
                    title = stringResource(R.string.settings_mode_pay),
                    description = stringResource(R.string.settings_mode_pay_desc),
                    icon = Icons.Default.Payment,
                    isSelected = currentMode == Mode.PAY,
                    color = AppColors.PayPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(Mode.PAY) }
                )
                ModeCard(
                    title = stringResource(R.string.settings_mode_collect),
                    description = stringResource(R.string.settings_mode_collect_desc),
                    icon = Icons.Default.PointOfSale,
                    isSelected = currentMode == Mode.COLLECT,
                    color = AppColors.CollectPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(Mode.COLLECT) }
                )
                ModeCard(
                    title = stringResource(R.string.settings_mode_receive_only),
                    description = stringResource(R.string.settings_mode_receive_only_desc),
                    icon = Icons.Default.Lock,
                    isSelected = currentMode == Mode.RECEIVE_ONLY,
                    color = AppColors.CollectPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onModeChange(Mode.RECEIVE_ONLY)
                        onExecutionModeChange(ExecutionMode.RELAYER)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Seccion: Idioma
        LanguageSelector(appPreferences = appPreferences)

        Spacer(modifier = Modifier.height(24.dp))

        // Toggle modo avanzado
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { appPreferences.setAdvancedMode(!advancedMode) }
                },
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
                        if (advancedMode) Icons.Default.Code else Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = if (advancedMode) AppColors.PayPrimary else AppColors.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (advancedMode)
                                stringResource(R.string.settings_advanced_mode)
                            else
                                stringResource(R.string.settings_simple_mode),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (advancedMode)
                                stringResource(R.string.settings_advanced_mode_desc)
                            else
                                stringResource(R.string.settings_simple_mode_desc),
                            fontSize = 12.sp,
                            color = AppColors.Gray
                        )
                    }
                }
                Switch(
                    checked = advancedMode,
                    onCheckedChange = { enabled ->
                        scope.launch { appPreferences.setAdvancedMode(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.PayPrimary,
                        checkedTrackColor = AppColors.PayPrimary.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Receive-only configuration section
        if (currentMode == Mode.RECEIVE_ONLY) {
            SettingsSection(title = stringResource(R.string.settings_receive_only)) {
                // Security notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = AppColors.Success.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.receive_only_security_notice),
                            fontSize = 13.sp,
                            color = AppColors.Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = receiveOnlyEscrow,
                            onValueChange = { value ->
                                scope.launch { appPreferences.setReceiveOnlyEscrow(value) }
                            },
                            label = { Text(stringResource(R.string.settings_escrow_address)) },
                            placeholder = { Text("0x...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = AppColors.Gray)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = receiveOnlyMerchantId,
                            onValueChange = { value ->
                                scope.launch { appPreferences.setReceiveOnlyMerchantId(value) }
                            },
                            label = { Text(stringResource(R.string.settings_receive_only_merchant_id)) },
                            placeholder = { Text("0x...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Store, contentDescription = null, tint = AppColors.Gray)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                onApiKeyChange(it)
                            },
                            label = { Text(stringResource(R.string.settings_api_key)) },
                            placeholder = { Text("Gelato API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = AppColors.Gray)
                            }
                        )

                        if (apiKeyInput.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.receive_only_api_key_required),
                                fontSize = 12.sp,
                                color = AppColors.Error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Seccion: Wallet (hidden in RECEIVE_ONLY)
        if (currentMode != Mode.RECEIVE_ONLY) {
        SettingsSection(title = stringResource(R.string.settings_wallet)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (walletAddress != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_wallet_address), fontSize = 14.sp, color = AppColors.Gray)
                                Text(
                                    "${walletAddress.take(8)}...${walletAddress.takeLast(6)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = { }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), tint = AppColors.Gray)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.settings_wallet_none), color = AppColors.Gray)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mostrar error de creación si existe
                    createWalletError?.let { error ->
                        Text(
                            text = error,
                            color = AppColors.Error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (walletAddress == null) {
                            OutlinedButton(
                                onClick = {
                                    if (!isCreatingWallet) {
                                        isCreatingWallet = true
                                        createWalletError = null
                                        scope.launch {
                                            when (val result = walletManager.createWallet()) {
                                                is SecureWalletManager.CreateWalletResult.Success -> {
                                                    // El Flow se actualizará automáticamente
                                                    createWalletError = null
                                                }
                                                is SecureWalletManager.CreateWalletResult.EncryptionError -> {
                                                    createWalletError = activity.getString(R.string.error_encryption_restart)
                                                }
                                                is SecureWalletManager.CreateWalletResult.Error -> {
                                                    createWalletError = result.message
                                                }
                                            }
                                            isCreatingWallet = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isCreatingWallet
                            ) {
                                if (isCreatingWallet) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_wallet_create))
                            }
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_wallet_import))
                        }
                    }
                }
            }
        }
        } // end if (currentMode != Mode.RECEIVE_ONLY)

        Spacer(modifier = Modifier.height(24.dp))

        // Seccion: Red
        SettingsSection(title = stringResource(R.string.settings_network)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChainDialog = true },
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
                    Column {
                        Text(selectedChain.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.settings_chain_id, selectedChain.chainId), fontSize = 14.sp, color = AppColors.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Seccion: Tokens permitidos
        TokenAllowlistSection(
            selectedChain = selectedChain,
            tokenAllowlistRepository = tokenAllowlistRepository,
            tokenRepository = tokenRepository
        )

        // Sección Payer Privacy (solo en modo PAGAR, wallet y modo avanzado)
        if (advancedMode && currentMode == Mode.PAY && walletAddress != null) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.settings_payer_privacy)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_payer_privacy),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.settings_payer_privacy_desc),
                                    fontSize = 12.sp,
                                    color = AppColors.Gray
                                )
                            }

                            Switch(
                                checked = payerPrivacyEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        if (enabled) {
                                            privacyPayerRepository.enablePrivacy()
                                        } else {
                                            privacyPayerRepository.disablePrivacy()
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.PayPrimary,
                                    checkedTrackColor = AppColors.PayPrimary.copy(alpha = 0.5f)
                                ),
                                // Enable if API key provided OR public bundler available for chain
                                enabled = relayerApiKey.isNotEmpty() ||
                                    PublicBundler.hasPublicBundler(selectedChain.chainId)
                            )
                        }

                        // Show bundler availability info
                        val hasPublicBundler = PublicBundler.hasPublicBundler(selectedChain.chainId)
                        if (relayerApiKey.isEmpty() && !hasPublicBundler) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_payer_privacy_requires_api),
                                fontSize = 12.sp,
                                color = AppColors.Error
                            )
                        } else if (relayerApiKey.isEmpty() && hasPublicBundler) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_payer_privacy_public_bundler),
                                fontSize = 12.sp,
                                color = AppColors.Success
                            )
                        }

                        if (payerPrivacyEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = AppColors.Success,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_stealth_enabled),
                                    fontSize = 14.sp,
                                    color = AppColors.Success,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.settings_payer_privacy_info),
                                fontSize = 12.sp,
                                color = AppColors.Gray
                            )
                        }
                    }
                }
            }
        }

        // Solo mostrar opciones de ejecución en modo COBRAR y avanzado
        if (advancedMode && currentMode == Mode.COLLECT) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.settings_execution)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExecutionModeDialog = true },
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
                        Column {
                            Text(
                                when (executionMode) {
                                    ExecutionMode.DIRECT -> stringResource(R.string.settings_exec_direct)
                                    ExecutionMode.RELAYER -> stringResource(R.string.settings_exec_relayer)
                                    ExecutionMode.ACCOUNT_ABSTRACTION -> stringResource(R.string.settings_exec_aa)
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                when (executionMode) {
                                    ExecutionMode.DIRECT -> stringResource(R.string.settings_exec_direct_desc)
                                    ExecutionMode.RELAYER -> stringResource(R.string.settings_exec_relayer_desc)
                                    ExecutionMode.ACCOUNT_ABSTRACTION -> stringResource(R.string.settings_exec_aa_desc)
                                },
                                fontSize = 14.sp,
                                color = AppColors.Gray
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
                    }
                }

                if (executionMode != ExecutionMode.DIRECT) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            onApiKeyChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_api_key)) },
                        placeholder = {
                            Text(
                                when (executionMode) {
                                    ExecutionMode.RELAYER -> "Gelato API Key"
                                    ExecutionMode.ACCOUNT_ABSTRACTION -> "Pimlico API Key"
                                    else -> ""
                                }
                            )
                        },
                        singleLine = true
                    )
                }
            }
        }

        // Sección de identidad del comercio (COBRAR y RECEIVE_ONLY)
        if (currentMode == Mode.COLLECT || currentMode == Mode.RECEIVE_ONLY) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.settings_merchant_identity)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_merchant_identity_desc),
                            fontSize = 12.sp,
                            color = AppColors.Gray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = merchantDisplayName,
                            onValueChange = { value ->
                                scope.launch {
                                    appPreferences.setMerchantDisplayName(value)
                                }
                            },
                            label = { Text(stringResource(R.string.settings_merchant_name)) },
                            placeholder = { Text(stringResource(R.string.settings_merchant_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Store, contentDescription = null, tint = AppColors.Gray)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = merchantDomain,
                            onValueChange = { value ->
                                scope.launch {
                                    appPreferences.setMerchantDomain(value)
                                }
                            },
                            label = { Text(stringResource(R.string.settings_merchant_domain)) },
                            placeholder = { Text(stringResource(R.string.settings_merchant_domain_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null, tint = AppColors.Gray)
                            }
                        )
                    }
                }
            }

            // Sección Stealth Addresses (v0.4) - solo si hay wallet, modo avanzado, y no RECEIVE_ONLY
            if (advancedMode && walletAddress != null && currentMode != Mode.RECEIVE_ONLY) {
                Spacer(modifier = Modifier.height(24.dp))

                SettingsSection(title = stringResource(R.string.settings_stealth)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_stealth),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_stealth_desc),
                                        fontSize = 12.sp,
                                        color = AppColors.Gray
                                    )
                                }

                                if (isInitializingStealth) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Switch(
                                        checked = stealthEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                if (enabled) {
                                                    isInitializingStealth = true
                                                    // Derive stealth keys from wallet
                                                    val credentials = walletManager.getCredentialsUnsafe()
                                                    if (credentials != null) {
                                                        val stealthKeys = StealthAddress.deriveStealthKeys(credentials)
                                                        val metaAddress = stealthKeys.getMetaAddress()
                                                        stealthPaymentRepository.enableStealth(metaAddress)
                                                    }
                                                    isInitializingStealth = false
                                                } else {
                                                    stealthPaymentRepository.disableStealth()
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AppColors.CollectPrimary,
                                            checkedTrackColor = AppColors.CollectPrimary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            if (stealthEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = AppColors.Success,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_stealth_enabled),
                                        fontSize = 14.sp,
                                        color = AppColors.Success,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.settings_stealth_info),
                                    fontSize = 12.sp,
                                    color = AppColors.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sección de seguridad (solo si hay wallet, no en RECEIVE_ONLY)
        if (walletAddress != null && currentMode != Mode.RECEIVE_ONLY) {
            Spacer(modifier = Modifier.height(24.dp))

            SecuritySettingsSection(
                walletManager = walletManager,
                onExportKey = {
                    scope.launch {
                        when (val result = walletManager.exportPrivateKey(activity)) {
                            is SecureWalletManager.ExportResult.Success -> {
                                exportedKey = result.privateKey
                                showExportDialog = true
                            }
                            is SecureWalletManager.ExportResult.Cancelled -> { }
                            else -> { }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = when (currentMode) {
                    Mode.PAY -> AppColors.PayPrimary
                    Mode.COLLECT, Mode.RECEIVE_ONLY -> AppColors.CollectPrimary
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.settings_save), color = AppColors.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Bottom sheet seleccion de red
    if (showChainDialog) {
        TitledBottomSheet(
            title = stringResource(R.string.settings_network),
            onDismiss = { showChainDialog = false }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ChainConfig.CHAINS.forEach { chain ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChainChange(chain)
                                showChainDialog = false
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(chain.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Text("ID: ${chain.chainId}", fontSize = 12.sp, color = AppColors.Gray)
                        }
                        if (chain.chainId == selectedChain.chainId) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success)
                        }
                    }
                    if (chain != ChainConfig.CHAINS.last()) {
                        Divider()
                    }
                }
            }
        }
    }

    // Bottom sheet importar wallet
    if (showImportDialog) {
        TitledBottomSheet(
            title = stringResource(R.string.import_title),
            onDismiss = {
                showImportDialog = false
                importError = null
                privateKeyInput = ""
                seedPhraseInput = ""
                importMode = ImportMode.PRIVATE_KEY
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Tab selector
                TabRow(
                    selectedTabIndex = if (importMode == ImportMode.PRIVATE_KEY) 0 else 1,
                    backgroundColor = AppColors.LightGray,
                    contentColor = AppColors.Black
                ) {
                    Tab(
                        selected = importMode == ImportMode.PRIVATE_KEY,
                        onClick = {
                            importMode = ImportMode.PRIVATE_KEY
                            importError = null
                        },
                        text = {
                            Text(
                                stringResource(R.string.import_private_key),
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = importMode == ImportMode.SEED_PHRASE,
                        onClick = {
                            importMode = ImportMode.SEED_PHRASE
                            importError = null
                        },
                        text = {
                            Text(
                                stringResource(R.string.import_seed_phrase),
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (importMode) {
                    ImportMode.PRIVATE_KEY -> {
                        Text(stringResource(R.string.import_prompt), fontSize = 14.sp, color = AppColors.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = privateKeyInput,
                            onValueChange = {
                                privateKeyInput = it
                                importError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0x...") },
                            singleLine = true,
                            isError = importError != null,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = AppColors.PayPrimary,
                                cursorColor = AppColors.PayPrimary
                            )
                        )
                    }
                    ImportMode.SEED_PHRASE -> {
                        Text(stringResource(R.string.import_prompt_seed), fontSize = 14.sp, color = AppColors.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = seedPhraseInput,
                            onValueChange = {
                                seedPhraseInput = it
                                importError = null
                            },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            placeholder = { Text(stringResource(R.string.import_seed_hint)) },
                            singleLine = false,
                            maxLines = 4,
                            isError = importError != null,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = AppColors.PayPrimary,
                                cursorColor = AppColors.PayPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val wordCount = seedPhraseInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                        Text(
                            text = "$wordCount / 12-24",
                            fontSize = 12.sp,
                            color = if (wordCount in listOf(12, 15, 18, 21, 24)) AppColors.Success else AppColors.Gray
                        )
                    }
                }

                importError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    val errorText = when (it) {
                        "invalid_key" -> stringResource(R.string.import_error_invalid)
                        "invalid_mnemonic" -> stringResource(R.string.import_error_invalid_seed)
                        "encryption_error" -> stringResource(R.string.import_error_encryption)
                        else -> it
                    }
                    Text(errorText, color = AppColors.Error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val result = when (importMode) {
                                ImportMode.PRIVATE_KEY -> walletManager.importWallet(privateKeyInput)
                                ImportMode.SEED_PHRASE -> walletManager.importWalletFromMnemonic(seedPhraseInput)
                            }

                            when (result) {
                                is SecureWalletManager.ImportWalletResult.Success -> {
                                    privateKeyInput = ""
                                    seedPhraseInput = ""
                                    showImportDialog = false
                                }
                                is SecureWalletManager.ImportWalletResult.InvalidKey -> {
                                    importError = "invalid_key"
                                }
                                is SecureWalletManager.ImportWalletResult.InvalidMnemonic -> {
                                    importError = "invalid_mnemonic"
                                }
                                is SecureWalletManager.ImportWalletResult.EncryptionError -> {
                                    importError = "encryption_error"
                                }
                                is SecureWalletManager.ImportWalletResult.Error -> {
                                    importError = result.message
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = when (importMode) {
                        ImportMode.PRIVATE_KEY -> privateKeyInput.length >= 64
                        ImportMode.SEED_PHRASE -> {
                            val wordCount = seedPhraseInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                            wordCount in listOf(12, 15, 18, 21, 24)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.import_button), color = AppColors.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // Bottom sheet exportar clave
    if (showExportDialog && exportedKey != null) {
        BottomSheetContainer(
            onDismiss = {
                showExportDialog = false
                exportedKey = null
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AppColors.Error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.export_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.export_warning),
                    fontSize = 14.sp,
                    color = AppColors.Error,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    backgroundColor = AppColors.LightGray,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        exportedKey!!,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showExportDialog = false
                        exportedKey = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.export_understood), color = AppColors.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // Bottom sheet modo de ejecución
    if (showExecutionModeDialog) {
        TitledBottomSheet(
            title = stringResource(R.string.settings_execution),
            onDismiss = { showExecutionModeDialog = false }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ExecutionModeOption(
                    title = stringResource(R.string.settings_exec_direct),
                    description = stringResource(R.string.settings_exec_direct_desc),
                    isSelected = executionMode == ExecutionMode.DIRECT,
                    onClick = {
                        onExecutionModeChange(ExecutionMode.DIRECT)
                        showExecutionModeDialog = false
                    }
                )
                Divider()
                ExecutionModeOption(
                    title = stringResource(R.string.settings_exec_relayer),
                    description = stringResource(R.string.settings_exec_relayer_desc),
                    isSelected = executionMode == ExecutionMode.RELAYER,
                    enabled = selectedChain.supportsRelayer,
                    onClick = {
                        onExecutionModeChange(ExecutionMode.RELAYER)
                        showExecutionModeDialog = false
                    }
                )
                Divider()
                ExecutionModeOption(
                    title = stringResource(R.string.settings_exec_aa),
                    description = stringResource(R.string.settings_exec_aa_desc),
                    isSelected = executionMode == ExecutionMode.ACCOUNT_ABSTRACTION,
                    enabled = selectedChain.supportsAA,
                    onClick = {
                        onExecutionModeChange(ExecutionMode.ACCOUNT_ABSTRACTION)
                        showExecutionModeDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExecutionModeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = if (enabled) AppColors.Black else AppColors.Gray)
            Text(description, fontSize = 12.sp, color = AppColors.Gray)
            if (!enabled) {
                Text(stringResource(R.string.settings_not_available), fontSize = 11.sp, color = AppColors.Error)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = if (isSelected) 4.dp else 1.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isSelected) color.copy(alpha = 0.1f) else AppColors.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) color else AppColors.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else AppColors.Black
            )
            Text(text = description, fontSize = 12.sp, color = AppColors.Gray)
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}
