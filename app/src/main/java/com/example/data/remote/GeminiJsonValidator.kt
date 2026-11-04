package com.example.data.remote

import com.example.data.estimator.ShelfLifeEstimator
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.FoodProduct
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.RipenessState
import com.example.domain.model.StorageConditions
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class GeminiResponseDto(
    val producto: String,
    val identificacionConfiable: Boolean,
    val multiplesProductosOAmbiguo: Boolean,
    val madurez: String,
    val observacionesVisuales: List<String>,
    val posibleDeterioro: Boolean,
    val senalesDeterioro: List<String>,
    val vidaUtilMin: Int?,
    val vidaUtilMax: Int?,
    val diasHastaMadurezMin: Int?,
    val diasHastaMadurezMax: Int?,
    val estimacionValidada: Boolean,
    val recomendaciones: List<String>,
    val limitaciones: List<String>,
    val boxYmin: Int? = null,
    val boxXmin: Int? = null,
    val boxYmax: Int? = null,
    val boxXmax: Int? = null,
    val productosDetectados: List<GeminiResponseDto> = emptyList()
)

object GeminiJsonValidator {

    private val OVERCONFIDENT_PATTERNS = listOf(
        Regex("\\b100%\\s*(seguro|garantizado|inocuo|confiable)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(totalmente|completamente)\\s+inocuo\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgarantiza(mos)?\\s+que\\s+es\\s+seguro\\b", RegexOption.IGNORE_CASE),
        Regex("\\blibres?\\s+de\\s+bacterias\\b", RegexOption.IGNORE_CASE),
        Regex("\\bexactamente\\s+(\\d+)\\s+d[ií]as\\b", RegexOption.IGNORE_CASE)
    )

    fun parseJson(rawText: String): GeminiResponseDto {
        val cleaned = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val startIdx = cleaned.indexOf('{')
        val endIdx = cleaned.lastIndexOf('}')
        if (startIdx < 0 || endIdx <= startIdx) {
            throw IllegalArgumentException("El formato JSON devuelto por el modelo es inválido.")
        }
        val jsonBody = cleaned.substring(startIdx, endIdx + 1)
        val root = JSONObject(jsonBody)
        return parseSingleItemObject(root, parseNested = true)
    }

    private fun parseSingleItemObject(
        obj: JSONObject,
        parseNested: Boolean
    ): GeminiResponseDto {
        val producto = obj.optString("producto", "").trim()
        val identificacionConfiable = obj.optBoolean("identificacion_confiable", producto.isNotBlank() && !isUnknownOrNonFoodLabel(producto))
        val multiples = obj.optBoolean("multiples_productos_o_ambiguo", false)
        val madurez = obj.optString("madurez", "indeterminado").trim()
        val observaciones = optStringList(obj, "observaciones_visuales")
        val posibleDeterioro = obj.optBoolean("posible_deterioro", false)
        val senalesDeterioro = optStringList(obj, "senales_deterioro")
        val vidaMin = optNullableInt(obj, "vida_util_min")
        val vidaMax = optNullableInt(obj, "vida_util_max")
        val madurezMin = optNullableInt(obj, "dias_hasta_madurez_min")
        val madurezMax = optNullableInt(obj, "dias_hasta_madurez_max")
        val estimacionValidada = obj.optBoolean("estimacion_validada", true)
        val recomendaciones = optStringList(obj, "recomendaciones")
        val limitaciones = optStringList(obj, "limitaciones")
        val boxYmin = optNullableInt(obj, "box_ymin")
        val boxXmin = optNullableInt(obj, "box_xmin")
        val boxYmax = optNullableInt(obj, "box_ymax")
        val boxXmax = optNullableInt(obj, "box_xmax")

        val nestedItems = mutableListOf<GeminiResponseDto>()
        if (parseNested) {
            val array = obj.optJSONArray("productos_detectados")
            if (array != null) {
                for (i in 0 until min(array.length(), 8)) {
                    val itemObj = array.optJSONObject(i) ?: continue
                    nestedItems.add(parseSingleItemObject(itemObj, parseNested = false))
                }
            }
        }

        return GeminiResponseDto(
            producto = producto,
            identificacionConfiable = identificacionConfiable,
            multiplesProductosOAmbiguo = multiples || nestedItems.size > 1,
            madurez = madurez,
            observacionesVisuales = observaciones,
            posibleDeterioro = posibleDeterioro,
            senalesDeterioro = senalesDeterioro,
            vidaUtilMin = vidaMin,
            vidaUtilMax = vidaMax,
            diasHastaMadurezMin = madurezMin,
            diasHastaMadurezMax = madurezMax,
            estimacionValidada = estimacionValidada,
            recomendaciones = recomendaciones,
            limitaciones = limitaciones,
            boxYmin = boxYmin,
            boxXmin = boxXmin,
            boxYmax = boxYmax,
            boxXmax = boxXmax,
            productosDetectados = nestedItems
        )
    }

