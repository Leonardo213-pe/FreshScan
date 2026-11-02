package com.example.domain.model

enum class RipenessState(
    val displayName: String,
    val shortDescription: String
) {
    UNRIPE(
        displayName = "Inmaduro",
        shortDescription = "El alimento aún no ha alcanzado su punto óptimo de consumo y requiere tiempo de maduración."
    ),
    RIPENING(
        displayName = "En proceso de maduración",
        shortDescription = "Transición activa hacia la madurez; próximo a su punto ideal de consumo."
    ),
    RIPE(
        displayName = "Maduro",
        shortDescription = "En su punto visual óptimo para consumo inmediato o conservación a corto plazo."
    ),
    OVERRIPE(
        displayName = "Sobremaduro",
        shortDescription = "Ha superado su madurez óptima; presenta senescencia avanzada y vida útil muy reducida."
    ),
    UNDETERMINED(
        displayName = "Indeterminado",
        shortDescription = "La imagen o el tipo de alimento no permite determinar visualmente el estado de maduración con certeza."
    );

    companion object {
        fun fromRawString(raw: String?): RipenessState {
            if (raw.isNullOrBlank()) return UNDETERMINED
            val normalized = raw.trim().lowercase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")

            return when {
                normalized == "inmaduro" || normalized == "verde" || normalized == "unripe" -> UNRIPE
                normalized.contains("proceso") || normalized.contains("maduracion") || normalized == "madurando" || normalized == "ripening" -> RIPENING
                normalized == "sobremaduro" || normalized.contains("sobre maduro") ||
                    normalized.contains("pasado") || normalized == "overripe" -> OVERRIPE
                normalized == "maduro" || normalized == "ripe" -> RIPE
                normalized == "indeterminado" || normalized == "undetermined" -> UNDETERMINED
                else -> UNDETERMINED
            }
        }

        fun fromApiString(raw: String?): RipenessState = fromRawString(raw)
    }
}
