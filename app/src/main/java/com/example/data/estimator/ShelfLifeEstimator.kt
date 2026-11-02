package com.example.data.estimator

import com.example.domain.model.FoodProduct
import com.example.domain.model.FutureStoragePlan
import com.example.domain.model.PreparationState
import com.example.domain.model.RipenessState
import com.example.domain.model.ShelfLifeEstimate
import com.example.domain.model.StorageConditions
import com.example.domain.model.StorageEnvironment
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ShelfLifeEstimator(
    private val referenceSource: ConservationReferenceSource = ConservationReferenceSource
) {
    fun estimate(
        matchedProduct: FoodProduct?,
        isReliableIdentification: Boolean,
        multipleOrAmbiguousItems: Boolean,
        ripenessState: RipenessState,
        hasVisibleDeterioration: Boolean,
        deteriorationSigns: List<String>,
        geminiShelfLifeMin: Int? = null,
        geminiShelfLifeMax: Int? = null,
        geminiRipeningDaysMin: Int? = null,
        geminiRipeningDaysMax: Int? = null,
        geminiLimitations: List<String> = emptyList(),
        storageConditions: StorageConditions? = null
    ): ShelfLifeEstimate {
        val reference = referenceSource.findByProduct(matchedProduct)
        val limitations = geminiLimitations.toMutableList()
        val considered = mutableListOf<String>()

        if (!isReliableIdentification || multipleOrAmbiguousItems) {
            if (limitations.none { it.contains("identificación", ignoreCase = true) }) {
                limitations.add(ShelfLifeEstimate.UNRELIABLE_SHELF_LIFE_MESSAGE)
            }
            return ShelfLifeEstimate(
                remainingShelfLifeDaysMin = null,
                remainingShelfLifeDaysMax = null,
                daysToRipenMin = null,
                daysToRipenMax = null,
                roomTempDaysMin = reference?.ripeRoomTempDays?.first,
                roomTempDaysMax = reference?.ripeRoomTempDays?.last,
                refrigeratedDaysMin = reference?.ripeRefrigeratedDays?.first,
                refrigeratedDaysMax = reference?.ripeRefrigeratedDays?.last,
                isValidatedEstimate = false,
                consideredConditions = considered,
                predictionLimitations = limitations.distinct(),
                generalStorageReference = reference?.generalReferenceSummary,
                referenceSourceCitation = reference?.referenceCitation
            )
        }

        if (hasVisibleDeterioration) {
            val severe = deteriorationSigns.any { sign ->
                val lower = sign.lowercase()
                lower.contains("moho") || lower.contains("pudric") ||
                    lower.contains("ferment") || lower.contains("exudado") ||
                    lower.contains("solanina")
            }
            val safeMin = 0
            val safeMax = if (severe) 0 else 1
            considered.add("Deterioro visual detectado: se prioriza inocuidad alimentaria sobre vida útil estándar.")
            return ShelfLifeEstimate(
                remainingShelfLifeDaysMin = safeMin,
                remainingShelfLifeDaysMax = safeMax,
                daysToRipenMin = 0,
                daysToRipenMax = 0,
                roomTempDaysMin = 0,
                roomTempDaysMax = safeMax,
                refrigeratedDaysMin = 0,
                refrigeratedDaysMax = safeMax,
                isValidatedEstimate = true,
                consideredConditions = considered,
                predictionLimitations = limitations.distinct(),
                generalStorageReference = reference?.generalReferenceSummary,
                referenceSourceCitation = reference?.referenceCitation
            )
        }

        val geminiRange = if (geminiShelfLifeMin != null && geminiShelfLifeMax != null && geminiShelfLifeMax >= geminiShelfLifeMin) {
            geminiShelfLifeMin.coerceIn(0, 60)..geminiShelfLifeMax.coerceIn(geminiShelfLifeMin.coerceIn(0, 60), 60)
        } else {
            null
        }

        val baseRange = if (reference != null) {
            selectBaseShelfLifeRange(reference, ripenessState, storageConditions)
        } else {
            geminiRange ?: (2..4)
        }

        val adjustedRange = if (reference != null) {
            applyPostharvestRules(
                baseRange = baseRange,
                reference = reference,
                isClimacteric = matchedProduct?.isClimacteric == true,
                storageConditions = storageConditions,
                geminiRange = geminiRange,
                considered = considered,
                limitations = limitations
            )
        } else {
            considered.add("Estimación visual orientativa para alimento fuera del catálogo calibrado.")
            baseRange
        }

        val ripenRange: IntRange? = when {
            ripenessState == RipenessState.UNRIPE || ripenessState == RipenessState.RIPENING -> {
                if (matchedProduct?.isClimacteric == false) {
                    considered.add("Fruto no climatérico: no continúa endulzando tras la cosecha.")
                    null
                } else {
                    val baseRipen = reference?.typicalRipeningDaysRoomTemp
                        ?: if (geminiRipeningDaysMin != null && geminiRipeningDaysMax != null && geminiRipeningDaysMax >= geminiRipeningDaysMin) {
                            geminiRipeningDaysMin..geminiRipeningDaysMax
                        } else {
                            2..5
                        }
                    val scaledRipen = if (ripenessState == RipenessState.RIPENING) {
                        max(1, baseRipen.first / 2)..max(2, (baseRipen.last * 0.65f).roundToInt())
                    } else {
                        baseRipen
                    }
                    if (storageConditions != null) {
                        adjustRipeningForStorage(scaledRipen, storageConditions)
                    } else {
                        scaledRipen
                    }
                }
            }
            else -> null
        }

        val roomRange = if (reference != null) {
            computeEnvironmentSpecificRange(
                reference = reference,
                ripenessState = ripenessState,
                isRefrigerated = false,
                isCut = storageConditions?.preparationState == PreparationState.CUT_OR_PEELED,
                geminiRange = geminiRange
            )
        } else {
            adjustedRange
        }

        val fridgeRange = if (reference != null) {
            computeEnvironmentSpecificRange(
                reference = reference,
                ripenessState = ripenessState,
                isRefrigerated = true,
                isCut = storageConditions?.preparationState == PreparationState.CUT_OR_PEELED,
                geminiRange = geminiRange
            )
        } else {
            (adjustedRange.first + 1)..(adjustedRange.last + 3)
        }

        return ShelfLifeEstimate(
            remainingShelfLifeDaysMin = adjustedRange.first,
            remainingShelfLifeDaysMax = adjustedRange.last,
            daysToRipenMin = ripenRange?.first,
            daysToRipenMax = ripenRange?.last,
            roomTempDaysMin = roomRange.first,
            roomTempDaysMax = roomRange.last,
            refrigeratedDaysMin = fridgeRange.first,
            refrigeratedDaysMax = fridgeRange.last,
            isValidatedEstimate = reference != null || geminiRange != null,
            consideredConditions = considered.distinct(),
            predictionLimitations = limitations.distinct(),
            generalStorageReference = reference?.generalReferenceSummary,
            referenceSourceCitation = reference?.referenceCitation
        )
    }

    private fun selectBaseShelfLifeRange(
        reference: ConservationReferenceEntry,
        ripenessState: RipenessState,
        storageConditions: StorageConditions?
    ): IntRange {
        if (storageConditions?.preparationState == PreparationState.CUT_OR_PEELED) {
            return reference.cutRefrigeratedDays
        }

        val useRefrigerated = storageConditions?.futureStoragePlan == FutureStoragePlan.REFRIGERATE_NOW ||
            (storageConditions?.storageEnvironment == StorageEnvironment.REFRIGERATED &&
                storageConditions.futureStoragePlan != FutureStoragePlan.KEEP_AT_ROOM_TEMP)

        val base = if (useRefrigerated) reference.ripeRefrigeratedDays else reference.ripeRoomTempDays

        return when (ripenessState) {
            RipenessState.UNRIPE -> {
                val extra = reference.typicalRipeningDaysRoomTemp ?: (2..4)
                (base.first + extra.first)..(base.last + extra.last)
            }
            RipenessState.RIPENING -> {
                val extra = reference.typicalRipeningDaysRoomTemp ?: (1..2)
                (base.first + max(1, extra.first / 2))..(base.last + max(1, extra.last / 2))
            }
            RipenessState.RIPE -> base
            RipenessState.OVERRIPE -> max(0, base.first / 3)..max(1, min(2, base.last / 2))
            RipenessState.UNDETERMINED -> base
        }
    }

    private fun applyPostharvestRules(
        baseRange: IntRange,
        reference: ConservationReferenceEntry,
        isClimacteric: Boolean,
        storageConditions: StorageConditions?,
        geminiRange: IntRange?,
        considered: MutableList<String>,
        limitations: MutableList<String>
    ): IntRange {
        var minDays = baseRange.first.toFloat()
        var maxDays = baseRange.last.toFloat()

        if (geminiRange != null) {
            minDays = (minDays * 0.65f) + (geminiRange.first * 0.35f)
            maxDays = (maxDays * 0.65f) + (geminiRange.last * 0.35f)
        }

        if (storageConditions != null) {
            considered.add("Ambiente actual: ${storageConditions.storageEnvironment.displayName}")
            considered.add("Plan de conservación: ${storageConditions.futureStoragePlan.displayName}")

            if (storageConditions.preparationState == PreparationState.CUT_OR_PEELED) {
                considered.add("Alimento cortado/pelado: pérdida de barrera epidérmica reduce drásticamente la vida útil.")
                minDays = min(minDays, reference.cutRefrigeratedDays.first.toFloat())
                maxDays = min(maxDays, reference.cutRefrigeratedDays.last.toFloat())
            }

            storageConditions.approximateTemperatureCelsius?.let { temp ->
                considered.add("Temperatura reportada: ${temp.roundToInt()} °C")
                if (temp > 26f && storageConditions.futureStoragePlan == FutureStoragePlan.KEEP_AT_ROOM_TEMP) {
                    val heatFactor = if (temp >= 30f) 0.55f else 0.72f
                    minDays *= heatFactor
                    maxDays *= heatFactor
                    considered.add("Ajuste térmico Q10: temperatura cálida (>26 °C) acelera respiración y transpiración.")
                }
                val chillingLimit = reference.chillingSensitiveBelowCelsius
                if (chillingLimit != null && temp < chillingLimit && isClimacteric) {
                    limitations.add("Nota fisiológica: bajo $chillingLimit °C este producto puede presentar pardeamiento epidérmico por frío.")
                }
            }

            storageConditions.daysAlreadyStored?.let { daysStored ->
                if (daysStored > 0) {
                    considered.add("Días previos almacenados descontados: $daysStored d")
                    val deduction = (daysStored * 0.45f).coerceAtMost(maxDays * 0.5f)
                    minDays = max(0f, minDays - deduction)
                    maxDays = max(1f, maxDays - deduction)
                }
            }
        } else {
            considered.add("Estimación estándar calibrada con tablas poscosecha (${reference.productName}).")
        }

        val finalMin = max(0, minDays.roundToInt())
        val finalMax = max(finalMin, maxDays.roundToInt())
        return finalMin..finalMax
    }

    private fun adjustRipeningForStorage(
        baseRipen: IntRange,
        storageConditions: StorageConditions
    ): IntRange {
        val factor = when (storageConditions.futureStoragePlan) {
            FutureStoragePlan.REFRIGERATE_NOW -> 1.6f
            FutureStoragePlan.KEEP_AT_ROOM_TEMP -> {
                val temp = storageConditions.approximateTemperatureCelsius
                if (temp != null && temp >= 27f) 0.7f else 1.0f
            }
            FutureStoragePlan.CONSUME_SOON -> 0.8f
            FutureStoragePlan.FREEZE -> 0f
        }
        if (factor == 0f) return 0..0
        val minD = max(1, (baseRipen.first * factor).roundToInt())
        val maxD = max(minD, (baseRipen.last * factor).roundToInt())
        return minD..maxD
    }

    private fun computeEnvironmentSpecificRange(
        reference: ConservationReferenceEntry,
        ripenessState: RipenessState,
        isRefrigerated: Boolean,
        isCut: Boolean,
        geminiRange: IntRange?
    ): IntRange {
        if (isCut) {
            return if (isRefrigerated) reference.cutRefrigeratedDays else 0..1
        }
        val base = if (isRefrigerated) reference.ripeRefrigeratedDays else reference.ripeRoomTempDays
        return when (ripenessState) {
            RipenessState.UNRIPE -> {
                val extra = reference.typicalRipeningDaysRoomTemp ?: (2..4)
                (base.first + extra.first)..(base.last + extra.last)
            }
            RipenessState.RIPENING -> {
                val extra = reference.typicalRipeningDaysRoomTemp ?: (1..2)
                (base.first + max(1, extra.first / 2))..(base.last + max(1, extra.last / 2))
            }
            RipenessState.RIPE -> base
            RipenessState.OVERRIPE -> max(0, base.first / 3)..max(1, min(3, base.last / 2))
            RipenessState.UNDETERMINED -> geminiRange ?: base
        }
    }

    companion object
}
