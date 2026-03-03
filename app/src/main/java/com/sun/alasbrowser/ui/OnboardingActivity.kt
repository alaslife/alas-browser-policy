package com.sun.alasbrowser.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sun.alasbrowser.MainActivity
import com.sun.alasbrowser.R
import com.sun.alasbrowser.data.BrowserPreferences
import kotlin.math.roundToInt

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val preferences = BrowserPreferences(this)
        setContent {
            com.sun.alasbrowser.ui.theme.AlasBrowserTheme(appTheme = preferences.appTheme) {
                OnboardingScreen {
                    completeOnboarding()
                }
            }
        }
    }

    private fun completeOnboarding() {
        val preferences = BrowserPreferences(this)
        preferences.setIsFirstLaunch(false)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E1E),
                        Color(0xFF000000)
                    )
                ))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 50.dp, top = 60.dp), // Added top padding
            verticalArrangement = Arrangement.SpaceBetween, // Distribute space
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Content pushed towards top-center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                 modifier = Modifier.padding(top = 40.dp)
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_bluesky_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 24.dp)
                )

                // Text
                val cyreneFont = FontFamily(Font(R.font.cyrene_regular))
                
                Text(
                    text = "Fast, Secure, Private.",
                    color = Color.LightGray,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Terms Link
                Text(
                    text = "Read our Privacy Policy & Terms",
                    color = Color(0xFF4CAF50), // Greenish accent
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable {
                            context.startActivity(Intent(context, TermsActivity::class.java))
                        }
                        .padding(8.dp)
                )
            }

            // Slider at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                 SlideToStartButton(onSlideComplete = onComplete)
            }
        }
    }
}

@Composable
fun SlideToStartButton(onSlideComplete: () -> Unit) {
    // Determine the width of the swipe area
    val width = 300.dp
    val dragSize = 52.dp // Size of the draggable circle
    
    val density = LocalDensity.current
    val totalWidthPx = with(density) { width.toPx() }
    val dragSizePx = with(density) { dragSize.toPx() }
    val maxDrag = totalWidthPx - dragSizePx
    
    var offsetX by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(offsetX) {
        if (offsetX >= maxDrag * 0.9f) {
            onSlideComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .width(width)
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF333333))
    ) {
        // Text in the background
        Text(
            text = "Slide to get started",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // Draggable Circle
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newValue = offsetX + delta
                        offsetX = newValue.coerceIn(0f, maxDrag)
                    },
                    onDragStopped = {
                         // Reset if not completed
                         if (offsetX < maxDrag * 0.9f) {
                             offsetX = 0f
                         }
                    }
                )
                .size(60.dp) 
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(4.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Slide",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
