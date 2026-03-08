package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.model.PublicBundler
import io.cleansky.contactless.ui.AppColors

@Composable
internal fun PayerPrivacySection(
    selectedChain: ChainConfig,
    relayerApiKey: String,
    payerPrivacyEnabled: Boolean,
    onPrivacyToggle: (Boolean) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_payer_privacy)) {
        val hasPublicBundler = PublicBundler.hasPublicBundler(selectedChain.chainId)
        val privacyToggleEnabled = relayerApiKey.isNotEmpty() || hasPublicBundler

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_payer_privacy),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_payer_privacy_desc),
                            fontSize = 12.sp,
                            color = AppColors.Gray,
                        )
                    }

                    Switch(
                        checked = payerPrivacyEnabled,
                        onCheckedChange = onPrivacyToggle,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = AppColors.PayPrimary,
                                checkedTrackColor = AppColors.PayPrimary.copy(alpha = 0.5f),
                            ),
                        enabled = privacyToggleEnabled,
                    )
                }

                if (relayerApiKey.isEmpty() && !hasPublicBundler) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_payer_privacy_requires_api),
                        fontSize = 12.sp,
                        color = AppColors.Error,
                    )
                } else if (relayerApiKey.isEmpty() && hasPublicBundler) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_payer_privacy_public_bundler),
                        fontSize = 12.sp,
                        color = AppColors.Success,
                    )
                }

                if (payerPrivacyEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_stealth_enabled),
                            fontSize = 14.sp,
                            color = AppColors.Success,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_payer_privacy_info),
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExecutionSettingsSection(
    executionMode: ExecutionMode,
    apiKeyInput: String,
    onShowExecutionModeDialog: () -> Unit,
    onApiKeyInputChange: (String) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_execution)) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onShowExecutionModeDialog() },
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
                Column {
                    Text(
                        executionModeTitle(executionMode),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        executionModeDescription(executionMode),
                        fontSize = 14.sp,
                        color = AppColors.Gray,
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
            }
        }

        if (executionMode != ExecutionMode.DIRECT) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = onApiKeyInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text(executionModeApiKeyPlaceholder(executionMode)) },
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun StealthSettingsSection(
    stealthEnabled: Boolean,
    isInitializingStealth: Boolean,
    onStealthToggle: (Boolean) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_stealth)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_stealth),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_stealth_desc),
                            fontSize = 12.sp,
                            color = AppColors.Gray,
                        )
                    }

                    if (isInitializingStealth) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Switch(
                            checked = stealthEnabled,
                            onCheckedChange = onStealthToggle,
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CollectPrimary,
                                    checkedTrackColor = AppColors.CollectPrimary.copy(alpha = 0.5f),
                                ),
                        )
                    }
                }

                if (stealthEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_stealth_enabled),
                            fontSize = 14.sp,
                            color = AppColors.Success,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_stealth_info),
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                    )
                }
            }
        }
    }
}
