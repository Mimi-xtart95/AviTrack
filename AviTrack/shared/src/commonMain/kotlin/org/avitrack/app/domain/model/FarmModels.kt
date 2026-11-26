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
    val placementEpochDay: Int = UNKNOWN_PLACEMENT_EPOCH_DAY,
    val placementAgeWeeks: Int = 18,
    val comfortTempMin: Double = 18.0,
    val comfortTempMax: Double = 28.0,
    val sensorDeviationMax: Double = 2.0,
) {
    init {
        require(initialBirdCount >= 0)
        require(placementAgeWeeks >= 0)
        require(comfortTempMin.isFinite() && comfortTempMax.isFinite() && comfortTempMin <= comfortTempMax)
        require(sensorDeviationMax.isFinite() && sensorDeviationMax >= 0.0)
    }

    companion object {
        const val UNKNOWN_PLACEMENT_EPOCH_DAY = Int.MAX_VALUE
    }
}

data class IntakeTargets(
    val targetFeedGramsPerBird: Double,
    val minFeedGramsPerBird: Double,
    val maxFeedGramsPerBird: Double,
    val minWaterCm3PerBird: Double,
    val maxWaterCm3PerBird: Double,
)

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
    /** Cumulative water meter reading in cubic meters. */
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
    val flockAgeWeeks: Int,
    val intakeTargets: IntakeTargets,
)

data class EarlyAlert(
    val barnId: String,
    val epochDay: Int,
    val type: AlertType,
    val severity: AlertSeverity,
)