    fun validateAndMapToDomain(
        dto: GeminiResponseDto,
        storageConditions: StorageConditions? = null,
        manualProductOverride: String? = null,
        shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator()
    ): FoodAnalysis {
        val primaryAnalysis = mapSingleDtoToDomain(
            dto = dto,
            storageConditions = storageConditions,
            manualProductOverride = manualProductOverride,
            shelfLifeEstimator = shelfLifeEstimator
        )

        val rawDetectedList = dto.productosDetectados.mapIndexed { index, childDto ->
            val mappedChild = mapSingleDtoToDomain(
                dto = childDto,
                storageConditions = storageConditions,
                manualProductOverride = if (dto.productosDetectados.size == 1) manualProductOverride else null,
                shelfLifeEstimator = shelfLifeEstimator
            )
            if (mappedChild.boundingBox == null) {
                mappedChild.copy(
                    boundingBox = createFallbackBoxForIndex(index, dto.productosDetectados.size)
                )
            } else {
                mappedChild
            }
        }.filter { it.isReliableIdentification }

        val deduplicatedItems = deduplicateOverlappingDetections(rawDetectedList)

        return if (deduplicatedItems.isNotEmpty()) {
            val firstItem = deduplicatedItems.first()
            primaryAnalysis.copy(
                boundingBox = primaryAnalysis.boundingBox ?: firstItem.boundingBox,
                multipleOrAmbiguousItems = dto.multiplesProductosOAmbiguo || deduplicatedItems.size > 1,
                detectedItems = deduplicatedItems
            )
        } else {
            primaryAnalysis.copy(
                detectedItems = listOf(primaryAnalysis)
            )
        }
    }

