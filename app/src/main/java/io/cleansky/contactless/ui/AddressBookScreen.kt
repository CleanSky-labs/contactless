package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.util.AddressUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddressBookScreen(
    addressBookRepository: AddressBookRepository,
    onBack: () -> Unit,
    onSelectAddress: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val contacts by addressBookRepository.contactsFlow.collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.address.contains(searchQuery, ignoreCase = true) ||
                it.note.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.address_book_title),
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(@Suppress("DEPRECATION") Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.address_book_add))
                }
            },
            backgroundColor = Color.White,
            elevation = 0.dp
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.address_book_search)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.Gray)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = AppColors.Gray)
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = AppColors.PayPrimary,
                cursorColor = AppColors.PayPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (filteredContacts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ContactPage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AppColors.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            stringResource(R.string.address_book_no_results)
                        else
                            stringResource(R.string.address_book_empty),
                        color = AppColors.Gray,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.address_book_empty_hint),
                            color = AppColors.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredContacts, key = { it.address }) { contact ->
                    ContactCard(
                        contact = contact,
                        onClick = {
                            if (onSelectAddress != null) {
                                onSelectAddress(contact.address)
                            } else {
                                editingContact = contact
                            }
                        },
                        onDelete = {
                            scope.launch {
                                addressBookRepository.removeContact(contact.address)
                            }
                        }
                    )
                }
            }
        }
    }

    // Add contact sheet
    if (showAddSheet) {
        AddContactSheet(
            onDismiss = { showAddSheet = false },
            onSave = { address, name, note ->
                scope.launch {
                    addressBookRepository.addContact(
                        Contact(
                            address = address,
                            name = name,
                            note = note
                        )
                    )
                    showAddSheet = false
                }
            }
        )
    }

    // Edit contact sheet
    editingContact?.let { contact ->
        EditContactSheet(
            contact = contact,
            onDismiss = { editingContact = null },
            onSave = { name, note ->
                scope.launch {
                    addressBookRepository.updateContact(contact.address, name, note)
                    editingContact = null
                }
            },
            onDelete = {
                scope.launch {
                    addressBookRepository.removeContact(contact.address)
                    editingContact = null
                }
            }
        )
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(2).uppercase(),
                    color = AppColors.PayPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${contact.address.take(8)}...${contact.address.takeLast(6)}",
                    color = AppColors.Gray,
                    fontSize = 13.sp
                )
                if (contact.note.isNotEmpty()) {
                    Text(
                        text = contact.note,
                        color = AppColors.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Copy button
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(contact.address))
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = AppColors.Gray,
                    modifier = Modifier.size(20.dp)
                )
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
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun AddContactSheet(
    onDismiss: () -> Unit,
    onSave: (address: String, name: String, note: String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    TitledBottomSheet(
        title = stringResource(R.string.address_book_add),
        onDismiss = onDismiss
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
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.PayPrimary,
                    cursorColor = AppColors.PayPrimary
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = address.isNotBlank() && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun EditContactSheet(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var note by remember { mutableStateOf(contact.note) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    TitledBottomSheet(
        title = stringResource(R.string.address_book_edit),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Address (read-only)
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AppColors.LightGray,
                elevation = 0.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${contact.address.take(12)}...${contact.address.takeLast(8)}",
                        fontSize = 13.sp,
                        color = AppColors.Gray
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(contact.address)) },
                        modifier = Modifier.size(24.dp)
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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_book_name)) },
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
                singleLine = false,
                maxLines = 2,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.PayPrimary,
                    cursorColor = AppColors.PayPrimary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), note.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = AppColors.Error,
                    modifier = Modifier.size(18.dp)
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
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
