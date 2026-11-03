# FreshScan AI

Aplicación móvil nativa para Android desarrollada en **Kotlin** y **Jetpack Compose** que estima el estado de madurez y la vida útil restante de frutas y verduras frescas a partir de fotografías, brindando recomendaciones prácticas de conservación para reducir el desperdicio alimentario doméstico.

---

## Características Principales (Versión 2.0)

- **Escaneo Visual en Tiempo Real**: Visor con **CameraX** (`Preview` + `ImageAnalysis` con `STRATEGY_KEEP_ONLY_LATEST`) que muestra en tiempo real un recuadro dinámico con resorte (`spring()`) y color semántico según el estado de madurez detectado antes de tomar la fotografía, además de selector nativo de galería (`PickVisualMedia`).
- **Arquitectura Híbrida Silenciosa**:
  1. **Detección en Dispositivo (YOLOv8 Local)**: Segmentación en cuadrícula espacial y extracción de vectores cromáticos HSV y detección de manchas para respuesta inmediata (~20 ms).
  2. **Análisis Multimodal Profundo (Gemini Vision)**: Evaluación detallada de fisiología poscosecha, signos de deterioro e individualización de múltiples alimentos mediante **Firebase AI Logic**.
  3. **Aprendizaje Colectivo en Segundo Plano (Cloud Firestore)**: Las reglas de pronóstico por rangos de color (`collective_forecasts`) y las muestras etiquetadas en formato YOLO se registran y sincronizan de forma 100% automática y transparente en segundo plano, manteniendo la interfaz limpia para el usuario final.
- **Detección e Individualización Multi-Producto**:
  - Identifica múltiples frutas o verduras en una misma fotografía (o varias unidades con distinto grado de madurez).
  - Permite tocar cualquier recuadro delimitador sobre la imagen o elegir cada unidad desde los selectores interactivos para ver su miniatura recortada con zoom, corregir su nombre individualmente o ajustar sus condiciones de almacenamiento por separado.
- **Estimación Fisiológica Poscosecha (`ShelfLifeEstimator`)**:
  - Combina la inspección visual con tablas de referencia poscosecha (USDA FoodKeeper, UC Davis Postharvest Technology Center y FAO) considerando sensibilidad al frío, producción de etileno, temperatura reportada y estado de preparación (entero vs. cortado/pelado).
- **Guía de Conservación Offline**: Catálogo interactivo consultable sin conexión con rangos óptimos de temperatura, días de conservación en mesón vs. refrigeración y notas sobre etileno.

---

## Arquitectura del Proyecto

El proyecto implementa **Clean Architecture + MVVM (Model-View-ViewModel)** con flujo de datos unidireccional (**UDF**) y estado reactivo mediante `StateFlow`:

```text
app/src/main/java/com/example/
├── domain/                  # Capa de Dominio (Kotlin puro)
│   ├── model/               # FoodAnalysis, FoodProduct, RipenessState, ShelfLifeEstimate, etc.
│   ├── repository/          # Contrato FoodAnalysisRepository
│   └── usecase/             # AnalyzeFoodUseCase, ImproveAnalysisUseCase
├── data/                    # Capa de Datos e Infraestructura
│   ├── estimator/           # ShelfLifeEstimator y ConservationReferenceSource
│   ├── local/               # CameraManager, LocalYoloDetector, ImageProcessor, caches locales
│   └── remote/              # FirebaseGeminiDataSource, GeminiJsonValidator, VisualForecastRepository
├── security/                # Firebase App Check y autenticación con Credential Manager
└── ui/                      # Capa de Presentación (Jetpack Compose Material 3)
    ├── components/          # Badges semánticos, diálogos de corrección y consentimiento
    ├── navigation/          # Rutas tipadas de Navigation Compose
    ├── screens/             # HomeScreen, CameraScreen, ProcessingScreen, ResultsScreen, etc.
    ├── theme/               # Paleta botánica Material 3 (Color, Theme, Type)
    └── viewmodel/           # FreshScanViewModel y FreshScanUiState
```

## Optimizaciones de Rendimiento y Recursos

- **Gestión Eficiente de Cámara y Ciclo de Vida (`CameraManager` y `CameraScreen`)**:
  - Frecuencia de análisis en vivo adaptada a **650 ms** por frame para equilibrar fluidez visual y consumo de CPU/batería.
  - Pausa automática del analizador (`setAnalysisPaused`) cuando el usuario captura o revisa una fotografía, o cuando la pantalla entra en pausa/segundo plano (`ON_PAUSE` / `ON_STOP`).
  - Cierre explícito del `ExecutorService` (`analysisExecutor.shutdown()`) al desvincular la cámara (`unbind()`) y recreación segura bajo demanda.
- **Sincronización Inteligente con Cloud Firestore (`FreshScanViewModel` y `MainActivity`)**:
  - Uso exclusivo de suscripción reactiva en tiempo real (`observeCollectiveForecasts`) sin sondeos (*polling*) periódicos redundantes.
  - Suspensión automática del listener cuando la aplicación pasa a segundo plano (`Lifecycle.Event.ON_STOP`) y reanudación al volver al primer plano (`Lifecycle.Event.ON_START`), con respaldo puntual (`refreshCollectiveKnowledgeIfNeeded`) únicamente cuando el flujo de datos está inactivo.
- **Optimización de Memoria y Procesamiento de Imágenes (`ImageProcessor` y `LocalYoloDetector`)**:
  - Escalado y rotación combinados en un **único paso de transformación** mediante una sola `Matrix` (`scaleAndRotate`), evitando asignaciones intermedias de `Bitmap`.
  - Omisión del cálculo de hash perceptual durante el análisis de vista previa en vivo (`isLivePreview = true`) y reciclaje inmediato (`bitmap.recycle()`) de todos los bitmaps temporales dentro de bloques `finally`.
- **Minificación y Reducción de Recursos en Producción (`R8` / `ProGuard`)**:
  - Compilación `release` configurada con `isMinifyEnabled = true` e `isShrinkResources = true`, acompañada de reglas específicas en `app/proguard-rules.pro` para preservar modelos de dominio, serialización JSON y anotaciones `@PropertyName` / `@Keep` de Firebase Firestore.

---

## Requisitos y Compilación

1. **JDK**: Java 11 o superior.
2. **Android SDK**: `compileSdk 36`, `minSdk 24`.
3. **Compilar APK de depuración**:
   ```bash
   ./gradlew :app:assembleDebug
   ```
4. **Compilar APK de producción (con R8 y reducción de recursos)**:
   ```bash
   ./gradlew :app:assembleRelease
   ```
5. **Ejecutar pruebas unitarias y Robolectric**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

