package com.example.domain.model

import com.google.firebase.Timestamp
import kotlin.math.max
import kotlin.math.min

data class VisualForecastRule(
    val id: String = "",
    val catalogScope: String = CATALOG_SCOPE,
    val userId: String = "",
    val productKey: String = "",
    val productName: String = "",
    val ripenessState: String = RipenessState.UNDETERMINED.name,
    val hasVisibleDeterioration: Boolean = false,
    val remainingShelfLifeDaysMin: Long = 0L,
    val remainingShelfLifeDaysMax: Long = 0L,
    val daysToRipenMin: Long = 0L,
    val daysToRipenMax: Long = 0L,
    val visualObservations: List<String> = emptyList(),
    val deteriorationSigns: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val redMinPermille: Long = 0L,
    val redMaxPermille: Long = 1000L,
    val yellowMinPermille: Long = 0L,
    val yellowMaxPermille: Long = 1000L,
    val greenMinPermille: Long = 0L,
    val greenMaxPermille: Long = 1000L,
    val orangeMinPermille: Long = 0L,
    val orangeMaxPermille: Long = 1000L,
    val purpleMinPermille: Long = 0L,
    val purpleMaxPermille: Long = 1000L,
    val spotMinPermille: Long = 0L,
    val spotMaxPermille: Long = 1000L,
    val aspectRatioMinPermille: Long = 200L,
    val aspectRatioMaxPermille: Long = 3500L,
    val confirmations: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun parsedRipenessState(): RipenessState = RipenessState.fromRawString(ripenessState)

    companion object {
        const val CATALOG_SCOPE = "global_v1"
        const val COLOR_TOLERANCE_PERMILLE = 110
        const val SPOT_TOLERANCE_PERMILLE = 75
        const val ASPECT_TOLERANCE_PERMILLE = 260

        fun fromValidatedAnalysis(
            userId: String,
            featureVector: VisualFeatureVector,
            analysis: FoodAnalysis
        ): VisualForecastRule {
            val productKey = analysis.matchedCatalogProduct?.id
                ?: analysis.displayProductName.lowercase().trim().replace(Regex("[^a-z0-9]+"), "_").take(40)
            val ripeness = analysis.ripenessState.name
            val redBucket = featureVector.redPermille / 80
            val yellowBucket = featureVector.yellowPermille / 80
            val greenBucket = featureVector.greenPermille / 80
            val spotBucket = featureVector.spotPermille / 50
            val ruleId = "${productKey}_${ripeness}_r${redBucket}_y${yellowBucket}_g${greenBucket}_s${spotBucket}"
                .take(110)

            val shelfMin = (analysis.shelfLifeEstimate.remainingShelfLifeDaysMin ?: 1).coerceIn(0, 90).toLong()
            val shelfMax = (analysis.shelfLifeEstimate.remainingShelfLifeDaysMax ?: shelfMin.toInt()).coerceIn(shelfMin.toInt(), 90).toLong()
            val ripenMin = (analysis.shelfLifeEstimate.daysToRipenMin ?: 0).coerceIn(0, 30).toLong()
            val ripenMax = (analysis.shelfLifeEstimate.daysToRipenMax ?: ripenMin.toInt()).coerceIn(ripenMin.toInt(), 30).toLong()

            return VisualForecastRule(
                id = ruleId,
                catalogScope = CATALOG_SCOPE,
                userId = userId,
                productKey = productKey,
                productName = analysis.displayProductName.take(80),
                ripenessState = ripeness,
                hasVisibleDeterioration = analysis.hasVisibleDeterioration,
                remainingShelfLifeDaysMin = shelfMin,
                remainingShelfLifeDaysMax = shelfMax,
                daysToRipenMin = ripenMin,
                daysToRipenMax = ripenMax,
                visualObservations = analysis.visualObservations.take(6).map { it.take(180) },
                deteriorationSigns = analysis.deteriorationSigns.take(5).map { it.take(180) },
                recommendations = analysis.recommendations.take(6).map { it.take(180) },
                redMinPermille = max(0, featureVector.redPermille - COLOR_TOLERANCE_PERMILLE).toLong(),
                redMaxPermille = min(1000, featureVector.redPermille + COLOR_TOLERANCE_PERMILLE).toLong(),
                yellowMinPermille = max(0, featureVector.yellowPermille - COLOR_TOLERANCE_PERMILLE).toLong(),
                yellowMaxPermille = min(1000, featureVector.yellowPermille + COLOR_TOLERANCE_PERMILLE).toLong(),
                greenMinPermille = max(0, featureVector.greenPermille - COLOR_TOLERANCE_PERMILLE).toLong(),
                greenMaxPermille = min(1000, featureVector.greenPermille + COLOR_TOLERANCE_PERMILLE).toLong(),
                orangeMinPermille = max(0, featureVector.orangePermille - COLOR_TOLERANCE_PERMILLE).toLong(),
                orangeMaxPermille = min(1000, featureVector.orangePermille + COLOR_TOLERANCE_PERMILLE).toLong(),
                purpleMinPermille = max(0, featureVector.purplePermille - COLOR_TOLERANCE_PERMILLE).toLong(),
                purpleMaxPermille = min(1000, featureVector.purplePermille + COLOR_TOLERANCE_PERMILLE).toLong(),
                spotMinPermille = max(0, featureVector.spotPermille - SPOT_TOLERANCE_PERMILLE).toLong(),
                spotMaxPermille = min(1000, featureVector.spotPermille + SPOT_TOLERANCE_PERMILLE).toLong(),
                aspectRatioMinPermille = max(200, featureVector.aspectRatioPermille - ASPECT_TOLERANCE_PERMILLE).toLong(),
                aspectRatioMaxPermille = min(3500, featureVector.aspectRatioPermille + ASPECT_TOLERANCE_PERMILLE).toLong(),
                confirmations = 1L,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        }
    }
}
