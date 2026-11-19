package org.avitrack.app.i18n

import androidx.compose.runtime.compositionLocalOf
import org.avitrack.app.domain.model.AlertType
import org.avitrack.app.domain.model.SyncStatus

enum class AppLanguage(val code: String, val label: String) {
    EN("en", "English"),
    ES("es", "Español"),
}

data class AlertText(val title: String, val description: String)

data class AppStrings(
    val barnMode: String,
    val officeDashboard: String,
    val language: String,
    val barn: String,
    val breed: String,
    val date: String,
    val initialBirdCount: String,
    val aliveBirds: String,
    val analogTempMin: String,
    val analogTempMax: String,
    val digitalTempMin: String,
    val digitalTempMax: String,
    val relativeHumidityAvg: String,
    val mortalityCount: String,
    val feedKg: String,
    val waterM3: String,
    val litterPercentage: String,
    val notes: String,
    val celsiusUnit: String,
    val percentUnit: String,
    val birdsUnit: String,
    val birdsPerDayUnit: String,
    val kilogramsPerDayUnit: String,
    val cubicMetersPerDayUnit: String,
    val gramsPerBirdPerDayUnit: String,
    val cubicCentimetersPerBirdPerDayUnit: String,
    val offline: String,
    val pending: String,
    val synced: String,
    val syncFailed: String,
    val save: String,
    val sync: String,
    val export: String,
    val activeAlerts: String,
    val noActiveAlerts: String,
    private val lowTemperature: AlertText,
    private val highTemperature: AlertText,
    private val sensorDrift: AlertText,
    private val sustainedMortality: AlertText,
    private val lowFeedIntake: AlertText,
    private val highFeedIntake: AlertText,
    private val lowWaterIntake: AlertText,
    private val highWaterIntake: AlertText,
    private val lowHumidity: AlertText,
    private val highHumidity: AlertText,
) {
    fun alertText(type: AlertType): AlertText = when (type) {
        AlertType.LOW_TEMPERATURE -> lowTemperature
        AlertType.HIGH_TEMPERATURE -> highTemperature
        AlertType.SENSOR_DRIFT -> sensorDrift
        AlertType.SUSTAINED_MORTALITY -> sustainedMortality
        AlertType.LOW_FEED_INTAKE -> lowFeedIntake
        AlertType.HIGH_FEED_INTAKE -> highFeedIntake
        AlertType.LOW_WATER_INTAKE -> lowWaterIntake
        AlertType.HIGH_WATER_INTAKE -> highWaterIntake
        AlertType.LOW_HUMIDITY -> lowHumidity
        AlertType.HIGH_HUMIDITY -> highHumidity
    }

    fun syncStatusText(status: SyncStatus): String = when (status) {
        SyncStatus.PENDING -> pending
        SyncStatus.SYNCED -> synced
        SyncStatus.FAILED -> syncFailed
    }
}

val EnglishStrings = AppStrings(
    barnMode = "Barn Mode",
    officeDashboard = "Office Dashboard",
    language = "Language",
    barn = "Barn",
    breed = "Strain",
    date = "Date",
    initialBirdCount = "Initial flock size",
    aliveBirds = "Live birds",
    analogTempMin = "Analog minimum temperature",
    analogTempMax = "Analog maximum temperature",
    digitalTempMin = "Digital minimum temperature",
    digitalTempMax = "Digital maximum temperature",
    relativeHumidityAvg = "Average relative humidity",
    mortalityCount = "Daily mortality",
    feedKg = "Daily feed consumption",
    waterM3 = "Daily water consumption",
    litterPercentage = "Litter percentage",
    notes = "Notes",
    celsiusUnit = "°C",
    percentUnit = "%",
    birdsUnit = "birds",
    birdsPerDayUnit = "birds/day",
    kilogramsPerDayUnit = "kg/day",
    cubicMetersPerDayUnit = "m³/day",
    gramsPerBirdPerDayUnit = "g/bird/day",
    cubicCentimetersPerBirdPerDayUnit = "cm³/bird/day",
    offline = "Offline",
    pending = "Pending",
    synced = "Synced",
    syncFailed = "Sync failed",
    save = "Save",
    sync = "Sync",
    export = "Export",
    activeAlerts = "Active alerts",
    noActiveAlerts = "No active alerts",
    lowTemperature = AlertText("Low temperature", "A temperature reading fell below 18 °C."),
    highTemperature = AlertText("High temperature", "A temperature reading exceeded 28 °C."),
    sensorDrift = AlertText("Sensor drift", "Matching analog and digital readings differ by more than 2 °C."),
    sustainedMortality = AlertText(
        "Sustained mortality",
        "Daily mortality exceeded 0.02% of live birds for at least three consecutive days without explanatory notes.",
    ),
    lowFeedIntake = AlertText("Low feed intake", "Daily feed intake fell below 110 g per bird."),
    highFeedIntake = AlertText("High feed intake", "Daily feed intake exceeded 126 g per bird."),
    lowWaterIntake = AlertText("Low water intake", "Daily water intake fell below 210 cm³ per bird."),
    highWaterIntake = AlertText("High water intake", "Daily water intake exceeded 260 cm³ per bird."),
    lowHumidity = AlertText("Low humidity", "Average relative humidity fell below 50%."),
    highHumidity = AlertText("High humidity", "Average relative humidity exceeded 75%."),
)

