package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.CloudConsentStore
import com.example.data.remote.DemoFoodAnalysisRepository
import com.example.domain.model.StorageConditions
import com.example.domain.model.StorageEnvironment
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.theme.FreshScanTheme
import com.example.ui.viewmodel.FreshScanViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeConsentStore(private var accepted: Boolean = true) : CloudConsentStore {
        override fun hasAcceptedCloudProcessing(): Boolean = accepted
        override fun setAcceptedCloudProcessing(accepted: Boolean) {
            this.accepted = accepted
        }
    }

    @Test
    fun homeScreen_rendersCleanMinimalistInterfaceWithoutTechnicalClutter() {
        composeTestRule.setContent {
            FreshScanTheme {
                HomeScreen(
                    onStartCameraScan = {},
                    onOpenConservationGuide = {},
                    onOpenAbout = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("scan_food_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_guide_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_ai_info_button").assertIsDisplayed()
    }

    @Test
    fun multiProductSelection_allowsSwitchingAndIndividualCorrectionInViewModelAndResultsScreen() {
        val multiJson = """
            {
              "producto": "Plátano / Banano",
              "identificacion_confiable": true,
              "multiples_productos_o_ambiguo": true,
              "madurez": "maduro",
              "observaciones_visuales": ["Piel amarilla"],
              "posible_deterioro": false,
              "senales_deterioro": [],
              "vida_util_min": 2,
              "vida_util_max": 4,
              "estimacion_validada": true,
              "recomendaciones": ["Consumir pronto"],
              "limitaciones": [],
              "box_ymin": 120,
              "box_xmin": 80,
              "box_ymax": 720,
              "box_xmax": 440,
              "productos_detectados": [
                {
                  "producto": "Plátano / Banano",
                  "identificacion_confiable": true,
                  "madurez": "maduro",
                  "observaciones_visuales": ["Piel amarilla uniforme"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 2,
                  "vida_util_max": 4,
                  "recomendaciones": ["Consumir en 2 a 4 días"],
                  "box_ymin": 120,
                  "box_xmin": 80,
                  "box_ymax": 720,
                  "box_xmax": 440
                },
                {
                  "producto": "Tomate",
                  "identificacion_confiable": true,
                  "madurez": "en_maduracion",
                  "observaciones_visuales": ["Tono pintón rojizo"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 4,
                  "vida_util_max": 6,
                  "recomendaciones": ["Mantener fuera del frío hasta que madure"],
                  "box_ymin": 200,
                  "box_xmin": 520,
                  "box_ymax": 780,
                  "box_xmax": 880
                }
              ]
            }
        """.trimIndent()

        val repo = DemoFoodAnalysisRepository(simulatedJsonResponse = multiJson)
        val viewModel = FreshScanViewModel(
            repository = repo,
            consentStore = FakeConsentStore(accepted = true)
        )

        val testBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        viewModel.onPhotoCapturedOrPicked(testBitmap)
        viewModel.requestAutomaticAnalysis()

        composeTestRule.waitForIdle()
        val initialAnalysis = viewModel.uiState.value.currentAnalysis
        requireNotNull(initialAnalysis)
        assertEquals(2, initialAnalysis.allProducts.size)

        viewModel.selectProduct(1)
        assertEquals(1, viewModel.uiState.value.selectedProductIndex)

        viewModel.correctRecognizedProduct(
            newProductName = "Maracuyá / Granadilla",
            newRipenessState = com.example.domain.model.RipenessState.RIPE,
            reanalyzeWithGemini = false,
            targetProductIndex = 1
        )

        val updatedAnalysis = viewModel.uiState.value.currentAnalysis!!
        assertEquals("Plátano / Banano", updatedAnalysis.allProducts[0].displayProductName)
        assertEquals("Maracuyá / Granadilla", updatedAnalysis.allProducts[1].displayProductName)
        assertEquals(com.example.domain.model.RipenessState.RIPE, updatedAnalysis.allProducts[1].ripenessState)

        composeTestRule.setContent {
            FreshScanTheme {
                ResultsScreen(
                    bitmap = testBitmap,
                    analysis = updatedAnalysis,
                    hasPreviousAutomaticResult = false,
                    onImproveAnalysisClick = {},
                    onRestorePreviousResult = {},
                    onCorrectProduct = { _, _, _ -> },
                    onNewScanClick = {},
                    selectedProductIndex = 1,
                    onSelectProductIndex = { viewModel.selectProduct(it) }
                )
            }
        }

        composeTestRule.onNodeWithTag("detected_products_selector_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_product_crop_preview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_selector_chip_1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("product_selector_chip_0").performClick()
        assertEquals(0, viewModel.uiState.value.selectedProductIndex)
    }

    @Test
    fun advancedAnalysis_updatesOnlySelectedProductWhenMultipleItemsExist() {
        val multiJson = """
            {
              "producto": "Plátano / Banano",
              "identificacion_confiable": true,
              "multiples_productos_o_ambiguo": true,
              "madurez": "maduro",
              "observaciones_visuales": ["Piel amarilla"],
              "posible_deterioro": false,
              "senales_deterioro": [],
              "vida_util_min": 2,
              "vida_util_max": 4,
              "estimacion_validada": true,
              "recomendaciones": ["Consumir pronto"],
              "limitaciones": [],
              "box_ymin": 100,
              "box_xmin": 100,
              "box_ymax": 700,
              "box_xmax": 450,
              "productos_detectados": [
                {
                  "producto": "Plátano / Banano",
                  "identificacion_confiable": true,
                  "madurez": "maduro",
                  "observaciones_visuales": ["Piel amarilla"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 2,
                  "vida_util_max": 4,
                  "recomendaciones": ["Consumir pronto"],
                  "box_ymin": 100,
                  "box_xmin": 100,
                  "box_ymax": 700,
                  "box_xmax": 450
                },
                {
                  "producto": "Aguacate / Palta",
                  "identificacion_confiable": true,
                  "madurez": "maduro",
                  "observaciones_visuales": ["Piel oscura"],
                  "posible_deterioro": false,
                  "senales_deterioro": [],
                  "vida_util_min": 2,
                  "vida_util_max": 3,
                  "recomendaciones": ["Refrigerar"],
                  "box_ymin": 150,
                  "box_xmin": 500,
                  "box_ymax": 750,
                  "box_xmax": 880
                }
              ]
            }
        """.trimIndent()

        val repo = DemoFoodAnalysisRepository(simulatedJsonResponse = multiJson)
        val viewModel = FreshScanViewModel(
            repository = repo,
            consentStore = FakeConsentStore(accepted = true)
        )

        val testBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        viewModel.onPhotoCapturedOrPicked(testBitmap)
        viewModel.requestAutomaticAnalysis()
        composeTestRule.waitForIdle()

        viewModel.selectProduct(1)
        viewModel.requestAdvancedAnalysis(
            StorageConditions(
                approximateTemperatureCelsius = 4f,
                storageEnvironment = StorageEnvironment.REFRIGERATED
            )
        )
        composeTestRule.waitForIdle()

        val root = viewModel.uiState.value.currentAnalysis!!
        assertEquals(false, root.allProducts[0].isAdvancedAnalysis)
        assertEquals(true, root.allProducts[1].isAdvancedAnalysis)
    }
}
