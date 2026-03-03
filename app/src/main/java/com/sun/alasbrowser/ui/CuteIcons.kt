package com.sun.alasbrowser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CuteFlaskIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        // Flask body
        val path = Path().apply {
            moveTo(width * 0.35f, height * 0.2f)
            lineTo(width * 0.35f, height * 0.4f)
            lineTo(width * 0.2f, height * 0.7f)
            cubicTo(
                width * 0.15f, height * 0.8f,
                width * 0.15f, height * 0.85f,
                width * 0.2f, height * 0.9f
            )
            lineTo(width * 0.8f, height * 0.9f)
            cubicTo(
                width * 0.85f, height * 0.85f,
                width * 0.85f, height * 0.8f,
                width * 0.8f, height * 0.7f
            )
            lineTo(width * 0.65f, height * 0.4f)
            lineTo(width * 0.65f, height * 0.2f)
        }
        
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
        )
        
        // Flask neck
        drawLine(
            color = tint,
            start = Offset(width * 0.4f, height * 0.1f),
            end = Offset(width * 0.6f, height * 0.1f),
            strokeWidth = width * 0.08f,
            cap = StrokeCap.Round
        )
        
        // Bubbles
        drawCircle(
            color = tint,
            radius = width * 0.06f,
            center = Offset(width * 0.4f, height * 0.65f)
        )
        drawCircle(
            color = tint,
            radius = width * 0.04f,
            center = Offset(width * 0.6f, height * 0.7f)
        )
    }
}

