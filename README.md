# FreshScan AI

Aplicación móvil nativa para Android desarrollada en **Kotlin** y **Jetpack Compose** que estima el estado de madurez y la vida útil restante de frutas y verduras frescas a partir de fotografías, brindando recomendaciones prácticas de conservación para reducir el desperdicio alimentario doméstico.

---

## Características Principales (Versión 2.3)

- **Captura Óptica Estable con CameraX y Galería**: Visor fotográfico con **CameraX** (`Preview` + `ImageCapture`), guía de encuadre dedicada, conmutación instantánea de cámara frontal/trasera, control de flash y selector nativo de galería (`PickVisualMedia`) sin permisos invasivos.
- **Inferencia Multimodal de Alta Velocidad (`Gemini 3.8 Flash` + Fallback `Gemini 3.5 Flash-Lite`)**:
  - Prioriza el modelo **`gemini-3.8-flash`** (timeout estricto de `14s`) con respaldo inmediato en **`gemini-3.5-flash-lite`** (`10s`) mediante ejecución asíncrona cancelable (`networkCallScope.async` + `withTimeout`), evitando bloqueos o pantallas de carga indefinidas incluso en conexiones móviles inestables.
  - Optimiza la imagen de entrada a un máximo de **`640 px`** con compresión eficiente antes de la llamada, reduciendo drásticamente el tiempo de subida y la latencia de inferencia.
- **Segmentación Inteligente de Región de Interés (`segmentProduceRegionBounds`) y Recorte Rectangular Multi-Producto (`cropToBoundingBox`)**:
  - Antes de enviar la fotografía a **Gemini**, `ImageProcessor` analiza automáticamente el mapa cromático y de luminancia para segmentar y recortar únicamente el **rectángulo delimitador (ROI)** donde se encuentran las frutas o verduras, descartando fondos irrelevantes (mesas, teclados, paredes o bordes vacíos).
  - Cuando hay múltiples frutas o verduras en una mesa, Gemini devuelve las coordenadas normalizadas (`[ymin, xmin, ymax, xmax]` en escala `0..1000`) de cada unidad individual, `FirebaseGeminiRepository` las remapea a la foto original completa e `ImageProcessor.cropToBoundingBox` genera el recorte rectangular individual con margen contextual del `8%` para cada ejemplar.
- **Sistema de Aprendizaje Evolutivo Continuo con Gemini (`AdaptiveVisualLearner`)**:
  1. **Análisis Multimodal Profundo (Gemini Vision via Firebase AI Logic)**: Evalúa fisiología poscosecha, textura epidérmica (como arrugas de maduración en *Maracuyá / Granadilla* o tubérculos superficiales en *Pepino / Pepinillo* frente a deterioro real), signos de deterioro e individualización de múltiples alimentos, rechazando objetos que no sean frutas o verduras.
  2. **Memoria Visual Evolutiva (Inicia Vacía y Aprende de Gemini)**: A diferencia de clasificadores estáticos preprogramados que fuerzan falsos positivos, `AdaptiveVisualLearner` inicia vacío (0 reglas inventadas) y aprende progresivamente cada vez que se escanea un alimento con Gemini o cuando el usuario corrige manualmente el nombre o la madurez.
  3. **Sincronización Local y en la Nube (`LocalVisualForecastStore` + `Cloud Firestore`)**: Los vectores cromáticos HSV, proporción de manchas y reglas de vida útil aprendidos de cada escaneo con Gemini se guardan en el dispositivo y se sincronizan en segundo plano con la colección `collective_forecasts` de **Cloud Firestore**, inyectándose como memoria evolutiva en los siguientes análisis de Gemini.
- **Corrección Manual de Alimento y Estado de Madurez**:
  - Permite corregir en un toque tanto el **nombre del alimento** (seleccionando chips rápidos como *Pepino / Pepinillo*, *Maracuyá / Granadilla*, *Plátano*, *Aguacate*, *Mango*, etc., o escribiendo cualquier fruta o verdura) como su **estado de madurez** (*Inmaduro / Verde*, *En maduración*, *Maduro*, *Sobremaduro*), recalculando al instante la vida útil y entrenando la memoria evolutiva.
