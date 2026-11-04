# UNIVERSIDAD DE SAN MARTÍN DE PORRES (USMP)
**Facultad de Ingeniería y Arquitectura**  
**Escuela Profesional de Ingeniería de Computación y Sistemas**  
**Asignatura:** Inteligencia Artificial  
**Semestre:** 2026-II  

---

# PROYECTO FINAL
## **FreshScan AI: Sistema Inteligente de Segmentación Óptica, IA Multimodal (Gemini) y Aprendizaje Evolutivo Continuo para la Estimación de Madurez y Vida Útil Postcosecha de Frutas y Verduras**

---

## I. OBJETIVO GENERAL

Desarrollar un sistema inteligente móvil en Android (`FreshScan AI`) que integre captura óptica optimizada, segmentación automática de regiones de interés en el borde (*Edge Computing*), aprendizaje profundo multimodal (*Deep Learning* / Modelos de Visión-Lenguaje con **Gemini**) y un motor de aprendizaje evolutivo continuo (que inicia desde cero y aprende progresivamente de cada escaneo con Gemini y de las correcciones del usuario tanto en el dispositivo como en la nube), con el fin de estimar con precisión el estado de madurez y los días restantes de vida útil de frutas y verduras frescas, solucionando el problema real del desperdicio alimentario y el riesgo sanitario en hogares y comercios, en alineación directa con los **Objetivos de Desarrollo Sostenible (ODS) 12, 2 y 3** priorizados por la ONU y la OCDE.

---

## II. COMPETENCIAS Y CAPACIDADES DESARROLLADAS

### a. Competencias clave
- **Diseño e implementación de soluciones inteligentes móviles e IA evolutiva:** Preprocesamiento y segmentación inteligente desde la cámara del dispositivo móvil combinados con inferencia multimodal en la nube y memoria visual evolutiva.
- **Análisis de datos y aplicación de modelos predictivos:** Estimación cuantitativa del índice de madurez (`0% – 100%`) y regresión de vida útil restante (en días) según condiciones térmicas y de almacenamiento.
- **Integración de hardware móvil y software inteligente para resolver un problema real:** Acoplamiento entre el subsistema óptico (`CameraX` / `Camera2`), el segmentador de región de frutas/verduras (`ImageProcessor.segmentProduceRegionBounds`), el motor de aprendizaje evolutivo (`AdaptiveVisualLearner`), el análisis multimodal (`Gemini 2.5 Flash`) y la persistencia distribuida (`Cloud Firestore`).
- **Conciencia ética y responsabilidad con el entorno y la sociedad:** Prevención del desperdicio de alimentos aptos para el consumo y protección de la salud pública mediante alertas tempranas de descomposición y contaminación fúngica.
- **Trabajo colaborativo y comunicación efectiva:** Arquitectura modular bajo principios de *Clean Architecture* y documentación técnica reproducible.

### b. Capacidades específicas
- Determinar y cuantificar problemas reales alineados a los ODS en las áreas de alimentación, agricultura postcosecha, salud y medio ambiente.
- Recolectar, limpiar, segmentar, normalizar y analizar datos visuales reales capturados por sensores ópticos móviles.
- Implementar algoritmos de segmentación de región de interés (aislamiento de frutas y verduras respecto al fondo), redes neuronales multimodales con inyección de memoria evolutiva (*Retrieval-Augmented Visual Learning*) y regresión de degradación biológica.
- Integrar el hardware de captura óptica móvil con flujos reactivos de procesamiento de imágenes y aprendizaje incremental.
- Generar visualizaciones intuitivas, recortes individualizados por producto y reportes interactivos de conservación en refrigeración y temperatura ambiente.
- Evaluar el impacto técnico, social, ético y de sostenibilidad energética del sistema.

---

## III. ELEMENTOS DEL PROYECTO

---

### 1. Diagnóstico del Problema

#### 1.1. Elección y justificación de los ODS abordados
El proyecto se alinea directamente con tres Objetivos de Desarrollo Sostenible (ODS):

1. **ODS 12: Producción y Consumo Responsables (Prioritario – Meta 12.3):**
   - *Meta 12.3:* "De aquí a 2030, reducir a la mitad el desperdicio de alimentos per cápita mundial en la venta al por menor y a nivel de los consumidores y reducir las pérdidas de alimentos en las cadenas de producción y suministro, incluidas las pérdidas posteriores a la cosecha".
   - *Justificación:* Una de las principales causas del desperdicio doméstico y minorista de frutas y verduras frescas es la incapacidad del consumidor para distinguir entre madurez óptima, sobremadurez aprovechable (como el arrugamiento fisiológico normal en el maracuyá) y deterioro microbiológico real, sumada al desconocimiento del impacto del método de almacenamiento (refrigeración vs. ambiente) en los días restantes de vida útil.
