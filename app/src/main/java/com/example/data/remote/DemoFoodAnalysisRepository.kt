package com.example.data.remote

import android.graphics.Bitmap
import com.example.data.estimator.ShelfLifeEstimator
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.StorageConditions

class DemoFoodAnalysisRepository(
    private val simulatedJsonResponse: String = DEFAULT_PREVIEW_JSON,
    private val shouldFail: Boolean = false,
    private val failureMessage: String = "Simulated network error"
) : com.example.domain.repository.FoodAnalysisRepository {

    override suspend fun analyzeFood(
        bitmap: Bitmap,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        regionBox: NormalizedBoundingBox?
    ): Result<FoodAnalysis> {
        if (shouldFail) {
            return Result.failure(IllegalStateException(failureMessage))
        }
        return try {
            val dto = GeminiJsonValidator.parseJson(simulatedJsonResponse)
            val analysis = GeminiJsonValidator.validateAndMapToDomain(
                dto = dto,
                storageConditions = storageConditions,
                manualProductOverride = manualProductOverride,
                shelfLifeEstimator = ShelfLifeEstimator()
            )
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun warmUp() {}

    companion object {
        const val DEFAULT_PREVIEW_JSON = """
            {
              "producto": "Plátano / Banano",
              "identificacion_confiable": true,
              "multiples_productos_o_ambiguo": false,
              "madurez": "maduro",
              "observaciones_visuales": [
                "Color amarillo uniforme en el 90% de la superficie",
                "Pequeñas pecas marrones de azúcar incipientes",
                "Pedúnculo intacto sin signos de moho"
              ],
              "posible_deterioro": false,
              "senales_deterioro": [],
              "vida_util_min": 2,
              "vida_util_max": 4,
              "dias_hasta_madurez_min": 0,
              "dias_hasta_madurez_max": 0,
              "estimacion_validada": true,
              "recomendaciones": [
                "Ideal para consumo inmediato en fresco",
                "Si desea conservarlo 3 a 5 días adicionales, guárdelo en refrigeración"
              ],
              "limitaciones": [
                "Evaluación basada únicamente en la superficie visible"
              ],
              "box_ymin": 180,
              "box_xmin": 140,
              "box_ymax": 820,
              "box_xmax": 860
            }
        """
    }
}
