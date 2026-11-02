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
        yoloHintBox: NormalizedBoundingBox? = null
    ): Result<FoodAnalysis>

    suspend fun analyzeFastRecognition(
        bitmap: Bitmap,
        yoloHintBox: NormalizedBoundingBox? = null,
        yoloHintLabel: String? = null
    ): Result<FoodAnalysis> {
        return analyzeFood(
            bitmap = bitmap,
            storageConditions = null,
            manualProductOverride = yoloHintLabel,
            yoloHintBox = yoloHintBox
        )
    }

    fun warmUp() {}
}