2. **ODS 2: Hambre Cero (Seguridad Alimentaria y Aprovechamiento Nutricional):**
   - Maximizar el aprovechamiento de alimentos perecibles antes de que alcancen su punto de descarte permite optimizar los recursos alimentarios familiares y comunitarios.
3. **ODS 3: Salud y Bienestar (Prevención de Enfermedades Transmitidas por Alimentos - ETA):**
   - El consumo accidental de frutas o hortalizas con colonización fúngica incipiente (micotoxinas) o descomposición tisular severa representa un riesgo directo para la salud gastrointestinal. El sistema emite alertas preventivas automáticas cuando detecta signos de deterioro crítico.

#### 1.2. Impacto local y global del problema
- **Impacto Global:** Según reportes de la FAO y el Índice de Desperdicio de Alimentos del PNUMA, aproximadamente el **13% de los alimentos se pierde en la cadena postcosecha** y un **17% adicional se desperdicia a nivel de hogares y comercios minoristas**. Las frutas y hortalizas representan el grupo con mayor tasa de pérdida física (entre el 40% y el 50% de su peso producido) debido a su alta actividad metabólica postcosecha (respiración y transpiración). Además, el desperdicio alimentario global genera cerca del **8% al 10% de las emisiones mundiales de gases de efecto invernadero (GEI)** al descomponerse en vertederos produciendo metano ($CH_4$).
- **Impacto Local (Perú y Latinoamérica):** En el contexto peruano, donde gran parte del abastecimiento hortofrutícola proviene de mercados mayoristas, mercados de abasto y ferias locales sin cadena de frío continua ni etiquetado de "fecha de vencimiento", los hogares desechan semanalmente productos que aún podrían consumirse o, por el contrario, almacenan a temperatura ambiente productos que requieren refrigeración inmediata. **FreshScan AI** democratiza el control de calidad postcosecha utilizando un dispositivo que el ciudadano ya posee: su teléfono inteligente.

---

### 2. Diseño del Sistema

#### 2.1. Arquitectura: Sensores del Dispositivo, Segmentación de Imagen y Aprendizaje Evolutivo con Gemini
A diferencia de sistemas estacionarios limitados a un laboratorio, **FreshScan AI** utiliza el **hardware integrado del teléfono inteligente como nodo de captura óptica y segmentación en el borde (*Edge Device*)**, articulado con una infraestructura en la nube para inferencia multimodal con **Gemini** y aprendizaje evolutivo continuo:

1. **Subsistema de Captura Óptica Estable (Hardware Móvil):**
   - **Sensor de Imagen CMOS (Cámara Trasera / Frontal):** Gestionado mediante la API `CameraX` (`Camera2` pipeline) en `CameraManager.kt`.
   - **Captura Fotográfica Bajo Demanda:** Flujo de visualización `Preview` fluido con guía de encuadre dedicada y captura de alta calidad (`ImageCapture`), evitando falsos positivos sobre el fondo antes de tomar la fotografía y garantizando la re-vinculación instantánea del visor al realizar múltiples escaneos consecutivos.
2. **Subsistema de Segmentación Inteligente y Aprendizaje Local (*On-Device Edge Computing*):**
   - **Normalización y Segmentación de Región de Interés (`ImageProcessor.kt`):**
     - Decodificación eficiente con submuestreo (`inSampleSize`), lectura de metadatos ópticos `EXIF` para corrección de rotación de cámara y reescalado matricial en un solo paso (`Matrix.postScale` + `Matrix.postRotate`) hasta un máximo de `1024 px` por lado.
     - **Optimización por Segmentación de Frutas/Verduras (`segmentProduceRegionBounds`):** Antes de enviar la imagen a Gemini, el procesador analiza la distribución espacial de saturación cromática, contraste orgánico y centralidad para **recortar y enviar únicamente la región donde están las frutas o verduras**, descartando bordes irrelevantes del fondo. Una vez que Gemini responde, las cajas delimitadoras se remapean matemáticamente de vuelta a las coordenadas de la imagen original completa.
   - **Motor de Aprendizaje Evolutivo (`AdaptiveVisualLearner.kt`):**
     - Inicia con una **memoria completamente vacía (0 reglas preprogramadas)** para no inventar frutas inexistentes ni confundir objetos cotidianos con alimentos.
     - Extrae vectores de características visuales (`VisualFeatureVector`: matiz dominante HSV, saturación, brillo, proporción de manchas oscuras y relación de aspecto) de cada alimento validado por Gemini o corregido por el usuario, construyendo progresivamente una base de conocimiento que se inyecta en futuros escaneos (`buildLearnedPromptHintForGemini`) y sirve como respaldo offline cuando ya existen muestras aprendidas.
