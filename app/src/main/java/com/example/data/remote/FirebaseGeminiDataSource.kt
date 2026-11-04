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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    private val networkCallScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val detectedItemSchema = Schema.obj(
        mapOf(
            "producto" to Schema.string("Nombre en español de esta fruta o verdura (ej. Pepino, Pepinillo, Maracuyá, Plátano, Manzana)"),
            "identificacion_confiable" to Schema.boolean("true si es una fruta, verdura u hortaliza real"),
            "madurez" to Schema.enumeration(
                listOf("inmaduro", "en_maduracion", "maduro", "sobremaduro", "indeterminado"),
                "Estado de madurez de esta unidad"
            ),
            "observaciones_visuales" to Schema.array(
                Schema.string("Rasgo visual observado")
            ),
            "posible_deterioro" to Schema.boolean("true si muestra deterioro visible"),
            "senales_deterioro" to Schema.array(
                Schema.string("Señal de deterioro")
            ),
            "vida_util_min" to Schema.integer("Días mínimos restantes"),
            "vida_util_max" to Schema.integer("Días máximos restantes"),
            "dias_hasta_madurez_min" to Schema.integer("Días mínimos hasta madurez óptima"),
            "dias_hasta_madurez_max" to Schema.integer("Días máximos hasta madurez óptima"),
            "recomendaciones" to Schema.array(
                Schema.string("Recomendación de conservación")
            ),
            "box_ymin" to Schema.integer("Coordenada superior (0..1000)"),
            "box_xmin" to Schema.integer("Coordenada izquierda (0..1000)"),
            "box_ymax" to Schema.integer("Coordenada inferior (0..1000)"),
            "box_xmax" to Schema.integer("Coordenada derecha (0..1000)")
        ),
        optionalProperties = listOf(
            "observaciones_visuales",
            "senales_deterioro",
            "vida_util_min",
            "vida_util_max",
            "dias_hasta_madurez_min",
            "dias_hasta_madurez_max",
            "recomendaciones",
            "box_ymin",
            "box_xmin",
            "box_ymax",
            "box_xmax"
        )
    )

    private val responseSchema = Schema.obj(
        mapOf(
            "producto" to Schema.string("Nombre específico del alimento principal en español (ej. Pepinillo, Pepino, Plátano, Maracuyá), o 'No se detectó fruta ni verdura'"),
            "identificacion_confiable" to Schema.boolean("true ÚNICAMENTE si aparece al menos una fruta, verdura, hortaliza o tubérculo real"),
            "multiples_productos_o_ambiguo" to Schema.boolean("true si hay múltiples frutas/verduras o varias unidades"),
            "madurez" to Schema.enumeration(
                listOf("inmaduro", "en_maduracion", "maduro", "sobremaduro", "indeterminado"),
                "Estado de madurez del alimento principal"
            ),
            "observaciones_visuales" to Schema.array(
                Schema.string("Observación visual sobre color, textura, firmeza o manchas")
            ),
            "posible_deterioro" to Schema.boolean("true si se observan signos de moho, pudrición o daño severo"),
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
                "Lista de cada fruta o verdura individual visible en la imagen"
            )
        ),
        optionalProperties = listOf(
            "senales_deterioro",
            "vida_util_min",
            "vida_util_max",
            "dias_hasta_madurez_min",
            "dias_hasta_madurez_max",
            "estimacion_validada",
            "limitaciones",
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
        maxOutputTokens = 1024
    }

    private data class PriorityModelEntry(
        val model: GenerativeModel,
        val timeoutMs: Long
    )

    private val modelsByPriority: List<PriorityModelEntry> by lazy {
        val aiService = Firebase.ai(backend = GenerativeBackend.googleAI())
        listOf(
            PriorityModelEntry(
                model = aiService.generativeModel(
                    modelName = "gemini-3.8-flash",
                    generationConfig = config
                ),
                timeoutMs = PRIMARY_MODEL_TIMEOUT_MS
            ),
            PriorityModelEntry(
                model = aiService.generativeModel(
                    modelName = "gemini-3.5-flash-lite",
                    generationConfig = config
                ),
                timeoutMs = FALLBACK_MODEL_TIMEOUT_MS
            )
        )
    }

    override fun warmUp() {
        try {
            modelsByPriority.firstOrNull()?.model?.hashCode()
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

        for (entry in modelsByPriority) {
            val deferred = networkCallScope.async {
                entry.model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            try {
                val response = withTimeout(entry.timeoutMs) {
                    deferred.await()
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    return text
                }
            } catch (e: CancellationException) {
                deferred.cancel()
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    lastError = e
                    continue
                }
                throw e
            } catch (e: Throwable) {
                deferred.cancel()
                lastError = e
            } finally {
                if (deferred.isActive) {
                    deferred.cancel()
                }
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
            Analiza cuidadosamente la imagen proporcionada y responde SIEMPRE en español neutro claro y conciso.
            $overrideSection
            $learnedSection
            $storageSection

            REGLAS CRÍTICAS DE PRECISIÓN Y RECHAZO DE FALSOS POSITIVOS:
            1. VERIFICACIÓN ESTRICTA DE PRESENCIA DE FRUTA O VERDURA: Si en la imagen NO hay ninguna fruta, verdura, hortaliza o tubérculo real (por ejemplo, laptop, teclado, pantalla, mano vacía, mesa vacía, pared u objeto no alimenticio), DEBES devolver `identificacion_confiable = false`, `producto = "No se detectó fruta ni verdura"`, `madurez = "indeterminado"` y `productos_detectados = []`.
            2. IDENTIFICACIÓN EXACTA SIN FORZAR A OTRA ESPECIE: Identifica la fruta, verdura u hortaliza por su nombre real exacto en español (ej. Pepino, Pepinillo, Maracuyá, Granadilla, Pitahaya, Plátano, Manzana, Naranja, Mandarina, Palta/Aguacate, Mango, Papaya, Tomate, Pimiento, Brócoli, Zanahoria, Papa, Cebolla, etc.).
               - **Pepino / Pepinillo** (*Cucumis sativus*): hortaliza cilíndrica alargada de piel verde oscura o verde con estrías/pequeños tubérculos superficiales; cuando está firme y verde es `madurez = "maduro"` (óptimo para consumo fresco), y si presenta amarillamiento longitudinal o extremos arrugados/blandos es `sobremaduro`.
               - **Maracuyá** (*Passiflora edulis*): forma ovalada/redondeada, cáscara amarilla o verde-amarillenta de superficie lisa cerosa o ligeramente arrugada al madurar; no confundir con naranja ni mandarina.
            3. DETECCIÓN MULTI-PRODUCTO E INDIVIDUALIZACIÓN:
               - Si aparecen 2 o más frutas/verduras en la imagen, marca `multiples_productos_o_ambiguo = true` e incluye cada unidad en `productos_detectados` (máximo 6 unidades principales) con sus coordenadas `box_ymin`, `box_xmin`, `box_ymax`, `box_xmax` en escala 0 a 1000 y su `madurez`.
               - Si solo hay 1 fruta o verdura, puedes omitir `productos_detectados` o incluir esa única unidad para responder más rápido.
            4. INOCUIDAD ALIMENTARIA: Si observas moho, pudrición blanda, exudado o solanina (verde en papas), marca `posible_deterioro = true` y advierte no consumir.
        """.trimIndent()
    }

    companion object {
        const val PRIMARY_MODEL_TIMEOUT_MS = 14_000L
        const val FALLBACK_MODEL_TIMEOUT_MS = 10_000L
    }
}

