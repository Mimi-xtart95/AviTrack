package org.avitrack.app.domain.engine

import org.avitrack.app.domain.model.AlertSeverity
import org.avitrack.app.domain.model.AlertType
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.DailyMetrics
import org.avitrack.app.domain.model.EarlyAlert
import kotlin.math.abs

object ZootechnicalEngine {
    const val TARGET_FEED_GRAMS_PER_BIRD = 118.0

    private const val MIN_TEMPERATURE_CELSIUS = 18.0
    private const val MAX_TEMPERATURE_CELSIUS = 28.0
    private const val MAX_SENSOR_DRIFT_CELSIUS = 2.0
    private const val MAX_MORTALITY_PERCENTAGE = 0.02
    private const val SUSTAINED_MORTALITY_DAYS = 3
    private const val MIN_FEED_GRAMS_PER_BIRD = 110.0
    private const val MAX_FEED_GRAMS_PER_BIRD = 126.0
    private const val MIN_WATER_CUBIC_CENTIMETERS_PER_BIRD = 210.0
    private const val MAX_WATER_CUBIC_CENTIMETERS_PER_BIRD = 260.0
    private const val GRAMS_PER_KILOGRAM = 1_000.0
    private const val CUBIC_CENTIMETERS_PER_CUBIC_METER = 1_000_000.0
    private const val PERCENT_MULTIPLIER = 100.0

    /** Uses one log per farm-local date for the barn, through the desired cutoff date. */
    fun calculateAliveBirds(barn: Barn, logs: Iterable<DailyLog>): Int {
        val loggedDays = mutableSetOf<Int>()
        var aliveBirds = barn.initialBirdCount
        for (log in logs) {
            if (log.barnId != barn.id) continue
            require(loggedDays.add(log.epochDay)) { "Duplicate daily log for the barn." }
            require(log.mortalityCount <= aliveBirds) { "Cumulative mortality exceeds the initial flock." }
            aliveBirds -= log.mortalityCount
        }
        return aliveBirds
    }

    /** Uses the barn's earlier records from [history] and [log] as the entry for its date. */
    fun calculateDailyMetrics(
        barn: Barn,
        log: DailyLog,
        history: Iterable<DailyLog> = emptyList(),
    ): DailyMetrics {
        require(log.barnId == barn.id)
        val previousLogs = previousLogsFor(log, history)
        val previousLog = previousLogs.maxByOrNull { it.epochDay }
        return calculateMetrics(log, calculateAliveBirds(barn, previousLogs + log), previousLog)
    }

    /**
     * Uses the barn's history from the start of the flock.
     * Sustained mortality is critical. All other alerts are warnings.
     */
    fun evaluateAlerts(
        barn: Barn,
        log: DailyLog,
        history: Iterable<DailyLog> = emptyList(),
    ): List<EarlyAlert> {
        require(log.barnId == barn.id)
        val previousLogs = previousLogsFor(log, history)
        val previousLog = previousLogs.maxByOrNull { it.epochDay }
        val aliveBirds = calculateAliveBirds(barn, previousLogs + log)
        return buildList {
            fun addAlert(type: AlertType, severity: AlertSeverity = AlertSeverity.WARNING) {
                add(EarlyAlert(log.barnId, log.epochDay, type, severity))
            }

            if (minOf(log.analogTempMin, log.digitalTempMin) < MIN_TEMPERATURE_CELSIUS) {
                addAlert(AlertType.LOW_TEMPERATURE)
            }
            if (maxOf(log.analogTempMax, log.digitalTempMax) > MAX_TEMPERATURE_CELSIUS) {
                addAlert(AlertType.HIGH_TEMPERATURE)
            }
            if (exceedsSensorDrift(log.analogTempMin, log.digitalTempMin) ||
                exceedsSensorDrift(log.analogTempMax, log.digitalTempMax)
            ) {
                addAlert(AlertType.SENSOR_DRIFT)
            }
            if (hasSustainedMortality(log, aliveBirds, previousLogs)) {
                addAlert(AlertType.SUSTAINED_MORTALITY, AlertSeverity.CRITICAL)
            }
            if (aliveBirds > 0) {
                // Compare in input units so conversion roundoff does not trigger alerts at the limits.
                val minFeedKg = MIN_FEED_GRAMS_PER_BIRD * aliveBirds / GRAMS_PER_KILOGRAM
                val maxFeedKg = MAX_FEED_GRAMS_PER_BIRD * aliveBirds / GRAMS_PER_KILOGRAM
                when {
                    log.feedKg < minFeedKg -> addAlert(AlertType.LOW_FEED_INTAKE)
                    log.feedKg > maxFeedKg -> addAlert(AlertType.HIGH_FEED_INTAKE)
                }
                if (previousLog != null && dailyWaterM3(log, previousLog) != null) {
                    val daysElapsed = daysBetween(log, previousLog)
                    val volumeFactor = aliveBirds * daysElapsed / CUBIC_CENTIMETERS_PER_CUBIC_METER
                    val minReading = previousLog.waterM3 + MIN_WATER_CUBIC_CENTIMETERS_PER_BIRD * volumeFactor
                    val maxReading = previousLog.waterM3 + MAX_WATER_CUBIC_CENTIMETERS_PER_BIRD * volumeFactor
                    when {
                        log.waterM3 < minReading -> addAlert(AlertType.LOW_WATER_INTAKE)
                        log.waterM3 > maxReading -> addAlert(AlertType.HIGH_WATER_INTAKE)
                    }
                }
            }
        }
    }