3. **Subsistema de Inteligencia Artificial en la Nube y Aprendizaje Colectivo:**
   - **Motor Multimodal (`FirebaseGeminiDataSource.kt`):** Integración con el modelo **Gemini 2.5 Flash** mediante el SDK oficial de **Firebase AI Logic**. Analiza la región segmentada de los alimentos junto con la memoria evolutiva aprendida, rechaza explícitamente escenas sin frutas ni verduras (`identificacion_confiable = false`), distingue frutas tropicales como el *Maracuyá / Granadilla* de los cítricos, detecta múltiples productos simultáneamente, clasifica especie y estado de madurez, y retorna un esquema JSON estructurado con coordenadas espaciales para cada ejemplar.
   - **Motor de Estimación Postcosecha (`ShelfLifeEstimator.kt`):** Combina las predicciones visuales con curvas de degradación biológica por especie y las calibra con las reglas empíricas aprendidas.
   - **Persistencia y Aprendizaje Colectivo (`VisualForecastRepository.kt` + `Cloud Firestore`):** Sincronización reactiva en segundo plano mediante un listener en tiempo real (`addSnapshotListener`) vinculado al ciclo de vida de la aplicación (`ON_START` / `ON_STOP`), que actualiza el conocimiento colectivo (`collective_forecasts`) y almacena perfiles visuales aprendidos (`LearnedDatasetManager.kt`).

#### 2.2. Diagrama de Conexión, Software y Hardware

```text
+-----------------------------------------------------------------------------------+
|                        HARDWARE DEL DISPOSITIVO MÓVIL (ANDROID)                   |
|  [Sensor Óptico CMOS (Cámara)]  --->  [ISP / Hardware JPEG Encoder]               |
+-------------------------------------------------|---------------------------------+
                                                  | Fotografía Capturada / Galería
                                                  v
+-----------------------------------------------------------------------------------+
|                     CAPA DE PROCESAMIENTO LOCAL (EDGE / ON-DEVICE)                |
|                                                                                   |
|  1. CameraManager.kt (CameraX Preview + ImageCapture estable entre escaneos)      |
|  2. ImageProcessor.kt (EXIF + Segmentación de Región de Frutas/Verduras + dHash)  |
|  3. AdaptiveVisualLearner.kt (Memoria Evolutiva Vacía -> Aprende de Gemini)       |
+-----------------------+-----------------------------------+-----------------------+
                        |                                   |
     Recorte Optimizado de Frutas/Verduras + Contexto Aprendido de Escaneos Previos
                        |                                   |
                        v                                   v
+-------------------------------+         +-----------------------------------------+
|     INTERFAZ DE USUARIO       |         |      INFRAESTRUCTURA CLOUD (FIREBASE)   |
|     (Jetpack Compose M3)      |         |                                         |
|                               |         |  [Firebase AI Logic - Gemini 2.5 Flash] |
| - CameraScreen (Encuadre)     |<--------+  - Análisis sobre Región Segmentada     |
| - ResultsScreen (Tarjetas     |         |  - Detección Multi-Producto y Madurez   |
|   individuales con recortes,  |         |  - Rechazo de Objetos No Alimentarios   |
|   corrección fruta/madurez,   |         |  - Retroalimentación al Motor Evolutivo |
|   alertas y días de vida)     |         |                                         |
| - ConservationGuide / Auth    |         |  [Cloud Firestore + Firebase Auth]      |
+-------------------------------+         |  - Colección: collective_forecasts      |
                        ^                 |  - Memoria Local: learned_visual_dataset|
                        |                 +-----------------------------------------+
                        +-----------------------------------+
                                          |
                                          v
                        +-----------------------------------+
                        |     ShelfLifeEstimator.kt         |
                        |  Fusión Híbrida:                  |
                        |  Modelo Físico Postcosecha +      |
                        |  Reglas Aprendidas por Gemini     |
                        +-----------------------------------+
```

---

### 3. Adquisición y Análisis de Datos

#### 3.1. Descripción del Dataset (Catálogo Base Postcosecha + Memoria Evolutiva en App y Nube)
El sistema opera sobre una arquitectura de datos dual que combina una base de conocimiento postcosecha parametrizada con un **dataset evolutivo** que aprende con cada uso:

