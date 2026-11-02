package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.FoodProduct
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.RipenessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class YoloDatasetManager(private val context: Context) {

    fun getDatasetRootDir(): File {
        val root = File(context.filesDir, "yolo_dataset")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun getSavedSampleCount(): Int {
        val labelsDir = File(getDatasetRootDir(), "labels")
        if (!labelsDir.exists()) return 0
        return labelsDir.listFiles { file -> file.extension == "txt" }?.size ?: 0
    }

    suspend fun saveLabeledSample(
        bitmap: Bitmap,
        analysis: FoodAnalysis
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val rootDir = getDatasetRootDir()
            val imagesDir = File(rootDir, "images").apply { if (!exists()) mkdirs() }
            val labelsDir = File(rootDir, "labels").apply { if (!exists()) mkdirs() }
            val metaDir = File(rootDir, "metadata").apply { if (!exists()) mkdirs() }

            ensureClassesFile(rootDir)

            val sampleId = "sample_${System.currentTimeMillis()}_${analysis.sessionId.take(6)}"
            val imageFile = File(imagesDir, "$sampleId.jpg")
            val labelFile = File(labelsDir, "$sampleId.txt")
            val metaFile = File(metaDir, "$sampleId.json")

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val itemsToExport = analysis.allProducts
            val yoloLines = itemsToExport.map { item ->
                val classId = resolveClassId(
                    productId = item.matchedCatalogProduct?.id ?: "other",
                    ripenessState = item.ripenessState.name
                )
                val box = item.boundingBox ?: NormalizedBoundingBox(0.15f, 0.15f, 0.85f, 0.85f)
                val xCenter = ((box.xmin + box.xmax) / 2f).coerceIn(0f, 1f)
                val yCenter = ((box.ymin + box.ymax) / 2f).coerceIn(0f, 1f)
                val width = (box.xmax - box.xmin).coerceIn(0.02f, 1f)
                val height = (box.ymax - box.ymin).coerceIn(0.02f, 1f)
                String.format(
                    Locale.US,
                    "%d %.6f %.6f %.6f %.6f",
                    classId,
                    xCenter,
                    yCenter,
                    width,
                    height
                )
            }
            labelFile.writeText(yoloLines.joinToString("\n"))

            val metadataJson = JSONObject().apply {
                put("sample_id", sampleId)
                put("timestamp_ms", System.currentTimeMillis())
                put("primary_product", analysis.displayProductName)
                put("ripeness_state", analysis.ripenessState.name)
                put("has_visible_deterioration", analysis.hasVisibleDeterioration)
                put("detected_items_count", itemsToExport.size)
                put("engine_source", analysis.analysisEngineSource)
            }
            metaFile.writeText(metadataJson.toString(2))

            Result.success(labelFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureClassesFile(rootDir: File) {
        val classesFile = File(rootDir, "classes.txt")
        if (!classesFile.exists()) {
            classesFile.writeText(buildClassNames().joinToString("\n"))
        }
    }

    private fun buildClassNames(): List<String> {
        val list = mutableListOf<String>()
        val states = listOf(
            RipenessState.UNRIPE.name,
            RipenessState.RIPENING.name,
            RipenessState.RIPE.name,
            RipenessState.OVERRIPE.name
        )
        for (product in FoodProduct.SUPPORTED_CATALOG) {
            for (state in states) {
                list.add("${product.id}_${state.lowercase(Locale.US)}")
            }
        }
        list.add("other_food")
        return list
    }

    private fun resolveClassId(productId: String, ripenessState: String): Int {
        val classes = buildClassNames()
        val key = "${productId.lowercase(Locale.US)}_${ripenessState.lowercase(Locale.US)}"
        val idx = classes.indexOf(key)
        return if (idx >= 0) idx else classes.lastIndex
    }
}