- **Detección e Individualización Multi-Producto**:
  - Identifica múltiples frutas o verduras en una misma fotografía (hasta 6 unidades principales simultáneas con distinto grado de madurez).
  - Permite tocar cualquier recuadro delimitador sobre la imagen o elegir cada unidad desde los selectores interactivos para ver su miniatura recortada con zoom, corregir su nombre/madurez individualmente o ajustar sus condiciones de almacenamiento por separado.
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
│   ├── local/               # CameraManager, AdaptiveVisualLearner, ImageProcessor, LearnedDatasetManager
│   └── remote/              # FirebaseGeminiDataSource, FirebaseGeminiRepository, GeminiJsonValidator, VisualForecastRepository
├── security/                # Firebase App Check y autenticación con Credential Manager
└── ui/                      # Capa de Presentación (Jetpack Compose Material 3)
    ├── components/          # Badges semánticos, diálogo de corrección de fruta/madurez y consentimiento
    ├── navigation/          # Rutas tipadas de Navigation Compose
    ├── screens/             # HomeScreen, CameraScreen, ProcessingScreen, ResultsScreen, etc.
    ├── theme/               # Paleta botánica Material 3 (Color, Theme, Type)
    └── viewmodel/           # FreshScanViewModel y FreshScanUiState
```

## Optimizaciones de Rendimiento, Segmentación e Inteligencia Evolutiva (v2.3)

- **Inferencia Rápida con Cancelación Estricta de Timeout (`FirebaseGeminiDataSource`)**:
  - Cadena de prioridad `gemini-3.8-flash` (`14s`) -> `gemini-3.5-flash-lite` (`10s`) con cancelación real de tareas en segundo plano (`deferred.cancel()`) y `maxOutputTokens = 1024`, garantizando respuestas rápidas sin bloqueos.
- **Segmentación Automática del Alimento antes de Escanear (`ImageProcessor.segmentProduceRegionBounds`)**:
  - Aísla automáticamente el recuadro de interés que contiene las frutas o verduras (descartando fondos acromáticos, superficies vacías o bordes periféricos) y reescala a `640 px` antes de transmitir la imagen a Gemini, proyectando luego las coordenadas de salida de vuelta al sistema de referencia de la fotografía original.
- **Reconocimiento Fisiológico Ampliado (`FoodProduct` y `ConservationReferenceSource`)**:
  - Incluye criterios detallados para hortalizas cilíndricas como **Pepino / Pepinillo** (*Cucumis sativus*) y frutas tropicales como **Maracuyá / Granadilla** (*Passiflora edulis*), diferenciando rasgos naturales de la variedad (tubérculos superficiales en pepinillo o arrugamiento ligero en maracuyá maduro) de signos reales de senescencia o pudrición.
- **Gestión Estable de Cámara y Ciclo de Vida (`CameraManager` y `CameraScreen`)**:
  - Vinculación segura de `PreviewView` verificando la instancia propietaria (`boundPreviewView`) y preservación en el backstack al pulsar *"Escanear otro alimento"*, garantizando que la cámara trasera cargue de inmediato en cada nuevo escaneo.
- **Aprendizaje Evolutivo Silencioso (`AdaptiveVisualLearner` y `VisualForecastRepository`)**:
  - Suscripción reactiva en tiempo real (`observeCollectiveForecasts`) con suspensión automática cuando la aplicación pasa a segundo plano (`Lifecycle.Event.ON_STOP`) y reanudación en primer plano (`Lifecycle.Event.ON_START`).
- **Instalación Limpia y Empaquetado Automático (`app/build.gradle.kts`)**:
  - Icono adaptativo personalizado (`@mipmap/ic_launcher`), exclusión del bloque de metadatos de dependencias (`dependenciesInfo { includeInApk = false }`) para instalación directa más limpia y generación automática del archivo `FreshScanAI-v<versionName>.apk` en la raíz del proyecto al ejecutar `assembleDebug` o `copyDebugApkToRoot`.

---

## Requisitos y Compilación

1. **JDK**: Java 11 o superior.
2. **Android SDK**: `compileSdk 36`, `minSdk 24`.
3. **Compilar APK de depuración y actualizar APK en la raíz (`FreshScanAI-v2.3.apk`)**:
   ```bash
   gradle :app:assembleDebug
   ```
4. **Compilar APK de producción (con R8 y reducción de recursos)**:
   ```bash
   gradle :app:assembleRelease
   ```
5. **Ejecutar pruebas unitarias y Robolectric**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
