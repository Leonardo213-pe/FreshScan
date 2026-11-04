package com.example.domain.usecase

import android.graphics.Bitmap
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.StorageConditions
import com.example.domain.repository.FoodAnalysisRepository

class ImproveAnalysisUseCase(
    private val repository: FoodAnalysisRepository
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        previousAnalysis: FoodAnalysis,
        storageConditions: StorageConditions,
        manualProductOverride: String? = null
    ): Result<FoodAnalysis> {
        val productOverride = manualProductOverride
            ?: previousAnalysis.manuallyCorrectedName
            ?: previousAnalysis.displayProductName

        return repository.analyzeFood(
            bitmap = bitmap,
            storageConditions = storageConditions,
            manualProductOverride = productOverride,
            regionBox = previousAnalysis.boundingBox
        )
    }
}
