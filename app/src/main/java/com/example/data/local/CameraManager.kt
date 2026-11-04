package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "CameraManager"

enum class FlashModeOption(val imageCaptureMode: Int, val label: String) {
    OFF(ImageCapture.FLASH_MODE_OFF, "Flash apagado"),
    ON(ImageCapture.FLASH_MODE_ON, "Flash encendido"),
    AUTO(ImageCapture.FLASH_MODE_AUTO, "Flash automático");

    fun next(): FlashModeOption = when (this) {
        OFF -> AUTO
        AUTO -> ON
        ON -> OFF
    }
}

class CameraManager(
    private val context: Context,
    private val imageProcessor: ImageProcessor = ImageProcessor()
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private var boundPreviewView: PreviewView? = null

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        flashMode: FlashModeOption
    ): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val captureResolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1024, 1024),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(captureResolutionSelector)
                    .setFlashMode(flashMode.imageCaptureMode)
                    .build()
                imageCapture = capture

                val requestedSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val effectiveSelector = if (provider.hasCamera(requestedSelector)) {
                    requestedSelector
                } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(IllegalStateException("No se detectó ninguna cámara disponible en este dispositivo."))
                        )
                    }
                    return@addListener
                }

                provider.unbindAll()
                boundPreviewView = previewView
                activeCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    effectiveSelector,
                    preview,
                    capture
                )

                val hasFlashUnit = activeCamera?.cameraInfo?.hasFlashUnit() == true
                if (continuation.isActive) {
                    continuation.resume(Result.success(hasFlashUnit))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular CameraX", e)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateFlashMode(flashMode: FlashModeOption) {
        imageCapture?.flashMode = flashMode.imageCaptureMode
    }

    fun canSwitchCamera(): Boolean {
        val provider = cameraProvider ?: return true
        return try {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun takePhotoInMemory(): Result<Bitmap> = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            if (continuation.isActive) {
                continuation.resume(
                    Result.failure(IllegalStateException("La cámara aún no está lista para capturar."))
                )
            }
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val rawBitmap = image.toBitmap()
                        val transformed = imageProcessor.scaleAndRotate(
                            bitmap = rawBitmap,
                            maxDimension = ImageProcessor.CAPTURE_DIMENSION_PX,
                            rotationDegrees = rotation,
                            recycleSourceIfChanged = true
                        )
                        if (continuation.isActive) {
                            continuation.resume(Result.success(transformed))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar la imagen capturada", e)
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(e))
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error en la captura fotográfica CameraX", exception)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            }
        )
    }

    @Synchronized
    fun unbind(ownerPreviewView: PreviewView? = null) {
        try {
            if (ownerPreviewView == null || boundPreviewView === ownerPreviewView) {
                cameraProvider?.unbindAll()
                boundPreviewView = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al desvincular CameraX", e)
        }
    }
}
