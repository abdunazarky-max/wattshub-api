package com.hyzin.whtsappclone

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.concurrent.futures.await

@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }

    var hasCameraPermission by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
        try {
            cameraProvider = cameraProviderFuture.await()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to get camera provider", e)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (capturedImageUri != null) {
            // Preview State
            AsyncImage(
                model = capturedImageUri,
                contentDescription = "Captured Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Top Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { capturedImageUri = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Retake", tint = Color.White)
                }
            }

            // Bottom Actions (Send and Retake Buttons)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { capturedImageUri = null },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Retake", color = Color.White, fontSize = 16.sp)
                    }

                    FloatingActionButton(
                        onClick = { onImageCaptured(capturedImageUri!!) },
                        containerColor = Color(0xFF00A884), // WhatsApp Green
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Image")
                    }
                }
            }
        } else {
            var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

            // Camera Preview State
            LaunchedEffect(cameraProvider, lifecycleOwner, surfaceProvider, lensFacing) {
                val provider = cameraProvider ?: return@LaunchedEffect
                val sProvider = surfaceProvider ?: return@LaunchedEffect
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(sProvider)
                }
                
                val imgCap = ImageCapture.Builder().build()
                imageCapture = imgCap
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imgCap
                    )
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case binding failed", exc)
                }
            }

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.surfaceProvider.also { surfaceProvider = it }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.DarkGray, CircleShape)
                )

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(if (isCapturing) Color.Gray else Color.White, CircleShape)
                        .clickable(enabled = !isCapturing && imageCapture != null) {
                            isCapturing = true
                            val photoFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture?.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        val uri = Uri.fromFile(photoFile)
                                        capturedImageUri = uri
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                                        Toast.makeText(context, "Capture Failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                )

                IconButton(onClick = { 
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
