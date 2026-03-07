package io.cleansky.contactless

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.model.*
import io.cleansky.contactless.ui.AppColors
import io.cleansky.contactless.ui.InputBottomSheet
import io.cleansky.contactless.ui.TitledBottomSheet
import io.cleansky.contactless.util.NumberFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryFilter { ALL, RECEIVED, SENT }

@Composable
fun HistoryScreen(
    transactionRepository: TransactionRepository,
    addressBookRepository: AddressBookRepository,
    selectedChain: ChainConfig,
    onTransactionClick: (Transaction) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val transactions by transactionRepository.transactionsFlow.collectAsState(initial = emptyList())
    val contacts by addressBookRepository.contactsFlow.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedContactAddress by remember { mutableStateOf<String?>(null) }
    var showContactFilter by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf<String?>(null) }
    var showTransactionDetail by remember { mutableStateOf<Transaction?>(null) }

    // Filtrar por chain actual
    val chainTransactions = transactions.filter { it.chainId == selectedChain.chainId }

    // Aplicar filtros
    val filteredTransactions = applyHistoryFilters(chainTransactions, filter, selectedContactAddress)

    // Obtener direcciones únicas para el filtro
    val uniqueAddresses = chainTransactions.map { it.counterparty }.distinct()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Stats header
        StatsHeader(
            transactions = filteredTransactions,
            symbol = selectedChain.symbol,
            decimals = selectedChain.decimals
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = stringResource(R.string.history_filter_all),
                selected = filter == HistoryFilter.ALL && selectedContactAddress == null,
                onClick = {
                    filter = HistoryFilter.ALL
                    selectedContactAddress = null
                }
            )
            FilterChip(
                label = stringResource(R.string.history_filter_received),
                selected = filter == HistoryFilter.RECEIVED,
                onClick = { filter = HistoryFilter.RECEIVED }
            )
            FilterChip(
                label = stringResource(R.string.history_filter_sent),
                selected = filter == HistoryFilter.SENT,
                onClick = { filter = HistoryFilter.SENT }
            )

            if (uniqueAddresses.isNotEmpty()) {
                Divider(
                    modifier = Modifier
                        .height(32.dp)
                        .width(1.dp),
                    color = AppColors.LightGray
                )
                FilterChip(
                    label = contactFilterLabel(selectedContactAddress, contacts),
                    selected = selectedContactAddress != null,
                    onClick = { showContactFilter = true },
                    icon = Icons.Default.Person
                )
            }
        }

        HistoryTransactionsContent(
            filteredTransactions = filteredTransactions,
            selectedContactAddress = selectedContactAddress,
            filter = filter,
            selectedChain = selectedChain,
            contacts = contacts,
            onTransactionClick = { showTransactionDetail = it }
        )
    }

    if (showContactFilter) {
        ContactFilterSheet(
            uniqueAddresses = uniqueAddresses,
            contacts = contacts,
            selectedContactAddress = selectedContactAddress,
            onDismiss = { showContactFilter = false },
            onSelectAddress = { address ->
                selectedContactAddress = address
                showContactFilter = false
            },
            onClearFilter = {
                selectedContactAddress = null
                showContactFilter = false
            }
        )
    }

    showTransactionDetail?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            selectedChain = selectedChain,
            contacts = contacts,
            onDismiss = { showTransactionDetail = null },
            onAddOrEditContact = { showAddContact = it },
            onRefund = {
                showTransactionDetail = null
                onTransactionClick(transaction)
            }
        )
    }

    showAddContact?.let { address ->
        AddContactSheet(
            address = address,
            contacts = contacts,
            onDismiss = { showAddContact = null },
            onSave = { name, note ->
                scope.launch {
                    addressBookRepository.addContact(
                        io.cleansky.contactless.model.Contact(
                            address = address,
                            name = name.trim(),
                            note = note.trim()
                        )
                    )
                    showAddContact = null
                }
            }
        )
    }
}

