package com.sun.alasbrowser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludedSitesDialog(
    preferences: BrowserPreferences,
    onDismiss: () -> Unit
) {
    var excludedSites by remember { mutableStateOf(preferences.excludedSites.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSiteUrl by remember { mutableStateOf("") }
    
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF292A2D),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("Add site to exclude", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Column {
                    Text(
                        "Enter the website URL to exclude from ad blocking",
                        color = Color(0xFF9AA0A6),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    TextField(
                        value = newSiteUrl,
                        onValueChange = { newSiteUrl = it },
                        placeholder = { Text("example.com", color = Color(0xFF9AA0A6)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF3C4043),
                            unfocusedContainerColor = Color(0xFF3C4043),
                            focusedIndicatorColor = Color(0xFF8AB4F8),
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF8AB4F8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSiteUrl.isNotBlank()) {
                            val cleanUrl = newSiteUrl.trim()
                                .removePrefix("http://")
                                .removePrefix("https://")
                                .removePrefix("www.")
                                .split("/").first()
                            
                            preferences.addExcludedSite(cleanUrl)
                            excludedSites = preferences.excludedSites.toList()
                            newSiteUrl = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add", color = Color(0xFF8AB4F8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color(0xFF9AA0A6))
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF292A2D),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Excluded sites",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add site", tint = Color(0xFF8AB4F8))
                }
            }
        },
        text = {
            if (excludedSites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFF5F6368),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No excluded sites",
                        color = Color(0xFF9AA0A6),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sites you add here won't be blocked",
                        color = Color(0xFF5F6368),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Ad blocking is disabled on these sites",
                        color = Color(0xFF9AA0A6),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    excludedSites.forEach { site ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF9AA0A6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                site,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    preferences.removeExcludedSite(site)
                                    excludedSites = preferences.excludedSites.toList()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF9AA0A6)
                                )
                            }
                        }
                        if (site != excludedSites.last()) {
                            HorizontalDivider(
                                color = Color(0xFF3C4043),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF8AB4F8))
            }
        }
    )
}
