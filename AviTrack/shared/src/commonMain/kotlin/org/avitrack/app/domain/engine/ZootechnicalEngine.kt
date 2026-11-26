package org.avitrack.app.domain.engine

import org.avitrack.app.domain.model.AlertSeverity
import org.avitrack.app.domain.model.AlertType
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.DailyMetrics
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.domain.model.IntakeTargets
import kotlin.math.abs

object ZootechnicalEngine {
    private const val DAYS_PER_WEEK = 7
    private const val MAX_MORTALITY_PERCENTAGE = 0.02
    private const val SUSTAINED_MORTALITY_DAYS = 3
    private const val GRAMS_PER_KILOGRAM = 1_000.0
    private const val CUBIC_CENTIMETERS_PER_CUBIC_METER = 1_000_000.0
    private const val PERCENT_MULTIPLIER = 100.0

    fun flockAgeWeeksAt(barn: Barn, epochDay: Int): Int {
        val daysSincePlacement = (epochDay.toLong() - barn.placementEpochDay).coerceAtLeast(0)
        return (barn.placementAgeWeeks + daysSincePlacement / DAYS_PER_WEEK).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun standardIntakeTargetsForWeek(ageWeeks: Int): IntakeTargets = when {
        ageWeeks < 18 -> IntakeTargets(82.0, 74.0, 92.0, 150.0, 195.0)
        ageWeeks <= 19 -> IntakeTargets(94.0, 84.0, 104.0, 170.0, 220.0)
        ageWeeks <= 22 -> IntakeTargets(108.0, 96.0, 118.0, 195.0, 245.0)
        ageWeeks <= 50 -> IntakeTargets(118.0, 105.0, 130.0, 210.0, 260.0)
        else -> IntakeTargets(114.0, 102.0, 126.0, 205.0, 255.0)
    }

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
        return calculateMetrics(barn, log, calculateAliveBirds(barn, previousLogs + log), previousLog)
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
        val targets = standardIntakeTargetsForWeek(flockAgeWeeksAt(barn, log.epochDay))
        return buildList {
            fun addAlert(type: AlertType, severity: AlertSeverity = AlertSeverity.WARNING) {
                add(EarlyAlert(log.barnId, log.epochDay, type, severity))
            }

            if (minOf(log.analogTempMin, log.digitalTempMin) < barn.comfortTempMin) {
                addAlert(AlertType.LOW_TEMPERATURE)
            }
            if (maxOf(log.analogTempMax, log.digitalTempMax) > barn.comfortTempMax) {
                addAlert(AlertType.HIGH_TEMPERATURE)
            }
            if (exceedsSensorDrift(log.analogTempMin, log.digitalTempMin, barn.sensorDeviationMax) ||
                exceedsSensorDrift(log.analogTempMax, log.digitalTempMax, barn.sensorDeviationMax)
            ) {
                addAlert(AlertType.SENSOR_DRIFT)
            }
            if (hasSustainedMortality(log, aliveBirds, previousLogs)) {
                addAlert(AlertType.SUSTAINED_MORTALITY, AlertSeverity.CRITICAL)
            }
            if (aliveBirds > 0) {
                // Compare in input units so conversion roundoff does not trigger alerts at the limits.
                val minFeedKg = targets.minFeedGramsPerBird * aliveBirds / GRAMS_PER_KILOGRAM
                val maxFeedKg = targets.maxFeedGramsPerBird * aliveBirds / GRAMS_PER_KILOGRAM
                when {
                    log.feedKg < minFeedKg -> addAlert(AlertType.LOW_FEED_INTAKE)
                    log.feedKg > maxFeedKg -> addAlert(AlertType.HIGH_FEED_INTAKE)
                }
                if (previousLog != null && dailyWaterM3(log, previousLog) != null) {
                    val daysElapsed = daysBetween(log, previousLog)
                    val volumeFactor = aliveBirds * daysElapsed / CUBIC_CENTIMETERS_PER_CUBIC_METER
                    val minReading = previousLog.waterM3 + targets.minWaterCm3PerBird * volumeFactor
                    val maxReading = previousLog.waterM3 + targets.maxWaterCm3PerBird * volumeFactor
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

    private fun exceedsSensorDrift(analog: Double, digital: Double, limit: Double): Boolean =
        maxOf(analog, digital) > minOf(analog, digital) + limit

    private fun daysBetween(log: DailyLog, previousLog: DailyLog): Double =
        (log.epochDay.toLong() - previousLog.epochDay).coerceAtLeast(1).toDouble()

    private fun dailyWaterM3(log: DailyLog, previousLog: DailyLog): Double? {
        val volume = log.waterM3 - previousLog.waterM3
        return volume.takeIf { it.isFinite() && it >= 0.0 }?.div(daysBetween(log, previousLog))
    }

    private fun calculateMetrics(barn: Barn, log: DailyLog, aliveBirds: Int, previousLog: DailyLog?): DailyMetrics {
        val denominator = aliveBirds.takeIf { it > 0 }
        val ageWeeks = flockAgeWeeksAt(barn, log.epochDay)
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
            flockAgeWeeks = ageWeeks,
            intakeTargets = standardIntakeTargetsForWeek(ageWeeks),
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
