package com.example.domain.usecase

import android.graphics.Bitmap
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.repository.FoodAnalysisRepository

class AnalyzeFoodUseCase(
    private val repository: FoodAnalysisRepository
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        manualProductOverride: String? = null,
        regionBox: NormalizedBoundingBox? = null
    ): Result<FoodAnalysis> {
        return repository.analyzeFood(
            bitmap = bitmap,
            storageConditions = null,
            manualProductOverride = manualProductOverride,
            regionBox = regionBox
        )
    }
}
