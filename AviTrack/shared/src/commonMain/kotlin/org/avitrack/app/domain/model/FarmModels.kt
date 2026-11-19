package org.avitrack.app.domain.model

private const val MAX_LITTER_PERCENTAGE = 100
private const val LITTER_PERCENTAGE_STEP = 10

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED,
}

enum class AlertSeverity {
    WARNING,
    CRITICAL,
}

enum class AlertType {
    LOW_TEMPERATURE,
    HIGH_TEMPERATURE,
    SENSOR_DRIFT,
    SUSTAINED_MORTALITY,
    LOW_FEED_INTAKE,
    HIGH_FEED_INTAKE,
    LOW_WATER_INTAKE,
    HIGH_WATER_INTAKE,
    LOW_HUMIDITY,
    HIGH_HUMIDITY,
}

data class Barn(
    val id: String,
    val name: String,
    val initialBirdCount: Int,
) {
    init {
        require(initialBirdCount >= 0)
    }
}

/** The epoch day represents the farm's local calendar date, measured from 1970-01-01. */
data class DailyLog(
    val barnId: String,
    val epochDay: Int,
    val analogTempMin: Double,
    val analogTempMax: Double,
    val digitalTempMin: Double,
    val digitalTempMax: Double,
    val relativeHumidityAvg: Double,
    val mortalityCount: Int,
    val feedKg: Double,
    val waterM3: Double,
    val litterPercentage: Int,
    val notes: String = "",
    val syncStatus: SyncStatus = SyncStatus.PENDING,
) {
    init {
        require(mortalityCount >= 0)
        require(litterPercentage in 0..MAX_LITTER_PERCENTAGE)
        require(litterPercentage % LITTER_PERCENTAGE_STEP == 0)
    }
}

/** Per-bird metrics use the closing flock count and are null when the flock is empty. */
data class DailyMetrics(
    val aliveBirds: Int,
    val mortalityPercentage: Double?,
    val feedGramsPerBird: Double?,
    val waterCubicCentimetersPerBird: Double?,
    val sensorDriftCelsius: Double,
)

data class EarlyAlert(
    val barnId: String,
    val epochDay: Int,
    val type: AlertType,
    val severity: AlertSeverity,
)
