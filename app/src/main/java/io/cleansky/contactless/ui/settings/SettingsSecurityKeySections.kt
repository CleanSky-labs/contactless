package io.cleansky.contactless

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.ConfirmationBottomSheet
import io.cleansky.contactless.ui.SecuritySettingsSection
import kotlinx.coroutines.launch

@Composable
internal fun SecuritySection(
    walletManager: SecureWalletManager,
) {
    SecuritySettingsSection(
        walletManager = walletManager,
        onExportKey = null,
        showDestructiveActions = false,
    )
}

@Composable
internal fun KeyManagementSection(
    walletAddress: String?,
    walletManager: SecureWalletManager,
    onImportWallet: () -> Unit,
    onExportKey: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasWallet = walletAddress != null

    SettingsSection(title = stringResource(R.string.settings_key_management)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_key_management_desc),
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onImportWallet,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_wallet_import))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExportKey,
                    enabled = hasWallet,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.security_export))
                }

                if (!hasWallet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_export_requires_wallet),
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.Error,
                            ),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.security_delete))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.security_delete_title),
            message = "${stringResource(
                R.string.security_delete_warning,
            )}\n\n${stringResource(R.string.security_delete_warning2)}\n\n${stringResource(R.string.security_delete_warning3)}",
            confirmText = stringResource(R.string.security_delete_confirm),
            confirmColor = AppColors.Error,
            icon = Icons.Default.DeleteForever,
            iconColor = AppColors.Error,
            onConfirm = {
                scope.launch {
                    walletManager.deleteWallet()
                    showDeleteDialog = false
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
