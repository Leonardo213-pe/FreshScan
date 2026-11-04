package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import com.example.domain.model.NormalizedBoundingBox
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImageQualityReport(
    val averageBrightness: Float,
    val edgeContrastScore: Float,
    val isTooDark: Boolean,
    val isTooOverexposed: Boolean,
    val isLowContrastOrBlurry: Boolean,
    val warningMessage: String?
)

class ImageProcessor {

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var inSampleSize = 1
            val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxDim / inSampleSize > CAPTURE_DIMENSION_PX * 1.5f) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val scaled = scaleDownIfNeeded(decoded, CAPTURE_DIMENSION_PX)
            if (scaled !== decoded && !decoded.isRecycled) {
                decoded.recycle()
            }
            scaled
        } catch (_: Exception) {
            null
        }
    }

    fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
            !bitmap.isRecycled &&
            bitmap.width >= MIN_DIMENSION_PX &&
            bitmap.height >= MIN_DIMENSION_PX
    }

    fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int = MAX_DIMENSION_PX): Bitmap {
        return scaleAndRotate(
            bitmap = bitmap,
            maxDimension = maxDimension,
            rotationDegrees = 0,
            recycleSourceIfChanged = false
        )
    }

    fun scaleAndRotate(
        bitmap: Bitmap,
        maxDimension: Int = MAX_DIMENSION_PX,
        rotationDegrees: Int = 0,
        recycleSourceIfChanged: Boolean = false
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val longestSide = max(width, height)
        val scale = if (longestSide > maxDimension) {
            maxDimension.toFloat() / longestSide.toFloat()
        } else {
            1f
        }
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360

        if (scale >= 1f && normalizedRotation == 0) {
            return bitmap
        }

        val matrix = Matrix().apply {
            if (scale < 1f) {
                postScale(scale, scale)
            }
            if (normalizedRotation != 0) {
                postRotate(normalizedRotation.toFloat())
            }
        }

        val transformed = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        if (recycleSourceIfChanged && transformed !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }

    /**
     * Segmentación automática de la región de interés (ROI) de frutas y verduras:
     * Analiza la distribución espacial de saturación cromática y contraste de bordes
     * respecto al fondo periférico para aislar únicamente la región donde se ubican
     * los alimentos antes de enviarla a Gemini Vision.
     */
    fun segmentProduceRegionBounds(bitmap: Bitmap): NormalizedBoundingBox? {
        if (!isValidBitmap(bitmap)) return null
        val gridX = 32
        val gridY = 32
        val width = bitmap.width
        val height = bitmap.height
        val hsv = FloatArray(3)

        // Muestrear el color promedio del borde periférico para distinguir el fondo
        var borderSatSum = 0f
        var borderValSum = 0f
        var borderCount = 0
        for (gy in 0 until gridY) {
            val py = ((gy + 0.5f) * height / gridY).toInt().coerceIn(0, height - 1)
            for (gx in 0 until gridX) {
                if (gx == 0 || gx == gridX - 1 || gy == 0 || gy == gridY - 1) {
                    val px = ((gx + 0.5f) * width / gridX).toInt().coerceIn(0, width - 1)
                    Color.colorToHSV(bitmap.getPixel(px, py), hsv)
                    borderSatSum += hsv[1]
                    borderValSum += hsv[2]
                    borderCount++
                }
            }
        }
        val avgBorderSat = if (borderCount > 0) borderSatSum / borderCount else 0.15f
        val avgBorderVal = if (borderCount > 0) borderValSum / borderCount else 0.50f

        var minFgX = gridX
        var maxFgX = -1
        var minFgY = gridY
        var maxFgY = -1
        var activeCells = 0

        for (gy in 1 until gridY - 1) {
            val py = ((gy + 0.5f) * height / gridY).toInt().coerceIn(0, height - 1)
            for (gx in 1 until gridX - 1) {
                val px = ((gx + 0.5f) * width / gridX).toInt().coerceIn(0, width - 1)
                val pixel = bitmap.getPixel(px, py)
                Color.colorToHSV(pixel, hsv)
                val s = hsv[1]
                val v = hsv[2]

                val satDiff = abs(s - avgBorderSat)
                val valDiff = abs(v - avgBorderVal)
                val isOrganicForeground = (s >= 0.24f && v in 0.12f..0.96f) ||
                    (satDiff >= 0.18f && v in 0.14f..0.95f) ||
                    (valDiff >= 0.25f && s >= 0.16f)

                if (isOrganicForeground) {
                    activeCells++
                    if (gx < minFgX) minFgX = gx
                    if (gx > maxFgX) maxFgX = gx
                    if (gy < minFgY) minFgY = gy
                    if (gy > maxFgY) maxFgY = gy
                }
            }
        }

        if (activeCells < 8 || maxFgX <= minFgX || maxFgY <= minFgY) {
            return null
        }

        val rawBox = NormalizedBoundingBox(
            ymin = (minFgY.toFloat() / gridY).coerceIn(0f, 1f),
            xmin = (minFgX.toFloat() / gridX).coerceIn(0f, 1f),
            ymax = ((maxFgY + 1).toFloat() / gridY).coerceIn(0f, 1f),
            xmax = ((maxFgX + 1).toFloat() / gridX).coerceIn(0f, 1f)
        )

        return computeEffectiveCropBounds(bitmap, rawBox, paddingFraction = 0.12f)
    }

    fun computeEffectiveCropBounds(
        bitmap: Bitmap,
        box: NormalizedBoundingBox,
        paddingFraction: Float = 0.14f
    ): NormalizedBoundingBox? {
        if (!box.isValid || bitmap.width < MIN_DIMENSION_PX || bitmap.height < MIN_DIMENSION_PX) {
            return null
        }
        val boxW = box.xmax - box.xmin
        val boxH = box.ymax - box.ymin
        if (boxW * boxH > 0.84f) return null

        val padX = boxW * paddingFraction
        val padY = boxH * paddingFraction
        val leftNorm = max(0f, box.xmin - padX)
        val topNorm = max(0f, box.ymin - padY)
        val rightNorm = min(1f, box.xmax + padX)
        val bottomNorm = min(1f, box.ymax + padY)

        val cropW = ((rightNorm - leftNorm) * bitmap.width).roundToInt()
        val cropH = ((bottomNorm - topNorm) * bitmap.height).roundToInt()
        if (cropW < MIN_DIMENSION_PX || cropH < MIN_DIMENSION_PX) return null

        return NormalizedBoundingBox(
            ymin = topNorm,
            xmin = leftNorm,
            ymax = bottomNorm,
            xmax = rightNorm
        )
    }

    fun remapBoxFromCropToFullImage(
        innerBox: NormalizedBoundingBox?,
        cropBoundsOnFullImage: NormalizedBoundingBox?
    ): NormalizedBoundingBox? {
        if (innerBox == null || !innerBox.isValid) return cropBoundsOnFullImage
        if (cropBoundsOnFullImage == null || !cropBoundsOnFullImage.isValid) return innerBox

        val cropW = cropBoundsOnFullImage.xmax - cropBoundsOnFullImage.xmin
        val cropH = cropBoundsOnFullImage.ymax - cropBoundsOnFullImage.ymin
        return NormalizedBoundingBox(
            ymin = (cropBoundsOnFullImage.ymin + innerBox.ymin * cropH).coerceIn(0f, 1f),
            xmin = (cropBoundsOnFullImage.xmin + innerBox.xmin * cropW).coerceIn(0f, 1f),
            ymax = (cropBoundsOnFullImage.ymin + innerBox.ymax * cropH).coerceIn(0f, 1f),
            xmax = (cropBoundsOnFullImage.xmin + innerBox.xmax * cropW).coerceIn(0f, 1f)
        )
    }

    fun cropToBoundingBox(
        bitmap: Bitmap,
        box: NormalizedBoundingBox?,
        paddingFraction: Float = 0.14f
    ): Bitmap {
        if (box == null) return bitmap
        val effectiveBounds = computeEffectiveCropBounds(bitmap, box, paddingFraction) ?: return bitmap
        val x = (effectiveBounds.xmin * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (effectiveBounds.ymin * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = ((effectiveBounds.xmax - effectiveBounds.xmin) * bitmap.width).roundToInt()
            .coerceIn(1, bitmap.width - x)
        val h = ((effectiveBounds.ymax - effectiveBounds.ymin) * bitmap.height).roundToInt()
            .coerceIn(1, bitmap.height - y)

        return try {
            Bitmap.createBitmap(bitmap, x, y, w, h)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun optimizeForGemini(
        bitmap: Bitmap,
        regionBox: NormalizedBoundingBox? = null
    ): Bitmap {
        val targetBox = regionBox ?: segmentProduceRegionBounds(bitmap)
        val cropped = if (targetBox != null) {
            cropToBoundingBox(bitmap, targetBox, paddingFraction = 0.0f)
        } else {
            bitmap
        }
        return scaleDownIfNeeded(cropped, MAX_DIMENSION_PX)
    }

    fun compressToWebPBytes(bitmap: Bitmap, quality: Int = WEBP_QUALITY): ByteArray {
        val output = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, quality.coerceIn(50, 95), output)
        return output.toByteArray()
    }

    fun computePerceptualHash(bitmap: Bitmap): Long {
        val tiny = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        try {
            var hash = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val leftPixel = tiny.getPixel(x, y)
                    val rightPixel = tiny.getPixel(x + 1, y)
                    val leftLuma = Color.red(leftPixel) * 299 + Color.green(leftPixel) * 587 + Color.blue(leftPixel) * 114
                    val rightLuma = Color.red(rightPixel) * 299 + Color.green(rightPixel) * 587 + Color.blue(rightPixel) * 114
                    if (leftLuma > rightLuma) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }
            return hash
        } finally {
            if (tiny !== bitmap && !tiny.isRecycled) {
                tiny.recycle()
            }
        }
    }

    fun assessImageQuality(bitmap: Bitmap): ImageQualityReport {
        val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        var totalLuma = 0f
        var edgeAccum = 0f
        var count = 0

        try {
            for (y in 1 until 63) {
                for (x in 1 until 63) {
                    val p = sample.getPixel(x, y)
                    val luma = (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f)
                    val px = sample.getPixel(x + 1, y)
                    val py = sample.getPixel(x, y + 1)
                    val lumaX = (Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f)
                    val lumaY = (Color.red(py) * 0.299f + Color.green(py) * 0.587f + Color.blue(py) * 0.114f)

                    totalLuma += luma
                    edgeAccum += abs(luma - lumaX) + abs(luma - lumaY)
                    count++
                }
            }
        } finally {
            if (sample !== bitmap && !sample.isRecycled) {
                sample.recycle()
            }
        }

        val avgBrightness = if (count > 0) totalLuma / count else 128f
        val edgeScore = if (count > 0) edgeAccum / count else 15f

        val isTooDark = avgBrightness < 38f
        val isTooOverexposed = avgBrightness > 235f
        val isLowContrast = edgeScore < 6.5f

        val warning = when {
            isTooDark -> "La imagen tiene poca iluminación. Acérquese a una fuente de luz o active el flash para mejorar la precisión."
            isTooOverexposed -> "La imagen presenta exceso de brillo o reflejos intensos que pueden ocultar manchas sutiles."
            isLowContrast -> "La imagen parece ligeramente desenfocada. Toque el alimento en pantalla antes de capturar."
            else -> null
        }

        return ImageQualityReport(
            averageBrightness = avgBrightness,
            edgeContrastScore = edgeScore,
            isTooDark = isTooDark,
            isTooOverexposed = isTooOverexposed,
            isLowContrastOrBlurry = isLowContrast,
            warningMessage = warning
        )
    }

    fun rotateBitmapIfRequired(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        const val MAX_DIMENSION_PX = 512
        const val CAPTURE_DIMENSION_PX = 1024
        const val MIN_DIMENSION_PX = 48
        const val WEBP_QUALITY = 80
    }
}
