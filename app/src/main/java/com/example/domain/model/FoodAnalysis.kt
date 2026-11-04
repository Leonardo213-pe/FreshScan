package com.example.domain.model

import java.util.UUID

data class FoodAnalysis(
    val sessionId: String = UUID.randomUUID().toString(),
    val rawProductName: String,
    val manuallyCorrectedName: String? = null,
    val matchedCatalogProduct: FoodProduct? = null,
    val isReliableIdentification: Boolean = false,
    val multipleOrAmbiguousItems: Boolean = false,
    val ripenessState: RipenessState = RipenessState.UNDETERMINED,
    val visualObservations: List<String> = emptyList(),
    val hasVisibleDeterioration: Boolean = false,
    val deteriorationSigns: List<String> = emptyList(),
    val shelfLifeEstimate: ShelfLifeEstimate = ShelfLifeEstimate(),
    val recommendations: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val appliedStorageConditions: StorageConditions? = null,
    val isAdvancedAnalysis: Boolean = false,
    val safetyWarning: String = DEFAULT_SAFETY_WARNING,
    val boundingBox: NormalizedBoundingBox? = null,
    val detectedItems: List<FoodAnalysis> = emptyList(),
    val isPreliminaryResult: Boolean = false,
    val localConfidence: Float? = null,
    val inferenceTimeMs: Long? = null,
    val analysisEngineSource: String = "Gemini Vision + Aprendizaje Evolutivo"
) {
    val displayProductName: String
        get() = manuallyCorrectedName?.takeIf { it.isNotBlank() }
            ?: rawProductName.takeIf {
                it.isNotBlank() &&
                    !it.equals("Producto no identificado", ignoreCase = true) &&
                    matchedCatalogProduct == null
            }
            ?: matchedCatalogProduct?.canonicalName
            ?: rawProductName.ifBlank { "Producto no identificado" }

    val isSupportedForAdvancedFeatures: Boolean
        get() = isReliableIdentification

    val hasMultipleDetectedProducts: Boolean
        get() = detectedItems.size > 1

    val allProducts: List<FoodAnalysis>
        get() = if (detectedItems.isNotEmpty()) detectedItems else listOf(this)

    companion object {
        const val DEFAULT_SAFETY_WARNING =
            "Nota de seguridad alimentaria: Esta evaluación es orientativa y se basa únicamente en la apariencia visual externa. Si percibe mal olor, textura viscosa, moho o dudas sobre su inocuidad, deseche el alimento."
    }
}
