package io.cleansky.contactless

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cleansky.contactless.model.ExecutionMode

@Composable
internal fun executionModeTitle(mode: ExecutionMode): String {
    return when (mode) {
        ExecutionMode.DIRECT -> stringResource(R.string.settings_exec_direct)
        ExecutionMode.RELAYER -> stringResource(R.string.settings_exec_relayer)
        ExecutionMode.ACCOUNT_ABSTRACTION -> stringResource(R.string.settings_exec_aa)
    }
}

@Composable
internal fun executionModeDescription(mode: ExecutionMode): String {
    return when (mode) {
        ExecutionMode.DIRECT -> stringResource(R.string.settings_exec_direct_desc)
        ExecutionMode.RELAYER -> stringResource(R.string.settings_exec_relayer_desc)
        ExecutionMode.ACCOUNT_ABSTRACTION -> stringResource(R.string.settings_exec_aa_desc)
    }
}

internal fun executionModeApiKeyPlaceholder(mode: ExecutionMode): String {
    return when (mode) {
        ExecutionMode.RELAYER -> "Gelato API Key"
        ExecutionMode.ACCOUNT_ABSTRACTION -> "Pimlico API Key"
        ExecutionMode.DIRECT -> ""
    }
}