val SpanishStrings = AppStrings(
    barnMode = "Modo nave",
    officeDashboard = "Panel de oficina",
    language = "Idioma",
    barn = "Nave",
    breed = "Estirpe",
    date = "Fecha",
    initialBirdCount = "Censo inicial",
    aliveBirds = "Aves vivas",
    analogTempMin = "Temperatura mínima analógica",
    analogTempMax = "Temperatura máxima analógica",
    digitalTempMin = "Temperatura mínima digital",
    digitalTempMax = "Temperatura máxima digital",
    relativeHumidityAvg = "Humedad relativa media",
    mortalityCount = "Bajas diarias",
    feedKg = "Consumo diario de pienso",
    waterM3 = "Consumo diario de agua",
    litterPercentage = "Porcentaje de cama",
    notes = "Notas",
    celsiusUnit = "°C",
    percentUnit = "%",
    birdsUnit = "aves",
    birdsPerDayUnit = "aves/día",
    kilogramsPerDayUnit = "kg/día",
    cubicMetersPerDayUnit = "m³/día",
    gramsPerBirdPerDayUnit = "g/ave/día",
    cubicCentimetersPerBirdPerDayUnit = "cm³/ave/día",
    offline = "Sin conexión",
    pending = "Pendiente",
    synced = "Sincronizado",
    syncFailed = "Error de sincronización",
    save = "Guardar",
    sync = "Sincronizar",
    export = "Exportar",
    activeAlerts = "Alertas activas",
    noActiveAlerts = "No hay alertas activas",
    lowTemperature = AlertText("Temperatura baja", "Una lectura de temperatura bajó de 18 °C."),
    highTemperature = AlertText("Temperatura alta", "Una lectura de temperatura superó los 28 °C."),
    sensorDrift = AlertText(
        "Desviación entre sensores",
        "Las lecturas analógica y digital correspondientes difieren en más de 2 °C.",
    ),
    sustainedMortality = AlertText(
        "Mortalidad sostenida",
        "Las bajas diarias superaron el 0,02 % de las aves vivas durante al menos tres días seguidos sin notas explicativas.",
    ),
    lowFeedIntake = AlertText("Consumo bajo de pienso", "El consumo diario de pienso bajó de 110 g por ave."),
    highFeedIntake = AlertText("Consumo alto de pienso", "El consumo diario de pienso superó los 126 g por ave."),
    lowWaterIntake = AlertText("Consumo bajo de agua", "El consumo diario de agua bajó de 210 cm³ por ave."),
    highWaterIntake = AlertText("Consumo alto de agua", "El consumo diario de agua superó los 260 cm³ por ave."),
    lowHumidity = AlertText("Humedad baja", "La humedad relativa media bajó del 50 %."),
    highHumidity = AlertText("Humedad alta", "La humedad relativa media superó el 75 %."),
)

val LocalStrings = compositionLocalOf { EnglishStrings }
