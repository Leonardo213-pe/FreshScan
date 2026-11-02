package com.example.domain.model

data class ShelfLifeEstimate(
    val remainingShelfLifeDaysMin: Int? = null,
    val remainingShelfLifeDaysMax: Int? = null,
    val daysToRipenMin: Int? = null,
    val daysToRipenMax: Int? = null,
    val roomTempDaysMin: Int? = null,
    val roomTempDaysMax: Int? = null,
    val refrigeratedDaysMin: Int? = null,
    val refrigeratedDaysMax: Int? = null,
    val isValidatedEstimate: Boolean = false,
    val consideredConditions: List<String> = emptyList(),
    val predictionLimitations: List<String> = emptyList(),
    val generalStorageReference: String? = null,
    val referenceSourceCitation: String? = null
) {
    companion object {
        const val UNRELIABLE_SHELF_LIFE_MESSAGE =
            "No es posible estimar con suficiente confiabilidad la vida útil restante ni los días exactos de maduración únicamente con la información actual."
    }
}
