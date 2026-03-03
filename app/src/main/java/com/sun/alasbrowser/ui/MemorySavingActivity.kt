package com.sun.alasbrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences

class MemorySavingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val preferences = BrowserPreferences(this)
        
        setContent {
            MemorySavingScreen(
                preferences = preferences,
                onBack = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySavingScreen(
    preferences: BrowserPreferences,
    onBack: () -> Unit
) {
    var goBackWithoutRefresh by remember { mutableStateOf(preferences.memorySavingEnabled) }
    var pageMemoryLimit by remember { mutableIntStateOf(preferences.pageMemoryLimit) }
    var tabMemoryLimit by remember { mutableIntStateOf(preferences.tabMemoryLimit) }
    var showPageLimitDialog by remember { mutableStateOf(false) }
    var showTabLimitDialog by remember { mutableStateOf(false) }
    
    if (showPageLimitDialog) {
        MemoryLimitDialog(
            title = "Page memory limit",
            currentLimit = pageMemoryLimit,
            onLimitSelected = { limit ->
                pageMemoryLimit = limit
                preferences.setPageMemoryLimit(limit)
                showPageLimitDialog = false
            },
            onDismiss = { showPageLimitDialog = false }
        )
    }
    
    if (showTabLimitDialog) {
        MemoryLimitDialog(
            title = "Tab memory limit",
            currentLimit = tabMemoryLimit,
            onLimitSelected = { limit ->
                tabMemoryLimit = limit
                preferences.setTabMemoryLimit(limit)
                showTabLimitDialog = false
            },
            onDismiss = { showTabLimitDialog = false }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Memory saving",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
             
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1F1F1F)
                )
            )
        },
        containerColor = Color(0xFF1F1F1F)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Page",
                color = Color(0xFF9AA0A6),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF292A2D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                goBackWithoutRefresh = !goBackWithoutRefresh
                                preferences.setMemorySavingEnabled(goBackWithoutRefresh)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Go back without refresh",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Some sites may not be supported.\nThis feature uses more memory.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Switch(
                            checked = goBackWithoutRefresh,
                            onCheckedChange = { checked ->
                                goBackWithoutRefresh = checked
                                preferences.setMemorySavingEnabled(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8AB4F8),
                                uncheckedThumbColor = Color(0xFF5F6368),
                                uncheckedTrackColor = Color(0xFF3C4043)
                            )
                        )
                    }
                    
                    HorizontalDivider(
                        color = Color(0xFF3C4043),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPageLimitDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Memory limit",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pageMemoryLimit.toString(),
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "If you exceed the limit, memory is automatically\nreleased starting with the oldest pages.\nIf you have too many pages open, your browser may\nbe slow.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            
            Text(
                text = "Tab",
                color = Color(0xFF9AA0A6),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF292A2D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {  }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open limit",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Unlimited",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "If too many tabs are open, the browser may slow\ndown.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    
                    HorizontalDivider(
                        color = Color(0xFF3C4043),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTabLimitDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Memory limit",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tabMemoryLimit.toString(),
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "If you exceed the limit, memory is automatically\nreleased starting with the oldest tabs.\nIf too many tabs are open, the browser may slow\ndown.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    
                    HorizontalDivider(
                        color = Color(0xFF3C4043),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {  }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Memory release prevention list",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryLimitDialog(
    title: String,
    currentLimit: Int,
    onLimitSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val limits = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF292A2D),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                limits.forEach { limit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLimitSelected(limit) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = limit == currentLimit,
                            onClick = { onLimitSelected(limit) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF8AB4F8),
                                unselectedColor = Color(0xFF9AA0A6)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = limit.toString(),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}
