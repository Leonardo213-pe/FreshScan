package com.example.domain.repository

import android.graphics.Bitmap
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.StorageConditions

interface FoodAnalysisRepository {
    suspend fun analyzeFood(
        bitmap: Bitmap,
        storageConditions: StorageConditions? = null,
        manualProductOverride: String? = null,
        regionBox: NormalizedBoundingBox? = null
    ): Result<FoodAnalysis>

    fun warmUp() {}
}
