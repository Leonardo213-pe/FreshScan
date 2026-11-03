package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.CameraManager
import com.example.data.local.FlashModeOption
import com.example.data.local.ImageProcessor
import com.example.data.local.LocalYoloDetector
import com.example.domain.model.AnalysisEngineMode
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.RipenessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    capturedBitmap: Bitmap?,
    isCameraPermissionGranted: Boolean,
    lensFacingBack: Boolean,
    flashMode: FlashModeOption,
    hasFlashUnit: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onToggleLens: () -> Unit,
    onCycleFlash: () -> Unit,
    onHasFlashUnitChanged: (Boolean) -> Unit,
    onPhotoReady: (Bitmap?) -> Unit,
    onRetakePhoto: () -> Unit,
    onConfirmAndAnalyze: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenConservationGuide: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    engineMode: AnalysisEngineMode = AnalysisEngineMode.HYBRID_YOLO_GEMINI,
    onEngineModeChange: (AnalysisEngineMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (capturedBitmap != null) {
            onRetakePhoto()
        } else {
            onNavigateBack()
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraManager = remember { CameraManager(context) }
    val imageProcessor = remember { ImageProcessor() }
    val liveYoloDetector = remember { LocalYoloDetector() }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isLifecycleResumed by remember { mutableStateOf(true) }
    var cameraErrorText by remember { mutableStateOf<String?>(null) }
    var liveDetection by remember { mutableStateOf<FoodAnalysis?>(null) }

    DisposableEffect(lifecycleOwner) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        onPermissionResult(granted)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraManager.unbind()
        }
    }

    LaunchedEffect(isCapturing, capturedBitmap, isLifecycleResumed) {
        cameraManager.setAnalysisPaused(isCapturing || capturedBitmap != null || !isLifecycleResumed)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(granted)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    imageProcessor.decodeSampledBitmapFromUri(context, uri)
                }
                if (decoded != null) {
                    onPhotoReady(decoded)
                } else {
                    snackbarHostState.showSnackbar("No se pudo abrir la imagen seleccionada.")
                }
            }
        }
    }

    LaunchedEffect(previewViewRef, isCameraPermissionGranted, lensFacingBack, flashMode, capturedBitmap) {
        val previewView = previewViewRef
        if (isCameraPermissionGranted && previewView != null && capturedBitmap == null) {
            cameraErrorText = null
            cameraManager.setAnalysisPaused(isCapturing || !isLifecycleResumed)
            val lensFacing = if (lensFacingBack) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            val bindResult = cameraManager.bindCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                lensFacing = lensFacing,
                flashMode = flashMode,
                onLiveFrameAvailable = { frameBitmap ->
                    try {
                        val detected = liveYoloDetector.detectInstant(
                            bitmap = frameBitmap,
                            isPreliminaryForHybrid = true,
                            isLivePreview = true
                        )
                        liveDetection = if (detected.boundingBox != null && detected.boundingBox.isValid) {
                            detected
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                    } finally {
                        if (!frameBitmap.isRecycled) {
                            frameBitmap.recycle()
                        }
                    }
                }
            )
            bindResult.onSuccess { hasFlash ->
                onHasFlashUnitChanged(hasFlash)
            }.onFailure { err ->
                cameraErrorText = err.localizedMessage
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (capturedBitmap != null) "Confirmar Fotografía" else "FreshScan AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenConservationGuide,
                        modifier = Modifier.testTag("camera_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.action_conservation_guide)
                        )
                    }
                },
                actions = {
                    if (capturedBitmap == null && isCameraPermissionGranted && hasFlashUnit) {
                        IconButton(
                            onClick = onCycleFlash,
                            modifier = Modifier.testTag("flash_toggle_button")
                        ) {
                            val flashIcon = when (flashMode) {
                                FlashModeOption.OFF -> Icons.Default.FlashOff
                                FlashModeOption.ON -> Icons.Default.FlashOn
                                FlashModeOption.AUTO -> Icons.Default.FlashAuto
                            }
                            Icon(
                                imageVector = flashIcon,
                                contentDescription = flashMode.label
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.testTag("camera_about_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_about)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (capturedBitmap != null) {
                MinimalPhotoPreviewSection(
                    bitmap = capturedBitmap,
                    onRetake = onRetakePhoto,
                    onConfirmAnalyze = onConfirmAndAnalyze
                )
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.camera_framing_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isCameraPermissionGranted) {
                            Column(
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.camera_permission_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.camera_permission_rationale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    modifier = Modifier.testTag("grant_camera_permission_button")
                                ) {
                                    Text(stringResource(R.string.camera_grant_permission))
                                }
                            }
                        } else if (cameraErrorText != null) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = cameraErrorText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Text(stringResource(R.string.camera_pick_gallery))
                                }
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        previewViewRef = this
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("camera_preview_view")
                            )

                            LiveDetectionOverlay(
                                liveDetection = liveDetection,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("pick_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.camera_pick_gallery)
                            )
                        }

                        Button(
                            onClick = {
                                if (isCameraPermissionGranted && !isCapturing) {
                                    isCapturing = true
                                    coroutineScope.launch {
                                        val result = cameraManager.takePhotoInMemory()
                                        isCapturing = false
                                        result.onSuccess { bmp ->
                                            onPhotoReady(bmp)
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(
                                                err.localizedMessage ?: "Error al capturar la foto"
                                            )
                                        }
                                    }
                                } else if (!isCameraPermissionGranted) {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !isCapturing,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("capture_photo_button")
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = stringResource(R.string.camera_take_photo),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onToggleLens,
                            enabled = isCameraPermissionGranted,
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("switch_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cambiar cámara"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDetectionOverlay(
    liveDetection: FoodAnalysis?,
    modifier: Modifier = Modifier
) {
    val box = liveDetection?.boundingBox
    val hasValidBox = box != null && box.isValid

    val targetXmin = if (hasValidBox) box!!.xmin else 0.18f
    val targetYmin = if (hasValidBox) box!!.ymin else 0.22f
    val targetXmax = if (hasValidBox) box!!.xmax else 0.82f
    val targetYmax = if (hasValidBox) box!!.ymax else 0.78f

    val animSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val xmin by animateFloatAsState(targetValue = targetXmin, animationSpec = animSpec, label = "xmin")
    val ymin by animateFloatAsState(targetValue = targetYmin, animationSpec = animSpec, label = "ymin")
    val xmax by animateFloatAsState(targetValue = targetXmax, animationSpec = animSpec, label = "xmax")
    val ymax by animateFloatAsState(targetValue = targetYmax, animationSpec = animSpec, label = "ymax")

    val boxColor = when {
        !hasValidBox -> Color.White.copy(alpha = 0.45f)
        liveDetection?.hasVisibleDeterioration == true -> Color(0xFFE53935)
        liveDetection?.ripenessState == RipenessState.RIPE -> Color(0xFF43A047)
        liveDetection?.ripenessState == RipenessState.RIPENING -> Color(0xFFFB8C00)
        liveDetection?.ripenessState == RipenessState.UNRIPE -> Color(0xFF7CB342)
        liveDetection?.ripenessState == RipenessState.OVERRIPE -> Color(0xFFF4511E)
        else -> Color(0xFF26A69A)
    }

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = xmin * size.width
            val top = ymin * size.height
            val rectW = (xmax - xmin) * size.width
            val rectH = (ymax - ymin) * size.height

            drawRoundRect(
                color = boxColor.copy(alpha = 0.14f),
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
            )
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        if (hasValidBox && liveDetection != null) {
            val pillX = (xmin * widthPx).roundToInt()
            val pillY = ((ymin * heightPx) - with(density) { 36.dp.toPx() })
                .coerceAtLeast(with(density) { 12.dp.toPx() })
                .roundToInt()

            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .offset { IntOffset(pillX, pillY) }
                    .testTag("live_detection_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(boxColor)
                    )
                    Text(
                        text = "${liveDetection.displayProductName} · ${liveDetection.ripenessState.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalPhotoPreviewSection(
    bitmap: Bitmap,
    onRetake: () -> Unit,
    onConfirmAnalyze: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vista previa de la foto capturada",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("captured_photo_preview")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("retake_photo_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.camera_retake_photo))
            }

            Button(
                onClick = onConfirmAnalyze,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("confirm_analyze_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.camera_analyze_food),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
