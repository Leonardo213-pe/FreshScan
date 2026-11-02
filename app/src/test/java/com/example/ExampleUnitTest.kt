package com.example

import com.example.data.estimator.ShelfLifeEstimator
import com.example.data.local.LocalYoloDetector
import com.example.data.remote.GeminiJsonValidator
import com.example.domain.model.FoodProduct
import com.example.domain.model.FutureStoragePlan
import com.example.domain.model.PreparationState
import com.example.domain.model.RipenessState
import com.example.domain.model.StorageConditions
import com.example.domain.model.StorageEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val estimator = ShelfLifeEstimator()

    @Test
    fun productCatalog_matchesAccentedAndColloquialSpanishNames() {
        val banana = FoodProduct.findMatchingProduct("Plátano maduro")
        assertNotNull(banana)
        assertEquals("banana", banana?.id)

        val avocado = FoodProduct.findMatchingProduct("Palta Hass")
        assertNotNull(avocado)
        assertEquals("avocado", avocado?.id)

        val strawberry = FoodProduct.findMatchingProduct("Frutillas frescas")
        assertNotNull(strawberry)
        assertEquals("strawberry", strawberry?.id)
    }

    @Test
    fun shelfLifeEstimator_returnsNullEstimateWhenIdentificationIsUnreliable() {
        val result = estimator.estimate(
            matchedProduct = null,
            isReliableIdentification = false,
            multipleOrAmbiguousItems = false,
            ripenessState = RipenessState.UNDETERMINED,
            hasVisibleDeterioration = false,
            deteriorationSigns = emptyList(),
            geminiShelfLifeMin = 3,
            geminiShelfLifeMax = 5,
            geminiRipeningDaysMin = null,
            geminiRipeningDaysMax = null,
            geminiLimitations = emptyList(),
            storageConditions = null
        )

        assertFalse(result.isValidatedEstimate)
        assertNull(result.remainingShelfLifeDaysMin)
        assertNull(result.remainingShelfLifeDaysMax)
    }

    @Test
    fun shelfLifeEstimator_adjustsDownwardWhenCutOrPeeled() {
        val apple = FoodProduct.findMatchingProduct("Manzana")
        val wholeEstimate = estimator.estimate(
            matchedProduct = apple,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = RipenessState.RIPE,
            hasVisibleDeterioration = false,
            deteriorationSigns = emptyList(),
            geminiShelfLifeMin = null,
            geminiShelfLifeMax = null,
            geminiRipeningDaysMin = null,
            geminiRipeningDaysMax = null,
            geminiLimitations = emptyList(),
            storageConditions = StorageConditions(
                storageEnvironment = StorageEnvironment.REFRIGERATED,
                preparationState = PreparationState.WHOLE,
                futureStoragePlan = FutureStoragePlan.REFRIGERATE_NOW
            )
        )

        val cutEstimate = estimator.estimate(
            matchedProduct = apple,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = RipenessState.RIPE,
            hasVisibleDeterioration = false,
            deteriorationSigns = emptyList(),
            geminiShelfLifeMin = null,
            geminiShelfLifeMax = null,
            geminiRipeningDaysMin = null,
            geminiRipeningDaysMax = null,
            geminiLimitations = emptyList(),
            storageConditions = StorageConditions(
                storageEnvironment = StorageEnvironment.REFRIGERATED,
                preparationState = PreparationState.CUT_OR_PEELED,
                futureStoragePlan = FutureStoragePlan.REFRIGERATE_NOW
            )
        )

        assertTrue((cutEstimate.remainingShelfLifeDaysMax ?: 99) < (wholeEstimate.remainingShelfLifeDaysMax ?: 0))
    }

    @Test
    fun geminiJsonValidator_parsesMultiProductResponseAndDeduplicatesOverlappingBoxes() {
        val sampleJson = """
            {
              "producto": "Plátano / Banano",
              "identificacion_confiable": true,
              "multiples_productos_o_ambiguo": true,
              "madurez": "maduro",
              "observaciones_visuales": ["Superficie amarilla"],
              "posible_deterioro": false,
              "senales_deterioro": [],
              "vida_util_min": 2,
              "vida_util_max": 4,
              "dias_hasta_madurez_min": 0,
              "dias_hasta_madurez_max": 0,
              "estimacion_validada": true,
              "recomendaciones": ["Consumir pronto"],
              "limitaciones": [],
              "box_ymin": 100,
              "box_xmin": 100,
              "box_ymax": 500,
              "box_xmax": 450,
              "productos_detectados": [
                {
                  "producto": "Plátano / Banano",
                  "identificacion_confiable": true,
                  "madurez": "maduro",
                  "observaciones_visuales": ["Amarillo uniforme"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 2,
                  "vida_util_max": 4,
                  "recomendaciones": ["Ideal para hoy"],
                  "box_ymin": 100,
                  "box_xmin": 100,
                  "box_ymax": 500,
                  "box_xmax": 450
                },
                {
                  "producto": "Manzana",
                  "identificacion_confiable": true,
                  "madurez": "inmaduro",
                  "observaciones_visuales": ["Rojo firme"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 7,
                  "vida_util_max": 14,
                  "recomendaciones": ["Refrigerar"],
                  "box_ymin": 150,
                  "box_xmin": 550,
                  "box_ymax": 600,
                  "box_xmax": 900
                }
              ]
            }
        """.trimIndent()

        val dto = GeminiJsonValidator.parseJson(sampleJson)
        val analysis = GeminiJsonValidator.validateAndMapToDomain(dto)

        assertTrue(analysis.hasMultipleDetectedProducts)
        assertEquals(2, analysis.allProducts.size)
        assertEquals("Plátano / Banano", analysis.allProducts[0].displayProductName)
        assertEquals("Manzana", analysis.allProducts[1].displayProductName)
    }

    @Test
    fun localYoloDetector_isInstantiable() {
        val detector = LocalYoloDetector()
        assertNotNull(detector)
    }
}
