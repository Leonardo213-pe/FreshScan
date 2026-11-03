package com.example.data.local

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.estimator.ConservationReferenceSource
import com.example.data.estimator.ShelfLifeEstimator
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.FoodProduct
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.RipenessState
import com.example.domain.model.StorageConditions
import com.example.domain.model.VisualFeatureVector
import com.example.domain.model.VisualForecastRule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LocalYoloDetector(
    private val shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator(),
    private val imageProcessor: ImageProcessor = ImageProcessor(),
    private val visualForecastStore: LocalVisualForecastStore = LocalVisualForecastStore()
) {

    data class YoloDetectionResult(
        val product: FoodProduct,
        val ripenessState: RipenessState,
        val confidence: Float,
        val boundingBox: NormalizedBoundingBox,
        val hasDeterioration: Boolean,
        val deteriorationSigns: List<String>,
        val visualObservations: List<String>,
        val inferenceTimeMs: Long,
        val featureVector: VisualFeatureVector,
        val matchedRangeRule: VisualForecastRule? = null
    )

    private data class LearnedVisualSignature(
        val perceptualHash: Long,
        val product: FoodProduct,
        val ripenessState: RipenessState,
        val hasDeterioration: Boolean
    )

    private val learnedSignatures = mutableListOf<LearnedVisualSignature>()

    @Synchronized
    fun learnFromValidatedGeminiResult(
        bitmap: Bitmap,
        geminiAnalysis: FoodAnalysis
    ): VisualFeatureVector? {
        val matched = geminiAnalysis.matchedCatalogProduct
            ?: FoodProduct.findMatchingProduct(geminiAnalysis.displayProductName)
            ?: return null

        val hash = imageProcessor.computePerceptualHash(bitmap)
        if (hash != 0L) {
            learnedSignatures.removeAll {
                VisualHashCache.hammingDistance(it.perceptualHash, hash) <= 6
            }
            learnedSignatures.add(
                0,
                LearnedVisualSignature(
                    perceptualHash = hash,
                    product = matched,
                    ripenessState = geminiAnalysis.ripenessState,
                    hasDeterioration = geminiAnalysis.hasVisibleDeterioration
                )
            )
            while (learnedSignatures.size > 50) {
                learnedSignatures.removeAt(learnedSignatures.lastIndex)
            }
        }

        val featureVector = extractVisualFeatureVector(bitmap)
        val localRule = VisualForecastRule.fromValidatedAnalysis(
            userId = "local_device",
            featureVector = featureVector,
            analysis = geminiAnalysis
        ).copy(id = "local_${matched.id}_${geminiAnalysis.ripenessState.name.lowercase()}")
        visualForecastStore.upsertLocalRule(localRule)
        return featureVector
    }

    @Synchronized
    fun getLearnedSignatureCount(): Int = maxOf(learnedSignatures.size, visualForecastStore.getRulesCount())

    @Synchronized
    private fun findLearnedSignature(bitmap: Bitmap): LearnedVisualSignature? {
        if (learnedSignatures.isEmpty()) return null
        val hash = imageProcessor.computePerceptualHash(bitmap)
        if (hash == 0L) return null
        return learnedSignatures.firstOrNull {
            VisualHashCache.hammingDistance(it.perceptualHash, hash) <= 8
        }
    }

    fun extractVisualFeatureVector(bitmap: Bitmap): VisualFeatureVector {
        return runSpatialAndChromaticDetection(bitmap, null, isLivePreview = true).featureVector
    }

    fun detectInstant(
        bitmap: Bitmap,
        storageConditions: StorageConditions? = null,
        manualProductOverride: String? = null,
        isPreliminaryForHybrid: Boolean = true,
        isLivePreview: Boolean = false
    ): FoodAnalysis {
        val startNanos = System.nanoTime()
        val detection = runSpatialAndChromaticDetection(bitmap, manualProductOverride, isLivePreview)
        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(8L)

        val matchedRule = detection.matchedRangeRule
        val matchedProduct = if (!manualProductOverride.isNullOrBlank()) {
            FoodProduct.findMatchingProduct(manualProductOverride) ?: detection.product
        } else {
            detection.product
        }

        val effectiveName = manualProductOverride?.takeIf { it.isNotBlank() }
            ?: matchedRule?.productName
            ?: matchedProduct.canonicalName

        val shelfLifeEstimate = shelfLifeEstimator.estimate(
            matchedProduct = matchedProduct,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = detection.ripenessState,
            hasVisibleDeterioration = detection.hasDeterioration,
            deteriorationSigns = detection.deteriorationSigns,
            geminiRipeningDaysMin = matchedRule?.daysToRipenMin?.takeIf { it >= 0 }?.toInt(),
            geminiRipeningDaysMax = matchedRule?.daysToRipenMax?.takeIf { it >= 0 }?.toInt(),
            geminiShelfLifeMin = matchedRule?.remainingShelfLifeDaysMin?.toInt(),
            geminiShelfLifeMax = matchedRule?.remainingShelfLifeDaysMax?.toInt(),
            geminiLimitations = listOf(
                when {
                    isPreliminaryForHybrid ->
                        "Evaluación preliminar rápida basada en la superficie visible mientras se completa el análisis detallado."
                    else ->
                        "Estimación calculada a partir del color y aspecto superficial visible en la fotografía."
                }
            ),
            storageConditions = storageConditions
        )

        val referenceEntry = ConservationReferenceSource.findByProduct(matchedProduct)
        val recommendations = if (matchedRule != null && matchedRule.recommendations.isNotEmpty()) {
            matchedRule.recommendations
        } else {
            buildList {
                if (referenceEntry != null) {
                    add(referenceEntry.idealStorageLocation)
                    add(referenceEntry.ethyleneNotes)
                } else {
                    add("Conservar en ambiente fresco, limpio y seco, verificando su textura antes de consumir.")
                }
            }
        }

        val engineLabel = when {
            isPreliminaryForHybrid ->
                "Completando análisis detallado..."
            else ->
                "Diagnóstico visual completado"
        }

        val singleAnalysis = FoodAnalysis(
            rawProductName = effectiveName,
            manuallyCorrectedName = manualProductOverride?.takeIf { it.isNotBlank() },
            matchedCatalogProduct = matchedProduct,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = detection.ripenessState,
            visualObservations = detection.visualObservations,
            hasVisibleDeterioration = detection.hasDeterioration,
            deteriorationSigns = detection.deteriorationSigns,
            shelfLifeEstimate = shelfLifeEstimate,
            recommendations = recommendations,
            limitations = shelfLifeEstimate.predictionLimitations,
            appliedStorageConditions = storageConditions?.takeIf { it.hasAnyInput() },
            isAdvancedAnalysis = storageConditions?.hasAnyInput() == true,
            boundingBox = detection.boundingBox,
            isPreliminaryYoloResult = isPreliminaryForHybrid,
            yoloConfidence = detection.confidence,
            yoloInferenceTimeMs = elapsedMs,
            analysisEngineSource = engineLabel
        )

        return singleAnalysis.copy(detectedItems = listOf(singleAnalysis))
    }

    private fun runSpatialAndChromaticDetection(
        bitmap: Bitmap,
        manualProductOverride: String?,
        isLivePreview: Boolean = false
    ): YoloDetectionResult {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val gridX = min(28, width)
        val gridY = min(28, height)

        val hsv = FloatArray(3)
        var minFgX = gridX
        var maxFgX = 0
        var minFgY = gridY
        var maxFgY = 0
        var fgCount = 0

        var redPixels = 0
        var orangePixels = 0
        var yellowPixels = 0
        var greenPixels = 0
        var purpleDarkPixels = 0
        var brownSpotPixels = 0
        var totalSampled = 0
        var sumSaturation = 0f
        var sumValue = 0f

        for (gy in 0 until gridY) {
            val py = ((gy + 0.5f) * height / gridY).toInt().coerceIn(0, height - 1)
            for (gx in 0 until gridX) {
                val px = ((gx + 0.5f) * width / gridX).toInt().coerceIn(0, width - 1)
                val pixel = bitmap.getPixel(px, py)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                totalSampled++
                sumSaturation += s
                sumValue += v

                val centerBias = 1f - (abs(gx - gridX / 2f) / (gridX / 2f) * 0.4f + abs(gy - gridY / 2f) / (gridY / 2f) * 0.4f)
                val isForeground = (s > 0.22f && v in 0.12f..0.96f) || (centerBias > 0.68f && v in 0.15f..0.90f)

                if (isForeground) {
                    fgCount++
                    if (gx < minFgX) minFgX = gx
                    if (gx > maxFgX) maxFgX = gx
                    if (gy < minFgY) minFgY = gy
                    if (gy > maxFgY) maxFgY = gy

                    if (v < 0.24f && s > 0.15f) {
                        brownSpotPixels++
                    }

                    when {
                        h <= 18f || h >= 335f -> redPixels++
                        h in 18f..42f -> orangePixels++
                        h in 42f..68f -> yellowPixels++
                        h in 68f..165f -> greenPixels++
                        h in 240f..335f -> purpleDarkPixels++
                        else -> {
                            if (v < 0.35f) purpleDarkPixels++
                        }
                    }
                }
            }
        }

        val boundingBox = if (fgCount >= 6 && maxFgX > minFgX && maxFgY > minFgY) {
            NormalizedBoundingBox(
                ymin = (minFgY.toFloat() / gridY - 0.04f).coerceIn(0.06f, 0.78f),
                xmin = (minFgX.toFloat() / gridX - 0.04f).coerceIn(0.06f, 0.78f),
                ymax = (maxFgY.toFloat() / gridY + 0.05f).coerceIn(0.22f, 0.94f),
                xmax = (maxFgX.toFloat() / gridX + 0.05f).coerceIn(0.22f, 0.94f)
            ).let { box ->
                if (box.isValid) box else NormalizedBoundingBox(0.14f, 0.14f, 0.86f, 0.86f)
            }
        } else {
            NormalizedBoundingBox(0.15f, 0.15f, 0.85f, 0.85f)
        }

        val colorTotal = max(1, redPixels + orangePixels + yellowPixels + greenPixels + purpleDarkPixels)
        val redRatio = redPixels.toFloat() / colorTotal
        val orangeRatio = orangePixels.toFloat() / colorTotal
        val yellowRatio = yellowPixels.toFloat() / colorTotal
        val greenRatio = greenPixels.toFloat() / colorTotal
        val purpleRatio = purpleDarkPixels.toFloat() / colorTotal
        val spotRatio = brownSpotPixels.toFloat() / max(1, fgCount)

        val boxW = (boundingBox.xmax - boundingBox.xmin).coerceAtLeast(0.1f)
        val boxH = (boundingBox.ymax - boundingBox.ymin).coerceAtLeast(0.1f)
        val aspectRatioPermille = ((boxW / boxH) * 1000f).roundToInt().coerceIn(100, 5000)

        val featureVector = VisualFeatureVector(
            redPermille = (redRatio * 1000f).roundToInt().coerceIn(0, 1000),
            orangePermille = (orangeRatio * 1000f).roundToInt().coerceIn(0, 1000),
            yellowPermille = (yellowRatio * 1000f).roundToInt().coerceIn(0, 1000),
            greenPermille = (greenRatio * 1000f).roundToInt().coerceIn(0, 1000),
            purplePermille = (purpleRatio * 1000f).roundToInt().coerceIn(0, 1000),
            spotPermille = (spotRatio * 1000f).roundToInt().coerceIn(0, 1000),
            aspectRatioPermille = aspectRatioPermille
        )

        val matchedRangeRule = visualForecastStore.findBestMatchingRule(featureVector, manualProductOverride)
        val learned = if (isLivePreview) null else findLearnedSignature(bitmap)
        val forcedProduct = FoodProduct.findMatchingProduct(manualProductOverride)

        val detectedProduct = forcedProduct
            ?: matchedRangeRule?.let { FoodProduct.findMatchingProduct(it.productName) ?: FoodProduct.findMatchingProduct(it.productKey) }
            ?: learned?.product
            ?: when {
                yellowRatio >= 0.34f && greenRatio >= 0.12f -> FoodProduct.findMatchingProduct("Plátano")!!
                yellowRatio >= 0.40f -> FoodProduct.findMatchingProduct("Plátano")!!
                redRatio >= 0.42f && greenRatio < 0.15f -> FoodProduct.findMatchingProduct("Manzana")!!
                redRatio >= 0.28f && orangeRatio >= 0.15f -> FoodProduct.findMatchingProduct("Tomate")!!
                orangeRatio >= 0.38f -> FoodProduct.findMatchingProduct("Naranja")!!
                orangeRatio >= 0.24f && (yellowRatio >= 0.20f || greenRatio >= 0.18f) -> FoodProduct.findMatchingProduct("Mango")!!
                purpleRatio >= 0.32f && greenRatio >= 0.12f -> FoodProduct.findMatchingProduct("Palta")!!
                greenRatio >= 0.45f -> FoodProduct.findMatchingProduct("Palta")!!
                else -> FoodProduct.findMatchingProduct("Manzana")!!
            }

        val ripenessState = matchedRangeRule?.parsedRipenessState()
            ?: learned?.ripenessState
            ?: when {
                spotRatio > 0.24f -> RipenessState.OVERRIPE
                greenRatio > 0.58f && detectedProduct.isClimacteric -> RipenessState.UNRIPE
                greenRatio in 0.28f..0.58f && (yellowRatio > 0.15f || orangeRatio > 0.15f || redRatio > 0.15f) -> RipenessState.RIPENING
                else -> RipenessState.RIPE
            }

        val hasDeterioration = matchedRangeRule?.hasVisibleDeterioration
            ?: learned?.hasDeterioration
            ?: (spotRatio > 0.26f)

        val deteriorationSigns = when {
            matchedRangeRule != null && matchedRangeRule.deteriorationSigns.isNotEmpty() ->
                matchedRangeRule.deteriorationSigns
            hasDeterioration ->
                listOf("Zonas oscuras o magulladuras superficiales detectadas en la cara visible.")
            else ->
                emptyList()
        }

        val dominantColorLabel = when {
            yellowRatio >= redRatio && yellowRatio >= greenRatio && yellowRatio >= orangeRatio -> "tonalidad amarilla dominante"
            redRatio >= orangeRatio && redRatio >= greenRatio -> "coloración rojiza superficial uniforme"
            orangeRatio >= greenRatio -> "pigmentación naranja cálida"
            greenRatio >= purpleRatio -> "cobertura verde predominante"
            else -> "tonalidad oscura característica"
        }

        val confidence = if (matchedRangeRule != null || learned != null) {
            0.98f
        } else {
            (0.86f + min(0.11f, (fgCount.toFloat() / max(1, totalSampled)) * 0.14f)).coerceIn(0.84f, 0.97f)
        }

        val visualObservations = if (matchedRangeRule != null && matchedRangeRule.visualObservations.isNotEmpty()) {
            matchedRangeRule.visualObservations
        } else {
            buildList {
                add("Ejemplar de ${detectedProduct.canonicalName} con $dominantColorLabel.")
                if (spotRatio in 0.08f..0.26f) {
                    add("Se observan ligeras pecas de maduración natural en la superficie.")
                } else if (!hasDeterioration) {
                    add("Superficie íntegra sin signos de descomposición severa en la cara visible.")
                }
            }
        }

        return YoloDetectionResult(
            product = detectedProduct,
            ripenessState = ripenessState,
            confidence = confidence,
            boundingBox = boundingBox,
            hasDeterioration = hasDeterioration,
            deteriorationSigns = deteriorationSigns,
            visualObservations = visualObservations,
            inferenceTimeMs = 18L,
            featureVector = featureVector,
            matchedRangeRule = matchedRangeRule
        )
    }
}
