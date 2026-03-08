package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.ui.AppColors

@Composable
internal fun ModeSelectionSection(
    currentMode: Mode,
    onModeChange: (Mode) -> Unit,
    onExecutionModeChange: (ExecutionMode) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_mode)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ModeSelectionRow(
                    title = stringResource(R.string.settings_mode_pay),
                    description = stringResource(R.string.settings_mode_pay_desc),
                    icon = Icons.Default.Payment,
                    isSelected = currentMode == Mode.PAY,
                    color = AppColors.PayPrimary,
                    onClick = { onModeChange(Mode.PAY) },
                )
                Divider()
                ModeSelectionRow(
                    title = stringResource(R.string.settings_mode_collect),
                    description = stringResource(R.string.settings_mode_collect_desc),
                    icon = Icons.Default.PointOfSale,
                    isSelected = currentMode == Mode.COLLECT,
                    color = AppColors.CollectPrimary,
                    onClick = { onModeChange(Mode.COLLECT) },
                )
                Divider()
                ModeSelectionRow(
                    title = stringResource(R.string.settings_mode_receive_only),
                    description = stringResource(R.string.settings_mode_receive_only_desc),
                    icon = Icons.Default.Lock,
                    isSelected = currentMode == Mode.RECEIVE_ONLY,
                    color = AppColors.CollectPrimary,
                    onClick = {
                        onModeChange(Mode.RECEIVE_ONLY)
                        onExecutionModeChange(ExecutionMode.RELAYER)
                    },
                )
            }
        }
    }
}

@Composable
internal fun AdvancedModeToggleCard(
    advancedMode: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!advancedMode) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (advancedMode) Icons.Default.Code else Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = if (advancedMode) AppColors.PayPrimary else AppColors.Gray,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text =
                            if (advancedMode) {
                                stringResource(R.string.settings_advanced_mode)
                            } else {
                                stringResource(R.string.settings_simple_mode)
                            },
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text =
                            if (advancedMode) {
                                stringResource(R.string.settings_advanced_mode_desc)
                            } else {
                                stringResource(R.string.settings_simple_mode_desc)
                            },
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                    )
                }
            }
            Switch(
                checked = advancedMode,
                onCheckedChange = onToggle,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = AppColors.PayPrimary,
                        checkedTrackColor = AppColors.PayPrimary.copy(alpha = 0.5f),
                    ),
            )
        }
    }
}

@Composable
internal fun NetworkSection(
    selectedChain: ChainConfig,
    onShowChainDialog: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_network)) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onShowChainDialog() },
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
                    Text(selectedChain.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_chain_id, selectedChain.chainId),
                        fontSize = 14.sp,
                        color = AppColors.Gray,
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
            }
        }
    }
}

internal fun modePrimaryColor(mode: Mode) =
    when (mode) {
        Mode.PAY -> AppColors.PayPrimary
        Mode.COLLECT, Mode.RECEIVE_ONLY -> AppColors.CollectPrimary
    }

@Composable
internal fun ExecutionModeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
internal fun SettingsGroupHeader(
    title: String,
    subtitle: String? = null,
) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Black,
    )
    subtitle?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = it,
            fontSize = 13.sp,
            color = AppColors.Gray,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
internal fun AdvancedModeHint() {
    Text(
        text = stringResource(R.string.settings_advanced_hint),
        fontSize = 13.sp,
        color = AppColors.Gray,
    )
}

@Composable
private fun ModeSelectionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) color else AppColors.Gray,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) color else AppColors.Black,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = AppColors.Gray,
                )
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = color)
        }
    }
}
