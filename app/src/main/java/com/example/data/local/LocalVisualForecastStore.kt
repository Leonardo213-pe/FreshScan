package com.example.data.local

import android.content.Context
import com.example.domain.model.FoodProduct
import com.example.domain.model.VisualFeatureVector
import com.example.domain.model.VisualForecastRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalVisualForecastStore(context: Context? = null) {
    private val storageFile: File? = context?.let {
        File(it.filesDir, "collective_visual_forecasts_v1.json")
    }
    private val cachedRules = mutableListOf<VisualForecastRule>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun getAllRules(): List<VisualForecastRule> = cachedRules.toList()

    @Synchronized
    fun getRulesCount(): Int = cachedRules.size

    @Synchronized
    fun replaceAllFromCloud(cloudRules: List<VisualForecastRule>) {
        cachedRules.clear()
        cachedRules.addAll(cloudRules)
        persistToDisk()
    }

    @Synchronized
    fun upsertLocalRule(rule: VisualForecastRule) {
        val existingIdx = cachedRules.indexOfFirst {
            it.id == rule.id || (it.productKey == rule.productKey &&
                it.ripenessState == rule.ripenessState &&
                rangesOverlap(it, rule))
        }
        if (existingIdx >= 0) {
            val prev = cachedRules[existingIdx]
            cachedRules[existingIdx] = rule.copy(
                id = prev.id.ifBlank { rule.id },
                confirmations = prev.confirmations + 1L
            )
        } else {
            cachedRules.add(0, rule)
        }
        persistToDisk()
    }

    @Synchronized
    fun findBestMatchingRule(
        features: VisualFeatureVector,
        hintProductName: String? = null
    ): VisualForecastRule? {
        if (cachedRules.isEmpty()) return null
        val matchedProduct = FoodProduct.findMatchingProduct(hintProductName)
        val expectedProductKey = matchedProduct?.id

        val candidates = cachedRules.filter { rule ->
            val productMatches = expectedProductKey == null ||
                rule.productKey.equals(expectedProductKey, ignoreCase = true) ||
                rule.productName.contains(hintProductName ?: "", ignoreCase = true)
            productMatches && features.matchesRange(rule)
        }

        return candidates.minByOrNull { rule ->
            features.distanceToRuleCenter(rule) - (rule.confirmations * 15L)
        }
    }

    private fun rangesOverlap(a: VisualForecastRule, b: VisualForecastRule): Boolean {
        return a.redMinPermille <= b.redMaxPermille && b.redMinPermille <= a.redMaxPermille &&
            a.yellowMinPermille <= b.yellowMaxPermille && b.yellowMinPermille <= a.yellowMaxPermille &&
            a.greenMinPermille <= b.greenMaxPermille && b.greenMinPermille <= a.greenMaxPermille &&
            a.spotMinPermille <= b.spotMaxPermille && b.spotMinPermille <= a.spotMaxPermille
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            val array = JSONArray(raw)
            cachedRules.clear()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                cachedRules.add(fromJson(obj))
            }
        } catch (_: Exception) {
        }
    }

    private fun persistToDisk() {
        val file = storageFile ?: return
        try {
            val array = JSONArray()
            cachedRules.take(250).forEach { rule ->
                array.put(toJson(rule))
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
        }
    }

    private fun toJson(rule: VisualForecastRule): JSONObject {
        return JSONObject().apply {
            put("id", rule.id)
            put("catalogScope", rule.catalogScope)
            put("userId", rule.userId)
            put("productKey", rule.productKey)
            put("productName", rule.productName)
            put("ripenessState", rule.ripenessState)
            put("hasVisibleDeterioration", rule.hasVisibleDeterioration)
            put("remainingShelfLifeDaysMin", rule.remainingShelfLifeDaysMin)
            put("remainingShelfLifeDaysMax", rule.remainingShelfLifeDaysMax)
            put("daysToRipenMin", rule.daysToRipenMin)
            put("daysToRipenMax", rule.daysToRipenMax)
            put("visualObservations", JSONArray(rule.visualObservations))
            put("deteriorationSigns", JSONArray(rule.deteriorationSigns))
            put("recommendations", JSONArray(rule.recommendations))
            put("redMinPermille", rule.redMinPermille)
            put("redMaxPermille", rule.redMaxPermille)
            put("yellowMinPermille", rule.yellowMinPermille)
            put("yellowMaxPermille", rule.yellowMaxPermille)
            put("greenMinPermille", rule.greenMinPermille)
            put("greenMaxPermille", rule.greenMaxPermille)
            put("orangeMinPermille", rule.orangeMinPermille)
            put("orangeMaxPermille", rule.orangeMaxPermille)
            put("purpleMinPermille", rule.purpleMinPermille)
            put("purpleMaxPermille", rule.purpleMaxPermille)
            put("spotMinPermille", rule.spotMinPermille)
            put("spotMaxPermille", rule.spotMaxPermille)
            put("aspectRatioMinPermille", rule.aspectRatioMinPermille)
            put("aspectRatioMaxPermille", rule.aspectRatioMaxPermille)
            put("confirmations", rule.confirmations)
        }
    }

    private fun fromJson(obj: JSONObject): VisualForecastRule {
        fun toStringList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i).trim()
                if (v.isNotEmpty()) list.add(v)
            }
            return list
        }

        return VisualForecastRule(
            id = obj.optString("id"),
            catalogScope = obj.optString("catalogScope", VisualForecastRule.CATALOG_SCOPE),
            userId = obj.optString("userId"),
            productKey = obj.optString("productKey"),
            productName = obj.optString("productName"),
            ripenessState = obj.optString("ripenessState"),
            hasVisibleDeterioration = obj.optBoolean("hasVisibleDeterioration", false),
            remainingShelfLifeDaysMin = obj.optLong("remainingShelfLifeDaysMin", 1L),
            remainingShelfLifeDaysMax = obj.optLong("remainingShelfLifeDaysMax", 3L),
            daysToRipenMin = obj.optLong("daysToRipenMin", 0L),
            daysToRipenMax = obj.optLong("daysToRipenMax", 0L),
            visualObservations = toStringList(obj.optJSONArray("visualObservations")),
            deteriorationSigns = toStringList(obj.optJSONArray("deteriorationSigns")),
            recommendations = toStringList(obj.optJSONArray("recommendations")),
            redMinPermille = obj.optLong("redMinPermille", 0L),
            redMaxPermille = obj.optLong("redMaxPermille", 1000L),
            yellowMinPermille = obj.optLong("yellowMinPermille", 0L),
            yellowMaxPermille = obj.optLong("yellowMaxPermille", 1000L),
            greenMinPermille = obj.optLong("greenMinPermille", 0L),
            greenMaxPermille = obj.optLong("greenMaxPermille", 1000L),
            orangeMinPermille = obj.optLong("orangeMinPermille", 0L),
            orangeMaxPermille = obj.optLong("orangeMaxPermille", 1000L),
            purpleMinPermille = obj.optLong("purpleMinPermille", 0L),
            purpleMaxPermille = obj.optLong("purpleMaxPermille", 1000L),
            spotMinPermille = obj.optLong("spotMinPermille", 0L),
            spotMaxPermille = obj.optLong("spotMaxPermille", 1000L),
            aspectRatioMinPermille = obj.optLong("aspectRatioMinPermille", 200L),
            aspectRatioMaxPermille = obj.optLong("aspectRatioMaxPermille", 3500L),
            confirmations = obj.optLong("confirmations", 1L)
        )
    }
}
