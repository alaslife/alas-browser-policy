package com.sun.alasbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationBottomSheet(
    onDismiss: () -> Unit,
    onTranslate: (String, String) -> Unit,
    translateImmediately: Boolean = false
) {
    var sourceLanguage by remember { mutableStateOf("Auto Detect") }
    var targetLanguage by remember { mutableStateOf("Hindi") }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var isTranslated by remember { mutableStateOf(false) }
    var lastTranslatedTo by remember { mutableStateOf("") }

    val languageMap = mapOf(
        "Auto Detect" to "auto",
        "Afrikaans" to "af", "Akan" to "ak", "Albanian" to "sq", "Amharic" to "am", "Arabic" to "ar", 
        "Armenian" to "hy", "Assamese" to "as", "Aymara" to "ay", "Azerbaijani" to "az", "Bambara" to "bm",
        "Basque" to "eu", "Belarusian" to "be", "Bengali" to "bn", "Bhojpuri" to "bho", "Bosnian" to "bs",
        "Bulgarian" to "bg", "Catalan" to "ca", "Cebuano" to "ceb", "Chinese (Simplified)" to "zh-CN",
        "Chinese (Traditional)" to "zh-TW", "Corsican" to "co", "Croatian" to "hr", "Czech" to "cs",
        "Danish" to "da", "Divehi" to "dv", "Dogri" to "doi", "Dutch" to "nl", "English" to "en",
        "Esperanto" to "eo", "Estonian" to "et", "Ewe" to "ee", "Filipino" to "fil", "Finnish" to "fi",
        "French" to "fr", "Galician" to "gl", "Georgian" to "ka", "German" to "de", "Greek" to "el",
        "Guarani" to "gn", "Gujarati" to "gu", "Haitian Creole" to "ht", "Hausa" to "ha", "Hawaiian" to "haw",
        "Hebrew" to "he", "Hindi" to "hi", "Hmong" to "hmn", "Hungarian" to "hu", "Icelandic" to "is",
        "Igbo" to "ig", "Ilocano" to "ilo", "Indonesian" to "id", "Irish" to "ga", "Italian" to "it",
        "Japanese" to "ja", "Javanese" to "jv", "Kannada" to "kn", "Kazakh" to "kk", "Khmer" to "km",
        "Kinyarwanda" to "rw", "Korean" to "ko", "Krio" to "kri", "Kurdish" to "ku", "Kyrgyz" to "ky",
        "Lao" to "lo", "Latin" to "la", "Latvian" to "lv", "Lingala" to "ln", "Lithuanian" to "lt",
        "Luganda" to "lg", "Luxembourgish" to "lb", "Macedonian" to "mk", "Maithili" to "mai",
        "Malagasy" to "mg", "Malay" to "ms", "Malayalam" to "ml", "Maltese" to "mt", "Maori" to "mi",
        "Marathi" to "mr", "Mizo" to "lus", "Mongolian" to "mn", "Myanmar" to "my", "Nepali" to "ne",
        "Norwegian" to "no", "Nyanja" to "ny", "Odia" to "or", "Oromo" to "om", "Pashto" to "ps",
        "Persian" to "fa", "Polish" to "pl", "Portuguese" to "pt", "Punjabi" to "pa", "Quechua" to "qu",
        "Romanian" to "ro", "Russian" to "ru", "Samoan" to "sm", "Sanskrit" to "sa", "Scots Gaelic" to "gd",
        "Sepedi" to "nso", "Serbian" to "sr", "Sesotho" to "st", "Shona" to "sn", "Sindhi" to "sd",
        "Sinhala" to "si", "Slovak" to "sk", "Slovenian" to "sl", "Somali" to "so", "Spanish" to "es",
        "Sundanese" to "su", "Swahili" to "sw", "Swedish" to "sv", "Tagalog" to "tl", "Tajik" to "tg",
        "Tamil" to "ta", "Tatar" to "tt", "Telugu" to "te", "Thai" to "th", "Tigrinya" to "ti",
        "Tsonga" to "ts", "Turkish" to "tr", "Turkmen" to "tk", "Twi" to "tw", "Ukrainian" to "uk",
        "Urdu" to "ur", "Uyghur" to "ug", "Uzbek" to "uz", "Vietnamese" to "vi", "Welsh" to "cy",
        "Xhosa" to "xh", "Yiddish" to "yi", "Yoruba" to "yo", "Zulu" to "zu"
    )

    // Function to trigger translation
    fun performTranslation() {
        val sourceLangCode = languageMap[sourceLanguage] ?: "auto"
        val targetLangCode = languageMap[targetLanguage] ?: "hi"
        
        // Always trigger translation - the script will handle if it's already translated
        onTranslate(sourceLangCode, targetLangCode)
        lastTranslatedTo = targetLangCode
        isTranslated = true
    }

    // Trigger initial translation when sheet opens
    LaunchedEffect(translateImmediately) {
        if (translateImmediately) {
            performTranslation()
        }
    }
    
    val languages = languageMap.keys.toList().sorted()

    // Language picker dialogs
    if (showSourcePicker) {
        LanguagePickerDialog(
            languages = languages,
            selectedLanguage = sourceLanguage,
            onLanguageSelected = { lang ->
                sourceLanguage = lang
                showSourcePicker = false
                performTranslation()
            },
            onDismiss = { showSourcePicker = false }
        )
    }
    
    if (showTargetPicker) {
        LanguagePickerDialog(
            languages = languages,
            selectedLanguage = targetLanguage,
            onLanguageSelected = { lang ->
                targetLanguage = lang
                showTargetPicker = false
                performTranslation()
            },
            onDismiss = { showTargetPicker = false }
        )
    }

    // Main translation bar
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language selector container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source language
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showSourcePicker = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = sourceLanguage,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select source language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Swap button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            val temp = sourceLanguage
                            sourceLanguage = targetLanguage
                            targetLanguage = temp
                            
                            if (sourceLanguage == "Auto Detect") {
                                sourceLanguage = if (targetLanguage == "English") "Hindi" else "English"
                            }
                            
                            performTranslation()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⇄",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Target language
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showTargetPicker = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = targetLanguage,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select target language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Close button and revert button
            Row(
                modifier = Modifier.widthIn(min = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Revert to original button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            onTranslate("revert", "revert") // Special command to revert
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Revert to original",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Close button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close translation",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LanguagePickerDialog(
    languages: List<String>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            languages
        } else {
            languages.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            tonalElevation = 24.dp,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search languages",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Search languages",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )
                }

                // Languages list
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLanguages) { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageSelected(language) }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = language,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = if (language == selectedLanguage) FontWeight.SemiBold else FontWeight.Normal
                            )
                            
                            if (language == selectedLanguage) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        if (language != filteredLanguages.lastOrNull()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}