private fun applyHistoryFilters(
    transactions: List<Transaction>,
    filter: HistoryFilter,
    selectedContactAddress: String?
): List<Transaction> {
    return transactions.filter { tx ->
        val typeMatch = when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.RECEIVED -> tx.type == TransactionType.PAYMENT_RECEIVED || tx.type == TransactionType.REFUND_RECEIVED
            HistoryFilter.SENT -> tx.type == TransactionType.PAYMENT_SENT || tx.type == TransactionType.REFUND_SENT
        }
        val contactMatch = selectedContactAddress?.let { tx.counterparty.equals(it, ignoreCase = true) } ?: true
        typeMatch && contactMatch
    }
}

@Composable
private fun contactFilterLabel(selectedContactAddress: String?, contacts: List<Contact>): String {
    if (selectedContactAddress == null) return stringResource(R.string.history_filter_contact)
    val contact = contacts.find { it.address.equals(selectedContactAddress, ignoreCase = true) }
    return contact?.name ?: "${selectedContactAddress.take(6)}..."
}

@Composable
private fun HistoryTransactionsContent(
    filteredTransactions: List<Transaction>,
    selectedContactAddress: String?,
    filter: HistoryFilter,
    selectedChain: ChainConfig,
    contacts: List<Contact>,
    onTransactionClick: (Transaction) -> Unit
) {
    if (filteredTransactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = AppColors.Gray.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.history_empty),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (selectedContactAddress != null || filter != HistoryFilter.ALL)
                        stringResource(R.string.history_no_results)
                    else
                        stringResource(R.string.history_empty_desc, selectedChain.name),
                    fontSize = 14.sp,
                    color = AppColors.Gray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredTransactions) { transaction ->
            val contactName = contacts.find {
                it.address.equals(transaction.counterparty, ignoreCase = true)
            }?.name

            TransactionCard(
                transaction = transaction,
                contactName = contactName,
                symbol = selectedChain.symbol,
                decimals = selectedChain.decimals,
                onClick = { onTransactionClick(transaction) }
            )
        }
    }
}