1. **Catálogo Hortofrutícola Parametrizado + Reconocimiento Abierto:**
   - **Frutas:** Plátano (`banana`), Manzana (`apple`), Naranja / Mandarina (`orange`), Fresa (`strawberry`), Aguacate / Palta (`avocado`), Mango (`mango`), Limón (`lemon`), Papaya (`papaya`), Maracuyá / Granadilla (`passion_fruit`), Piña (`pineapple`), Uva (`grape`), más soporte abierto para cualquier fruta identificada por Gemini o ingresada por el usuario.
   - **Verduras y Hortalizas:** Tomate / Jitomate (`tomato`), Lechuga (`lettuce`), Pimiento (`pepper`), Papa (`potato`), Zanahoria (`carrot`), Brócoli (`broccoli`), Pepino (`cucumber`), Cebolla (`onion`), más soporte abierto.
   - Cada clase cuenta con parámetros fisiológicos base documentados en literatura postcosecha: vida útil base en refrigeración, vida útil base a temperatura ambiente, sensibilidad al frío, producción de etileno y criterios visuales de madurez.

2. **Memoria Visual Evolutiva en Dispositivo y Cloud Firestore:**
   - **Colección `collective_forecasts` (Cloud Firestore) y `LocalVisualForecastStore`:** Almacena reglas visuales aprendidas (`VisualForecastRule`) a partir de cada análisis confiable de Gemini y de cada corrección manual realizada por el usuario. Cada regla registra el nombre del producto, sus rangos cromáticos HSV (`hueMin`, `hueMax`, `saturationMin`, `saturationMax`, `valueMin`, `valueMax`), el umbral de manchas superficiales (`maxSpotRatio`), el estado de madurez aprendido, los días estimados de vida útil y el contador de confirmaciones (`sampleCount`).
   - **Repositorio `LearnedDatasetManager`:** Registra localmente las muestras visuales validadas por Gemini junto con sus vectores de características y cajas delimitadoras normalizadas (`xMin`, `yMin`, `xMax`, `yMax`) para alimentar el aprendizaje continuo del dispositivo.

#### 3.2. Exploración, Segmentación y Limpieza de Datos (EDA y Preprocesamiento Visual)
Las imágenes capturadas en entornos domésticos o mercados presentan alta variabilidad en resolución, orientación, iluminación y elementos distractores en el fondo. En `ImageProcessor.kt` se implementó un pipeline determinista de limpieza, segmentación y normalización de datos:

1. **Control de Dimensiones y Submuestreo (`calculateInSampleSize`):** Las cámaras móviles modernas capturan imágenes de 12 MP a 50 MP. El sistema calcula dinámicamente una potencia de 2 para decodificar la imagen cerca de `1024 px` en su lado mayor.
2. **Corrección Geométrica Unificada (`scaleAndRotate`):** Se inspeccionan los encabezados `EXIF` (`TAG_ORIENTATION`) del archivo JPEG y se construye una única matriz de transformación afín (`android.graphics.Matrix`) que aplica simultáneamente el factor de escala exacto y la rotación (`90°`, `180°`, `270°`), reciclando inmediatamente los bitmaps intermedios.
3. **Segmentación Automática del Alimento (`segmentProduceRegionBounds`):**
   - Antes de transmitir la fotografía a Gemini, el algoritmo divide la imagen en una rejilla de análisis (`24x24` bloques) y evalúa en cada celda su saturación cromática, diferencia respecto al color medio de los bordes periféricos de la imagen y contraste orgánico.
   - De este modo, **aísla y recorta únicamente el área que contiene las frutas o verduras**, evitando enviar grandes extensiones de mesa, pared o fondo innecesario.
   - Si las frutas o verduras ocupan un subconjunto del encuadre, se envía a Gemini únicamente el recorte optimizado y posteriormente se proyectan las coordenadas detectadas de vuelta al espacio `[0, 1]` de la fotografía original.
4. **Deduplicación mediante Hash Perceptual (`computePerceptualHash`):** Para evitar cálculos redundantes y sesgos por muestras idénticas, la imagen se reduce a una rejilla de luminancia para generar un hash perceptual (`dHash`) de 64 bits.

---

### 4. Modelo de IA

El núcleo inteligente de **FreshScan AI** implementa un **sistema evolutivo guiado por Gemini** que combina segmentación previa de región de interés, inferencia multimodal profunda, memoria evolutiva incremental y modelado físico postcosecha:

#### 4.1. Técnicas Implementadas

1. **Segmentación de Región de Interés y Extracción de Vectores Visuales (`ImageProcessor.kt` + `AdaptiveVisualLearner.kt`):**
   - Aísla la región de los alimentos en la imagen antes de consultar a Gemini y extrae firmas cuantitativas en espacio de color `HSV` (Matiz, Saturación, Valor), proporción de manchas epidérmicas (`darkSpotRatio`) y geometría (`aspectRatio`).
   - Aplica **Non-Maximum Suppression (NMS)** e **Intersection over Union (IoU)** para depurar cajas delimitadoras superpuestas en escenas con múltiples alimentos.

