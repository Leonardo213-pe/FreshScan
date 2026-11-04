package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.data.estimator.ShelfLifeEstimator
import com.example.data.local.AdaptiveVisualLearner
import com.example.data.local.ImageProcessor
import com.example.data.local.NetworkMonitor
import com.example.data.local.VisualHashCache
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.StorageConditions
import com.example.domain.repository.FoodAnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

private const val TAG = "FirebaseGeminiRepo"

class FirebaseGeminiRepository(
    private val dataSource: GeminiAnalysisDataSource = FirebaseGeminiDataSource(),
    private val imageProcessor: ImageProcessor = ImageProcessor(),
    private val shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator(),
    private val networkMonitor: NetworkMonitor? = null,
    private val visualHashCache: VisualHashCache = VisualHashCache(),
    private val adaptiveLearner: AdaptiveVisualLearner? = null
) : FoodAnalysisRepository {

    override fun warmUp() {
        dataSource.warmUp()
    }

    override suspend fun analyzeFood(
        bitmap: Bitmap,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        regionBox: NormalizedBoundingBox?
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
            if (manualProductOverride.isNullOrBlank()) {
                val cached = visualHashCache.findMatch(
                    perceptualHash = perceptualHash,
                    storageConditions = storageConditions,
                    manualProductOverride = manualProductOverride
                )
                if (cached != null && cached.isReliableIdentification) {
                    return@withContext Result.success(cached)
                }
            }

            // Optimización de segmentación: recorta automáticamente la región donde se ubican
            // las frutas/verduras para no enviar fondo innecesario a Gemini Vision.
            val effectiveCropBounds = if (regionBox != null) {
                imageProcessor.computeEffectiveCropBounds(bitmap, regionBox)
            } else {
                imageProcessor.segmentProduceRegionBounds(bitmap)
            }

            val optimizedBitmap = imageProcessor.optimizeForGemini(bitmap, effectiveCropBounds)
            val qualityReport = imageProcessor.assessImageQuality(optimizedBitmap)
            val learnedContext = adaptiveLearner?.buildLearnedPromptHintForGemini(optimizedBitmap)

            val rawJson = dataSource.analyzeImageJson(
                bitmap = optimizedBitmap,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride,
                learnedMemoryContext = learnedContext
            )

            val dto = GeminiJsonValidator.parseJson(rawJson)
            var analysis = GeminiJsonValidator.validateAndMapToDomain(
                dto = dto,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride,
                shelfLifeEstimator = shelfLifeEstimator
            )

            if (!analysis.isReliableIdentification && manualProductOverride.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "No se detectó ninguna fruta o verdura en la fotografía. Enfoca directamente el alimento con buena iluminación e intenta de nuevo."
                    )
                )
            }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Error analyzing food image", e)
            Result.failure(mapExceptionToUserFriendlyError(e))
        }
    }

    private fun mapExceptionToUserFriendlyError(error: Throwable): Exception {
        if (error is IllegalArgumentException) return error
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
