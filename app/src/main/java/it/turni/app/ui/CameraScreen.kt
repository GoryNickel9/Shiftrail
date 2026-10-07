package it.turni.app.ui

import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.turni.app.R
import java.io.File
import java.util.UUID

@Composable
fun CameraScreen(onCaptured: (File) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(2560, 1920),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(owner) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (!disposed)
                    try {
                        provider = future.get()
                        val preview =
                            Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                        provider?.unbindAll()
                        camera =
                            provider?.bindToLifecycle(
                                owner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        previewView.setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                view.performClick()
                                val point =
                                    previewView.meteringPointFactory.createPoint(event.x, event.y)
                                camera
                                    ?.cameraControl
                                    ?.startFocusAndMetering(
                                        FocusMeteringAction.Builder(point).build()
                                    )
                            }
                            true
                        }
                    } catch (_: Exception) {
                        onError(context.getString(R.string.camera_failed))
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.camera_help))
        AndroidView(factory = { previewView }, Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = {
                    flash = !flash
                    capture.flashMode =
                        if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                },
                enabled = camera?.cameraInfo?.hasFlashUnit() == true && !capturing,
            ) {
                Text(stringResource(R.string.flash))
            }
            Button(
                onClick = {
                    capturing = true
                    capture.targetRotation =
                        previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    val file = File(context.cacheDir, "turni-${UUID.randomUUID()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                capturing = false
                                onCaptured(file)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                file.delete()
                                onError(context.getString(R.string.camera_failed))
                            }
                        },
                    )
                },
                enabled = camera != null && !capturing,
            ) {
                Text(stringResource(R.string.capture))
            }
        }
    }
}
