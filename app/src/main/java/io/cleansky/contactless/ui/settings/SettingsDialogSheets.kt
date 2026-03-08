package io.cleansky.contactless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.ExecutionMode
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.BottomSheetContainer
import io.cleansky.contactless.ui.TitledBottomSheet
import kotlinx.coroutines.launch

@Composable
internal fun SettingsDialogs(
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
    if (showChainDialog) {
        ChainSelectionSheet(
            selectedChain = selectedChain,
            onChainChange = onChainChange,
            onDismiss = onDismissChainDialog,
        )
    }

    if (showImportDialog) {
        ImportWalletSheet(
            walletManager = walletManager,
            onDismiss = onDismissImportDialog,
            onImported = onDismissImportDialog,
        )
    }

    if (showExportDialog && exportedKey != null) {
        ExportPrivateKeySheet(
            exportedKey = exportedKey,
            onDismiss = onDismissExportDialog,
        )
    }

    if (showExecutionModeDialog) {
        ExecutionModeSheet(
            executionMode = executionMode,
            selectedChain = selectedChain,
            onExecutionModeChange = onExecutionModeChange,
            onDismiss = onDismissExecutionModeDialog,
        )
    }
}

@Composable
private fun ChainSelectionSheet(
    selectedChain: ChainConfig,
    onChainChange: (ChainConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    TitledBottomSheet(
        title = stringResource(R.string.settings_network),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ChainConfig.CHAINS.forEach { chain ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChainChange(chain)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(chain.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("ID: ${chain.chainId}", fontSize = 12.sp, color = AppColors.Gray)
                    }
                    if (chain.chainId == selectedChain.chainId) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success)
                    }
                }
                if (chain != ChainConfig.CHAINS.last()) {
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun ImportWalletSheet(
    walletManager: SecureWalletManager,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var privateKeyInput by remember { mutableStateOf("") }
    var seedPhraseInput by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf(ImportMode.PRIVATE_KEY) }
    var importError by remember { mutableStateOf<String?>(null) }

    TitledBottomSheet(
        title = stringResource(R.string.import_title),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TabRow(
                selectedTabIndex = if (importMode == ImportMode.PRIVATE_KEY) 0 else 1,
                backgroundColor = AppColors.LightGray,
                contentColor = AppColors.Black,
            ) {
                Tab(
                    selected = importMode == ImportMode.PRIVATE_KEY,
                    onClick = {
                        importMode = ImportMode.PRIVATE_KEY
                        importError = null
                    },
                    text = { Text(stringResource(R.string.import_private_key), fontSize = 12.sp) },
                )
                Tab(
                    selected = importMode == ImportMode.SEED_PHRASE,
                    onClick = {
                        importMode = ImportMode.SEED_PHRASE
                        importError = null
                    },
                    text = { Text(stringResource(R.string.import_seed_phrase), fontSize = 12.sp) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (importMode) {
                ImportMode.PRIVATE_KEY -> {
                    Text(stringResource(R.string.import_prompt), fontSize = 14.sp, color = AppColors.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = privateKeyInput,
                        onValueChange = {
                            privateKeyInput = it
                            importError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0x...") },
                        singleLine = true,
                        isError = importError != null,
                        colors =
                            TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = AppColors.PayPrimary,
                                cursorColor = AppColors.PayPrimary,
                            ),
                    )
                }

                ImportMode.SEED_PHRASE -> {
                    Text(stringResource(R.string.import_prompt_seed), fontSize = 14.sp, color = AppColors.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = seedPhraseInput,
                        onValueChange = {
                            seedPhraseInput = it
                            importError = null
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        placeholder = { Text(stringResource(R.string.import_seed_hint)) },
                        singleLine = false,
                        maxLines = 4,
                        isError = importError != null,
                        colors =
                            TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = AppColors.PayPrimary,
                                cursorColor = AppColors.PayPrimary,
                            ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val wordCount = seedPhraseInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    Text(
                        text = "$wordCount / 12-24",
                        fontSize = 12.sp,
                        color = if (wordCount in listOf(12, 15, 18, 21, 24)) AppColors.Success else AppColors.Gray,
                    )
                }
            }

            importError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = importErrorMessage(it),
                    color = AppColors.Error,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        when (val result = performWalletImport(walletManager, importMode, privateKeyInput, seedPhraseInput)) {
                            is SecureWalletManager.ImportWalletResult.Success -> onImported()
                            is SecureWalletManager.ImportWalletResult.InvalidKey -> importError = "invalid_key"
                            is SecureWalletManager.ImportWalletResult.InvalidMnemonic -> importError = "invalid_mnemonic"
                            is SecureWalletManager.ImportWalletResult.EncryptionError -> importError = "encryption_error"
                            is SecureWalletManager.ImportWalletResult.Error -> importError = result.message
                        }
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = isImportInputValid(importMode, privateKeyInput, seedPhraseInput),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.import_button), color = AppColors.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ExportPrivateKeySheet(
    exportedKey: String,
    onDismiss: () -> Unit,
) {
    BottomSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.Error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.export_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.export_warning),
                fontSize = 14.sp,
                color = AppColors.Error,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                backgroundColor = AppColors.LightGray,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    exportedKey,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.export_understood), color = AppColors.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ExecutionModeSheet(
    executionMode: ExecutionMode,
    selectedChain: ChainConfig,
    onExecutionModeChange: (ExecutionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    TitledBottomSheet(
        title = stringResource(R.string.settings_execution),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ExecutionModeOption(
                title = stringResource(R.string.settings_exec_direct),
                description = stringResource(R.string.settings_exec_direct_desc),
                isSelected = executionMode == ExecutionMode.DIRECT,
                onClick = {
                    onExecutionModeChange(ExecutionMode.DIRECT)
                    onDismiss()
                },
            )
            Divider()
            ExecutionModeOption(
                title = stringResource(R.string.settings_exec_relayer),
                description = stringResource(R.string.settings_exec_relayer_desc),
                isSelected = executionMode == ExecutionMode.RELAYER,
                enabled = selectedChain.supportsRelayer,
                onClick = {
                    onExecutionModeChange(ExecutionMode.RELAYER)
                    onDismiss()
                },
            )
            Divider()
            ExecutionModeOption(
                title = stringResource(R.string.settings_exec_aa),
                description = stringResource(R.string.settings_exec_aa_desc),
                isSelected = executionMode == ExecutionMode.ACCOUNT_ABSTRACTION,
                enabled = selectedChain.supportsAA,
                onClick = {
                    onExecutionModeChange(ExecutionMode.ACCOUNT_ABSTRACTION)
                    onDismiss()
                },
            )
        }
    }
}

private suspend fun performWalletImport(
    walletManager: SecureWalletManager,
    importMode: ImportMode,
    privateKeyInput: String,
    seedPhraseInput: String,
): SecureWalletManager.ImportWalletResult {
    return when (importMode) {
        ImportMode.PRIVATE_KEY -> walletManager.importWallet(privateKeyInput)
        ImportMode.SEED_PHRASE -> walletManager.importWalletFromMnemonic(seedPhraseInput)
    }
}

private fun isImportInputValid(
    importMode: ImportMode,
    privateKeyInput: String,
    seedPhraseInput: String,
): Boolean {
    return when (importMode) {
        ImportMode.PRIVATE_KEY -> privateKeyInput.length >= 64
        ImportMode.SEED_PHRASE -> {
            val wordCount = seedPhraseInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            wordCount in listOf(12, 15, 18, 21, 24)
        }
    }
}

@Composable
private fun importErrorMessage(error: String): String {
    return when (error) {
        "invalid_key" -> stringResource(R.string.import_error_invalid)
        "invalid_mnemonic" -> stringResource(R.string.import_error_invalid_seed)
        "encryption_error" -> stringResource(R.string.import_error_encryption)
        else -> error
    }
}
