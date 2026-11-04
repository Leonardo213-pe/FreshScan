package com.example.data.local

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.estimator.ConservationReferenceSource
import com.example.data.estimator.ShelfLifeEstimator
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.FoodProduct
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.RipenessState
import com.example.domain.model.ShelfLifeEstimate
import com.example.domain.model.StorageConditions
import com.example.domain.model.VisualFeatureVector
import com.example.domain.model.VisualForecastRule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Motor de Aprendizaje Visual Evolutivo que inicia vacío (sin heurísticas fijas que inventen frutas)
 * y aprende progresivamente de cada análisis realizado por Gemini Vision y de cada corrección
 * manual del usuario, sincronizando su memoria con el almacenamiento local y Cloud Firestore.
 */
class AdaptiveVisualLearner(
    private val shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator(),
    private val imageProcessor: ImageProcessor = ImageProcessor(),
    private val visualForecastStore: LocalVisualForecastStore = LocalVisualForecastStore()
) {

    data class SpatialFeatureResult(
        val boundingBox: NormalizedBoundingBox,
        val featureVector: VisualFeatureVector,
        val hasOrganicForeground: Boolean
    )

    private data class LearnedVisualSignature(
        val perceptualHash: Long,
        val productName: String,
        val matchedProduct: FoodProduct?,
        val ripenessState: RipenessState,
        val hasDeterioration: Boolean
    )

    private val learnedSignatures = mutableListOf<LearnedVisualSignature>()

    @Synchronized
    fun learnFromValidatedGeminiResult(
        bitmap: Bitmap,
        geminiAnalysis: FoodAnalysis
    ): VisualFeatureVector? {
        if (!geminiAnalysis.isReliableIdentification) return null
        val displayName = geminiAnalysis.displayProductName.trim()
        if (displayName.isBlank() || displayName.equals("Producto no identificado", ignoreCase = true)) {
            return null
        }

        val matched = geminiAnalysis.matchedCatalogProduct
            ?: FoodProduct.findMatchingProduct(displayName)

        val hash = imageProcessor.computePerceptualHash(bitmap)
        if (hash != 0L) {
            learnedSignatures.removeAll {
                VisualHashCache.hammingDistance(it.perceptualHash, hash) <= 6
            }
            learnedSignatures.add(
                0,
                LearnedVisualSignature(
                    perceptualHash = hash,
                    productName = displayName,
                    matchedProduct = matched,
                    ripenessState = geminiAnalysis.ripenessState,
                    hasDeterioration = geminiAnalysis.hasVisibleDeterioration
                )
            )
            while (learnedSignatures.size > 100) {
                learnedSignatures.removeAt(learnedSignatures.lastIndex)
            }
        }

        val featureVector = extractVisualFeatureVector(bitmap)
        val localRule = VisualForecastRule.fromValidatedAnalysis(
            userId = "local_device",
            featureVector = featureVector,
            analysis = geminiAnalysis
        )
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
        return extractSpatialFeatures(bitmap).featureVector
    }

    fun buildLearnedPromptHintForGemini(bitmap: Bitmap): String {
        val spatial = extractSpatialFeatures(bitmap)
        val exactSig = findLearnedSignature(bitmap)
        val similarRules = visualForecastStore.findSimilarLearnedRulesForPrompt(
            features = spatial.featureVector,
            maxExamples = 4
        )
        if (exactSig == null && similarRules.isEmpty()) return ""

        val lines = mutableListOf<String>()
        if (exactSig != null) {
            lines.add(
                "- Firma visual muy similar registrada previamente en este dispositivo: '${exactSig.productName}' (madurez: ${exactSig.ripenessState.displayName})."
            )
        }
        for (rule in similarRules) {
            lines.add(
                "- Ejemplo aprendido en memoria evolutiva (${rule.confirmations} confirmaciones): '${rule.productName}' en estado ${rule.parsedRipenessState().displayName} (vida útil ${rule.remainingShelfLifeDaysMin}-${rule.remainingShelfLifeDaysMax} días)."
            )
        }
        return """
            MEMORIA DE APRENDIZAJE EVOLUTIVO (patrones aprendidos previamente de escaneos y correcciones del usuario):
            ${lines.joinToString("\n")}
            Ten en cuenta esta memoria aprendida si coincide visualmente con el ejemplar de la foto, pero prioriza siempre lo que realmente observes en la imagen actual (y si no hay ninguna fruta o verdura en la imagen, marca identificacion_confiable = false).
        """.trimIndent()
    }

    /**
     * Evalúa una imagen utilizando únicamente el conocimiento que el sistema ya haya aprendido
     * de Gemini o de correcciones manuales (sin inventar frutas cuando la memoria está vacía).
     */
    fun evaluateFromLearnedMemory(
        bitmap: Bitmap,
        storageConditions: StorageConditions? = null,
        manualProductOverride: String? = null
    ): FoodAnalysis {
        val startNanos = System.nanoTime()
        val spatial = extractSpatialFeatures(bitmap)
        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(8L)

        val learnedSig = findLearnedSignature(bitmap)
        val matchedRule = visualForecastStore.findBestMatchingRule(
            features = spatial.featureVector,
            hintProductName = manualProductOverride ?: learnedSig?.productName
        )

        val resolvedProductName = manualProductOverride?.takeIf { it.isNotBlank() }
            ?: learnedSig?.productName
            ?: matchedRule?.productName

        if (resolvedProductName.isNullOrBlank()) {
            val emptyEstimate = ShelfLifeEstimate(
                isValidatedEstimate = false,
                predictionLimitations = listOf(
                    "Este alimento aún no ha sido registrado en la memoria evolutiva local. Analícelo con Gemini en línea para que el sistema aprenda sus rasgos."
                )
            )
            val unknownAnalysis = FoodAnalysis(
                rawProductName = "Sin coincidencia en memoria aprendida",
                isReliableIdentification = false,
                ripenessState = RipenessState.UNDETERMINED,
                visualObservations = listOf(
                    "El modelo evolutivo inicia vacío y aprende con cada escaneo de Gemini.",
                    "Conéctese a internet para analizar este alimento con Gemini y guardarlo automáticamente en la memoria evolutiva."
                ),
                shelfLifeEstimate = emptyEstimate,
                recommendations = listOf(
                    "Realice un escaneo con Gemini Vision para incorporar esta fruta o verdura a la base de aprendizaje."
                ),
                limitations = emptyEstimate.predictionLimitations,
                boundingBox = spatial.boundingBox,
                inferenceTimeMs = elapsedMs,
                analysisEngineSource = "Memoria Evolutiva (Pendiente de aprendizaje)"
            )
            return unknownAnalysis.copy(detectedItems = listOf(unknownAnalysis))
        }

        val matchedProduct = FoodProduct.findMatchingProduct(resolvedProductName)
            ?: learnedSig?.matchedProduct
        val ripenessState = matchedRule?.parsedRipenessState()
            ?: learnedSig?.ripenessState
            ?: RipenessState.RIPE
        val hasDeterioration = matchedRule?.hasVisibleDeterioration
            ?: learnedSig?.hasDeterioration
            ?: (spatial.featureVector.spotPermille > 260)

        val deteriorationSigns = matchedRule?.deteriorationSigns?.takeIf { it.isNotEmpty() }
            ?: if (hasDeterioration) {
                listOf("Manchas oscuras o signos superficiales detectados según memoria aprendida.")
            } else {
                emptyList()
            }

        val shelfLifeEstimate = shelfLifeEstimator.estimate(
            matchedProduct = matchedProduct,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = ripenessState,
            hasVisibleDeterioration = hasDeterioration,
            deteriorationSigns = deteriorationSigns,
            geminiRipeningDaysMin = matchedRule?.daysToRipenMin?.takeIf { it >= 0 }?.toInt(),
            geminiRipeningDaysMax = matchedRule?.daysToRipenMax?.takeIf { it >= 0 }?.toInt(),
            geminiShelfLifeMin = matchedRule?.remainingShelfLifeDaysMin?.toInt(),
            geminiShelfLifeMax = matchedRule?.remainingShelfLifeDaysMax?.toInt(),
            geminiLimitations = listOf(
                "Estimación recuperada de la memoria evolutiva aprendida previamente con Gemini."
            ),
            storageConditions = storageConditions
        )

        val referenceEntry = ConservationReferenceSource.findByProduct(matchedProduct)
        val recommendations = matchedRule?.recommendations?.takeIf { it.isNotEmpty() }
            ?: buildList {
                if (referenceEntry != null) {
                    add(referenceEntry.idealStorageLocation)
                    add(referenceEntry.ethyleneNotes)
                } else {
                    add("Conservar en ambiente fresco, limpio y seco, verificando su textura antes de consumir.")
                }
            }

        val observations = matchedRule?.visualObservations?.takeIf { it.isNotEmpty() }
            ?: listOf(
                "Identificado como $resolvedProductName a partir de los patrones aprendidos por el sistema."
            )

        val singleAnalysis = FoodAnalysis(
            rawProductName = resolvedProductName,
            manuallyCorrectedName = manualProductOverride?.takeIf { it.isNotBlank() },
            matchedCatalogProduct = matchedProduct,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = ripenessState,
            visualObservations = observations,
            hasVisibleDeterioration = hasDeterioration,
            deteriorationSigns = deteriorationSigns,
            shelfLifeEstimate = shelfLifeEstimate,
            recommendations = recommendations,
            limitations = shelfLifeEstimate.predictionLimitations,
            appliedStorageConditions = storageConditions?.takeIf { it.hasAnyInput() },
            isAdvancedAnalysis = storageConditions?.hasAnyInput() == true,
            boundingBox = spatial.boundingBox,
            isPreliminaryResult = false,
            localConfidence = 0.96f,
            inferenceTimeMs = elapsedMs,
            analysisEngineSource = "Memoria Evolutiva Aprendida"
        )

        return singleAnalysis.copy(detectedItems = listOf(singleAnalysis))
    }

    fun extractSpatialFeatures(bitmap: Bitmap): SpatialFeatureResult {
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

        for (gy in 0 until gridY) {
            val py = ((gy + 0.5f) * height / gridY).toInt().coerceIn(0, height - 1)
            for (gx in 0 until gridX) {
                val px = ((gx + 0.5f) * width / gridX).toInt().coerceIn(0, width - 1)
                val pixel = bitmap.getPixel(px, py)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

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

        val segmentedBox = imageProcessor.segmentProduceRegionBounds(bitmap)
        val boundingBox = segmentedBox ?: if (fgCount >= 6 && maxFgX > minFgX && maxFgY > minFgY) {
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

        return SpatialFeatureResult(
            boundingBox = boundingBox,
            featureVector = featureVector,
            hasOrganicForeground = fgCount >= 8
        )
    }
}
