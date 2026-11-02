package com.example.domain.model

data class NormalizedBoundingBox(
    val ymin: Float,
    val xmin: Float,
    val ymax: Float,
    val xmax: Float
) {
    val isValid: Boolean
        get() = xmin in 0f..1f &&
            ymin in 0f..1f &&
            xmax in 0f..1f &&
            ymax in 0f..1f &&
            xmax > xmin + 0.02f &&
            ymax > ymin + 0.02f
}
