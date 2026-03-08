package io.cleansky.contactless

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

internal enum class ImportMode { PRIVATE_KEY, SEED_PHRASE }

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
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showChainDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExecutionModeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(relayerApiKey) }
    var exportedKey by remember { mutableStateOf<String?>(null) }
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

    val onCreateWalletRequested = {
        if (!isCreatingWallet) {
            isCreatingWallet = true
            createWalletError = null
            scope.launch {
                createWalletError = createWalletErrorMessage(walletManager, activity)
                isCreatingWallet = false
            }
        }
    }

    val onStealthToggleRequested: (Boolean) -> Unit = { enabled ->
        scope.launch {
            isInitializingStealth = enabled
            handleStealthToggle(
                enabled = enabled,
                walletManager = walletManager,
                stealthPaymentRepository = stealthPaymentRepository,
            )
            isInitializingStealth = false
        }
    }

    val onApiKeyInputChange: (String) -> Unit = { value ->
        apiKeyInput = value
        onApiKeyChange(value)
    }

    val onRequestExportKey: () -> Unit = {
        scope.launch {
            when (val result = walletManager.exportPrivateKey(activity)) {
                is SecureWalletManager.ExportResult.Success -> {
                    exportedKey = result.privateKey
                    showExportDialog = true
                }
                is SecureWalletManager.ExportResult.Cancelled -> {}
                else -> {}
            }
        }
    }

    SettingsContent(
        currentMode = currentMode,
        selectedChain = selectedChain,
        walletAddress = walletAddress,
        executionMode = executionMode,
        relayerApiKey = relayerApiKey,
        tokenAllowlistRepository = tokenAllowlistRepository,
        tokenRepository = tokenRepository,
        privacyPayerRepository = privacyPayerRepository,
        appPreferences = appPreferences,
        advancedMode = advancedMode,
        receiveOnlyEscrow = receiveOnlyEscrow,
        receiveOnlyMerchantId = receiveOnlyMerchantId,
        apiKeyInput = apiKeyInput,
        isCreatingWallet = isCreatingWallet,
        createWalletError = createWalletError,
        payerPrivacyEnabled = payerPrivacyEnabled,
        merchantDisplayName = merchantDisplayName,
        merchantDomain = merchantDomain,
        stealthEnabled = stealthEnabled,
        isInitializingStealth = isInitializingStealth,
        walletManager = walletManager,
        onModeChange = onModeChange,
        onAdvancedModeToggle = { enabled ->
            scope.launch { appPreferences.setAdvancedMode(enabled) }
        },
        onExecutionModeChange = onExecutionModeChange,
        onApiKeyInputChange = onApiKeyInputChange,
        onCreateWalletRequested = onCreateWalletRequested,
        onShowImportWallet = { showImportDialog = true },
        onShowExecutionModeDialog = { showExecutionModeDialog = true },
        onStealthToggle = onStealthToggleRequested,
        onShowChainDialog = { showChainDialog = true },
        onRequestExportKey = onRequestExportKey,
        onBack = onBack,
    )

    SettingsDialogsHost(
        showChainDialog = showChainDialog,
        selectedChain = selectedChain,
        onChainChange = onChainChange,
        onDismissChainDialog = { showChainDialog = false },
        showImportDialog = showImportDialog,
        walletManager = walletManager,
        onDismissImportDialog = { showImportDialog = false },
        showExportDialog = showExportDialog,
        exportedKey = exportedKey,
        onDismissExportDialog = {
            showExportDialog = false
            exportedKey = null
        },
        showExecutionModeDialog = showExecutionModeDialog,
        executionMode = executionMode,
        onExecutionModeChange = onExecutionModeChange,
        onDismissExecutionModeDialog = { showExecutionModeDialog = false },
    )
}

