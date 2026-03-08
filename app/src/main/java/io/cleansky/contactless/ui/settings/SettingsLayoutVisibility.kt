package io.cleansky.contactless

internal data class SettingsLayoutVisibility(
    val showAdvancedBlocks: Boolean,
    val showKeyManagementSection: Boolean,
    val showExecutionSettings: Boolean,
    val showSecuritySection: Boolean,
    val showPayerPrivacySection: Boolean,
    val showStealthSection: Boolean,
    val showNetworkAndTokensSection: Boolean,
)

internal fun resolveSettingsLayoutVisibility(
    currentMode: Mode,
    advancedMode: Boolean,
    walletAddress: String?,
): SettingsLayoutVisibility {
    val hasWallet = walletAddress != null
    val showAdvancedBlocks = advancedMode
    val showKeyManagementSection = advancedMode && currentMode != Mode.RECEIVE_ONLY
    val showExecutionSettings = advancedMode && currentMode == Mode.COLLECT
    val showSecuritySection = advancedMode && hasWallet && currentMode != Mode.RECEIVE_ONLY
    val showPayerPrivacySection = advancedMode && hasWallet && currentMode == Mode.PAY
    val showStealthSection = advancedMode && hasWallet && currentMode == Mode.COLLECT
    val showNetworkAndTokensSection = advancedMode

    return SettingsLayoutVisibility(
        showAdvancedBlocks = showAdvancedBlocks,
        showKeyManagementSection = showKeyManagementSection,
        showExecutionSettings = showExecutionSettings,
        showSecuritySection = showSecuritySection,
        showPayerPrivacySection = showPayerPrivacySection,
        showStealthSection = showStealthSection,
        showNetworkAndTokensSection = showNetworkAndTokensSection,
    )
}