@Composable
fun CuteGalleryIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val cornerRadius = width * 0.15f
        
        // Back image
        drawRoundRect(
            color = tint.copy(alpha = 0.4f),
            topLeft = Offset(width * 0.15f, height * 0.1f),
            size = Size(width * 0.75f, height * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
        
        // Front image
        drawRoundRect(
            color = tint,
            topLeft = Offset(width * 0.1f, height * 0.25f),
            size = Size(width * 0.75f, height * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            style = Stroke(width = width * 0.08f)
        )
        
        // Mountain
        val mountainPath = Path().apply {
            moveTo(width * 0.15f, height * 0.75f)
            lineTo(width * 0.35f, height * 0.5f)
            lineTo(width * 0.55f, height * 0.65f)
            lineTo(width * 0.75f, height * 0.45f)
        }
        drawPath(
            path = mountainPath,
            color = tint,
            style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
        )
        
        // Sun
        drawCircle(
            color = tint,
            radius = width * 0.08f,
            center = Offset(width * 0.65f, height * 0.4f)
        )
    }
}

@Composable
fun CuteTabIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    count: String = ""
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val cornerRadius = width * 0.15f
        
        // Tab rectangle with rounded corners
        drawRoundRect(
            color = tint,
            topLeft = Offset(width * 0.15f, height * 0.25f),
            size = Size(width * 0.7f, height * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            style = Stroke(width = width * 0.1f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun CuteSettingsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val centerX = width / 2
        val centerY = height / 2
        val radius = width * 0.25f
        
        // Center circle
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = width * 0.08f)
        )
        
        // Gear teeth (6 cute rounded dots around)
        for (i in 0..5) {
            val angle = (i * 60f) * (Math.PI / 180f)
            val x = centerX + (width * 0.4f * cos(angle)).toFloat()
            val y = centerY + (width * 0.4f * sin(angle)).toFloat()
            
            drawCircle(
                color = tint,
                radius = width * 0.07f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun CutePlusIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val centerX = width / 2
        val centerY = height / 2
        val lineLength = width * 0.5f
        
        // Vertical line
        drawLine(
            color = tint,
            start = Offset(centerX, centerY - lineLength / 2),
            end = Offset(centerX, centerY + lineLength / 2),
            strokeWidth = width * 0.12f,
            cap = StrokeCap.Round
        )
        
        // Horizontal line
        drawLine(
            color = tint,
            start = Offset(centerX - lineLength / 2, centerY),
            end = Offset(centerX + lineLength / 2, centerY),
            strokeWidth = width * 0.12f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CuteSearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        
        // Search circle
        drawCircle(
            color = tint,
            radius = width * 0.3f,
            center = Offset(width * 0.4f, width * 0.4f),
            style = Stroke(width = width * 0.1f, cap = StrokeCap.Round)
        )
        
        // Handle
        drawLine(
            color = tint,
            start = Offset(width * 0.6f, width * 0.6f),
            end = Offset(width * 0.85f, width * 0.85f),
            strokeWidth = width * 0.1f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CuteMicIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        // Mic body
        drawRoundRect(
            color = tint,
            topLeft = Offset(width * 0.35f, height * 0.15f),
            size = Size(width * 0.3f, height * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.15f),
            style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
        )
        
        // Stand arc
        val arcPath = Path().apply {
            moveTo(width * 0.25f, height * 0.6f)
            cubicTo(
                width * 0.25f, height * 0.75f,
                width * 0.75f, height * 0.75f,
                width * 0.75f, height * 0.6f
            )
        }
        drawPath(
            path = arcPath,
            color = tint,
            style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
        )
        
        // Stand base
        drawLine(
            color = tint,
            start = Offset(width * 0.5f, height * 0.75f),
            end = Offset(width * 0.5f, height * 0.9f),
            strokeWidth = width * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CuteHeartIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    filled: Boolean = false
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.85f)
            
            // Right side
            cubicTo(
                width * 0.85f, height * 0.6f,
                width * 0.85f, height * 0.3f,
                width * 0.7f, height * 0.2f
            )
            cubicTo(
                width * 0.6f, height * 0.1f,
                width * 0.5f, height * 0.2f,
                width * 0.5f, height * 0.3f
            )
            
            // Left side
            cubicTo(
                width * 0.5f, height * 0.2f,
                width * 0.4f, height * 0.1f,
                width * 0.3f, height * 0.2f
            )
            cubicTo(
                width * 0.15f, height * 0.3f,
                width * 0.15f, height * 0.6f,
                width * 0.5f, height * 0.85f
            )
            close()
        }
        
        if (filled) {
            drawPath(path = path, color = tint)
        } else {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun CuteShareIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        // Three circles (nodes)
        drawCircle(
            color = tint,
            radius = width * 0.12f,
            center = Offset(width * 0.7f, height * 0.25f)
        )
        drawCircle(
            color = tint,
            radius = width * 0.12f,
            center = Offset(width * 0.3f, height * 0.5f)
        )
        drawCircle(
            color = tint,
            radius = width * 0.12f,
            center = Offset(width * 0.7f, height * 0.75f)
        )
        
        // Connecting lines
        drawLine(
            color = tint,
            start = Offset(width * 0.42f, height * 0.45f),
            end = Offset(width * 0.58f, height * 0.3f),
            strokeWidth = width * 0.06f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(width * 0.42f, height * 0.55f),
            end = Offset(width * 0.58f, height * 0.7f),
            strokeWidth = width * 0.06f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CuteGlobeIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val centerX = width / 2
        val centerY = height / 2
        val radius = width * 0.4f
        
        // Outer circle
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = width * 0.08f)
        )
        
        // Horizontal line
        drawLine(
            color = tint,
            start = Offset(width * 0.1f, centerY),
            end = Offset(width * 0.9f, centerY),
            strokeWidth = width * 0.08f,
            cap = StrokeCap.Round
        )
        
        // Vertical ellipse
        val ellipsePath = Path().apply {
            moveTo(centerX, height * 0.1f)
            cubicTo(
                width * 0.7f, height * 0.3f,
                width * 0.7f, height * 0.7f,
                centerX, height * 0.9f
            )
            cubicTo(
                width * 0.3f, height * 0.7f,
                width * 0.3f, height * 0.3f,
                centerX, height * 0.1f
            )
        }
        drawPath(
            path = ellipsePath,
            color = tint,
            style = Stroke(width = width * 0.08f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun CuteBackIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        // Arrow path
        val path = Path().apply {
            moveTo(width * 0.4f, height * 0.3f)
            lineTo(width * 0.2f, height * 0.5f)
            lineTo(width * 0.4f, height * 0.7f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = width * 0.1f, cap = StrokeCap.Round)
        )
        
        // Line
        drawLine(
            color = tint,
            start = Offset(width * 0.2f, height * 0.5f),
            end = Offset(width * 0.8f, height * 0.5f),
            strokeWidth = width * 0.1f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CuteMenuIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        
        // Three dots
        drawCircle(
            color = tint,
            radius = width * 0.08f,
            center = Offset(width * 0.5f, height * 0.25f)
        )
        drawCircle(
            color = tint,
            radius = width * 0.08f,
            center = Offset(width * 0.5f, height * 0.5f)
        )
        drawCircle(
            color = tint,
            radius = width * 0.08f,
            center = Offset(width * 0.5f, height * 0.75f)
        )
    }
}
