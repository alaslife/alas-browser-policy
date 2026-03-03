package com.sun.alasbrowser.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// Modern Color Palette
private val NeonPink = Color(0xFFFF0055)
private val NeonPurple = Color(0xFFD500F9)
private val DarkBackground = Color(0xFF0A0A0A)
private val DarkSurface = Color(0xFF161618)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFAAAAAA)

@Composable
fun FuturisticAdBlockerHero(
    isEnabled: Boolean,
    blockedToday: Int,
    totalBlocked: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp) // Increased height to fit all content comfortably
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Subtle animated background
        ElegantBackground()
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Central Shield with elegant animation
            ElegantShieldIcon(isEnabled = isEnabled)
            
            Spacer(Modifier.height(24.dp))
            
            // Status Text
            Text(
                text = if (isEnabled) "Protection Active" else "Protection Paused",
                color = if (isEnabled) NeonPink else Color(0xFF666666),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = if (isEnabled) "Advanced ad blocking enabled" else "Tap to enable protection",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            
            Spacer(Modifier.height(34.dp))
            
            // Statistics with clean design
            StatsOverview(blockedToday = blockedToday, totalBlocked = totalBlocked)
        }
    }
}

@Composable
fun ElegantShieldIcon(isEnabled: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = if (isEnabled) NeonPink else Color(0xFF444444)
    val glowColor = if (isEnabled) NeonPink.copy(alpha = 0.5f) else Color.Transparent
    
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background glow
        if (isEnabled) {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor,
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension / 2
                    )
                )
            }
        }
        
        // Main shield container
        Box(
            modifier = Modifier
                .size(100.dp * if (isEnabled) scale else 1f)
                .clip(CircleShape)
                .background(
                    color = color.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .drawWithCache {
                    onDrawBehind {
                        if (isEnabled) {
                            // Animated ring
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        NeonPink,
                                        NeonPurple,
                                        Color.Transparent
                                    )
                                ),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF444444),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun StatsOverview(blockedToday: Int, totalBlocked: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(
            value = blockedToday.toString(),
            label = "Blocked Today",
            accentColor = NeonPink
        )
        
        // Subtle divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF333333), Color.Transparent)
                    )
                )
        )
        
        StatItem(
            value = totalBlocked.toString(),
            label = "Total Blocked",
            accentColor = NeonPurple
        )
    }
}

@Composable
fun StatItem(value: String, label: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ElegantBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Dark gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    DarkBackground,
                    Color(0xFF0F0F0F)
                )
            )
        )
        
        // Grid pattern
        val gridSize = 60f
        val gridColor = Color(0xFF222222).copy(alpha = 0.5f)
        
        for (x in 0..(width/gridSize).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x * gridSize, 0f),
                end = Offset(x * gridSize, height),
                strokeWidth = 1f
            )
        }
        
         for (y in 0..(height/gridSize).toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y * gridSize),
                end = Offset(width, y * gridSize),
                strokeWidth = 1f
            )
        }
        
        // Animated sine wave
        val path = Path()
        val waveAmplitude = 20f
        val waveY = height * 0.8f
        
        path.moveTo(0f, waveY)
        for (x in 0..width.toInt() step 5) {
            val y = waveY + waveAmplitude * sin((x + offset) * 0.02).toFloat()
            path.lineTo(x.toFloat(), y)
        }
        
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeonPink.copy(alpha = 0.1f),
                    NeonPurple.copy(alpha = 0.1f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
fun AdvancedStatsCard(
    dataSavedMB: Float,
    timeSavedMin: Float,
    protectionLevel: Float,
    bandwidthSaved: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(24.dp), // More rounded
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Performance Overview",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Data Saved
            MetricRow(
                icon = Icons.Default.DataUsage,
                label = "Data Saved",
                value = "%.1f MB".format(dataSavedMB),
                progress = (dataSavedMB / 200f).coerceIn(0f, 1f),
                gradient = Brush.horizontalGradient(listOf(NeonPink, Color(0xFFFF4081)))
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Time Saved
            MetricRow(
                icon = Icons.Default.Timer,
                label = "Time Saved",
                value = "%.0f min".format(timeSavedMin),
                progress = (timeSavedMin / 120f).coerceIn(0f, 1f),
                gradient = Brush.horizontalGradient(listOf(NeonPurple, Color(0xFFE040FB)))
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Protection Level
            MetricRow(
                icon = Icons.Default.Security,
                label = "Protection Level",
                value = "${(protectionLevel * 100).toInt()}%",
                progress = protectionLevel,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)))
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Bandwidth Efficiency
            MetricRow(
                icon = Icons.Default.Speed,
                label = "Bandwidth Saved",
                value = "%.1f%%".format(bandwidthSaved),
                progress = bandwidthSaved / 100f,
                gradient = Brush.horizontalGradient(listOf(Color(0xFF76FF03), Color(0xFF64DD17)))
            )
        }
    }
}

@Composable
fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    gradient: Brush
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222222)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Elegant progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF222222))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(gradient)
            )
        }
    }
}

