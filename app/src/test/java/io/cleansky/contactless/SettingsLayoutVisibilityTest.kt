package io.cleansky.contactless

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutVisibilityTest {
    @Test
    fun `given simple mode when resolve visibility then advanced blocks are hidden`() {
        // Given
        val currentMode = Mode.PAY
        val advancedMode = false
        val walletAddress = "0x123"

        // When
        val visibility = resolveSettingsLayoutVisibility(currentMode, advancedMode, walletAddress)

        // Then
        assertFalse(visibility.showAdvancedBlocks)
        assertFalse(visibility.showKeyManagementSection)
        assertFalse(visibility.showExecutionSettings)
        assertFalse(visibility.showSecuritySection)
        assertFalse(visibility.showPayerPrivacySection)
        assertFalse(visibility.showStealthSection)
        assertFalse(visibility.showNetworkAndTokensSection)
    }

    @Test
    fun `given collect advanced with wallet when resolve visibility then execution stealth and security are visible`() {
        // Given
        val currentMode = Mode.COLLECT
        val advancedMode = true
        val walletAddress = "0xabc"

        // When
        val visibility = resolveSettingsLayoutVisibility(currentMode, advancedMode, walletAddress)

        // Then
        assertTrue(visibility.showAdvancedBlocks)
        assertTrue(visibility.showKeyManagementSection)
        assertTrue(visibility.showExecutionSettings)
        assertTrue(visibility.showSecuritySection)
        assertFalse(visibility.showPayerPrivacySection)
        assertTrue(visibility.showStealthSection)
        assertTrue(visibility.showNetworkAndTokensSection)
    }

    @Test
    fun `given pay advanced without wallet when resolve visibility then payer privacy and security stay hidden`() {
        // Given
        val currentMode = Mode.PAY
        val advancedMode = true
        val walletAddress: String? = null

        // When
        val visibility = resolveSettingsLayoutVisibility(currentMode, advancedMode, walletAddress)

        // Then
        assertTrue(visibility.showAdvancedBlocks)
        assertTrue(visibility.showKeyManagementSection)
        assertFalse(visibility.showExecutionSettings)
        assertFalse(visibility.showSecuritySection)
        assertFalse(visibility.showPayerPrivacySection)
        assertFalse(visibility.showStealthSection)
        assertTrue(visibility.showNetworkAndTokensSection)
    }

    @Test
    fun `given receive only advanced with wallet when resolve visibility then advanced payments remain safe`() {
        // Given
        val currentMode = Mode.RECEIVE_ONLY
        val advancedMode = true
        val walletAddress = "0xwallet"

        // When
        val visibility = resolveSettingsLayoutVisibility(currentMode, advancedMode, walletAddress)

        // Then
        assertTrue(visibility.showAdvancedBlocks)
        assertFalse(visibility.showKeyManagementSection)
        assertFalse(visibility.showExecutionSettings)
        assertFalse(visibility.showSecuritySection)
        assertFalse(visibility.showPayerPrivacySection)
        assertFalse(visibility.showStealthSection)
        assertTrue(visibility.showNetworkAndTokensSection)
    }
}
