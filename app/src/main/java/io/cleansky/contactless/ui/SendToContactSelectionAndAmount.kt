package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.model.Contact
import io.cleansky.contactless.model.Token
import io.cleansky.contactless.util.NumberFormatter
import java.math.BigDecimal
import java.math.BigInteger

@Composable
internal fun ContactListContent(
    contacts: List<Contact>,
    onContactSelected: (Contact) -> Unit,
) {
    if (contacts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.send_no_contacts),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.send_no_contacts_hint),
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Text(
            text = stringResource(R.string.send_select_contact),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(contacts) { contact ->
                ContactCard(
                    contact = contact,
                    onClick = { onContactSelected(contact) },
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppColors.PayPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = contact.name.take(1).uppercase().ifEmpty { "#" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PayPrimary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${contact.address.take(8)}...${contact.address.takeLast(6)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
            )
        }
    }
}

@Composable
internal fun ColumnScope.EnterAmountContent(
    contact: Contact,
    amount: String,
    onAmountChange: (String) -> Unit,
    token: Token?,
    tokenBalance: BigInteger?,
    chainSymbol: String,
    onContinue: () -> Unit,
    onChangeContact: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onChangeContact() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = AppColors.PayPrimary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.send_to, contact.name.ifBlank { contact.address.take(10) + "..." }),
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
                Text(
                    text = contact.name.ifBlank { "${contact.address.take(10)}...${contact.address.takeLast(6)}" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = stringResource(R.string.settings_wallet_import),
                fontSize = 12.sp,
                color = AppColors.PayPrimary,
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.send_amount),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = amount,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                onAmountChange(newValue)
            }
        },
        placeholder = { Text(stringResource(R.string.send_amount_hint)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        trailingIcon = {
            Text(
                text = token?.symbol ?: chainSymbol,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        colors =
            TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = AppColors.PayPrimary,
                cursorColor = AppColors.PayPrimary,
            ),
    )

    tokenBalance?.let { balance ->
        Spacer(modifier = Modifier.height(8.dp))
        val formattedBalance = NumberFormatter.formatBalance(balance, 18, 6)
        Text(
            text = "${stringResource(R.string.stealth_wallet_available, formattedBalance, chainSymbol)}",
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    val isValidAmount =
        amount.isNotBlank() &&
            try {
                amount.toBigDecimal() > BigDecimal.ZERO
            } catch (e: Exception) {
                false
            }

    Button(
        onClick = onContinue,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        enabled = isValidAmount,
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = AppColors.PayPrimary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text =
                if (isValidAmount) {
                    stringResource(R.string.send_confirm, amount, token?.symbol ?: chainSymbol)
                } else {
                    stringResource(R.string.send_enter_amount)
                },
            fontSize = 18.sp,
            color = Color.White,
        )
    }
}

@Composable
internal fun ColumnScope.LargeAmountWarningContent(
    contact: Contact,
    amount: String,
    token: Token,
    isVeryLarge: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(modifier = Modifier.weight(1f))

    Box(
        modifier =
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isVeryLarge) Color.Red.copy(alpha = 0.1f) else Color(0xFFFF9800).copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = if (isVeryLarge) Color.Red else Color(0xFFFF9800),
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.scam_amount_large_warning),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (isVeryLarge) Color.Red else Color(0xFFFF9800),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.scam_amount_large_desc, amount, token.symbol),
        fontSize = 16.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Gray.copy(alpha = 0.1f),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.send_to, ""),
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Text(
                text = contact.name.ifBlank { stringResource(R.string.address_book_unknown) },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${contact.address.take(10)}...${contact.address.takeLast(6)}",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }

    if (isVeryLarge) {
        Spacer(modifier = Modifier.height(16.dp))
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onConfirm,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        colors =
            ButtonDefaults.buttonColors(
                backgroundColor = if (isVeryLarge) Color.Red else Color(0xFFFF9800),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.scam_confirm_large),
            fontSize = 18.sp,
            color = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

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