2. **Redes Neuronales Profundas Multimodales con Memoria Evolutiva (`FirebaseGeminiDataSource.kt` – Gemini 2.5 Flash):**
   - Al escanear una fotografía, se invoca el modelo fundacional multimodal **Gemini 2.5 Flash** a través de `Firebase.ai(backend = GenerativeBackend.googleAI())`.
   - Antes de la llamada, `AdaptiveVisualLearner` busca en la memoria local y en la nube si existen reglas aprendidas de escaneos previos con características cromáticas similares (`learnedMemoryContext`) y las inyecta en el *prompt* de Gemini para que el modelo aproveche la experiencia acumulada (incluyendo las correcciones previas del usuario).
   - El modelo ejecuta simultáneamente:
     - **Verificación de Alimento Real:** Si la cámara apunta a un objeto no alimentario (teclado, mano vacía, mesa), devuelve `identificacion_confiable = false` y el sistema solicita capturar una fruta o verdura real en lugar de inventar un resultado.
     - **Detección Multi-Objeto e Individualización:** Identifica múltiples frutas o verduras individuales en una sola fotografía, devolviendo sus coordenadas normalizadas `[ymin, xmin, ymax, xmax]`.
     - **Clasificación Fisiológica de Estado de Madurez (`RipenessState`):** Clasifica cada ítem en `UNRIPE` (*Inmaduro / Verde*), `RIPENING` (*En maduración*), `RIPE` (*Maduro / Óptimo*) u `OVERRIPE` (*Sobremaduro*), distinguiendo rasgos propios de cada especie (por ejemplo, arrugas superficiales de madurez óptima en el *Maracuyá* frente a pudrición húmeda).
     - **Índice de Confianza y Señales Visuales:** Retorna indicadores visuales observados, signos de deterioro y recomendaciones de conservación.

3. **Aprendizaje Evolutivo Continuo desde Cero (`AdaptiveVisualLearner.kt` + `VisualForecastRepository.kt`):**
   - El motor local inicia **completamente vacío**.
   - Cada vez que Gemini completa un análisis válido, `AdaptiveVisualLearner.learnFromGeminiAnalysis` extrae el vector cromático del recorte de cada fruta o verdura y genera o refina una regla `VisualForecastRule` en `LocalVisualForecastStore`, `LearnedDatasetManager` y `Cloud Firestore` (`collective_forecasts`).
   - Si el usuario corrige manualmente el nombre de la fruta/verdura o su estado de madurez en la pantalla de resultados, el sistema registra esa corrección con peso reforzado, logrando que cada escaneo haga más inteligente a la aplicación.

4. **Modelo Predictivo de Regresión Postcosecha (`ShelfLifeEstimator.kt`):**
   - Calcula el rango de días restantes (`minDays` – `maxDays`) integrando la línea base fisiológica de cada especie (USDA / UC Davis / FAO) con el estado de madurez, lugar de almacenamiento (refrigerado, mesón, alacena), temperatura ambiente reportada, estado de preparación (entero vs. cortado/pelado) y las reglas aprendidas en la memoria evolutiva.

#### 4.2. Entrenamiento, Validación y Despliegue
- **Aprendizaje Evolutivo en Línea (Online Evolutionary Learning):** Cada escaneo con Gemini y cada corrección del usuario actualizan automáticamente los rangos HSV y días estimados mediante promedios ponderados incrementales tanto en el almacenamiento local como en `Cloud Firestore`.
- **Respaldo Basado en Memoria Aprendida:** Si el usuario selecciona el modo de memoria aprendida sin conexión una vez que el sistema ya ha aprendido perfiles visuales de escaneos previos con Gemini, `AdaptiveVisualLearner` evalúa la similitud contra las reglas aprendidas sin inventar datos falsos.
- **Despliegue Optimizado:** El paquete de producción se compila con **R8 (`isMinifyEnabled = true`)** y eliminación de recursos inactivos (**`isShrinkResources = true`**), manteniendo reglas de preservación (`proguard-rules.pro`) para los modelos de dominio y la serialización de Firebase Firestore.

---

### 5. Despliegue y Operación

