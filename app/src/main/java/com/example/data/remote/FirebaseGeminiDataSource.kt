package com.example.data.remote

import android.graphics.Bitmap
import com.example.domain.model.FoodProduct
import com.example.domain.model.StorageConditions
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

interface GeminiAnalysisDataSource {
    suspend fun analyzeImageJson(
        bitmap: Bitmap,
        storageConditions: StorageConditions? = null,
        manualProductOverride: String? = null,
        learnedMemoryContext: String? = null
    ): String

    fun warmUp() {}
}

class FirebaseGeminiDataSource : GeminiAnalysisDataSource {

    private val detectedItemSchema = Schema.obj(
        mapOf(
            "producto" to Schema.string("Nombre específico de esta fruta o verdura individual en español (ej. Maracuyá, Plátano, Manzana, Palta)"),
            "identificacion_confiable" to Schema.boolean("true ÚNICAMENTE si es realmente una fruta, verdura, hortaliza o tubérculo claramente visible"),
            "madurez" to Schema.enumeration(
                listOf("inmaduro", "en_maduracion", "maduro", "sobremaduro", "indeterminado"),
                "Estado de madurez específico de ESTA unidad individual"
            ),
            "observaciones_visuales" to Schema.array(
                Schema.string("Rasgo visual observado en esta unidad específica (color, manchas, piel)")
            ),
            "posible_deterioro" to Schema.boolean("true si esta unidad específica muestra deterioro visible"),
            "senales_deterioro" to Schema.array(
                Schema.string("Señal de deterioro en esta unidad")
            ),
            "vida_util_min" to Schema.integer("Días mínimos restantes para esta unidad"),
            "vida_util_max" to Schema.integer("Días máximos restantes para esta unidad"),
            "dias_hasta_madurez_min" to Schema.integer("Días mínimos hasta madurez óptima para esta unidad (0 si ya maduro)"),
            "dias_hasta_madurez_max" to Schema.integer("Días máximos hasta madurez óptima para esta unidad (0 si ya maduro)"),
            "recomendaciones" to Schema.array(
                Schema.string("Recomendación práctica para esta unidad")
            ),
            "box_ymin" to Schema.integer("Coordenada vertical superior normalizada (0 a 1000) de esta unidad"),
            "box_xmin" to Schema.integer("Coordenada horizontal izquierda normalizada (0 a 1000) de esta unidad"),
            "box_ymax" to Schema.integer("Coordenada vertical inferior normalizada (0 a 1000) de esta unidad"),
            "box_xmax" to Schema.integer("Coordenada horizontal derecha normalizada (0 a 1000) de esta unidad")
        ),
        optionalProperties = listOf(
            "vida_util_min",
            "vida_util_max",
            "dias_hasta_madurez_min",
            "dias_hasta_madurez_max"
        )
    )

    private val responseSchema = Schema.obj(
        mapOf(
            "producto" to Schema.string("Nombre específico del alimento principal en español, o 'No se detectó fruta ni verdura' si no hay ningún alimento fresco en la imagen"),
            "identificacion_confiable" to Schema.boolean("true ÚNICAMENTE si en la imagen aparece al menos una fruta, verdura o tubérculo real; false si es un teclado, mano vacía, objeto inanimado o imagen borrosa sin fruta"),
            "multiples_productos_o_ambiguo" to Schema.boolean("true si hay múltiples frutas/verduras distintas o varias unidades"),
            "madurez" to Schema.enumeration(
                listOf("inmaduro", "en_maduracion", "maduro", "sobremaduro", "indeterminado"),
                "Estado de madurez del alimento principal"
            ),
            "observaciones_visuales" to Schema.array(
                Schema.string("Observación visual concreta sobre color, textura, brillo, manchas o firmeza aparente")
            ),
            "posible_deterioro" to Schema.boolean("true si se observan signos de moho, pudrición, hundimientos o daño severo"),
            "senales_deterioro" to Schema.array(
                Schema.string("Señal específica de deterioro visible")
            ),
            "vida_util_min" to Schema.integer("Días mínimos de vida útil restante"),
            "vida_util_max" to Schema.integer("Días máximos de vida útil restante"),
            "dias_hasta_madurez_min" to Schema.integer("Días mínimos para alcanzar madurez óptima (0 si ya está maduro)"),
            "dias_hasta_madurez_max" to Schema.integer("Días máximos para alcanzar madurez óptima (0 si ya está maduro)"),
            "estimacion_validada" to Schema.boolean("true si la estimación de vida útil es confiable"),
            "recomendaciones" to Schema.array(
                Schema.string("Recomendación práctica de conservación o consumo en español")
            ),
            "limitaciones" to Schema.array(
                Schema.string("Limitación honesta del análisis visual")
            ),
            "box_ymin" to Schema.integer("Coordenada superior normalizada (0 a 1000) del alimento principal"),
            "box_xmin" to Schema.integer("Coordenada izquierda normalizada (0 a 1000) del alimento principal"),
            "box_ymax" to Schema.integer("Coordenada inferior normalizada (0 a 1000) del alimento principal"),
            "box_xmax" to Schema.integer("Coordenada derecha normalizada (0 a 1000) del alimento principal"),
            "productos_detectados" to Schema.array(
                detectedItemSchema,
                "Lista completa de CADA fruta o verdura individual visible en la imagen con su propio recuadro (box_ymin..box_xmax) y su propia madurez individual"
            )
        ),
        optionalProperties = listOf(
            "vida_util_min",
            "vida_util_max",
            "dias_hasta_madurez_min",
            "dias_hasta_madurez_max",
            "box_ymin",
            "box_xmin",
            "box_ymax",
            "box_xmax",
            "productos_detectados"
        )
    )