    private fun previousLogsFor(log: DailyLog, history: Iterable<DailyLog>): List<DailyLog> =
        history.filter { it.barnId == log.barnId && it.epochDay < log.epochDay }

    private fun exceedsSensorDrift(analog: Double, digital: Double): Boolean =
        maxOf(analog, digital) > minOf(analog, digital) + MAX_SENSOR_DRIFT_CELSIUS

    private fun daysBetween(log: DailyLog, previousLog: DailyLog): Double =
        (log.epochDay.toLong() - previousLog.epochDay).coerceAtLeast(1).toDouble()

    private fun dailyWaterM3(log: DailyLog, previousLog: DailyLog): Double? {
        val volume = log.waterM3 - previousLog.waterM3
        return volume.takeIf { it.isFinite() && it >= 0.0 }?.div(daysBetween(log, previousLog))
    }

    private fun calculateMetrics(log: DailyLog, aliveBirds: Int, previousLog: DailyLog?): DailyMetrics {
        val denominator = aliveBirds.takeIf { it > 0 }
        return DailyMetrics(
            aliveBirds = aliveBirds,
            mortalityPercentage = denominator?.let { log.mortalityCount * PERCENT_MULTIPLIER / it },
            feedGramsPerBird = denominator?.let { log.feedKg * GRAMS_PER_KILOGRAM / it },
            waterCubicCentimetersPerBird = denominator?.let {
                previousLog?.let { previous -> dailyWaterM3(log, previous) }
                    ?.times(CUBIC_CENTIMETERS_PER_CUBIC_METER)?.div(it)
            },
            sensorDriftCelsius = maxOf(
                abs(log.digitalTempMin - log.analogTempMin),
                abs(log.digitalTempMax - log.analogTempMax),
            ),
        )
    }

    private fun hasSustainedMortality(
        log: DailyLog,
        aliveBirds: Int,
        previousLogs: List<DailyLog>,
    ): Boolean {
        val previousLogsByDay = previousLogs.associateBy { it.epochDay.toLong() }
        var closingBirdCount = aliveBirds
        for (daysAgo in 0 until SUSTAINED_MORTALITY_DAYS) {
            val dayLog = if (daysAgo == 0) {
                log
            } else {
                previousLogsByDay[log.epochDay.toLong() - daysAgo] ?: return false
            }
            // Notes are free text, so any nonblank entry is treated as an explanation.
            if (dayLog.notes.isNotBlank()) return false
            if (dayLog.mortalityCount * PERCENT_MULTIPLIER <= closingBirdCount * MAX_MORTALITY_PERCENTAGE) {
                return false
            }
            closingBirdCount += dayLog.mortalityCount
        }
        return true
    }
}
