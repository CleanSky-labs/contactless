package io.cleansky.contactless.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cleansky.contactless.R
import io.cleansky.contactless.data.AppPreferences
import kotlinx.coroutines.launch

data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String
)

private val LANGUAGES = listOf(
    LanguageOption("", "System default", ""),
    LanguageOption("en", "English", "English"),
    LanguageOption("es", "Spanish", "Español"),
    LanguageOption("fr", "French", "Français"),
    LanguageOption("de", "German", "Deutsch"),
    LanguageOption("it", "Italian", "Italiano"),
    LanguageOption("pt", "Portuguese", "Português"),
    LanguageOption("ru", "Russian", "Русский"),
    LanguageOption("zh", "Chinese", "中文"),
    LanguageOption("ja", "Japanese", "日本語"),
    LanguageOption("ko", "Korean", "한국어"),
    LanguageOption("ar", "Arabic", "العربية"),
    LanguageOption("hi", "Hindi", "हिन्दी"),
    LanguageOption("tr", "Turkish", "Türkçe"),
    LanguageOption("nl", "Dutch", "Nederlands"),
    LanguageOption("pl", "Polish", "Polski"),
    LanguageOption("uk", "Ukrainian", "Українська"),
    LanguageOption("vi", "Vietnamese", "Tiếng Việt"),
    LanguageOption("th", "Thai", "ไทย"),
    LanguageOption("id", "Indonesian", "Bahasa Indonesia"),
    LanguageOption("ms", "Malay", "Bahasa Melayu"),
    LanguageOption("sw", "Swahili", "Kiswahili"),
)

@Composable
fun LanguageSelector(
    appPreferences: AppPreferences
) {
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    val currentLanguage by appPreferences.appLanguageFlow.collectAsState(initial = "")
    val systemDefaultText = stringResource(R.string.settings_language_system)

    val currentLanguageDisplay = remember(currentLanguage, systemDefaultText) {
        if (currentLanguage.isEmpty()) {
            systemDefaultText
        } else {
            LANGUAGES.find { it.code == currentLanguage }?.nativeName ?: currentLanguage
        }
    }

    Column {
        Text(
            text = stringResource(R.string.settings_language),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true },
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = AppColors.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = currentLanguageDisplay,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Gray)
            }
        }
    }

    if (showSheet) {
        TitledBottomSheet(
            title = stringResource(R.string.settings_language),
            onDismiss = { showSheet = false }
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(LANGUAGES) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    appPreferences.setAppLanguage(language.code)
                                }
                                showSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (language.code.isEmpty()) {
                                    systemDefaultText
                                } else {
                                    language.nativeName
                                },
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                            if (language.code.isNotEmpty()) {
                                Text(
                                    text = language.name,
                                    fontSize = 13.sp,
                                    color = AppColors.Gray
                                )
                            }
                        }
                        if (language.code == currentLanguage) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = AppColors.Success
                            )
                        }
                    }
                    if (language != LANGUAGES.last()) {
                        Divider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}
