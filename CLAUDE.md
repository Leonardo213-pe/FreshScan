# Guía Técnica de Arquitectura — FreshScan AI (v2.3)

## 1. Visión General de la Arquitectura
FreshScan AI sigue **Clean Architecture + MVVM (Model-View-ViewModel)** con **Unidirectional Data Flow (UDF)**:

- **UI Layer (`com.example.ui`)**: Pantallas declarativas en Jetpack Compose Material 3 (`HomeScreen`, `CameraScreen`, `ProcessingScreen`, `ResultsScreen`, `AdvancedAnalysisScreen`, `ConservationGuideScreen`, `AboutScreen`, `SignInScreen`) conectadas a `FreshScanViewModel` mediante `StateFlow<FreshScanUiState>`.
- **Domain Layer (`com.example.domain`)**: Modelos inmutables (`FoodAnalysis`, `FoodProduct`, `RipenessState`, `ShelfLifeEstimate`, `StorageConditions`, `NormalizedBoundingBox`, `VisualFeatureVector`, `VisualForecastRule`), contratos (`FoodAnalysisRepository`) y casos de uso (`AnalyzeFoodUseCase`, `ImproveAnalysisUseCase`).
- **Data Layer (`com.example.data`)**:
  - **Local (`com.example.data.local`)**:
    - `CameraManager`: Integración con CameraX (`Preview` e `ImageCapture`) con control de instancia de `PreviewView` para re-vinculación instantánea entre escaneos consecutivos.
    - `ImageProcessor`: Segmentación automática de la región de frutas/verduras (`segmentProduceRegionBounds`) para recortar el rectángulo de interés (ROI) antes de enviar a Gemini, reescalado optimizado a `640 px` (`optimizeForGemini`) para baja latencia, recorte rectangular individual por `NormalizedBoundingBox` con margen del `8%` (`cropToBoundingBox`), hash perceptual dHash de 64 bits y evaluación de calidad lumínica.
    - `AdaptiveVisualLearner`: Motor de aprendizaje evolutivo que inicia vacío (sin heurísticas estáticas falsas), extrae vectores cromáticos HSV y textura de manchas, aprende de cada análisis validado por Gemini y de cada corrección del usuario, e inyecta contexto evolutivo (`buildLearnedPromptHintForGemini`) en futuros escaneos.
    - `LocalVisualForecastStore` y `LearnedDatasetManager`: Almacenamiento local silencioso de reglas de pronóstico aprendidas y muestras etiquetadas por aprendizaje evolutivo.
  - **Estimator (`com.example.data.estimator`)**:
    - `ShelfLifeEstimator` y `ConservationReferenceSource`: Motor determinista basado en literatura poscosecha (USDA FoodKeeper, UC Davis Postharvest Center, FAO), incluyendo frutas tropicales como *Maracuyá / Granadilla* (`passion_fruit`) y hortalizas como *Pepino / Pepinillo* (`cucumber`).
  - **Remote (`com.example.data.remote`)**:
    - `FirebaseGeminiDataSource` y `FirebaseGeminiRepository`: Inferencia multimodal estructurada de baja latencia mediante cadena de prioridad **`gemini-3.8-flash`** (`14s` timeout) -> **`gemini-3.5-flash-lite`** (`10s` timeout) con cancelación asíncrona explícita (`networkCallScope.async` + `withTimeout`), enriquecida con la memoria evolutiva aprendida y el recorte inteligente de región de interés.
    - `GeminiJsonValidator`: Validación defensiva de JSON, rechazo estricto de objetos no alimentarios (`identificacion_confiable = false`), deduplicación espacial IoU de múltiples productos y filtro de prudencia alimentaria.
    - `VisualForecastRepository`: Sincronización silenciosa en segundo plano con Cloud Firestore (`collective_forecasts`).

## 2. Flujo de Segmentación Rectangular, Aprendizaje Evolutivo e Individualización Multi-Producto
1. Al capturar o elegir una fotografía, `FirebaseGeminiRepository` utiliza `ImageProcessor.segmentProduceRegionBounds(bitmap)` para aislar el rectángulo que contiene las frutas o verduras (descartando bordes y fondos vacíos), lo optimiza a un máximo de `640 px` mediante `optimizeForGemini` y consulta a `AdaptiveVisualLearner` para recuperar reglas aprendidas de escaneos previos similares.
2. `FirebaseGeminiDataSource` envía el recorte optimizado junto con el contexto de memoria evolutiva a `gemini-3.8-flash` (o `gemini-3.5-flash-lite` en fallback automático). Al recibir la respuesta JSON, `GeminiJsonValidator` valida que realmente exista una fruta o verdura (`identificacion_confiable = true`) y `FirebaseGeminiRepository` remapea las coordenadas `[ymin, xmin, ymax, xmax]` de cada producto de vuelta a la fotografía original completa, registrando el nuevo aprendizaje en `AdaptiveVisualLearner`, `LocalVisualForecastStore`, `LearnedDatasetManager` y `VisualForecastRepository` (Cloud Firestore).
3. `ResultsScreen` dibuja recuadros delimitadores interactivos sobre la fotografía y chips selectores por unidad (`#1`, `#2`, etc.), además de una miniatura recortada rectangular (`cropToBoundingBox`) del ejemplar activo.
4. Al corregir manualmente el nombre del alimento (incluyendo chips rápidos como *Pepino / Pepinillo*, *Maracuyá / Granadilla* o texto libre) y/o su estado de madurez, o al solicitar el cálculo avanzado de almacenamiento (`AdvancedAnalysisScreen`), `FreshScanViewModel` actualiza inmediatamente el producto seleccionado mediante `replaceProductAtIndex`, recalcula los días de vida útil y entrena tanto la memoria local como la nube con la corrección del usuario.
5. En cada compilación (`gradle assembleDebug`), el hook de `app/build.gradle.kts` limpia APKs anteriores en la raíz y genera automáticamente `FreshScanAI-v<versionName>.apk` (actualmente `FreshScanAI-v2.3.apk`).
