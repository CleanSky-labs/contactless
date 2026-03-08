package io.cleansky.contactless

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.data.TransactionRepository
import io.cleansky.contactless.model.ChainConfig
import io.cleansky.contactless.model.Transaction
import io.cleansky.contactless.model.TransactionType
import io.cleansky.contactless.ui.AppColors
import kotlinx.coroutines.launch

enum class HistoryFilter { ALL, RECEIVED, SENT }

@Composable
fun HistoryScreen(
    transactionRepository: TransactionRepository,
    addressBookRepository: AddressBookRepository,
    selectedChain: ChainConfig,
    onTransactionClick: (Transaction) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val transactions by transactionRepository.transactionsFlow.collectAsState(initial = emptyList())
    val contacts by addressBookRepository.contactsFlow.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedContactAddress by remember { mutableStateOf<String?>(null) }
    var showContactFilter by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf<String?>(null) }
    var showTransactionDetail by remember { mutableStateOf<Transaction?>(null) }

    val chainTransactions = transactions.filter { it.chainId == selectedChain.chainId }
    val filteredTransactions = applyHistoryFilters(chainTransactions, filter, selectedContactAddress)
    val uniqueAddresses = chainTransactions.map { it.counterparty }.distinct()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        StatsHeader(
            transactions = filteredTransactions,
            symbol = selectedChain.symbol,
            decimals = selectedChain.decimals,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = stringResource(R.string.history_filter_all),
                selected = filter == HistoryFilter.ALL && selectedContactAddress == null,
                onClick = {
                    filter = HistoryFilter.ALL
                    selectedContactAddress = null
                },
            )
            FilterChip(
                label = stringResource(R.string.history_filter_received),
                selected = filter == HistoryFilter.RECEIVED,
                onClick = { filter = HistoryFilter.RECEIVED },
            )
            FilterChip(
                label = stringResource(R.string.history_filter_sent),
                selected = filter == HistoryFilter.SENT,
                onClick = { filter = HistoryFilter.SENT },
            )

            if (uniqueAddresses.isNotEmpty()) {
                Divider(
                    modifier =
                        Modifier
                            .height(32.dp)
                            .width(1.dp),
                    color = AppColors.LightGray,
                )
                FilterChip(
                    label = contactFilterLabel(selectedContactAddress, contacts),
                    selected = selectedContactAddress != null,
                    onClick = { showContactFilter = true },
                    icon = Icons.Default.Person,
                )
            }
        }

        HistoryTransactionsContent(
            filteredTransactions = filteredTransactions,
            selectedContactAddress = selectedContactAddress,
            filter = filter,
            selectedChain = selectedChain,
            contacts = contacts,
            onTransactionClick = { showTransactionDetail = it },
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
            },
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
            },
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
                            note = note.trim(),
                        ),
                    )
                    showAddContact = null
                }
            },
        )
    }
}

private fun applyHistoryFilters(
    transactions: List<Transaction>,
    filter: HistoryFilter,
    selectedContactAddress: String?,
): List<Transaction> {
    return transactions.filter { tx ->
        val typeMatch =
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.RECEIVED -> tx.type == TransactionType.PAYMENT_RECEIVED || tx.type == TransactionType.REFUND_RECEIVED
                HistoryFilter.SENT -> tx.type == TransactionType.PAYMENT_SENT || tx.type == TransactionType.REFUND_SENT
            }
        val contactMatch = selectedContactAddress?.let { tx.counterparty.equals(it, ignoreCase = true) } ?: true
        typeMatch && contactMatch
    }
}

@Composable
private fun contactFilterLabel(
    selectedContactAddress: String?,
    contacts: List<io.cleansky.contactless.model.Contact>,
): String {
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
    contacts: List<io.cleansky.contactless.model.Contact>,
    onTransactionClick: (Transaction) -> Unit,
) {
    if (filteredTransactions.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = AppColors.Gray.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material.Text(
                    stringResource(R.string.history_empty),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Gray,
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material.Text(
                    if (selectedContactAddress != null || filter != HistoryFilter.ALL) {
                        stringResource(R.string.history_no_results)
                    } else {
                        stringResource(R.string.history_empty_desc, selectedChain.name)
                    },
                    fontSize = 14.sp,
                    color = AppColors.Gray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(filteredTransactions) { transaction ->
            val contactName =
                contacts.find {
                    it.address.equals(transaction.counterparty, ignoreCase = true)
                }?.name

            TransactionCard(
                transaction = transaction,
                contactName = contactName,
                symbol = selectedChain.symbol,
                decimals = selectedChain.decimals,
                onClick = { onTransactionClick(transaction) },
            )
        }
    }
}
