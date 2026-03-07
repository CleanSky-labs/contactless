package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import kotlinx.coroutines.launch

/**
 * Componente de configuración de seguridad para incluir en SettingsScreen
 */
@Composable
fun SecuritySettingsSection(
    walletManager: SecureWalletManager,
    onExportKey: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var biometricEnabled by remember { mutableStateOf(false) }
    var hasStrongBox by remember { mutableStateOf(false) }
    var biometricAvailable by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        biometricEnabled = walletManager.isBiometricEnabled()
        hasStrongBox = walletManager.hasStrongBox()
        biometricAvailable = walletManager.isBiometricAvailable()
    }

    Column {
        Text(
            text = stringResource(R.string.security_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Estado de seguridad
                SecurityStatusRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.security_encryption),
                    subtitle = stringResource(R.string.security_encryption_desc),
                    status = SecurityStatus.ACTIVE
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                SecurityStatusRow(
                    icon = Icons.Default.Memory,
                    title = stringResource(R.string.security_strongbox),
                    subtitle = if (hasStrongBox) stringResource(R.string.security_strongbox_active) else stringResource(R.string.security_strongbox_unavailable),
                    status = if (hasStrongBox) SecurityStatus.ACTIVE else SecurityStatus.UNAVAILABLE
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Toggle de biometría
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (biometricEnabled) AppColors.Success else AppColors.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.security_biometric),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (!biometricAvailable) stringResource(R.string.security_biometric_unavailable)
                                else if (biometricEnabled) stringResource(R.string.security_biometric_required)
                                else stringResource(R.string.security_biometric_disabled),
                                fontSize = 12.sp,
                                color = AppColors.Gray
                            )
                        }
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (walletManager.setBiometricEnabled(enabled)) {
                                    biometricEnabled = enabled
                                }
                            }
                        },
                        enabled = biometricAvailable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.Success,
                            checkedTrackColor = AppColors.Success.copy(alpha = 0.5f)
                        )
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Exportar clave (backup)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExportKey)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = AppColors.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.security_export), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.security_export_desc),
                                fontSize = 12.sp,
                                color = AppColors.Gray
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AppColors.Gray
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Eliminar wallet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = AppColors.Error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.security_delete),
                            fontWeight = FontWeight.Medium,
                            color = AppColors.Error
                        )
                        Text(
                            stringResource(R.string.security_delete_desc),
                            fontSize = 12.sp,
                            color = AppColors.Gray
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet de confirmación para eliminar
    if (showDeleteDialog) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.security_delete_title),
            message = "${stringResource(R.string.security_delete_warning)}\n\n${stringResource(R.string.security_delete_warning2)}\n\n${stringResource(R.string.security_delete_warning3)}",
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
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun SecurityStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    status: SecurityStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when (status) {
                SecurityStatus.ACTIVE -> AppColors.Success
                SecurityStatus.WARNING -> AppColors.Error
                SecurityStatus.UNAVAILABLE -> AppColors.Gray
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = AppColors.Gray)
        }
        Box(
            modifier = Modifier
                .background(
                    color = when (status) {
                        SecurityStatus.ACTIVE -> AppColors.Success.copy(alpha = 0.1f)
                        SecurityStatus.WARNING -> AppColors.Error.copy(alpha = 0.1f)
                        SecurityStatus.UNAVAILABLE -> AppColors.Gray.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = when (status) {
                    SecurityStatus.ACTIVE -> stringResource(R.string.security_status_active)
                    SecurityStatus.WARNING -> stringResource(R.string.security_status_warning)
                    SecurityStatus.UNAVAILABLE -> stringResource(R.string.security_status_na)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = when (status) {
                    SecurityStatus.ACTIVE -> AppColors.Success
                    SecurityStatus.WARNING -> AppColors.Error
                    SecurityStatus.UNAVAILABLE -> AppColors.Gray
                }
            )
        }
    }
}

enum class SecurityStatus {
    ACTIVE,
    WARNING,
    UNAVAILABLE
}
