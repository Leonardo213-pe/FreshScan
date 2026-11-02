package com.example.domain.model

enum class AnalysisEngineMode(
    val shortTitle: String,
    val description: String
) {
    HYBRID_YOLO_GEMINI(
        shortTitle = "Híbrido (YOLO + Gemini)",
        description = "Detección local ultrarrápida con YOLOv8 + análisis visual profundo con Gemini."
    ),
    YOLO_LOCAL_ONLY(
        shortTitle = "Solo YOLO Local",
        description = "Reconocimiento 100% en el dispositivo sin conexión a internet."
    ),
    GEMINI_CLOUD_ONLY(
        shortTitle = "Solo Gemini Cloud",
        description = "Evaluación directa en la nube con Gemini Vision."
    )
}
