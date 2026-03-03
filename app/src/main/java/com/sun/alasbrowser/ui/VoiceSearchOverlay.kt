package com.sun.alasbrowser.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- 2027 Fluid Glass Palette ---
private val BackgroundDark = Color(0xFF030303)
private val GlassWhite = Color(0xFFFFFFFF)
private val FluidBlue = Color(0xFF2A52BE)
private val FluidCyan = Color(0xFF00E5FF)
private val FluidPurple = Color(0xFF6B4EE6)
private val ErrorRed = Color(0xFFFF3366)

@Composable
fun ModernVoiceSearchOverlay(
    visible: Boolean,
    partialResult: String,
    error: String?,
    rms: Float = 0f,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartListening: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isListening = error == null
    val rmsNorm by remember(rms) {
        derivedStateOf { ((rms + 2f).coerceIn(0f, 15f) / 15f) }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, easing = LinearOutSlowInEasing)),
        exit = fadeOut(tween(400, easing = FastOutLinearInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume taps */ }
                .semantics { contentDescription = "Voice search overlay" }
        ) {
            // Ambient deeply blurred light background
            AmbientLiquidBackground(isListening, rmsNorm, hasError = error != null)

            // Close Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(24.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlassWhite.copy(alpha = 0.05f))
                    .border(0.5.dp, GlassWhite.copy(alpha = 0.15f), CircleShape)
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss() 
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = GlassWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Main Minimal UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Transcription area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayText = error ?: partialResult.takeIf { it.isNotBlank() } ?: "I'm listening..."
                    
                    AnimatedContent(
                        targetState = displayText,
                        transitionSpec = {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                        },
                        label = "text_swap"
                    ) { text ->
                        Text(
                            text = text,
                            color = GlassWhite.copy(alpha = if (text == "I'm listening...") 0.5f else 0.9f),
                            fontSize = if (text == partialResult && text.length > 25) 24.sp else 32.sp,
                            fontWeight = if (text == "I'm listening...") FontWeight.Normal else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 44.sp,
                            letterSpacing = if (text == "I'm listening...") 1.sp else 0.sp,
                            modifier = Modifier.semantics { contentDescription = text }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(72.dp))

                // 2027 Fluid Glass Orb
                FluidGlassOrb(
                    isListening = isListening,
                    rmsNorm = rmsNorm,
                    hasError = error != null,
                    onRetry = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartListening()
                    }
                )

                Spacer(modifier = Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun AmbientLiquidBackground(isListening: Boolean, rmsNorm: Float, hasError: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "phase2"
    )

    val intenseRms by animateFloatAsState(targetValue = if(isListening) rmsNorm else 0f, tween(300), label = "rms")
    val errorTint by animateColorAsState(targetValue = if(hasError) ErrorRed else FluidBlue, label = "tint1")
    val errorTint2 by animateColorAsState(targetValue = if(hasError) ErrorRed.copy(alpha = 0.5f) else FluidPurple, label = "tint2")

    Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
        val w = size.width
        val h = size.height

        val r1 = w * 0.8f + (intenseRms * w * 0.3f)
        val cx1 = w * 0.5f + cos(phase1) * w * 0.2f
        val cy1 = h * 0.4f + sin(phase1) * w * 0.2f
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(errorTint.copy(alpha = 0.12f + intenseRms * 0.08f), Color.Transparent),
                center = Offset(cx1, cy1),
                radius = r1
            )
        )

        val r2 = w * 0.75f + (intenseRms * w * 0.2f)
        val cx2 = w * 0.5f + cos(phase2 + PI.toFloat()) * w * 0.25f
        val cy2 = h * 0.6f + sin(phase2 + PI.toFloat()) * w * 0.25f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(errorTint2.copy(alpha = 0.1f + intenseRms * 0.05f), Color.Transparent),
                center = Offset(cx2, cy2),
                radius = r2
            ),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun FluidGlassOrb(
    isListening: Boolean,
    rmsNorm: Float,
    hasError: Boolean,
    onRetry: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "morph"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "rotate"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (isListening) rmsNorm else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 100f),
        label = "rms_spring"
    )

    val scale by animateFloatAsState(
        targetValue = if (hasError) 0.95f else 1f + (animatedRms * 0.15f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = hasError
            ) { onRetry() },
        contentAlignment = Alignment.Center
    ) {
        
        // Fluid Glass Path
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension * 0.4f
            
            val path = Path()
            val points = 80
            for (i in 0..points) {
                val angle = (i * 2 * PI / points).toFloat()
                
                // Fluid distortion: smooth, organic waves based on RMS
                val noise = sin(angle * 3 + morphPhase) * cos(angle * 2 - morphPhase * 0.5f)
                val distortion = if (isListening) noise * (8f + animatedRms * 24f) else 0f
                
                val currentR = baseR + distortion
                val px = cx + cos(angle) * currentR
                val py = cy + sin(angle) * currentR
                
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            val primaryTint = if (hasError) ErrorRed else FluidCyan
            val secondaryTint = if (hasError) ErrorRed else FluidBlue

            // Glass Fill
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryTint.copy(alpha = 0.15f),
                        secondaryTint.copy(alpha = 0.05f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )

            // Glass Highlight (Specular)
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(GlassWhite.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(cx * 0.6f, cy * 0.6f),
                    radius = baseR * 1.2f
                ),
                blendMode = BlendMode.Overlay
            )
            
            // Ultra-thin frosted glass rim
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassWhite.copy(alpha = 0.6f), 
                        primaryTint.copy(alpha = 0.3f),
                        GlassWhite.copy(alpha = 0.1f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Inner Core Content
        if (hasError) {
            Text(
                text = "Tap\nRetry",
                color = GlassWhite.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        } else {
            // Elegant single breathing pulse dot
            val dotPulse by infiniteTransition.animateFloat(
                initialValue = 0.6f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "dot_pulse"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = dotPulse + animatedRms * 0.5f
                        scaleY = dotPulse + animatedRms * 0.5f
                    }
                    .clip(CircleShape)
                    .background(GlassWhite.copy(alpha = 0.9f))
            )
        }
    }
}

// ── Voice Recognizer (Unchanged) ───────────────────────────────────────
@Composable
fun rememberVoiceRecognizer(
    state: BrowserScreenState,
    onFinalResult: (String) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(state.showVoiceOverlay) {
        if (!state.showVoiceOverlay) {
            return@DisposableEffect onDispose { }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state.voiceError = "Speech recognition not available"
            return@DisposableEffect onDispose { }
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var disposed = false
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (disposed) return
                state.isListening = true
                state.voiceError = null
                state.voicePartialResult = ""
                state.voiceRms = 0f
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (disposed) return
                state.voiceRms = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (disposed) return
                state.isListening = false
                state.voiceRms = 0f
            }
            override fun onError(error: Int) {
                if (disposed) return
                state.isListening = false
                state.voiceRms = 0f
                state.voiceError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    else -> "Something went wrong"
                }
            }
            override fun onResults(results: Bundle?) {
                if (disposed) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    state.voicePartialResult = text
                    onFinalResult(text)
                } else {
                    state.voiceError = "Didn't catch that. Try again"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (disposed) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    state.voicePartialResult = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
        onDispose {
            disposed = true
            try {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            } catch (_: Exception) { }
            state.isListening = false
            state.voiceRms = 0f
        }
    }
}
