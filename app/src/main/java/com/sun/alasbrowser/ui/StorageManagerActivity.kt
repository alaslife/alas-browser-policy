package com.sun.alasbrowser.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.ui.theme.AlasBrowserTheme
import com.sun.alasbrowser.utils.CacheManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StorageManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = BrowserPreferences(this)
        setContent {
            AlasBrowserTheme(appTheme = preferences.appTheme) {
                StorageManagerScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var cacheSize by remember { mutableStateOf(0L) }
    var formattedSize by remember { mutableStateOf("Calculating...") }
    var isCleaning by remember { mutableStateOf(false) }
    var isCleaned by remember { mutableStateOf(false) }
    
    var cleanWebStorage by remember { mutableStateOf(true) }
    var cleanCookies by remember { mutableStateOf(false) } // Default false to prevent accidental logout
    
    // Animation states
    val progressAnimation by animateFloatAsState(
        targetValue = if (isCleaned) 0f else 1f, // If cleaned, progress goes to 0 (empty cache)
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        val size = CacheManager.calculateTotalCacheSize(context)
        cacheSize = size
        formattedSize = CacheManager.formatSize(size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Storage Manager",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Visual Indicator Circle
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Capture colors outside Canvas (DrawScope is not Composable)
                val unfocusedColor = MaterialTheme.colorScheme.surfaceVariant
                val accentColor = MaterialTheme.colorScheme.primary
                val secondaryAccent = MaterialTheme.colorScheme.secondary
                
                // Background Track
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawArc(
                        color = unfocusedColor.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 40f, cap = StrokeCap.Round)
                    )
                }
                
                // Progress Arc (Animated)
                // We show full circle if cache > 0 and not cleaned
                // If cleaned, it animates to 0
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                accentColor,
                                secondaryAccent, 
                                accentColor // loop back
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progressAnimation,
                        useCenter = false,
                        style = Stroke(width = 40f, cap = StrokeCap.Round)
                    )
                }
                
                // Center Text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isCleaning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Cleaning...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else if (isCleaned) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Cleaned",
                            tint = Color(0xFF4CAF50), // Standard Success Green
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Optimized",
                            color = Color(0xFF4CAF50),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = formattedSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Junk Found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Options Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "What to clean?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    CleaningOption(
                        title = "Cached Images & Files",
                        subtitle = "Frees up space without affecting login",
                        checked = true, // Always true strictly speaking for "Cache"
                        onCheckedChange = { /* Prevent unchecking */ },
                        enabled = false
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CleaningOption(
                        title = "Website Storage",
                        subtitle = "Local storage data from visited sites",
                        checked = cleanWebStorage,
                        onCheckedChange = { cleanWebStorage = it }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CleaningOption(
                        title = "Cookies",
                        subtitle = "Signs you out of most websites",
                        checked = cleanCookies,
                        onCheckedChange = { cleanCookies = it }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                 }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action Button
            AnimatedVisibility(
                visible = !isCleaned,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        if (!isCleaning) {
                            isCleaning = true
                            scope.launch {
                                // Simulate scanning/working for better UX
                                delay(800)
                                
                                CacheManager.clearCache(
                                    context = context,
                                    includeWebStorage = cleanWebStorage,
                                    includeCookies = cleanCookies
                                ) {
                                    scope.launch {
                                        // Update state after cleaning
                                        val newSize = CacheManager.calculateTotalCacheSize(context)
                                        cacheSize = newSize
                                        formattedSize = CacheManager.formatSize(newSize)
                                        isCleaning = false
                                        isCleaned = true
                                        
                                        Toast.makeText(context, "Cleanup Complete!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isCleaning
                ) {
                    if (isCleaning) {
                        Text("Cleaning...", fontSize = 18.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Clean Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            if (isCleaned) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun CleaningOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}
