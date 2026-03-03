package com.sun.alasbrowser.ui

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserFullscreenVideo(
    customView: View,
    title: String?,
    onClose: () -> Unit
) {
    var videoScale by remember { mutableFloatStateOf(1f) }
    var videoOffsetX by remember { mutableFloatStateOf(0f) }
    var videoOffsetY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size = it }
    ) {
        AndroidView(
            factory = { customView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = videoScale,
                    scaleY = videoScale,
                    translationX = videoOffsetX,
                    translationY = videoOffsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Update scale (with limits)
                        videoScale = (videoScale * zoom).coerceIn(1f, 4f)

                        // Update offset (panning)
                        if (videoScale > 1f) {
                            videoOffsetX += pan.x
                            videoOffsetY += pan.y

                            // Limit panning to prevent video from going too far
                            val maxOffset = (size.width * (videoScale - 1) / 2f)
                            videoOffsetX = videoOffsetX.coerceIn(-maxOffset, maxOffset)
                            videoOffsetY = videoOffsetY.coerceIn(-maxOffset, maxOffset)
                        } else {
                            // Reset offset when zoomed out to 1x
                            videoOffsetX = 0f
                            videoOffsetY = 0f
                        }
                    }
                }
        )

        // Top bar with close button
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onClose()
                        // Reset internal state (though component will likely be destroyed)
                        videoScale = 1f
                        videoOffsetX = 0f
                        videoOffsetY = 0f
                    }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Title (if available)
                if (!title.isNullOrEmpty()) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Zoom indicator
        if (videoScale > 1f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding(),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "${(videoScale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }
}
