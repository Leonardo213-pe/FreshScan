# Guía Técnica de Arquitectura — FreshScan AI

## 1. Visión General de la Arquitectura
FreshScan AI sigue **Clean Architecture + MVVM (Model-View-ViewModel)** con **Unidirectional Data Flow (UDF)**:

- **UI Layer (`com.example.ui`)**: Pantallas declarativas en Jetpack Compose Material 3 (`HomeScreen`, `CameraScreen`, `ProcessingScreen`, `ResultsScreen`, `AdvancedAnalysisScreen`, `ConservationGuideScreen`, `AboutScreen`, `SignInScreen`) conectadas a `FreshScanViewModel` mediante `StateFlow<FreshScanUiState>`.
- **Domain Layer (`com.example.domain`)**: Modelos inmutables (`FoodAnalysis`, `FoodProduct`, `RipenessState`, `ShelfLifeEstimate`, `StorageConditions`, `NormalizedBoundingBox`, `VisualFeatureVector`, `VisualForecastRule`), contratos (`FoodAnalysisRepository`) y casos de uso (`AnalyzeFoodUseCase`, `ImproveAnalysisUseCase`).
- **Data Layer (`com.example.data`)**:
  - **Local (`com.example.data.local`)**:
    - `CameraManager`: Integración con CameraX (`Preview`, `ImageCapture` e `ImageAnalysis` con `STRATEGY_KEEP_ONLY_LATEST` para detección en vivo).
    - `LocalYoloDetector`: Motor de detección de regiones candidatas y cuantificación cromática HSV / manchas.
    - `ImageProcessor`: Submuestreo seguro de memoria, recorte por `NormalizedBoundingBox` con margen contextual, hash perceptual dHash de 64 bits y evaluación de calidad lumínica.
    - `LocalVisualForecastStore` y `YoloDatasetManager`: Almacenamiento local silencioso de reglas de pronóstico y muestras etiquetadas YOLO.
  - **Estimator (`com.example.data.estimator`)**:
    - `ShelfLifeEstimator` y `ConservationReferenceSource`: Motor determinista basado en literatura poscosecha (USDA FoodKeeper, UC Davis Postharvest Center, FAO).
  - **Remote (`com.example.data.remote`)**:
    - `FirebaseGeminiDataSource` y `FirebaseGeminiRepository`: Inferencia multimodal estructurada con `gemini-2.5-flash` y `gemini-2.5-flash-lite`.
    - `GeminiJsonValidator`: Validación defensiva de JSON, deduplicación espacial IoU de múltiples productos y filtro de prudencia alimentaria.
    - `VisualForecastRepository`: Sincronización silenciosa en segundo plano con Cloud Firestore (`collective_forecasts`).

## 2. Flujo Multi-Producto e Individualización
1. Cuando la imagen contiene varios alimentos, `GeminiJsonValidator` devuelve la lista depurada en `FoodAnalysis.detectedItems`.
2. `ResultsScreen` dibuja recuadros interactivos sobre la fotografía y chips selectores por unidad (`#1`, `#2`, etc.), además de una miniatura recortada (`cropToYoloBoundingBox`) del elemento activo.
3. Al corregir manualmente el nombre o solicitar el cálculo avanzado de almacenamiento (`AdvancedAnalysisScreen`), `FreshScanViewModel` aplica el recorte y reemplaza únicamente el elemento `selectedProductIndex` mediante `replaceProductAtIndex`, preservando el resto de los productos detectados en la sesión.