    private val config: GenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = this@FirebaseGeminiDataSource.responseSchema
        temperature = 0.1f
        maxOutputTokens = 1600
    }

    private val modelsByPriority: List<GenerativeModel> by lazy {
        val aiService = Firebase.ai(backend = GenerativeBackend.googleAI())
        listOf(
            aiService.generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = config
            ),
            aiService.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = config
            ),
            aiService.generativeModel(
                modelName = "gemini-2.5-flash-lite",
                generationConfig = config
            )
        )
    }

    override fun warmUp() {
        try {
            modelsByPriority.firstOrNull()?.hashCode()
        } catch (_: Exception) {
        }
    }

    override suspend fun analyzeImageJson(
        bitmap: Bitmap,
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        learnedMemoryContext: String?
    ): String {
        val prompt = buildPrompt(storageConditions, manualProductOverride, learnedMemoryContext)
        var lastError: Throwable? = null

        for (model in modelsByPriority) {
            try {
                val response = withTimeout(35_000L) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    return text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No se pudo obtener respuesta de Gemini")
    }

    private fun buildPrompt(
        storageConditions: StorageConditions?,
        manualProductOverride: String?,
        learnedMemoryContext: String?
    ): String {
        val matchedProduct = FoodProduct.findMatchingProduct(manualProductOverride)
        val speciesCriteria = matchedProduct?.let {
            "\nCriterio fisiológico para ${it.canonicalName}: ${it.speciesRipenessCriteria}"
        } ?: ""

        val overrideSection = if (!manualProductOverride.isNullOrBlank()) {
            "\nCORRECCIÓN CONFIRMADA POR EL USUARIO: El alimento en la imagen es '$manualProductOverride'. Evalúa obligatoriamente este alimento como '$manualProductOverride' y calcula su madurez y vida útil correspondientes.$speciesCriteria"
        } else {
            ""
        }

        val learnedSection = if (!learnedMemoryContext.isNullOrBlank()) {
            "\n$learnedMemoryContext"
        } else {
            ""
        }

        val storageSection = if (storageConditions != null) {
            """
            Condiciones de almacenamiento reportadas por el usuario:
            - Ambiente actual: ${storageConditions.storageEnvironment.displayName}
            - Temperatura aproximada: ${storageConditions.approximateTemperatureCelsius?.let { "$it °C" } ?: "No especificada"}
            - Días ya almacenados: ${storageConditions.daysAlreadyStored ?: 0}
            - Estado de preparación: ${storageConditions.preparationState.displayName}
            - Plan futuro: ${storageConditions.futureStoragePlan.displayName}
            Ajusta tu evaluación visual y recomendaciones considerando estas condiciones.
            """.trimIndent()
        } else {
            ""
        }

        return """
            Eres un especialista en fisiología poscosecha e inocuidad alimentaria de frutas, verduras, hortalizas y tubérculos frescos.
            Analiza cuidadosamente la imagen proporcionada y responde SIEMPRE en español neutro claro.
            $overrideSection
            $learnedSection
            $storageSection

            REGLAS CRÍTICAS DE PRECISIÓN Y RECHAZO DE FALSOS POSITIVOS:
            1. VERIFICACIÓN ESTRICTA DE PRESENCIA DE FRUTA O VERDURA: Si en la imagen NO hay ninguna fruta, verdura, hortaliza o tubérculo real (por ejemplo, si solo se ve una laptop, teclado, pantalla, mano vacía, mesa vacía, pared o cualquier objeto no alimenticio), DEBES devolver `identificacion_confiable = false`, `producto = "No se detectó fruta ni verdura"`, `madurez = "indeterminado"` y `productos_detectados = []`. ¡Jamás inventes una fruta ni la aproximes por color si no hay una fruta/verdura real!
            2. IDENTIFICACIÓN EXACTA SIN FORZAR A OTRA ESPECIE: Identifica la fruta o verdura por su nombre exacto real (ej. Maracuyá, Granadilla, Pitahaya, Chirimoya, Lúcuma, Tuna, Plátano, Manzana, Naranja, Mandarina, Palta/Aguacate, Mango, Papaya, Tomate, etc.).
               - Especial cuidado con el **Maracuyá** (*Passiflora edulis*): tiene forma ovalada/redondeada, cáscara amarilla o verde-amarillenta de superficie lisa cerosa (o ligeramente arrugada cuando está bien maduro y jugoso) sin los poros glandulares típicos de la naranja o mandarina. No confundas un maracuyá con naranja ni mandarina.
            3. DETECCIÓN MULTI-PRODUCTO E INDIVIDUALIZACIÓN:
               - Si aparecen 2 o más frutas/verduras en la imagen, marca `multiples_productos_o_ambiguo = true` e incluye CADA UNIDAD por separado dentro de `productos_detectados` (máximo 6 unidades principales).
               - Para CADA elemento en `productos_detectados`, devuelve sus propias coordenadas exactas `box_ymin`, `box_xmin`, `box_ymax`, `box_xmax` en escala 0 a 1000, su propio estado de `madurez`, sus `observaciones_visuales` específicas y su propia estimación de `vida_util_min`/`vida_util_max`.
            4. INOCUIDAD ALIMENTARIA: Sé prudente y científicamente honesto. Si observas moho, pudrición blanda, exudado o solanina (verde en papas), marca `posible_deterioro = true` y advierte no consumir.
        """.trimIndent()
    }
}