    private fun mapSingleDtoToDomain(
        dto: GeminiResponseDto,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        shelfLifeEstimator: ShelfLifeEstimator
    ): FoodAnalysis {
        val effectiveProductName = manualProductOverride?.takeIf { it.isNotBlank() }
            ?: dto.producto.ifBlank { "Producto no identificado" }

        val isUnknownLabel = isUnknownOrNonFoodLabel(effectiveProductName)
        val matchedCatalogProduct = if (isUnknownLabel) {
            null
        } else {
            FoodProduct.findMatchingProduct(effectiveProductName)
        }

        val isReliable = (!isUnknownLabel && dto.identificacionConfiable) ||
            (!manualProductOverride.isNullOrBlank() && !isUnknownOrNonFoodLabel(manualProductOverride))

        val parsedRipeness = if (!isReliable) {
            RipenessState.UNDETERMINED
        } else {
            val rawRipeness = RipenessState.fromRawString(dto.madurez)
            if (rawRipeness == RipenessState.UNDETERMINED && !manualProductOverride.isNullOrBlank()) {
                RipenessState.RIPE
            } else {
                rawRipeness
            }
        }

        val hasDeterioration = dto.posibleDeterioro || dto.senalesDeterioro.isNotEmpty()
        val sanitizedObservations = dto.observacionesVisuales
            .map { sanitizeCertaintyClaims(it) }
            .filter { it.isNotBlank() }
            .ifEmpty {
                if (isReliable) {
                    listOf("Evaluación visual completada sobre la superficie externa visible.")
                } else {
                    listOf("No se detectó ninguna fruta o verdura claramente visible en la fotografía.")
                }
            }

        val sanitizedDeterioration = dto.senalesDeterioro
            .map { sanitizeCertaintyClaims(it) }
            .filter { it.isNotBlank() }

        val sanitizedRecommendations = dto.recomendaciones
            .map { sanitizeCertaintyClaims(it) }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (hasDeterioration && sanitizedRecommendations.none {
                it.contains("desech", ignoreCase = true) || it.contains("no consum", ignoreCase = true)
            }
        ) {
            sanitizedRecommendations.add(
                0,
                "Por precaución alimentaria, evite consumir las zonas con deterioro visible o deseche la pieza si presenta moho o mal olor."
            )
        }
        if (sanitizedRecommendations.isEmpty()) {
            sanitizedRecommendations.add(
                "Conserve en un lugar limpio y fresco; verifique olor y firmeza al tacto antes de consumir."
            )
        }

        val sanitizedLimitations = dto.limitaciones
            .map { sanitizeCertaintyClaims(it) }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf("La cámara evalúa únicamente la apariencia superficial externa y no reemplaza la inspección de olor o textura interna.")
            }

        val shelfLife = shelfLifeEstimator.estimate(
            matchedProduct = matchedCatalogProduct,
            isReliableIdentification = isReliable,
            multipleOrAmbiguousItems = false,
            ripenessState = parsedRipeness,
            hasVisibleDeterioration = hasDeterioration,
            deteriorationSigns = sanitizedDeterioration,
            geminiShelfLifeMin = dto.vidaUtilMin,
            geminiShelfLifeMax = dto.vidaUtilMax,
            geminiRipeningDaysMin = dto.diasHastaMadurezMin,
            geminiRipeningDaysMax = dto.diasHastaMadurezMax,
            geminiLimitations = sanitizedLimitations,
            storageConditions = storageConditions
        )

        val boundingBox = parseNormalizedBoundingBox(
            ymin = dto.boxYmin,
            xmin = dto.boxXmin,
            ymax = dto.boxYmax,
            xmax = dto.boxXmax
        )

