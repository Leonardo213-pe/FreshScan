package com.example.data.local

import com.example.domain.model.FoodAnalysis
import com.example.domain.model.StorageConditions

class VisualHashCache(
    private val maxEntries: Int = 24,
    private val maxHammingDistance: Int = 5
) {
    private data class CacheEntry(
        val perceptualHash: Long,
        val storageKey: String,
        val manualOverrideKey: String,
        val analysis: FoodAnalysis
    )

    private val entries = mutableListOf<CacheEntry>()

    @Synchronized
    fun findMatch(
        perceptualHash: Long,
        storageConditions: StorageConditions?,
        manualProductOverride: String?
    ): FoodAnalysis? {
        val sKey = storageConditions?.toString() ?: "default"
        val mKey = manualProductOverride?.trim()?.lowercase() ?: ""

        return entries.firstOrNull { entry ->
            entry.storageKey == sKey &&
                entry.manualOverrideKey == mKey &&
                hammingDistance(entry.perceptualHash, perceptualHash) <= maxHammingDistance
        }?.analysis
    }

    @Synchronized
    fun put(
        perceptualHash: Long,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        analysis: FoodAnalysis
    ) {
        val sKey = storageConditions?.toString() ?: "default"
        val mKey = manualProductOverride?.trim()?.lowercase() ?: ""

        entries.removeAll { entry ->
            entry.storageKey == sKey &&
                entry.manualOverrideKey == mKey &&
                hammingDistance(entry.perceptualHash, perceptualHash) <= 2
        }
        entries.add(0, CacheEntry(perceptualHash, sKey, mKey, analysis))
        if (entries.size > maxEntries) {
            entries.removeAt(entries.lastIndex)
        }
    }

    companion object {
        fun hammingDistance(hashA: Long, hashB: Long): Int {
            return java.lang.Long.bitCount(hashA xor hashB)
        }
    }
}
