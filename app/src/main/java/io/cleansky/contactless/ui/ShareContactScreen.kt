package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.crypto.SecureWalletManager
import io.cleansky.contactless.data.AddressBookRepository
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.nfc.NfcManager
import kotlinx.coroutines.launch

sealed class ShareContactState {
    object Ready : ShareContactState()

    object Broadcasting : ShareContactState()

    data class Received(val contact: Contact) : ShareContactState()

    object Saved : ShareContactState()
}

@Composable
fun ShareContactScreen(
    walletManager: SecureWalletManager,
    nfcManager: NfcManager,
    addressBookRepository: AddressBookRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val walletAddress by walletManager.addressFlow.collectAsState(initial = null)

    var state by remember { mutableStateOf<ShareContactState>(ShareContactState.Ready) }
    var myName by remember { mutableStateOf("") }
    var receivedContact by remember { mutableStateOf<Contact?>(null) }

    // Set up NFC contact receiver
    DisposableEffect(Unit) {
        nfcManager.setOnContactReceived { contact ->
            receivedContact = contact
            state = ShareContactState.Received(contact)
            nfcManager.stopBroadcast()
        }
        onDispose {
            nfcManager.clearOnContactReceived()
            nfcManager.stopBroadcast()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val currentState = state) {
            ShareContactState.Ready -> {
                ReadyToShareContent(
                    walletAddress = walletAddress,
                    myName = myName,
                    onNameChange = { myName = it },
                    onStartSharing = {
                        walletAddress?.let { address ->
                            val contact =
                                Contact(
                                    address = address,
                                    name = myName.ifBlank { "" },
                                )
                            nfcManager.prepareContact(contact)
                            state = ShareContactState.Broadcasting
                        }
                    },
                )
            }
            ShareContactState.Broadcasting -> {
                BroadcastingContent(
                    onCancel = {
                        nfcManager.stopBroadcast()
                        state = ShareContactState.Ready
                    },
                )
            }
            is ShareContactState.Received -> {
                ContactReceivedContent(
                    contact = currentState.contact,
                    onSave = {
                        scope.launch {
                            addressBookRepository.addContact(currentState.contact)
                            state = ShareContactState.Saved
                        }
                    },
                    onDiscard = {
                        state = ShareContactState.Ready
                    },
                )
            }
            ShareContactState.Saved -> {
                ContactSavedContent(
                    onShareMore = {
                        state = ShareContactState.Ready
                    },
                    onDone = onBack,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadyToShareContent(
    walletAddress: String?,
    myName: String,
    onNameChange: (String) -> Unit,
    onStartSharing: () -> Unit,
) {
    Spacer(modifier = Modifier.height(32.dp))

    // Icon
    Box(
        modifier =
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ContactPhone,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = AppColors.PayPrimary,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.share_contact_title),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Name input
    OutlinedTextField(
        value = myName,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.share_contact_your_name)) },
        placeholder = { Text(stringResource(R.string.share_contact_your_name_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors =
            TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = AppColors.PayPrimary,
                cursorColor = AppColors.PayPrimary,
            ),
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Show address
    if (walletAddress != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Gray.copy(alpha = 0.1f),
            elevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.address_book_address),
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                Text(
                    text = "${walletAddress.take(10)}...${walletAddress.takeLast(8)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Share button
    Button(
        onClick = onStartSharing,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        enabled = walletAddress != null,
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.PayPrimary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.NearMe,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.share_contact_mutual),
            fontSize = 18.sp,
            color = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.share_contact_mutual_desc),
        fontSize = 12.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ColumnScope.BroadcastingContent(onCancel: () -> Unit) {
    Spacer(modifier = Modifier.weight(1f))

    // Animated NFC icon
    Box(
        modifier =
            Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(140.dp),
            color = AppColors.PayPrimary.copy(alpha = 0.3f),
            strokeWidth = 4.dp,
        )
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = AppColors.PayPrimary,
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = stringResource(R.string.share_contact_ready),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.share_contact_ready_desc),
        fontSize = 16.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.weight(1f))

    // Cancel button
    OutlinedButton(
        onClick = onCancel,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cancel),
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ColumnScope.ContactReceivedContent(
    contact: Contact,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Spacer(modifier = Modifier.weight(1f))

    // Success icon
    Box(
        modifier =
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppColors.CollectPrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = AppColors.CollectPrimary,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.share_contact_received),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (contact.name.isNotBlank()) {
        Text(
            text = stringResource(R.string.share_contact_received_name, contact.name),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Contact card
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (contact.name.isNotBlank()) {
                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "${contact.address.take(10)}...${contact.address.takeLast(8)}",
                fontSize = 14.sp,
                color = Color.Gray,
            )
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Save button
    Button(
        onClick = onSave,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.CollectPrimary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Save,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.share_contact_save),
            fontSize = 18.sp,
            color = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Discard button
    TextButton(
        onClick = onDiscard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.cancel),
            color = Color.Gray,
        )
    }
}

@Composable
private fun ColumnScope.ContactSavedContent(
    onShareMore: () -> Unit,
    onDone: () -> Unit,
) {
    Spacer(modifier = Modifier.weight(1f))

    // Success icon
    Box(
        modifier =
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppColors.CollectPrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = AppColors.CollectPrimary,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.address_book_saved),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.weight(1f))

    // Share more button
    OutlinedButton(
        onClick = onShareMore,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.share_contact_mutual),
            fontSize = 18.sp,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Done button
    Button(
        onClick = onDone,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.CollectPrimary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.refund_done),
            fontSize = 18.sp,
            color = Color.White,
        )
    }
}
