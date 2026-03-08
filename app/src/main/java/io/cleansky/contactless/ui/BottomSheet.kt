package io.cleansky.contactless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.cleansky.contactless.R

/**
 * Base bottom sheet wrapper with scrim and drag handle.
 * Wrapped in Dialog to ensure proper overlay on all screen content.
 */
@Composable
fun BottomSheetContainer(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color.White,
                elevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    // Drag handle
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AppColors.LightGray),
                        )
                    }
                    content()
                }
            }
        }
    }
}

/**
 * Bottom sheet with title and optional subtitle.
 */
@Composable
fun TitledBottomSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BottomSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
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
                    fontSize = 14.sp,
                    color = AppColors.Gray,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        content()
    }
}

/**
 * Selection list bottom sheet.
 */
@Composable
fun <T> SelectionBottomSheet(
    title: String,
    items: List<T>,
    selectedItem: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    itemContent: @Composable (T, Boolean) -> Unit,
) {
    TitledBottomSheet(title = title, onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp),
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    itemContent(item, isSelected)
                }
                if (item != items.last()) {
                    Divider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

/**
 * Confirmation bottom sheet for destructive actions.
 */
@Composable
fun ConfirmationBottomSheet(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = AppColors.Error,
    icon: ImageVector = Icons.Default.Warning,
    iconColor: Color = AppColors.Warning,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetContainer(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = AppColors.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = confirmColor),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(confirmText, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel), color = AppColors.Gray)
            }
        }
    }
}

/**
 * Input bottom sheet with text field.
 */
@Composable
fun InputBottomSheet(
    title: String,
    subtitle: String? = null,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonText: String,
    isLoading: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    TitledBottomSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                isError = error != null,
                enabled = !isLoading,
                colors =
                    TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AppColors.PayPrimary,
                        cursorColor = AppColors.PayPrimary,
                    ),
            )
            error?.let {
                Text(
                    text = it,
                    color = AppColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            extraContent()

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = enabled && !isLoading,
                colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.PayPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(buttonText, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
