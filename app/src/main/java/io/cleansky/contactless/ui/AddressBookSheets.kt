package io.cleansky.contactless.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.util.AddressUtils

@Composable
internal fun AddContactSheet(
    onDismiss: () -> Unit,
    onSave: (address: String, name: String, note: String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    TitledBottomSheet(
        title = stringResource(R.string.address_book_add),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    addressError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_address)) },
                placeholder = { Text("0x...") },
                singleLine = true,
                isError = addressError != null,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )
            addressError?.let {
                Text(it, color = AppColors.Error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_name)) },
                placeholder = { Text(stringResource(R.string.address_book_name_hint)) },
                singleLine = true,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_note)) },
                placeholder = { Text(stringResource(R.string.address_book_note_hint)) },
                singleLine = false,
                maxLines = 2,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val trimmedAddress = address.trim()
                    if (!AddressUtils.isValidEvmAddress(trimmedAddress)) {
                        addressError = context.getString(R.string.error_invalid_address)
                        return@Button
                    }
                    if (name.isBlank()) {
                        return@Button
                    }
                    onSave(trimmedAddress, name.trim(), note.trim())
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = address.isNotBlank() && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun EditContactSheet(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(contact.name) }
    var note by remember { mutableStateOf(contact.note) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    TitledBottomSheet(
        title = stringResource(R.string.address_book_edit),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AppColors.LightGray,
                elevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${contact.address.take(12)}...${contact.address.takeLast(8)}",
                        fontSize = 13.sp,
                        color = AppColors.Gray,
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(contact.address)) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = AppColors.Gray,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_name)) },
                singleLine = true,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_note)) },
                singleLine = false,
                maxLines = 2,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), note.trim())
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = AppColors.Error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.delete), color = AppColors.Error)
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.address_book_delete_title),
            message = stringResource(R.string.address_book_delete_message, contact.name),
            confirmText = stringResource(R.string.delete),
            confirmColor = AppColors.Error,
            icon = Icons.Default.Delete,
            iconColor = AppColors.Error,
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
