package com.sun.alasbrowser.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.alasbrowser.data.BrowserPreferences
import com.sun.alasbrowser.data.WebViewDarkMode

private val BackgroundColor = Color(0xFF111318)
private val CardColor = Color(0xFF1C1F27)
private val CardBorder = Color(0xFF2A2E38)
private val AccentGreen = Color(0xFF34C759)
private val AccentOrange = Color(0xFFFF9F0A)
private val AccentBlue = Color(0xFF6BB8FF)
private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFA0A4B0)
private val TextMuted = Color(0xFF6B6F7B)

// Color temperature gradient colors
private val TempCool = Color(0xFF7EB6FF)
private val TempNeutral = Color(0xFFFFF4E0)
private val TempWarm = Color(0xFFFF8C42)
private val TempHot = Color(0xFFE85D26)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NightModeSettingsScreen(
    preferences: BrowserPreferences,
    onBack: () -> Unit
) {
    var nightModeEnabled by remember { mutableStateOf(preferences.nightModeEnabled) }
    var colorTemperature by remember { mutableFloatStateOf(preferences.nightModeColorTemp) }
    var dimming by remember { mutableFloatStateOf(preferences.nightModeDimming) }
    var useDarkTheme by remember { mutableStateOf(preferences.appTheme.name.contains("DARK")) }
    var darkenWebpages by remember { mutableStateOf(preferences.webViewDarkMode == WebViewDarkMode.DARK_PREFERRED) }
    var dimKeyboard by remember { mutableStateOf(preferences.dimKeyboard) }
    var scheduleEnabled by remember { mutableStateOf(preferences.nightModeScheduleEnabled) }
    var scheduleStart by remember { mutableIntStateOf(preferences.nightModeScheduleStart) }
    var scheduleEnd by remember { mutableIntStateOf(preferences.nightModeScheduleEnd) }
    var showSchedulePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Night Mode",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundColor
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Master toggle card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (nightModeEnabled) AccentOrange.copy(alpha = 0.15f)
                                    else CardBorder
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (nightModeEnabled) AccentOrange else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Night Mode",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when {
                                    nightModeEnabled -> "Active now"
                                    scheduleEnabled -> "${formatHour(scheduleStart)} – ${formatHour(scheduleEnd)}"
                                    else -> "Off"
                                },
                                color = if (nightModeEnabled) AccentGreen else TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Switch(
                        checked = nightModeEnabled,
                        onCheckedChange = { enabled ->
                            nightModeEnabled = enabled
                            preferences.setNightModeEnabled(enabled)
                            if (enabled) {
                                preferences.setWebViewDarkMode(WebViewDarkMode.DARK_PREFERRED)
                                darkenWebpages = true
                            } else if (!scheduleEnabled) {
                                preferences.setWebViewDarkMode(WebViewDarkMode.LIGHT_PREFERRED)
                                darkenWebpages = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = CardBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preview",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        // Simulated webpage content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E2028))
                                .padding(14.dp)
                        ) {
                            // Fake URL bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2A2D38))
                            ) {
                                Text(
                                    text = "  example.com",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // Fake heading
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TextPrimary.copy(alpha = 0.8f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Fake text lines
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextSecondary.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextSecondary.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextSecondary.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Fake image placeholder
                            Row {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp, 36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF2C3E50),
                                                    Color(0xFF3498DB)
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(TextSecondary.copy(alpha = 0.3f))
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(TextSecondary.copy(alpha = 0.2f))
                                    )
                                }
                            }
                        }

                        // Night mode overlay on preview
                        if (nightModeEnabled || (scheduleEnabled && preferences.isNightModeActiveNow())) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(NightModeTuning.warmOverlayColor(colorTemperature))
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Color.Black.copy(
                                            alpha = NightModeTuning.dimOverlayAlpha(dimming)
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Color Temperature Slider Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbTwilight,
                                contentDescription = null,
                                tint = lerpColor(TempCool, TempWarm, colorTemperature),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Colour Temperature",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${(colorTemperature * 100).toInt()}%",
                            color = lerpColor(TempCool, TempWarm, colorTemperature),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gradient track preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(TempCool, TempNeutral, TempWarm, TempHot)
                                )
                            )
                    )

                    Slider(
                        value = colorTemperature,
                        onValueChange = {
                            colorTemperature = it
                            preferences.setNightModeColorTemp(it)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = lerpColor(TempCool, TempWarm, colorTemperature),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TempCool)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Cool", color = TextMuted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Warm", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TempWarm)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dimming Slider Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (dimming > 0.5f) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = lerpColor(Color(0xFFFFC857), Color(0xFF6B6F7B), dimming),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dimming",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${(dimming * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gradient track preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFFC857),
                                        Color(0xFF8A7A5A),
                                        Color(0xFF3A3A3A),
                                        Color(0xFF1A1A1A)
                                    )
                                )
                            )
                    )

                    Slider(
                        value = dimming,
                        onValueChange = {
                            dimming = it
                            preferences.setNightModeDimming(it)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Bright", color = TextMuted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Dark", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Additional Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column {
                    // Use dark theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                useDarkTheme = !useDarkTheme
                                val newTheme = if (useDarkTheme) {
                                    com.sun.alasbrowser.data.AppTheme.DARK
                                } else {
                                    com.sun.alasbrowser.data.AppTheme.LIGHT
                                }
                                preferences.appTheme = newTheme
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use dark theme",
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = useDarkTheme,
                            onCheckedChange = { checked ->
                                useDarkTheme = checked
                                val newTheme = if (checked) {
                                    com.sun.alasbrowser.data.AppTheme.DARK
                                } else {
                                    com.sun.alasbrowser.data.AppTheme.LIGHT
                                }
                                preferences.appTheme = newTheme
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = CardBorder
                            )
                        )
                    }

                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Darken webpages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                darkenWebpages = !darkenWebpages
                                val newMode = if (darkenWebpages) {
                                    WebViewDarkMode.DARK_PREFERRED
                                } else {
                                    WebViewDarkMode.LIGHT_PREFERRED
                                }
                                preferences.setWebViewDarkMode(newMode)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Darken webpages",
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Checkbox(
                            checked = darkenWebpages,
                            onCheckedChange = { checked ->
                                darkenWebpages = checked
                                val newMode = if (checked) {
                                    WebViewDarkMode.DARK_PREFERRED
                                } else {
                                    WebViewDarkMode.LIGHT_PREFERRED
                                }
                                preferences.setWebViewDarkMode(newMode)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentGreen,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                    }

                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Dim keyboard
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                dimKeyboard = !dimKeyboard
                                preferences.setDimKeyboard(dimKeyboard)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dim keyboard",
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Checkbox(
                            checked = dimKeyboard,
                            onCheckedChange = { checked ->
                                dimKeyboard = checked
                                preferences.setDimKeyboard(checked)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentGreen,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Schedule Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSchedulePicker = !showSchedulePicker }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (scheduleEnabled) AccentBlue.copy(alpha = 0.15f)
                                    else CardBorder
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Schedule",
                                tint = if (scheduleEnabled) AccentBlue else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Schedule",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (scheduleEnabled) {
                                    "${formatHour(scheduleStart)} – ${formatHour(scheduleEnd)}"
                                } else {
                                    "Off"
                                },
                                color = if (scheduleEnabled) AccentBlue else TextMuted,
                                fontSize = 13.sp
                            )
                        }

                        Switch(
                            checked = scheduleEnabled,
                            onCheckedChange = { enabled ->
                                scheduleEnabled = enabled
                                preferences.setNightModeScheduleEnabled(enabled)
                                if (enabled && !nightModeEnabled) {
                                    if (preferences.isNightModeActiveNow()) {
                                        preferences.setWebViewDarkMode(WebViewDarkMode.DARK_PREFERRED)
                                        darkenWebpages = true
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = CardBorder
                            )
                        )
                    }

                    if (showSchedulePicker && scheduleEnabled) {
                        HorizontalDivider(
                            color = CardBorder,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Start time",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HourSelector(
                                selectedHour = scheduleStart,
                                onHourSelected = { hour ->
                                    scheduleStart = hour
                                    preferences.setNightModeScheduleStart(hour)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "End time",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HourSelector(
                                selectedHour = scheduleEnd,
                                onHourSelected = { hour ->
                                    scheduleEnd = hour
                                    preferences.setNightModeScheduleEnd(hour)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HourSelector(
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    val presetHours = listOf(18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5, 6, 7, 8)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in presetHours.chunked(5)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (hour in row) {
                    val isSelected = hour == selectedHour
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> AccentBlue
                                    else -> CardBorder.copy(alpha = 0.5f)
                                }
                            )
                            .clickable { onHourSelected(hour) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatHour(hour),
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}

private fun formatHour(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
