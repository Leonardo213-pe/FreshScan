# Guía Técnica de Arquitectura — FreshScan AI

## 1. Visión General de la Arquitectura
FreshScan AI sigue **Clean Architecture + MVVM (Model-View-ViewModel)** con **Unidirectional Data Flow (UDF)**:

- **UI Layer (`com.example.ui`)**: Pantallas declarativas en Jetpack Compose Material 3 (`HomeScreen`, `CameraScreen`, `ProcessingScreen`, `ResultsScreen`, `AdvancedAnalysisScreen`, `ConservationGuideScreen`, `AboutScreen`, `SignInScreen`) conectadas a `FreshScanViewModel` mediante `StateFlow<FreshScanUiState>`.
- **Domain Layer (`com.example.domain`)**: Modelos inmutables (`FoodAnalysis`, `FoodProduct`, `RipenessState`, `ShelfLifeEstimate`, `StorageConditions`, `NormalizedBoundingBox`, `VisualFeatureVector`, `VisualForecastRule`), contratos (`FoodAnalysisRepository`) y casos de uso (`AnalyzeFoodUseCase`, `ImproveAnalysisUseCase`).
- **Data Layer (`com.example.data`)**:
  - **Local (`com.example.data.local`)**:
    - `CameraManager`: Integración con CameraX (`Preview` e `ImageCapture`) con control de instancia de `PreviewView` para re-vinculación instantánea entre escaneos consecutivos.
    - `ImageProcessor`: Segmentación automática de la región de frutas/verduras (`segmentProduceRegionBounds`) para recortar el fondo antes de enviar a Gemini, submuestreo seguro de memoria, recorte por `NormalizedBoundingBox` (`cropToBoundingBox`), hash perceptual dHash de 64 bits y evaluación de calidad lumínica.
    - `AdaptiveVisualLearner`: Motor de aprendizaje evolutivo que inicia vacío (sin heurísticas estáticas falsas), extrae vectores cromáticos HSV y textura de manchas, aprende de cada análisis validado por Gemini y de cada corrección del usuario, e inyecta contexto evolutivo (`buildLearnedPromptHintForGemini`) en futuros escaneos.
    - `LocalVisualForecastStore` y `LearnedDatasetManager`: Almacenamiento local silencioso de reglas de pronóstico aprendidas y muestras etiquetadas por aprendizaje evolutivo.
  - **Estimator (`com.example.data.estimator`)**:
    - `ShelfLifeEstimator` y `ConservationReferenceSource`: Motor determinista basado en literatura poscosecha (USDA FoodKeeper, UC Davis Postharvest Center, FAO), incluyendo frutas tropicales y climatéricas como *Maracuyá / Granadilla*.
  - **Remote (`com.example.data.remote`)**:
    - `FirebaseGeminiDataSource` y `FirebaseGeminiRepository`: Inferencia multimodal estructurada con `gemini-2.5-flash` y `gemini-2.5-flash-lite` enriquecida con la memoria evolutiva aprendida y el recorte inteligente de región de interés.
    - `GeminiJsonValidator`: Validación defensiva de JSON, rechazo estricto de objetos no alimentarios (`identificacion_confiable = false`), deduplicación espacial IoU de múltiples productos y filtro de prudencia alimentaria.
    - `VisualForecastRepository`: Sincronización silenciosa en segundo plano con Cloud Firestore (`collective_forecasts`).

## 2. Flujo de Segmentación, Aprendizaje Evolutivo e Individualización Multi-Producto
1. Al capturar o elegir una fotografía, `FirebaseGeminiRepository` utiliza `ImageProcessor.segmentProduceRegionBounds(bitmap)` para aislar y recortar la zona que contiene las frutas o verduras (en lugar de enviar todo el fondo irrelevante) y consulta a `AdaptiveVisualLearner` para recuperar reglas aprendidas de escaneos previos similares.
2. `FirebaseGeminiDataSource` envía el recorte optimizado junto con el contexto de memoria evolutiva a Gemini. Al recibir la respuesta, `GeminiJsonValidator` valida que realmente exista una fruta o verdura y `FirebaseGeminiRepository` remapea las coordenadas de cada producto de vuelta a la fotografía original completa, registrando el nuevo aprendizaje en `AdaptiveVisualLearner`, `LocalVisualForecastStore`, `LearnedDatasetManager` y `VisualForecastRepository` (Cloud Firestore).
3. `ResultsScreen` dibuja recuadros interactivos sobre la fotografía y chips selectores por unidad (`#1`, `#2`, etc.), además de una miniatura recortada (`cropToBoundingBox`) del elemento activo.
4. Al corregir manualmente el nombre del alimento (incluyendo cualquier fruta abierta como *Maracuyá*) y/o su estado de madurez, o al solicitar el cálculo avanzado de almacenamiento (`AdvancedAnalysisScreen`), `FreshScanViewModel` actualiza inmediatamente el producto seleccionado mediante `replaceProductAtIndex`, recalcula los días de vida útil y entrena tanto la memoria local como la nube con la corrección del usuario.
