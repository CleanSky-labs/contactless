package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.model.TransactionStatus
import io.cleansky.contactless.model.TransactionType
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.TitledBottomSheet
import io.cleansky.contactless.util.DateTimeUtils

@Composable
internal fun ContactFilterSheet(
    uniqueAddresses: List<String>,
    contacts: List<Contact>,
    selectedContactAddress: String?,
    onDismiss: () -> Unit,
    onSelectAddress: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    TitledBottomSheet(
        title = stringResource(R.string.history_filter_by_contact),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClearFilter() }
                        .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = null, tint = AppColors.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.history_show_all))
            }
            Divider()

            uniqueAddresses.forEach { address ->
                val contact = contacts.find { it.address.equals(address, ignoreCase = true) }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAddress(address) }
                            .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (contact?.name?.take(2) ?: address.take(2)).uppercase(),
                            color = AppColors.PayPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact?.name ?: stringResource(R.string.address_book_unknown), fontWeight = FontWeight.Medium)
                        Text("${address.take(8)}...${address.takeLast(6)}", fontSize = 12.sp, color = AppColors.Gray)
                    }
                    if (selectedContactAddress?.equals(address, ignoreCase = true) == true) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success)
                    }
                }
                if (address != uniqueAddresses.last()) Divider()
            }
        }
    }
}

@Composable
internal fun TransactionDetailSheet(
    transaction: Transaction,
    selectedChain: ChainConfig,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onAddOrEditContact: (String) -> Unit,
    onRefund: () -> Unit,
) {
    val contactName = contacts.find { it.address.equals(transaction.counterparty, ignoreCase = true) }?.name
    val clipboardManager = LocalClipboardManager.current
    val isIncoming = transaction.type == TransactionType.PAYMENT_RECEIVED || transaction.type == TransactionType.REFUND_RECEIVED

    TitledBottomSheet(
        title = transactionDetailTitle(transaction.type),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TransactionAmountHeader(
                transaction = transaction,
                selectedChain = selectedChain,
                isIncoming = isIncoming,
            )

            Spacer(modifier = Modifier.height(20.dp))

            TransactionCounterpartyCard(
                transaction = transaction,
                isIncoming = isIncoming,
                contactName = contactName,
                onAddOrEditContact = onAddOrEditContact,
                onCopyAddress = { clipboardManager.setText(AnnotatedString(transaction.counterparty)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            TransactionMetadataRows(
                transaction = transaction,
                onCopyTxHash = { clipboardManager.setText(AnnotatedString(transaction.txHash)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            RefundActionButton(
                transaction = transaction,
                onRefund = onRefund,
            )
        }
    }
}

@Composable
private fun transactionDetailTitle(type: TransactionType): String {
    return when (type) {
        TransactionType.PAYMENT_RECEIVED -> stringResource(R.string.history_payment_received)
        TransactionType.PAYMENT_SENT -> stringResource(R.string.history_payment_sent)
        TransactionType.REFUND_SENT -> stringResource(R.string.history_refund_sent)
        TransactionType.REFUND_RECEIVED -> stringResource(R.string.history_refund_received)
    }
}

@Composable
private fun TransactionAmountHeader(
    transaction: Transaction,
    selectedChain: ChainConfig,
    isIncoming: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text = "${if (isIncoming) "+" else "-"}${transaction.getFormattedAmount(selectedChain.decimals)} ${selectedChain.symbol}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (isIncoming) AppColors.Success else AppColors.Error,
        )
    }
}

@Composable
private fun TransactionCounterpartyCard(
    transaction: Transaction,
    isIncoming: Boolean,
    contactName: String?,
    onAddOrEditContact: (String) -> Unit,
    onCopyAddress: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = AppColors.LightGray,
        elevation = 0.dp,
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
                        if (isIncoming) stringResource(R.string.history_from) else stringResource(R.string.history_to),
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                    )
                    if (contactName != null) {
                        Text(contactName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                    Text(
                        "${transaction.counterparty.take(12)}...${transaction.counterparty.takeLast(8)}",
                        fontSize = 13.sp,
                        color = if (contactName != null) AppColors.Gray else AppColors.Black,
                    )
                }
                IconButton(onClick = onCopyAddress) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppColors.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onAddOrEditContact(transaction.counterparty) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    if (contactName != null) Icons.Default.Edit else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (contactName != null) {
                        stringResource(R.string.history_edit_contact)
                    } else {
                        stringResource(R.string.history_add_contact)
                    },
                )
            }
        }
    }
}

@Composable
private fun TransactionMetadataRows(
    transaction: Transaction,
    onCopyTxHash: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.history_date), color = AppColors.Gray)
        Text(DateTimeUtils.formatLocalDateTime(transaction.timestamp))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.history_tx_hash), color = AppColors.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${transaction.txHash.take(8)}...${transaction.txHash.takeLast(6)}", fontSize = 13.sp)
            IconButton(
                onClick = onCopyTxHash,
                modifier = Modifier.size(32.dp),
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
}

@Composable
private fun RefundActionButton(
    transaction: Transaction,
    onRefund: () -> Unit,
) {
    if (transaction.type != TransactionType.PAYMENT_RECEIVED || transaction.status != TransactionStatus.CONFIRMED) {
        return
    }

    Button(
        onClick = onRefund,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Error),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(@Suppress("DEPRECATION") Icons.Default.Undo, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.history_refund), color = Color.White)
    }
}

@Composable
internal fun AddContactSheet(
    address: String,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String) -> Unit,
) {
    val existingContact = contacts.find { it.address.equals(address, ignoreCase = true) }
    var name by remember { mutableStateOf(existingContact?.name ?: "") }
    var note by remember { mutableStateOf(existingContact?.note ?: "") }

    TitledBottomSheet(
        title = if (existingContact != null) stringResource(R.string.address_book_edit) else stringResource(R.string.address_book_add),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "${address.take(12)}...${address.takeLast(8)}",
                fontSize = 13.sp,
                color = AppColors.Gray,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            androidx.compose.material.OutlinedTextField(
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

            androidx.compose.material.OutlinedTextField(
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
                onClick = { if (name.isNotBlank()) onSave(name, note) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}