#### 5.1. Entorno Operativo en Dispositivos Android
- **Compatibilidad:** Android 7.0 Nougat (`minSdk = 24`) hasta Android 16 (`targetSdk = 36`), cubriendo más del 98% del parque activo de teléfonos inteligentes.
- **Gestión Eficiente de Recursos:**
  - **Cámara Estable entre Escaneos:** `CameraManager.kt` y `CameraScreen.kt` gestionan el ciclo de vida del `PreviewView` verificando la instancia vinculada y manteniendo la pantalla de cámara en la pila de navegación al pulsar *"Escanear otro alimento"*, garantizando recarga instantánea de la cámara trasera.
  - **Sincronización Inteligente en la Nube:** `MainActivity.kt` y `FreshScanViewModel.kt` vinculan la suscripción en tiempo real de Firestore al estado en primer plano de la aplicación (`ON_START` / `ON_STOP`), deteniendo el consumo de red cuando la app pasa a segundo plano.
  - **Segmentación y Reciclaje de Memoria Gráfica:** `ImageProcessor.kt` recorta la región de frutas/verduras antes del envío a la nube y libera (`bitmap.recycle()`) los mapas de bits temporales para evitar picos de memoria (`OutOfMemoryError`).

#### 5.2. Interacción con Usuarios (Alertas, Dashboards e Interfaces)
La interfaz fue construida íntegramente con **Jetpack Compose** y **Material Design 3 (M3)**, estructurada en pantallas intuitivas:

1. **Pantalla de Inicio (`HomeScreen`):**
   - Selección de modo de análisis (*Aprendizaje Evolutivo con Gemini*, *Solo Memoria Aprendida Offline* o *Análisis Directo con Gemini*), acceso rápido al escáner, galería y guía de conservación poscosecha.
2. **Pantalla de Escaneo Fotográfico (`CameraScreen`):**
   - Visor de cámara limpio con marco guía para centrar la fruta o verdura, control de flash, cambio de lente, botón de recarga rápida y acceso directo al selector nativo de galería (`PickVisualMedia`).
3. **Dashboard de Resultados Multi-Producto (`ResultsScreen`):**
   - **Fotografía general anotada:** Muestra todos los productos detectados en la escena con sus respectivas cajas delimitadoras interactivas.
   - **Tarjetas Individualizadas por Producto:** Recorta automáticamente la miniatura de cada ejemplar detectado y presenta:
     - Estado de madurez con código semafórico de color y nivel de confianza.
     - Rango estimado de **días restantes de vida útil** y recomendación accionable (*Consumir pronto*, *Congelar o cocinar*, *Óptimo para guardar*).
     - **Corrección Manual de Fruta y Madurez:** Permite corregir tanto el nombre del alimento (chips rápidos incluyendo *Maracuyá / Granadilla* o texto libre) como su estado de madurez (*Inmaduro*, *En maduración*, *Maduro*, *Sobremaduro*), actualizando al instante el resultado y entrenando la memoria evolutiva.
     - **Ajuste Avanzado de Almacenamiento (`AdvancedAnalysisScreen`):** Permite especificar si el producto está refrigerado o en mesón, temperatura ambiente y si está entero o cortado.
4. **Guía de Conservación Offline (`ConservationGuideScreen`) y Autenticación (`SignInScreen`):**
   - Catálogo poscosecha consultable sin internet e inicio de sesión nativo con **Google Sign-In (`CredentialManager`)** respaldado por **Firebase Authentication**.

---

### 6. Evaluación del Impacto

#### 6.1. Métricas de Evaluación Técnica y Social

| Dimensión | Métrica Evaluada | Resultado / Valor Obtenido |
| :--- | :--- | :--- |
| **Optimización de Imagen** | Segmentación previa de frutas/verduras | Recorte automático de región de interés (`segmentProduceRegionBounds`) antes de enviar a Gemini |
| **Eficiencia de Memoria** | Resolución máxima de trabajo | `1024 px` con transformación matricial única y reciclaje seguro |
| **Precisión Multimodal** | Granularidad de detección y filtrado | Detección multi-producto con rechazo de objetos no alimentarios |
| **Aprendizaje Continuo** | Evolución del modelo con cada uso | Inicia con `0` reglas estáticas y aprende de cada escaneo con Gemini y corrección del usuario |
| **Seguridad de Datos** | Validación de reglas en la nube | Reglas de Firestore verificadas (`firestore.rules`) para `collective_forecasts` |
| **Impacto Social / Doméstico** | Toma de decisiones informada | Estimación de vida útil según almacenamiento + alertas de consumo oportuno |

