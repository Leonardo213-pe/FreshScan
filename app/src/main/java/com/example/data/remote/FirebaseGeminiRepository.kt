package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.data.estimator.ShelfLifeEstimator
import com.example.data.local.ImageProcessor
import com.example.data.local.NetworkMonitor
import com.example.data.local.VisualHashCache
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.StorageConditions
import com.example.domain.repository.FoodAnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

private const val TAG = "FirebaseGeminiRepo"

class FirebaseGeminiRepository(
    private val dataSource: GeminiAnalysisDataSource = FirebaseGeminiDataSource(),
    private val imageProcessor: ImageProcessor = ImageProcessor(),
    private val shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator(),
    private val networkMonitor: NetworkMonitor? = null,
    private val visualHashCache: VisualHashCache = VisualHashCache()
) : FoodAnalysisRepository {

    override fun warmUp() {
        dataSource.warmUp()
    }

    override suspend fun analyzeFastRecognition(
        bitmap: Bitmap,
        yoloHintBox: NormalizedBoundingBox?,
        yoloHintLabel: String?
    ): Result<FoodAnalysis> = withContext(Dispatchers.IO) {
        try {
            if (networkMonitor != null && !networkMonitor.isCurrentlyConnected()) {
                return@withContext Result.failure(
                    IllegalStateException("Sin conexión a internet para el reconocimiento en la nube.")
                )
            }
            val fastBitmap = imageProcessor.scaleDownIfNeeded(bitmap, 384)
            val rawJson = dataSource.analyzeFastRecognitionJson(fastBitmap, yoloHintLabel)
            val dto = GeminiJsonValidator.parseJson(rawJson)
            val domain = GeminiJsonValidator.validateAndMapToDomain(
                dto = dto,
                storageConditions = null,
                manualProductOverride = yoloHintLabel,
                shelfLifeEstimator = shelfLifeEstimator
            )
            Result.success(domain)
        } catch (e: Throwable) {
            Log.w(TAG, "Fast recognition fallback: ${e.message}")
            Result.failure(mapExceptionToUserFriendlyError(e))
        }
    }

    override suspend fun analyzeFood(
        bitmap: Bitmap,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        yoloHintBox: NormalizedBoundingBox?
    ): Result<FoodAnalysis> = withContext(Dispatchers.IO) {
        try {
            if (!imageProcessor.isValidBitmap(bitmap)) {
                return@withContext Result.failure(
                    IllegalArgumentException("La imagen seleccionada es demasiado pequeña o inválida.")
                )
            }

            if (networkMonitor != null && !networkMonitor.isCurrentlyConnected()) {
                return@withContext Result.failure(
                    IllegalStateException("No hay conexión a internet disponible. Verifique su red e intente nuevamente.")
                )
            }

            val perceptualHash = imageProcessor.computePerceptualHash(bitmap)
            val cached = visualHashCache.findMatch(
                perceptualHash = perceptualHash,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride
            )
            if (cached != null) {
                return@withContext Result.success(cached)
            }

            val effectiveCropBounds = if (yoloHintBox != null) {
                imageProcessor.computeEffectiveCropBounds(bitmap, yoloHintBox)
            } else {
                null
            }

            val optimizedBitmap = imageProcessor.optimizeForGemini(bitmap, yoloHintBox)
            val qualityReport = imageProcessor.assessImageQuality(optimizedBitmap)

            val rawJson = dataSource.analyzeImageJson(
                bitmap = optimizedBitmap,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride
            )

            val dto = GeminiJsonValidator.parseJson(rawJson)
            var analysis = GeminiJsonValidator.validateAndMapToDomain(
                dto = dto,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride,
                shelfLifeEstimator = shelfLifeEstimator
            )

            if (effectiveCropBounds != null) {
                val remappedMainBox = imageProcessor.remapBoxFromCropToFullImage(
                    innerBox = analysis.boundingBox,
                    cropBoundsOnFullImage = effectiveCropBounds
                )
                val remappedItems = analysis.detectedItems.map { item ->
                    item.copy(
                        boundingBox = imageProcessor.remapBoxFromCropToFullImage(
                            innerBox = item.boundingBox,
                            cropBoundsOnFullImage = effectiveCropBounds
                        )
                    )
                }
                analysis = analysis.copy(
                    boundingBox = remappedMainBox,
                    detectedItems = remappedItems
                )
            }

            if (qualityReport.warningMessage != null) {
                analysis = analysis.copy(
                    limitations = (listOf(qualityReport.warningMessage) + analysis.limitations).distinct()
                )
            }

            visualHashCache.put(
                perceptualHash = perceptualHash,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride,
                analysis = analysis
            )

            Result.success(analysis)
        } catch (e: Throwable) {
            Log.e(TAG, "Error analyzing food image", e)
            Result.failure(mapExceptionToUserFriendlyError(e))
        }
    }

    private fun mapExceptionToUserFriendlyError(error: Throwable): Exception {
        val message = error.message.orEmpty()
        return when {
            error is TimeoutCancellationException || message.contains("timeout", ignoreCase = true) ->
                IllegalStateException("El servicio de análisis tardó demasiado en responder. Intente con mejor conexión.")
            message.contains("quota", ignoreCase = true) || message.contains("429") ->
                IllegalStateException("Se alcanzó el límite temporal de consultas. Espere unos segundos e intente de nuevo.")
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ->
                IllegalStateException("Error de conexión de red al comunicarse con el servidor de análisis.")
            else ->
                IllegalStateException(
                    message.ifBlank { "No se pudo completar el análisis visual de la imagen." },
                    error
                )
        }
    }
}
