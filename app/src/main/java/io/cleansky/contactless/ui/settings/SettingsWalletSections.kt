package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.data.AppPreferences
import io.cleansky.contactless.ui.AppColors
import kotlinx.coroutines.launch

@Composable
internal fun ReceiveOnlySection(
    receiveOnlyEscrow: String,
    receiveOnlyMerchantId: String,
    apiKeyInput: String,
    appPreferences: AppPreferences,
    onApiKeyInputChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    SettingsSection(title = stringResource(R.string.settings_receive_only)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
            backgroundColor = AppColors.Success.copy(alpha = 0.1f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = AppColors.Success,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.receive_only_security_notice),
                    fontSize = 13.sp,
                    color = AppColors.Success,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
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
                    },
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
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyInputChange,
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    placeholder = { Text("Gelato API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = AppColors.Gray)
                    },
                )

                if (apiKeyInput.isBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.receive_only_api_key_required),
                        fontSize = 12.sp,
                        color = AppColors.Error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WalletSection(
    walletAddress: String?,
    isCreatingWallet: Boolean,
    createWalletError: String?,
    onCreateWallet: () -> Unit,
    onImportWallet: (() -> Unit)?,
) {
    SettingsSection(title = stringResource(R.string.settings_wallet)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (walletAddress != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_wallet_address), fontSize = 14.sp, color = AppColors.Gray)
                            Text(
                                "${walletAddress.take(8)}...${walletAddress.takeLast(6)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
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

                createWalletError?.let { error ->
                    Text(
                        text = error,
                        color = AppColors.Error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (walletAddress == null) {
                        OutlinedButton(
                            onClick = onCreateWallet,
                            modifier = Modifier.weight(1f),
                            enabled = !isCreatingWallet,
                        ) {
                            if (isCreatingWallet) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_wallet_create))
                        }
                    }
                    if (onImportWallet != null) {
                        OutlinedButton(
                            onClick = onImportWallet,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_wallet_import))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MerchantIdentitySection(
    merchantDisplayName: String,
    merchantDomain: String,
    appPreferences: AppPreferences,
) {
    val scope = rememberCoroutineScope()

    SettingsSection(title = stringResource(R.string.settings_merchant_identity)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_merchant_identity_desc),
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = merchantDisplayName,
                    onValueChange = { value ->
                        scope.launch { appPreferences.setMerchantDisplayName(value) }
                    },
                    label = { Text(stringResource(R.string.settings_merchant_name)) },
                    placeholder = { Text(stringResource(R.string.settings_merchant_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Store, contentDescription = null, tint = AppColors.Gray)
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = merchantDomain,
                    onValueChange = { value ->
                        scope.launch { appPreferences.setMerchantDomain(value) }
                    },
                    label = { Text(stringResource(R.string.settings_merchant_domain)) },
                    placeholder = { Text(stringResource(R.string.settings_merchant_domain_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, tint = AppColors.Gray)
                    },
                )
            }
        }
    }
}
