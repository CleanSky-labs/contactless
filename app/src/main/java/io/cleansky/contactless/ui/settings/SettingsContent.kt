package io.cleansky.contactless

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.data.PrivacyPayerRepository
import io.cleansky.contactless.data.TokenAllowlistRepository
import io.cleansky.contactless.data.TokenRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.LanguageSelector
import io.cleansky.contactless.ui.TokenAllowlistSection

@Composable
internal fun SettingsContent(
    currentMode: Mode,
    selectedChain: ChainConfig,
    walletAddress: String?,
    executionMode: ExecutionMode,
    relayerApiKey: String,
    tokenAllowlistRepository: TokenAllowlistRepository,
    tokenRepository: TokenRepository,
    privacyPayerRepository: PrivacyPayerRepository,
    appPreferences: AppPreferences,
    advancedMode: Boolean,
    receiveOnlyEscrow: String,
    receiveOnlyMerchantId: String,
    apiKeyInput: String,
    isCreatingWallet: Boolean,
    createWalletError: String?,
    payerPrivacyEnabled: Boolean,
    merchantDisplayName: String,
    merchantDomain: String,
    stealthEnabled: Boolean,
    isInitializingStealth: Boolean,
    walletManager: SecureWalletManager,
    onModeChange: (Mode) -> Unit,
    onAdvancedModeToggle: (Boolean) -> Unit,
    onExecutionModeChange: (ExecutionMode) -> Unit,
    onApiKeyInputChange: (String) -> Unit,
    onCreateWalletRequested: () -> Unit,
    onShowImportWallet: () -> Unit,
    onShowExecutionModeDialog: () -> Unit,
    onStealthToggle: (Boolean) -> Unit,
    onShowChainDialog: () -> Unit,
    onRequestExportKey: () -> Unit,
    onBack: () -> Unit,
) {
    val layoutVisibility =
        resolveSettingsLayoutVisibility(
            currentMode = currentMode,
            advancedMode = advancedMode,
            walletAddress = walletAddress,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        SettingsGroupHeader(title = stringResource(R.string.settings_group_general))

        ModeSelectionSection(
            currentMode = currentMode,
            onModeChange = onModeChange,
            onExecutionModeChange = onExecutionModeChange,
        )

        Spacer(modifier = Modifier.height(24.dp))

        LanguageSelector(appPreferences = appPreferences)

        Spacer(modifier = Modifier.height(24.dp))

        AdvancedModeToggleCard(
            advancedMode = advancedMode,
            onToggle = onAdvancedModeToggle,
        )

        if (!layoutVisibility.showAdvancedBlocks) {
            Spacer(modifier = Modifier.height(8.dp))
            AdvancedModeHint()
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsGroupHeader(title = stringResource(R.string.settings_group_payments))

        PaymentCoreSections(
            currentMode = currentMode,
            walletAddress = walletAddress,
            receiveOnlyEscrow = receiveOnlyEscrow,
            receiveOnlyMerchantId = receiveOnlyMerchantId,
            apiKeyInput = apiKeyInput,
            appPreferences = appPreferences,
            isCreatingWallet = isCreatingWallet,
            createWalletError = createWalletError,
            onCreateWalletRequested = onCreateWalletRequested,
            onShowImportWallet = onShowImportWallet,
            showKeyManagementSection = layoutVisibility.showKeyManagementSection,
            executionMode = executionMode,
            showExecutionSettings = layoutVisibility.showExecutionSettings,
            onShowExecutionModeDialog = onShowExecutionModeDialog,
            merchantDisplayName = merchantDisplayName,
            merchantDomain = merchantDomain,
            onApiKeyInputChange = onApiKeyInputChange,
        )

        if (layoutVisibility.showAdvancedBlocks) {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_privacy_security))

            PrivacyAndSecuritySections(
                currentMode = currentMode,
                walletAddress = walletAddress,
                selectedChain = selectedChain,
                relayerApiKey = relayerApiKey,
                payerPrivacyEnabled = payerPrivacyEnabled,
                privacyPayerRepository = privacyPayerRepository,
                showPayerPrivacySection = layoutVisibility.showPayerPrivacySection,
                showStealthSection = layoutVisibility.showStealthSection,
                stealthEnabled = stealthEnabled,
                isInitializingStealth = isInitializingStealth,
                onStealthToggle = onStealthToggle,
                onShowImportWallet = onShowImportWallet,
                onRequestExportKey = onRequestExportKey,
                showKeyManagementSection = layoutVisibility.showKeyManagementSection,
                showSecuritySection = layoutVisibility.showSecuritySection,
                walletManager = walletManager,
            )
        }

        if (layoutVisibility.showNetworkAndTokensSection) {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_network_tokens))

            NetworkSection(
                selectedChain = selectedChain,
                onShowChainDialog = onShowChainDialog,
            )

            Spacer(modifier = Modifier.height(24.dp))

            TokenAllowlistSection(
                selectedChain = selectedChain,
                tokenAllowlistRepository = tokenAllowlistRepository,
                tokenRepository = tokenRepository,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = modePrimaryColor(currentMode),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.settings_save), color = AppColors.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun SettingsDialogsHost(
    showChainDialog: Boolean,
    selectedChain: ChainConfig,
    onChainChange: (ChainConfig) -> Unit,
    onDismissChainDialog: () -> Unit,
    showImportDialog: Boolean,
    walletManager: SecureWalletManager,
    onDismissImportDialog: () -> Unit,
    showExportDialog: Boolean,
    exportedKey: String?,
    onDismissExportDialog: () -> Unit,
    showExecutionModeDialog: Boolean,
    executionMode: ExecutionMode,
    onExecutionModeChange: (ExecutionMode) -> Unit,
    onDismissExecutionModeDialog: () -> Unit,
) {
    SettingsDialogs(
        showChainDialog = showChainDialog,
        selectedChain = selectedChain,
        onChainChange = onChainChange,
        onDismissChainDialog = onDismissChainDialog,
        showImportDialog = showImportDialog,
        walletManager = walletManager,
        onDismissImportDialog = onDismissImportDialog,
        showExportDialog = showExportDialog,
        exportedKey = exportedKey,
        onDismissExportDialog = onDismissExportDialog,
        showExecutionModeDialog = showExecutionModeDialog,
        executionMode = executionMode,
        onExecutionModeChange = onExecutionModeChange,
        onDismissExecutionModeDialog = onDismissExecutionModeDialog,
    )
}
