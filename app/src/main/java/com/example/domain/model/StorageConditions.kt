package com.example.domain.model

enum class StorageEnvironment(val displayName: String) {
    ROOM_TEMPERATURE("Temperatura ambiente"),
    COOL_PANTRY("Despensa fresca / seca"),
    REFRIGERATED("Refrigeración (nevera)")
}

enum class PreparationState(val displayName: String) {
    WHOLE("Entero / Sin cortar"),
    CUT_OR_PEELED("Cortado o pelado")
}

enum class FutureStoragePlan(val displayName: String) {
    KEEP_AT_ROOM_TEMP("Mantener a temp. ambiente"),
    REFRIGERATE_NOW("Guardar en refrigeración"),
    CONSUME_SOON("Consumir hoy / pronto"),
    FREEZE("Congelar")
}

data class StorageConditions(
    val approximateTemperatureCelsius: Float? = null,
    val storageEnvironment: StorageEnvironment = StorageEnvironment.ROOM_TEMPERATURE,
    val daysAlreadyStored: Int? = null,
    val preparationState: PreparationState = PreparationState.WHOLE,
    val futureStoragePlan: FutureStoragePlan = FutureStoragePlan.KEEP_AT_ROOM_TEMP
) {
    fun hasAnyInput(): Boolean {
        return approximateTemperatureCelsius != null ||
            daysAlreadyStored != null ||
            preparationState != PreparationState.WHOLE ||
            storageEnvironment != StorageEnvironment.ROOM_TEMPERATURE ||
            futureStoragePlan != FutureStoragePlan.KEEP_AT_ROOM_TEMP
    }
}
