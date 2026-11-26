package org.avitrack.app.domain

import org.avitrack.app.data.seed.DemoFarmSeed
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.AlertType
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZootechnicalEngineTest {
    private val barn = Barn(id = "barn-01", name = "Barn 01", initialBirdCount = 1_000)
    private val normalLog = DailyLog(
        barnId = barn.id,
        epochDay = FIRST_EPOCH_DAY,
        analogTempMin = 19.5,
        analogTempMax = 27.0,
        digitalTempMin = 19.5,
        digitalTempMax = 27.0,
        relativeHumidityAvg = 62.0,
        mortalityCount = 0,
        feedKg = 118.0,
        waterM3 = 1_200.0,
        litterPercentage = 20,
    )

    @Test
    fun subtractsCumulativeMortalityFromLiveBirdCount() {
        val firstDay = normalLog.copy(mortalityCount = 2)
        val secondDay = normalLog.copy(epochDay = SECOND_EPOCH_DAY, mortalityCount = 3)
        val thirdDay = normalLog.copy(epochDay = THIRD_EPOCH_DAY, mortalityCount = 4)

        assertEquals(1_000, ZootechnicalEngine.calculateAliveBirds(barn, emptyList()))
        assertEquals(998, ZootechnicalEngine.calculateAliveBirds(barn, listOf(firstDay)))
        assertEquals(995, ZootechnicalEngine.calculateAliveBirds(barn, listOf(firstDay, secondDay)))
        assertEquals(991, ZootechnicalEngine.calculateAliveBirds(barn, listOf(firstDay, secondDay, thirdDay)))
    }

    @Test
    fun convertsFeedAndWaterUsingClosingLiveBirdCount() {
        val largerBarn = barn.copy(initialBirdCount = 1_010)
        val previousLog = normalLog.copy(mortalityCount = 8)
        val currentLog = normalLog.copy(epochDay = SECOND_EPOCH_DAY, mortalityCount = 2, waterM3 = 1_200.235)
        val metrics = ZootechnicalEngine.calculateDailyMetrics(largerBarn, currentLog, listOf(previousLog))

        assertEquals(1_000, metrics.aliveBirds)
        assertEquals(118.0, metrics.feedGramsPerBird)
        assertEquals(235.0, assertNotNull(metrics.waterCubicCentimetersPerBird), WATER_TOLERANCE)
    }

    @Test
    fun acceptsBothWaterIntakeLimits() {
        val lowerLimit = normalLog.copy(epochDay = SECOND_EPOCH_DAY, waterM3 = 1_200.210)
        val upperLimit = normalLog.copy(epochDay = SECOND_EPOCH_DAY, waterM3 = 1_200.260)
        val history = listOf(normalLog)

        for ((expected, log) in listOf(210.0 to lowerLimit, 260.0 to upperLimit)) {
            val water = ZootechnicalEngine.calculateDailyMetrics(barn, log, history).waterCubicCentimetersPerBird
            assertEquals(expected, assertNotNull(water), WATER_TOLERANCE)
            assertTrue(alertTypes(log, history).isEmpty())
        }
        assertEquals(setOf(AlertType.LOW_WATER_INTAKE), alertTypes(lowerLimit.copy(waterM3 = 1_200.20), history))
        assertEquals(setOf(AlertType.HIGH_WATER_INTAKE), alertTypes(upperLimit.copy(waterM3 = 1_200.27), history))
    }

    @Test
    fun firstMeterReadingHasNoWaterMetricOrAlert() {
        assertNull(ZootechnicalEngine.calculateDailyMetrics(barn, normalLog).waterCubicCentimetersPerBird)
        assertTrue(alertTypes(normalLog).isEmpty())
    }

    @Test
    fun averagesWeekendConsumptionUsingLatestEarlierReadingFromTheSameBarn() {
        val saturday = normalLog.copy(epochDay = SECOND_EPOCH_DAY, waterM3 = 1_201.0)
        val monday = normalLog.copy(epochDay = FOURTH_EPOCH_DAY, waterM3 = 1_201.47)
        val otherBarn = normalLog.copy(barnId = "other", epochDay = THIRD_EPOCH_DAY)
        val history = listOf(saturday, otherBarn, normalLog, monday)

        val water = ZootechnicalEngine.calculateDailyMetrics(barn, monday, history).waterCubicCentimetersPerBird
        assertEquals(235.0, assertNotNull(water), WATER_TOLERANCE)
        assertTrue(alertTypes(monday, history).isEmpty())
    }

    @Test
    fun decreasingReadingHasNoWaterMetricOrAlert() {
        val log = normalLog.copy(epochDay = SECOND_EPOCH_DAY, waterM3 = 1_199.0)
        val history = listOf(normalLog)
        assertNull(ZootechnicalEngine.calculateDailyMetrics(barn, log, history).waterCubicCentimetersPerBird)
        assertTrue(alertTypes(log, history).isEmpty())
    }

    @Test
    fun extremeHumidityDoesNotGenerateAlerts() {
        for (humidity in listOf(20.0, 95.0)) {
            val log = normalLog.copy(epochDay = SECOND_EPOCH_DAY, waterM3 = 1_200.235, relativeHumidityAvg = humidity)
            assertTrue(alertTypes(log, listOf(normalLog)).isEmpty())
        }
    }

    @Test
    fun demoMeterReadingsIncreaseAndPreserveTheWaterAlertScenario() {
        val (barns, logs) = DemoFarmSeed.createDemoDataset()
        val alertsByBarn = barns.map { demoBarn ->
            val history = logs.filter { it.barnId == demoBarn.id }.sortedBy { it.epochDay }
            assertTrue(history.zipWithNext().all { (earlier, later) -> earlier.waterM3 < later.waterM3 })
            ZootechnicalEngine.evaluateAlerts(demoBarn, history.last(), history).map { it.type }
        }
        assertTrue(alertsByBarn.first().isEmpty())
        assertTrue(AlertType.HIGH_WATER_INTAKE in alertsByBarn.last())
    }

    @Test
    fun alertsWhenEitherProbeReadsBelowTheTemperatureLimit() {
        assertEquals(setOf(AlertType.LOW_TEMPERATURE), alertTypes(normalLog.copy(analogTempMin = 17.9)))
        assertEquals(setOf(AlertType.LOW_TEMPERATURE), alertTypes(normalLog.copy(digitalTempMin = 17.9)))
    }

    @Test
    fun alertsWhenEitherProbeReadsAboveTheTemperatureLimit() {
        assertEquals(setOf(AlertType.HIGH_TEMPERATURE), alertTypes(normalLog.copy(analogTempMax = 28.1)))
        assertEquals(setOf(AlertType.HIGH_TEMPERATURE), alertTypes(normalLog.copy(digitalTempMax = 28.1)))
    }

    @Test
    fun acceptsBothTemperatureLimits() {
        val log = normalLog.copy(
            analogTempMin = 18.0,
            analogTempMax = 28.0,
            digitalTempMin = 18.0,
            digitalTempMax = 28.0,
        )

        assertTrue(alertTypes(log).isEmpty())
    }

    @Test
    fun alertsOnSensorDriftAboveTwoDegreesInEitherDirection() {
        val digitalMinIsHigher = normalLog.copy(analogTempMin = 20.0, digitalTempMin = 22.1)
        val analogMaxIsHigher = normalLog.copy(analogTempMax = 27.5, digitalTempMax = 25.4)

        assertEquals(setOf(AlertType.SENSOR_DRIFT), alertTypes(digitalMinIsHigher))
        assertEquals(setOf(AlertType.SENSOR_DRIFT), alertTypes(analogMaxIsHigher))
    }

    @Test
    fun acceptsSensorDriftOfExactlyTwoDegrees() {
        val log = normalLog.copy(
            analogTempMin = 20.0,
            digitalTempMin = 22.0,
            digitalTempMax = 25.0,
        )

        assertFalse(AlertType.SENSOR_DRIFT in alertTypes(log))
    }

    @Test
    fun alertsFromTheThirdConsecutiveDayAboveTheMortalityLimit() {
        val firstDay = normalLog.copy(mortalityCount = 1)
        val secondDay = firstDay.copy(epochDay = SECOND_EPOCH_DAY)
        val thirdDay = firstDay.copy(epochDay = THIRD_EPOCH_DAY)
        val fourthDay = firstDay.copy(epochDay = FOURTH_EPOCH_DAY)

        assertFalse(AlertType.SUSTAINED_MORTALITY in alertTypes(firstDay))
        assertFalse(AlertType.SUSTAINED_MORTALITY in alertTypes(secondDay, listOf(firstDay)))
        assertTrue(AlertType.SUSTAINED_MORTALITY in alertTypes(thirdDay, listOf(firstDay, secondDay)))
        assertTrue(AlertType.SUSTAINED_MORTALITY in alertTypes(fourthDay, listOf(firstDay, secondDay, thirdDay)))
    }

    @Test
    fun explanatoryNoteBreaksTheMortalityStreak() {
        val firstDay = normalLog.copy(mortalityCount = 1)
        val secondDay = firstDay.copy(epochDay = SECOND_EPOCH_DAY, notes = "Documented accidental losses.")
        val thirdDay = firstDay.copy(epochDay = THIRD_EPOCH_DAY)

        assertFalse(AlertType.SUSTAINED_MORTALITY in alertTypes(thirdDay, listOf(firstDay, secondDay)))
    }

    @Test
    fun missingDateBreaksTheMortalityStreak() {
        val firstDay = normalLog.copy(mortalityCount = 1)
        val secondDay = firstDay.copy(epochDay = SECOND_EPOCH_DAY)
        val fourthDay = firstDay.copy(epochDay = FOURTH_EPOCH_DAY)

        assertFalse(AlertType.SUSTAINED_MORTALITY in alertTypes(fourthDay, listOf(firstDay, secondDay)))
    }

    @Test
    fun mortalityOfExactlyPointZeroTwoPercentDoesNotCountTowardsTheStreak() {
        // One death on the first day leaves 5,000 live birds. The next two days exceed the limit.
        val largerBarn = barn.copy(initialBirdCount = 5_001)
        val firstDay = normalLog.copy(mortalityCount = 1)
        val secondDay = firstDay.copy(epochDay = SECOND_EPOCH_DAY)
        val thirdDay = firstDay.copy(epochDay = THIRD_EPOCH_DAY)
        val alerts = ZootechnicalEngine.evaluateAlerts(largerBarn, thirdDay, listOf(firstDay, secondDay))

        assertFalse(alerts.any { it.type == AlertType.SUSTAINED_MORTALITY })
    }

    @Test
    fun noValidLitterPercentageGeneratesAnAlert() {
        for (percentage in MIN_LITTER_PERCENTAGE..MAX_LITTER_PERCENTAGE step LITTER_PERCENTAGE_STEP) {
            val log = normalLog.copy(litterPercentage = percentage)

            assertTrue(alertTypes(log).isEmpty(), "Unexpected alert at $percentage% litter.")
        }
    }

    private fun alertTypes(log: DailyLog, history: List<DailyLog> = emptyList()): Set<AlertType> =
        ZootechnicalEngine.evaluateAlerts(barn, log, history).map { it.type }.toSet()

    private companion object {
        const val FIRST_EPOCH_DAY = 20_000
        const val SECOND_EPOCH_DAY = FIRST_EPOCH_DAY + 1
        const val THIRD_EPOCH_DAY = SECOND_EPOCH_DAY + 1
        const val FOURTH_EPOCH_DAY = THIRD_EPOCH_DAY + 1
        const val MIN_LITTER_PERCENTAGE = 0
        const val MAX_LITTER_PERCENTAGE = 100
        const val LITTER_PERCENTAGE_STEP = 10
        const val WATER_TOLERANCE = 1e-6
    }
}
