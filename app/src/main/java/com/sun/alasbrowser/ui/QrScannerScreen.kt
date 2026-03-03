package com.sun.alasbrowser.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit
) {
    // Handle back button press
    BackHandler {
        onClose()
    }
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                onResult(value)
                                return@addOnSuccessListener
                            }
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            
                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                val scanner = BarcodeScanning.getClient()
                                
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { value ->
                                                // Ensure we only call onResult once
                                                imageProxy.close()
                                                onResult(value)
                                                return@addOnSuccessListener
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        // Only close if we didn't find a barcode or processed it
                                        // Note: imageProxy.close() is idempotent
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark overlay with transparent cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val scanAreaSize = 280.dp.toPx()
                val scanAreaLeft = (canvasWidth - scanAreaSize) / 2
                val scanAreaTop = (canvasHeight - scanAreaSize) / 2
                
                // Draw semi-transparent background
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    size = size
                )
                
                // Clear the center scan area
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(scanAreaLeft, scanAreaTop),
                    size = GeometrySize(scanAreaSize, scanAreaSize),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                
                // Draw white corners
                val cornerLength = 40.dp.toPx()
                val strokeWidth = 4.dp.toPx()
                val radius = 16.dp.toPx()
                
                // Top Left
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(scanAreaLeft, scanAreaTop),
                    size = GeometrySize(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + radius, scanAreaTop),
                    end = Offset(scanAreaLeft + radius + cornerLength, scanAreaTop),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft, scanAreaTop + radius),
                    end = Offset(scanAreaLeft, scanAreaTop + radius + cornerLength),
                    strokeWidth = strokeWidth
                )

                // Top Right
                drawArc(
                    color = Color.White,
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(scanAreaLeft + scanAreaSize - radius * 2, scanAreaTop),
                    size = GeometrySize(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + scanAreaSize - radius, scanAreaTop),
                    end = Offset(scanAreaLeft + scanAreaSize - radius - cornerLength, scanAreaTop),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + scanAreaSize, scanAreaTop + radius),
                    end = Offset(scanAreaLeft + scanAreaSize, scanAreaTop + radius + cornerLength),
                    strokeWidth = strokeWidth
                )

                // Bottom Left
                drawArc(
                    color = Color.White,
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(scanAreaLeft, scanAreaTop + scanAreaSize - radius * 2),
                    size = GeometrySize(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + radius, scanAreaTop + scanAreaSize),
                    end = Offset(scanAreaLeft + radius + cornerLength, scanAreaTop + scanAreaSize),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft, scanAreaTop + scanAreaSize - radius),
                    end = Offset(scanAreaLeft, scanAreaTop + scanAreaSize - radius - cornerLength),
                    strokeWidth = strokeWidth
                )

                // Bottom Right
                drawArc(
                    color = Color.White,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(scanAreaLeft + scanAreaSize - radius * 2, scanAreaTop + scanAreaSize - radius * 2),
                    size = GeometrySize(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + scanAreaSize - radius, scanAreaTop + scanAreaSize),
                    end = Offset(scanAreaLeft + scanAreaSize - radius - cornerLength, scanAreaTop + scanAreaSize),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White,
                    start = Offset(scanAreaLeft + scanAreaSize, scanAreaTop + scanAreaSize - radius),
                    end = Offset(scanAreaLeft + scanAreaSize, scanAreaTop + scanAreaSize - radius - cornerLength),
                    strokeWidth = strokeWidth
                )
            }
            
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(280.dp)) // Spacer for scan area
                
                Text(
                    text = "Scan QR code",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            
            // Scan from photo button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF2D2E30))
                    .clickable { photoPickerLauncher.launch("image/*") }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Scan from photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan from photo",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("Camera permission required", color = Color.White)
        }
    }
}