        return FoodAnalysis(
            sessionId = UUID.randomUUID().toString(),
            rawProductName = effectiveProductName,
            manuallyCorrectedName = manualProductOverride?.takeIf { it.isNotBlank() },
            matchedCatalogProduct = matchedCatalogProduct,
            isReliableIdentification = isReliable,
            multipleOrAmbiguousItems = dto.multiplesProductosOAmbiguo,
            ripenessState = parsedRipeness,
            visualObservations = sanitizedObservations,
            hasVisibleDeterioration = hasDeterioration,
            deteriorationSigns = sanitizedDeterioration,
            shelfLifeEstimate = shelfLife,
            recommendations = sanitizedRecommendations,
            limitations = sanitizedLimitations,
            appliedStorageConditions = storageConditions,
            isAdvancedAnalysis = storageConditions != null,
            boundingBox = boundingBox
        )
    }

    internal fun parseNormalizedBoundingBox(
        ymin: Int?,
        xmin: Int?,
        ymax: Int?,
        xmax: Int?
    ): NormalizedBoundingBox? {
        if (ymin == null || xmin == null || ymax == null || xmax == null) return null
        val box = NormalizedBoundingBox(
            ymin = (ymin / 1000f).coerceIn(0f, 1f),
            xmin = (xmin / 1000f).coerceIn(0f, 1f),
            ymax = (ymax / 1000f).coerceIn(0f, 1f),
            xmax = (xmax / 1000f).coerceIn(0f, 1f)
        )
        return if (box.isValid) box else null
    }

    private fun createFallbackBoxForIndex(index: Int, total: Int): NormalizedBoundingBox {
        val count = max(1, total)
        val colWidth = 0.84f / count
        val xmin = (0.08f + index * colWidth).coerceIn(0.05f, 0.85f)
        val xmax = (xmin + colWidth * 0.9f).coerceIn(xmin + 0.08f, 0.95f)
        return NormalizedBoundingBox(
            ymin = 0.18f,
            xmin = xmin,
            ymax = 0.82f,
            xmax = xmax
        )
    }

    private fun deduplicateOverlappingDetections(items: List<FoodAnalysis>): List<FoodAnalysis> {
        if (items.size <= 1) return items
        val kept = mutableListOf<FoodAnalysis>()
        for (candidate in items) {
            val candBox = candidate.boundingBox
            val isDuplicate = kept.any { existing ->
                val existBox = existing.boundingBox
                val sameProduct = existing.displayProductName.equals(candidate.displayProductName, ignoreCase = true)
                if (candBox != null && existBox != null) {
                    val iou = computeIoU(candBox, existBox)
                    val containment = computeMaxContainment(candBox, existBox)
                    iou > 0.52f || (sameProduct && (iou > 0.32f || containment > 0.72f))
                } else {
                    false
                }
            }
            if (!isDuplicate) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun computeIoU(a: NormalizedBoundingBox, b: NormalizedBoundingBox): Float {
        val interLeft = max(a.xmin, b.xmin)
        val interTop = max(a.ymin, b.ymin)
        val interRight = min(a.xmax, b.xmax)
        val interBottom = min(a.ymax, b.ymax)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        val areaA = max(0.0001f, (a.xmax - a.xmin) * (a.ymax - a.ymin))
        val areaB = max(0.0001f, (b.xmax - b.xmin) * (b.ymax - b.ymin))
        return interArea / (areaA + areaB - interArea)
    }

    private fun computeMaxContainment(a: NormalizedBoundingBox, b: NormalizedBoundingBox): Float {
        val interLeft = max(a.xmin, b.xmin)
        val interTop = max(a.ymin, b.ymin)
        val interRight = min(a.xmax, b.xmax)
        val interBottom = min(a.ymax, b.ymax)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val areaA = max(0.0001f, (a.xmax - a.xmin) * (a.ymax - a.ymin))
        val areaB = max(0.0001f, (b.xmax - b.xmin) * (b.ymax - b.ymin))
        return max(interArea / areaA, interArea / areaB)
    }

    fun isUnknownOrNonFoodLabel(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower.isBlank() ||
            lower == "desconocido" ||
            lower == "no identificado" ||
            lower == "producto no identificado" ||
            lower == "ninguno" ||
            lower.contains("no se detect") ||
            lower.contains("sin fruta") ||
            lower.contains("ninguna fruta") ||
            lower.contains("no hay fruta") ||
            lower.contains("no es un alimento") ||
            lower.contains("no es fruta") ||
            lower.contains("objeto no alimenticio") ||
            lower.contains("teclado") ||
            lower.contains("laptop") ||
            lower.contains("computadora") ||
            lower.contains("mano")
    }

    private fun sanitizeCertaintyClaims(text: String): String {
        var result = text.trim()
        for (pattern in OVERCONFIDENT_PATTERNS) {
            result = result.replace(pattern, "según la apariencia visual")
        }
        return result
    }

    private fun optNullableInt(obj: JSONObject, key: String): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return try {
            obj.getInt(key)
        } catch (_: Exception) {
            null
        }
    }

    private fun optStringList(obj: JSONObject, key: String): List<String> {
        val arr: JSONArray = obj.optJSONArray(key) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i, "").trim()
            if (item.isNotBlank()) {
                result.add(item)
            }
        }
        return result
    }
}