@Composable
internal fun PaymentCoreSections(
    currentMode: Mode,
    walletAddress: String?,
    receiveOnlyEscrow: String,
    receiveOnlyMerchantId: String,
    apiKeyInput: String,
    appPreferences: AppPreferences,
    isCreatingWallet: Boolean,
    createWalletError: String?,
    onCreateWalletRequested: () -> Unit,
    onShowImportWallet: () -> Unit,
    showKeyManagementSection: Boolean,
    executionMode: ExecutionMode,
    showExecutionSettings: Boolean,
    merchantDisplayName: String,
    merchantDomain: String,
    onApiKeyInputChange: (String) -> Unit,
    onShowExecutionModeDialog: () -> Unit,
) {
    if (currentMode == Mode.RECEIVE_ONLY) {
        ReceiveOnlySection(
            receiveOnlyEscrow = receiveOnlyEscrow,
            receiveOnlyMerchantId = receiveOnlyMerchantId,
            apiKeyInput = apiKeyInput,
            appPreferences = appPreferences,
            onApiKeyInputChange = onApiKeyInputChange,
        )
        Spacer(modifier = Modifier.height(24.dp))
        MerchantIdentitySection(
            merchantDisplayName = merchantDisplayName,
            merchantDomain = merchantDomain,
            appPreferences = appPreferences,
        )
        Spacer(modifier = Modifier.height(24.dp))
        return
    }

    WalletSection(
        walletAddress = walletAddress,
        isCreatingWallet = isCreatingWallet,
        createWalletError = createWalletError,
        onCreateWallet = onCreateWalletRequested,
        onImportWallet = if (showKeyManagementSection) null else onShowImportWallet,
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (showExecutionSettings) {
        ExecutionSettingsSection(
            executionMode = executionMode,
            apiKeyInput = apiKeyInput,
            onShowExecutionModeDialog = onShowExecutionModeDialog,
            onApiKeyInputChange = onApiKeyInputChange,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (currentMode == Mode.COLLECT) {
        MerchantIdentitySection(
            merchantDisplayName = merchantDisplayName,
            merchantDomain = merchantDomain,
            appPreferences = appPreferences,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun PrivacyAndSecuritySections(
    currentMode: Mode,
    walletAddress: String?,
    selectedChain: ChainConfig,
    relayerApiKey: String,
    payerPrivacyEnabled: Boolean,
    privacyPayerRepository: PrivacyPayerRepository,
    showPayerPrivacySection: Boolean,
    showStealthSection: Boolean,
    stealthEnabled: Boolean,
    isInitializingStealth: Boolean,
    onStealthToggle: (Boolean) -> Unit,
    onShowImportWallet: () -> Unit,
    onRequestExportKey: () -> Unit,
    showKeyManagementSection: Boolean,
    showSecuritySection: Boolean,
    walletManager: SecureWalletManager,
) {
    val scope = rememberCoroutineScope()

    AdvancedPayerPrivacySectionIfNeeded(
        showPayerPrivacySection = showPayerPrivacySection,
        currentMode = currentMode,
        walletAddress = walletAddress,
        selectedChain = selectedChain,
        relayerApiKey = relayerApiKey,
        payerPrivacyEnabled = payerPrivacyEnabled,
        scope = scope,
        privacyPayerRepository = privacyPayerRepository,
    )

    CollectModeSections(
        currentMode = currentMode,
        showStealthSection = showStealthSection,
        walletAddress = walletAddress,
        stealthEnabled = stealthEnabled,
        isInitializingStealth = isInitializingStealth,
        onStealthToggle = onStealthToggle,
    )

    if (showKeyManagementSection) {
        KeyManagementSection(
            walletAddress = walletAddress,
            walletManager = walletManager,
            onImportWallet = onShowImportWallet,
            onExportKey = onRequestExportKey,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSecuritySection) {
        SecuritySection(
            walletManager = walletManager,
        )
    }
}

@Composable
private fun AdvancedPayerPrivacySectionIfNeeded(
    showPayerPrivacySection: Boolean,
    currentMode: Mode,
    walletAddress: String?,
    selectedChain: ChainConfig,
    relayerApiKey: String,
    payerPrivacyEnabled: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    privacyPayerRepository: PrivacyPayerRepository,
) {
    if (!showPayerPrivacySection || currentMode != Mode.PAY || walletAddress == null) {
        return
    }

    PayerPrivacySection(
        selectedChain = selectedChain,
        relayerApiKey = relayerApiKey,
        payerPrivacyEnabled = payerPrivacyEnabled,
        onPrivacyToggle = { enabled ->
            scope.launch {
                if (enabled) {
                    privacyPayerRepository.enablePrivacy()
                } else {
                    privacyPayerRepository.disablePrivacy()
                }
            }
        },
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun CollectModeSections(
    currentMode: Mode,
    showStealthSection: Boolean,
    walletAddress: String?,
    stealthEnabled: Boolean,
    isInitializingStealth: Boolean,
    onStealthToggle: (Boolean) -> Unit,
) {
    if (currentMode != Mode.COLLECT) {
        return
    }

    if (showStealthSection && walletAddress != null) {
        StealthSettingsSection(
            stealthEnabled = stealthEnabled,
            isInitializingStealth = isInitializingStealth,
            onStealthToggle = onStealthToggle,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private suspend fun createWalletErrorMessage(
    walletManager: SecureWalletManager,
    activity: FragmentActivity,
): String? {
    return when (val result = walletManager.createWallet()) {
        is SecureWalletManager.CreateWalletResult.Success -> null
        is SecureWalletManager.CreateWalletResult.EncryptionError ->
            activity.getString(R.string.error_encryption_restart)
        is SecureWalletManager.CreateWalletResult.Error -> result.message
    }
}

private suspend fun handleStealthToggle(
    enabled: Boolean,
    walletManager: SecureWalletManager,
    stealthPaymentRepository: StealthPaymentRepository,
) {
    if (!enabled) {
        stealthPaymentRepository.disableStealth()
        return
    }

    val credentials = walletManager.getCredentialsUnsafe() ?: return
    val stealthKeys = StealthAddress.deriveStealthKeys(credentials)
    val metaAddress = stealthKeys.getMetaAddress()
    stealthPaymentRepository.enableStealth(metaAddress)
}