#### 6.2. Reflexión Ética y Sostenibilidad
- **Sostenibilidad Ambiental:** Al orientar al consumidor sobre cuántos días adicionales de vida útil gana un alimento al refrigerarlo adecuadamente o en qué momento debe consumirse, el sistema fomenta hábitos inmediatos de conservación que reducen la huella hídrica y de carbono asociada al desperdicio alimentario.
- **Ética y Responsabilidad en Salud:** **FreshScan AI** aplica un **principio de precaución alimentaria** (`SafetyFilter` en `GeminiJsonValidator.kt` y `ShelfLifeEstimator.kt`): ante la detección de moho, pudrición blanda o fermentación, el sistema clasifica el producto en estado crítico y despliega una alerta explícita de precaución sanitaria.
- **Privacidad desde el Diseño (*Privacy by Design*):** Las fotografías tomadas por los usuarios **no se suben ni almacenan públicamente** en la base de datos compartida; únicamente se sincronizan reglas numéricas anónimas de rangos cromáticos HSV y vida útil en `collective_forecasts`, protegiendo la privacidad del hogar del usuario.
- **Sostenibilidad Energética del Software (*Green Software*):** La eliminación del escaneo continuo innecesario en vista previa, la segmentación previa de la región de interés antes de transmitir la imagen y el uso de suscripciones reactivas a la nube reducen significativamente el consumo de batería, CPU y datos móviles.

---

### 7. Presentación de Resultados y Anexos Técnicos

#### 7.1. Producto Final Logrado
El producto final es la aplicación nativa Android **FreshScan AI** (`com.aistudio.freshscanai.fxkmpq`), completamente funcional en dispositivos físicos y emuladores, integrada con **Firebase AI Logic (Gemini 2.5 Flash)**, **Cloud Firestore** y **Firebase Authentication**, lista para demostración en vivo frente al jurado evaluador.

---

#### ANEXO A: Estructura Modular del Código Fuente (*Clean Architecture*)

```text
app/src/main/java/com/example/
├── MainActivity.kt                        # Actividad principal, navegación y ciclo de vida (ON_START/ON_STOP)
├── data/
│   ├── estimator/
│   │   ├── ConservationReferenceSource.kt # Parámetros poscosecha (USDA, UC Davis, FAO)
│   │   └── ShelfLifeEstimator.kt          # Motor predictivo de vida útil y fusión con reglas aprendidas
│   ├── local/
│   │   ├── AdaptiveVisualLearner.kt       # Motor de aprendizaje evolutivo que aprende de Gemini y del usuario
│   │   ├── CameraManager.kt               # Control estable de CameraX (Preview + ImageCapture)
│   │   ├── ImageProcessor.kt              # Segmentación de región de frutas/verduras, EXIF y recortes
│   │   ├── LearnedDatasetManager.kt       # Registro local de muestras aprendidas de Gemini
│   │   ├── LocalAnalysisCache.kt          # Caché local por hash perceptual dHash
│   │   └── LocalVisualForecastStore.kt    # Almacén local de reglas cromáticas aprendidas
│   └── remote/
│       ├── FirebaseGeminiDataSource.kt    # Inferencia multimodal con Gemini 2.5 Flash + memoria evolutiva
│       ├── FirebaseGeminiRepository.kt    # Orquestador de segmentación previa, Gemini y aprendizaje continuo
│       ├── GeminiJsonValidator.kt         # Validación JSON, rechazo de no-alimentos y filtro sanitario
│       └── VisualForecastRepository.kt    # Sincronización reactiva con Cloud Firestore (collective_forecasts)
├── domain/
│   ├── model/
│   │   ├── AnalysisEngineMode.kt          # Modos: Aprendizaje Evolutivo Gemini, Memoria Offline, Gemini Directo
│   │   ├── FoodAnalysis.kt                # Resultado integral e individualización multi-producto
│   │   ├── FoodProduct.kt                 # Catálogo de frutas y verduras (incluyendo Maracuyá) + soporte abierto
│   │   ├── NormalizedBoundingBox.kt       # Coordenadas normalizadas [0,1] y cálculo IoU
│   │   ├── RipenessState.kt               # Estados de madurez (Inmaduro, En maduración, Maduro, Sobremaduro)
│   │   ├── ShelfLifeEstimate.kt           # Rango de días estimados y recomendaciones
│   │   ├── StorageConditions.kt           # Condiciones térmicas, ubicación y preparación
│   │   └── VisualForecastRule.kt          # Regla cromática HSV aprendida y vector de características
│   ├── repository/
│   │   └── FoodAnalysisRepository.kt      # Contrato de repositorio de dominio
│   └── usecase/
│       ├── AnalyzeFoodUseCase.kt          # Caso de uso de análisis inicial
│       └── ImproveAnalysisUseCase.kt      # Caso de uso de recálculo con condiciones de almacenamiento
└── ui/
    ├── components/
    │   └── FreshScanComponents.kt         # Diálogo de corrección de fruta y madurez, badges y tarjetas
    ├── navigation/
    │   └── FreshScanRoutes.kt             # Rutas de navegación de la aplicación
    ├── screens/
    │   ├── HomeScreen.kt                  # Selección de modo de análisis y accesos principales
    │   ├── CameraScreen.kt                # Escáner fotográfico estable con guía de encuadre
    │   ├── ResultsScreen.kt               # Dashboard multi-producto, recortes, corrección y vida útil
    │   ├── AdvancedAnalysisScreen.kt      # Configuración de almacenamiento, temperatura y corte
    │   └── ConservationGuideScreen.kt     # Guía poscosecha offline
    └── viewmodel/
        └── FreshScanViewModel.kt          # Gestión de estado reactivo (StateFlow) y aprendizaje continuo
```

