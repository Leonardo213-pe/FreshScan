package com.example.domain.model

enum class AnalysisEngineMode(
    val shortTitle: String,
    val description: String
) {
    GEMINI_EVOLUTIONARY(
        shortTitle = "Gemini + Aprendizaje Evolutivo",
        description = "Segmentación de alimento + análisis con Gemini que entrena la memoria en la nube y en la app."
    ),
    LEARNED_MEMORY_OFFLINE(
        shortTitle = "Memoria Aprendida (Offline)",
        description = "Evalúa usando únicamente las frutas que el sistema ya aprendió previamente de Gemini."
    ),
    GEMINI_CLOUD_ONLY(
        shortTitle = "Solo Gemini Cloud",
        description = "Evaluación directa en la nube con Gemini Vision y segmentación de región."
    )
}