@Composable
private fun ContactFilterSheet(
    uniqueAddresses: List<String>,
    contacts: List<Contact>,
    selectedContactAddress: String?,
    onDismiss: () -> Unit,
    onSelectAddress: (String) -> Unit,
    onClearFilter: () -> Unit
) {
    TitledBottomSheet(
        title = stringResource(R.string.history_filter_by_contact),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearFilter() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ClearAll, contentDescription = null, tint = AppColors.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.history_show_all))
            }
            Divider()

            uniqueAddresses.forEach { address ->
                val contact = contacts.find { it.address.equals(address, ignoreCase = true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAddress(address) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (contact?.name?.take(2) ?: address.take(2)).uppercase(),
                            color = AppColors.PayPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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
private fun TransactionDetailSheet(
    transaction: Transaction,
    selectedChain: ChainConfig,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onAddOrEditContact: (String) -> Unit,
    onRefund: () -> Unit
) {
    val contactName = contacts.find { it.address.equals(transaction.counterparty, ignoreCase = true) }?.name
    val clipboardManager = LocalClipboardManager.current
    val isIncoming = transaction.type == TransactionType.PAYMENT_RECEIVED || transaction.type == TransactionType.REFUND_RECEIVED

    TitledBottomSheet(
        title = when (transaction.type) {
            TransactionType.PAYMENT_RECEIVED -> stringResource(R.string.history_payment_received)
            TransactionType.PAYMENT_SENT -> stringResource(R.string.history_payment_sent)
            TransactionType.REFUND_SENT -> stringResource(R.string.history_refund_sent)
            TransactionType.REFUND_RECEIVED -> stringResource(R.string.history_refund_received)
        },
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = "${if (isIncoming) "+" else "-"}${transaction.getFormattedAmount(selectedChain.decimals)} ${selectedChain.symbol}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncoming) AppColors.Success else AppColors.Error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AppColors.LightGray,
                elevation = 0.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isIncoming) stringResource(R.string.history_from) else stringResource(R.string.history_to),
                                fontSize = 12.sp,
                                color = AppColors.Gray
                            )
                            if (contactName != null) {
                                Text(contactName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            }
                            Text(
                                "${transaction.counterparty.take(12)}...${transaction.counterparty.takeLast(8)}",
                                fontSize = 13.sp,
                                color = if (contactName != null) AppColors.Gray else AppColors.Black
                            )
                        }
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(transaction.counterparty)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppColors.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onAddOrEditContact(transaction.counterparty) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            if (contactName != null) Icons.Default.Edit else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (contactName != null) stringResource(R.string.history_edit_contact) else stringResource(R.string.history_add_contact))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.history_date), color = AppColors.Gray)
                Text(formatDate(transaction.timestamp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.history_tx_hash), color = AppColors.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${transaction.txHash.take(8)}...${transaction.txHash.takeLast(6)}", fontSize = 13.sp)
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(transaction.txHash)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = AppColors.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transaction.type == TransactionType.PAYMENT_RECEIVED && transaction.status == TransactionStatus.CONFIRMED) {
                Button(
                    onClick = onRefund,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.Undo, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.history_refund), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AddContactSheet(
    address: String,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String) -> Unit
) {
    val existingContact = contacts.find { it.address.equals(address, ignoreCase = true) }
    var name by remember { mutableStateOf(existingContact?.name ?: "") }
    var note by remember { mutableStateOf(existingContact?.note ?: "") }

    TitledBottomSheet(
        title = if (existingContact != null) stringResource(R.string.address_book_edit) else stringResource(R.string.address_book_add),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "${address.take(12)}...${address.takeLast(8)}",
                fontSize = 13.sp,
                color = AppColors.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_name)) },
                placeholder = { Text(stringResource(R.string.address_book_name_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.PayPrimary,
                    cursorColor = AppColors.PayPrimary
                )
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
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.PayPrimary,
                    cursorColor = AppColors.PayPrimary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { if (name.isNotBlank()) onSave(name, note) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AppColors.PayPrimary else AppColors.LightGray,
        elevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) Color.White else AppColors.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                color = if (selected) Color.White else AppColors.Black,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun StatsHeader(
    transactions: List<Transaction>,
    symbol: String,
    decimals: Int
) {
    val totalReceived = transactions
        .filter { it.type == TransactionType.PAYMENT_RECEIVED }
        .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalSent = transactions
        .filter { it.type == TransactionType.PAYMENT_SENT }
        .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalRefundsSent = transactions
        .filter { it.type == TransactionType.REFUND_SENT }
        .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    val totalRefundsReceived = transactions
        .filter { it.type == TransactionType.REFUND_RECEIVED }
        .fold(java.math.BigInteger.ZERO) { acc, tx -> acc + tx.getAmountBigInt() }

    // Determinar qué mostrar basado en qué tipo de transacciones hay
    val hasReceived = totalReceived > java.math.BigInteger.ZERO
    val hasSent = totalSent > java.math.BigInteger.ZERO

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                stringResource(R.string.history_summary),
                fontSize = 14.sp,
                color = AppColors.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (hasReceived || (!hasReceived && !hasSent)) {
                    StatItem(
                        label = stringResource(R.string.history_received),
                        amount = formatAmount(totalReceived, decimals),
                        symbol = symbol,
                        color = AppColors.Success
                    )
                }
                if (hasSent) {
                    StatItem(
                        label = stringResource(R.string.history_sent),
                        amount = formatAmount(totalSent, decimals),
                        symbol = symbol,
                        color = AppColors.PayPrimary
                    )
                }
                if (totalRefundsSent > java.math.BigInteger.ZERO || totalRefundsReceived > java.math.BigInteger.ZERO) {
                    StatItem(
                        label = stringResource(R.string.history_refunded),
                        amount = formatAmount(totalRefundsSent + totalRefundsReceived, decimals),
                        symbol = symbol,
                        color = AppColors.Error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    amount: String,
    symbol: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = AppColors.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            amount,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(symbol, fontSize = 12.sp, color = AppColors.Gray)
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    contactName: String?,
    symbol: String,
    decimals: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono tipo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (transaction.type) {
                            TransactionType.PAYMENT_RECEIVED -> AppColors.Success.copy(alpha = 0.1f)
                            TransactionType.PAYMENT_SENT -> AppColors.PayPrimary.copy(alpha = 0.1f)
                            TransactionType.REFUND_SENT -> AppColors.Error.copy(alpha = 0.1f)
                            TransactionType.REFUND_RECEIVED -> AppColors.Success.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (transaction.type) {
                        TransactionType.PAYMENT_RECEIVED -> Icons.Default.ArrowDownward
                        TransactionType.PAYMENT_SENT -> Icons.Default.ArrowUpward
                        TransactionType.REFUND_SENT -> @Suppress("DEPRECATION") Icons.Default.Undo
                        TransactionType.REFUND_RECEIVED -> @Suppress("DEPRECATION") Icons.Default.Redo
                    },
                    contentDescription = null,
                    tint = when (transaction.type) {
                        TransactionType.PAYMENT_RECEIVED -> AppColors.Success
                        TransactionType.PAYMENT_SENT -> AppColors.PayPrimary
                        TransactionType.REFUND_SENT -> AppColors.Error
                        TransactionType.REFUND_RECEIVED -> AppColors.Success
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                // Show contact name or transaction type
                Text(
                    contactName ?: when (transaction.type) {
                        TransactionType.PAYMENT_RECEIVED -> stringResource(R.string.history_payment_received)
                        TransactionType.PAYMENT_SENT -> stringResource(R.string.history_payment_sent)
                        TransactionType.REFUND_SENT -> stringResource(R.string.history_refund_sent)
                        TransactionType.REFUND_RECEIVED -> stringResource(R.string.history_refund_received)
                    },
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatDate(transaction.timestamp),
                    fontSize = 12.sp,
                    color = AppColors.Gray
                )
                if (contactName == null) {
                    Text(
                        "${transaction.counterparty.take(6)}...${transaction.counterparty.takeLast(4)}",
                        fontSize = 12.sp,
                        color = AppColors.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Monto y estado
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (transaction.type == TransactionType.PAYMENT_RECEIVED || transaction.type == TransactionType.REFUND_RECEIVED) "+" else "-"}${transaction.getFormattedAmount(decimals)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (transaction.type) {
                        TransactionType.PAYMENT_RECEIVED, TransactionType.REFUND_RECEIVED -> AppColors.Success
                        else -> AppColors.Error
                    }
                )
                Text(symbol, fontSize = 12.sp, color = AppColors.Gray)

                // Badge de estado
                if (transaction.status != TransactionStatus.CONFIRMED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(transaction.status)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val (text, color) = when (status) {
        TransactionStatus.PENDING -> stringResource(R.string.history_status_pending) to AppColors.Gray
        TransactionStatus.CONFIRMED -> stringResource(R.string.history_status_confirmed) to AppColors.Success
        TransactionStatus.FAILED -> stringResource(R.string.history_status_failed) to AppColors.Error
        TransactionStatus.REFUNDED -> stringResource(R.string.history_status_refunded) to AppColors.Error
        TransactionStatus.PARTIALLY_REFUNDED -> stringResource(R.string.history_status_partial) to AppColors.Error
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}

private fun formatAmount(amount: java.math.BigInteger, decimals: Int): String {
    return NumberFormatter.formatCurrency(amount, decimals)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
