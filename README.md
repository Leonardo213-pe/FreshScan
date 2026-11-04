# FreshScan AI

Aplicación móvil nativa para Android desarrollada en **Kotlin** y **Jetpack Compose** que estima el estado de madurez y la vida útil restante de frutas y verduras frescas a partir de fotografías, brindando recomendaciones prácticas de conservación para reducir el desperdicio alimentario doméstico.

---

## Características Principales (Versión 2.0)

- **Captura Óptica Estable con CameraX y Galería**: Visor fotográfico con **CameraX** (`Preview` + `ImageCapture`), guía de encuadre dedicada, conmutación instantánea de cámara frontal/trasera, control de flash y selector nativo de galería (`PickVisualMedia`) sin permisos invasivos.
- **Segmentación Inteligente de Región de Interés (`segmentProduceRegionBounds`)**:
  - Antes de enviar la fotografía a **Gemini**, `ImageProcessor` analiza automáticamente el mapa cromático y de luminancia para segmentar y recortar únicamente el área donde se encuentran las frutas o verduras, descartando fondos irrelevantes (mesas, teclados, paredes o bordes vacíos).
  - Optimiza el ancho de banda, acelera la respuesta de la IA, incrementa la resolución efectiva sobre el alimento y remapea las coordenadas detectadas de vuelta a la imagen original completa.
- **Sistema de Aprendizaje Evolutivo Continuo con Gemini (`AdaptiveVisualLearner`)**:
  1. **Análisis Multimodal Profundo (Gemini Vision via Firebase AI Logic)**: Evalúa fisiología poscosecha, textura epidérmica (como arrugas de maduración en maracuyá vs. deterioro real), signos de deterioro e individualización de múltiples alimentos, rechazando objetos que no sean frutas o verduras.
  2. **Memoria Visual Evolutiva (Inicia Vacía y Aprende de Gemini)**: A diferencia de clasificadores estáticos preprogramados que fuerzan falsos positivos, `AdaptiveVisualLearner` inicia vacío (0 reglas inventadas) y aprende progresivamente cada vez que se escanea un alimento con Gemini o cuando el usuario corrige manualmente el nombre o la madurez.
  3. **Sincronización Local y en la Nube (`LocalVisualForecastStore` + `Cloud Firestore`)**: Los vectores cromáticos HSV, proporción de manchas y reglas de vida útil aprendidos de cada escaneo con Gemini se guardan en el dispositivo y se sincronizan en segundo plano con la colección `collective_forecasts` de **Cloud Firestore**, inyectándose como memoria evolutiva en los siguientes análisis de Gemini.
- **Corrección Manual de Alimento y Estado de Madurez**:
  - Permite corregir en un toque tanto el **nombre del alimento** (seleccionando chips rápidos como *Maracuyá / Granadilla*, *Plátano*, *Aguacate*, *Mango*, etc., o escribiendo cualquier fruta o verdura) como su **estado de madurez** (*Inmaduro / Verde*, *En maduración*, *Maduro*, *Sobremaduro*), recalculando al instante la vida útil y entrenando la memoria evolutiva.
- **Detección e Individualización Multi-Producto**:
  - Identifica múltiples frutas o verduras en una misma fotografía (o varias unidades con distinto grado de madurez).
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
│   └── remote/              # FirebaseGeminiDataSource, GeminiJsonValidator, VisualForecastRepository
├── security/                # Firebase App Check y autenticación con Credential Manager
└── ui/                      # Capa de Presentación (Jetpack Compose Material 3)
    ├── components/          # Badges semánticos, diálogo de corrección de fruta/madurez y consentimiento
    ├── navigation/          # Rutas tipadas de Navigation Compose
    ├── screens/             # HomeScreen, CameraScreen, ProcessingScreen, ResultsScreen, etc.
    ├── theme/               # Paleta botánica Material 3 (Color, Theme, Type)
    └── viewmodel/           # FreshScanViewModel y FreshScanUiState
```

## Optimizaciones de Rendimiento, Segmentación e Inteligencia Evolutiva

- **Segmentación Automática del Alimento antes de Escanear (`ImageProcessor.segmentProduceRegionBounds`)**:
  - Aísla automáticamente el recuadro de interés que contiene las frutas o verduras (descartando fondos acromáticos, superficies vacías o bordes periféricos) antes de transmitir la imagen a Gemini, y proyecta las coordenadas de salida de vuelta al sistema de referencia de la fotografía original.
- **Gestión Estable de Cámara y Ciclo de Vida (`CameraManager` y `CameraScreen`)**:
  - Vinculación segura de `PreviewView` verificando la instancia propietaria (`boundPreviewView`) y preservación en el backstack al pulsar *"Escanear otro alimento"*, garantizando que la cámara trasera cargue de inmediato en cada nuevo escaneo sin necesidad de alternar a la cámara frontal.
- **Aprendizaje Evolutivo Silencioso (`AdaptiveVisualLearner` y `VisualForecastRepository`)**:
  - Suscripción reactiva en tiempo real (`observeCollectiveForecasts`) con suspensión automática cuando la aplicación pasa a segundo plano (`Lifecycle.Event.ON_STOP`) y reanudación en primer plano (`Lifecycle.Event.ON_START`).
  - Cada escaneo validado por Gemini y cada corrección del usuario enriquecen las reglas cromáticas locales y en la nube sin bloquear la interfaz.
- **Optimización de Memoria y Procesamiento de Imágenes (`ImageProcessor`)**:
  - Escalado y rotación combinados en un **único paso de transformación** mediante una sola `Matrix` (`scaleAndRotate`), evitando asignaciones intermedias de `Bitmap` y reciclando bitmaps temporales de forma segura.
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