---

#### ANEXO B: Catálogo Base de Parámetros Postcosecha (`ConservationReferenceSource`)

| ID Producto | Producto | Vida Base Mesón (`20°C`) | Vida Base Refrigerado (`4°C`) | Sensible al Frío | Productor de Etileno |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `banana` | Plátano / Banano | 4.5 días | 6.5 días | Sí | Sí |
| `avocado` | Aguacate / Palta | 3.5 días | 8.0 días | Sí | Sí |
| `tomato` | Tomate / Jitomate | 5.0 días | 8.5 días | Sí | Sí |
| `apple` | Manzana | 9.0 días | 24.0 días | No | Sí |
| `strawberry` | Fresa / Frutilla | 1.5 días | 5.5 días | No | No |
| `mango` | Mango | 4.5 días | 9.0 días | Sí | Sí |
| `lemon` | Limón / Lima | 9.0 días | 24.0 días | Sí | No |
| `orange` | Naranja / Mandarina | 8.0 días | 18.0 días | No | No |
| `papaya` | Papaya | 3.5 días | 7.0 días | Sí | Sí |
| `passion_fruit` | Maracuyá / Granadilla | 6.0 días | 16.0 días | No | Sí |
| `pineapple` | Piña / Ananá | 3.5 días | 7.0 días | Sí | No |
| `grape` | Uva | 3.0 días | 12.0 días | No | No |
| `lettuce` | Lechuga / Hojas verdes | 1.5 días | 7.5 días | No | No |
| `pepper` | Pimiento / Morrón | 4.5 días | 11.0 días | Sí | No |
| `potato` | Papa / Patata | 16.0 días | 10.0 días | Sí | No |
| `carrot` | Zanahoria | 5.0 días | 21.0 días | No | No |
| `broccoli` | Brócoli | 1.5 días | 6.5 días | No | No |

---

#### ANEXO C: Guion Sugerido para la Presentación Oral (5 a 10 minutos ante el Jurado)

1. **Minuto 0:00 – 1:30 | Problemática y ODS:**
   - Explicar cómo el 40%–50% de las frutas y verduras se pierde o desperdicia por desconocimiento de su madurez real y método óptimo de conservación, impactando directamente el **ODS 12 (Meta 12.3)**, **ODS 2** y **ODS 3**.
2. **Minuto 1:30 – 3:30 | Segmentación Inteligente de Imagen en el Dispositivo:**
   - Explicar cómo `ImageProcessor.segmentProduceRegionBounds` analiza la fotografía antes de enviarla a la nube para recortar únicamente la región que contiene las frutas o verduras (descartando el fondo), optimizando tiempos de respuesta y precisión, y remapeando las coordenadas de vuelta a la foto original.
3. **Minuto 3:30 – 6:00 | IA Multimodal con Gemini y Aprendizaje Evolutivo Continuo:**
   - Detallar cómo el sistema reemplazó los clasificadores estáticos por un motor evolutivo (`AdaptiveVisualLearner`) que inicia vacío y aprende progresivamente de cada escaneo realizado con **Gemini 2.5 Flash** y de cada corrección manual del usuario, sincronizando las reglas aprendidas en la aplicación y en **Cloud Firestore**.
4. **Minuto 6:00 – 8:30 | Demostración en Vivo del Producto Final (`FreshScan AI`):**
   - Tomar una fotografía de una o varias frutas (por ejemplo, maracuyá, plátano o manzana), mostrar la detección e individualización con recortes, la posibilidad de corregir tanto el nombre como el estado de madurez, y el cálculo de días restantes según el método de almacenamiento.
5. **Minuto 8:30 – 10:00 | Impacto, Ética y Conclusiones:**
   - Destacar la privacidad por diseño (sincronización exclusiva de vectores cromáticos anónimos en la nube sin exponer fotografías privadas), el rechazo automático de objetos no alimentarios y el principio de precaución sanitaria.
