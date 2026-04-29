package com.example.smartphonapptest001.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smartphonapptest001.data.model.Attachment
import com.example.smartphonapptest001.data.model.AttachmentKind
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

@Composable
fun CameraChatBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    implementationMode: PreviewView.ImplementationMode = PreviewView.ImplementationMode.COMPATIBLE,
    onFrameCaptured: (Attachment) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFrameCaptured = rememberUpdatedState(onFrameCaptured)
    val previewView = remember(context, implementationMode) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            this.implementationMode = implementationMode
        }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraBound by remember { mutableStateOf(false) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (enabled && hasCameraPermission(context)) {
            val future = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            val listener = Runnable {
                runCatching {
                    val provider = future.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                    cameraBound = true
                }
            }
            future.addListener(listener, executor)
            onDispose {
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                cameraBound = false
            }
        } else {
            onDispose {
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                cameraBound = false
            }
        }
    }

    LaunchedEffect(enabled, cameraBound) {
        if (!enabled || !cameraBound || !hasCameraPermission(context)) return@LaunchedEffect
        while (true) {
            runCatching { previewView.bitmap }
                .getOrNull()
                ?.let { bitmap ->
                    latestOnFrameCaptured.value(bitmap.toRealtimePlantAttachment())
                }
            delay(800)
        }
    }

    if (enabled && hasCameraPermission(context)) {
        AndroidView(
            factory = { previewView },
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                    ),
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Camera preview unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun Bitmap.toRealtimePlantAttachment(): Attachment {
    val scaled = scaleDown(maxDimension = 896)
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    return Attachment(
        uri = null,
        displayName = "realtime-${System.currentTimeMillis()}.jpg",
        mimeType = "image/jpeg",
        kind = AttachmentKind.IMAGE,
        dataUrl = "data:image/jpeg;base64,$base64",
    )
}

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val longest = maxOf(width, height).toFloat()
    val scale = if (longest <= maxDimension) 1f else maxDimension / longest
    if (scale >= 1f) return this
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}
