package org.avitrack.app.data.seed

import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.SyncStatus
import kotlin.math.round

object DemoFarmSeed {
    private const val NORTH_BARN_ID = "barn-01-north"
    private const val SOUTH_BARN_ID = "barn-02-south"
    private const val NORTH_LOG_DAYS = 30
    private const val SOUTH_LOG_DAYS = 7
    private const val LAST_DEMO_EPOCH_DAY = 20_775 // 2026-11-18
    private const val INITIAL_WATER_METER_M3 = 1_200.0
    private const val WATER_METER_DECIMAL_SCALE = 10.0
    private const val STABLE_MIN_TEMPERATURE = 21.6
    private const val STABLE_MAX_TEMPERATURE = 23.4
    private const val STABLE_HUMIDITY = 62.0
    private const val ANALOG_MIN_OFFSET = 0.2
    private const val ANALOG_MAX_OFFSET = 0.3
    private const val LITTER_PERCENTAGE = 20
    private const val GRAMS_PER_KILOGRAM = 1_000.0
    private const val CUBIC_CENTIMETERS_PER_CUBIC_METER = 1_000_000.0

    private val dailyVariation = listOf(-0.4, 0.1, 0.4, -0.2, 0.0, 0.3, -0.1)
    private val southBarnStressDays = listOf(
        StressDay(waterPerBird = 286.0, digitalTempMax = 27.2, mortalityCount = 2),
        StressDay(waterPerBird = 294.0, digitalTempMax = 28.1, mortalityCount = 4),
        StressDay(waterPerBird = 303.0, digitalTempMax = 28.8, mortalityCount = 5),
        StressDay(waterPerBird = 314.0, digitalTempMax = 29.5, mortalityCount = 6),
    )

    /** The strain is demo metadata keyed by barn ID because Barn has no strain field. */
    fun breedFor(barnId: String): String? = when (barnId) {
        NORTH_BARN_ID -> "Lohmann Brown"
        SOUTH_BARN_ID -> "Hy-Line Brown"
        else -> null
    }

    fun createDemoDataset(): Pair<List<Barn>, List<DailyLog>> {
        val northBarn = Barn(
            NORTH_BARN_ID, "Barn 01 - North", 18_000,
            placementEpochDay = LAST_DEMO_EPOCH_DAY - NORTH_LOG_DAYS + 1,
            placementAgeWeeks = 24,
        )
        val southBarn = Barn(
            SOUTH_BARN_ID, "Barn 02 - South", 15_000,
            placementEpochDay = LAST_DEMO_EPOCH_DAY - SOUTH_LOG_DAYS + 1,
        )
        return listOf(northBarn, southBarn) to
            (createLogs(northBarn, NORTH_LOG_DAYS) + createLogs(southBarn, SOUTH_LOG_DAYS, southBarnStressDays))
    }

    private fun createLogs(barn: Barn, days: Int, stressDays: List<StressDay> = emptyList()): List<DailyLog> {
        val stableDays = days - stressDays.size
        var aliveBirds = barn.initialBirdCount
        var waterMeterM3 = INITIAL_WATER_METER_M3

        return List(days) { dayIndex ->
            val epochDay = barn.placementEpochDay + dayIndex
            val variation = dailyVariation[dayIndex % dailyVariation.size]
            val stressDay = stressDays.getOrNull(dayIndex - stableDays)
            val mortalityCount = stressDay?.mortalityCount ?: if (variation > 0.0) 2 else 1
            aliveBirds -= mortalityCount

            val age = ZootechnicalEngine.flockAgeWeeksAt(barn, epochDay)
            val targets = ZootechnicalEngine.standardIntakeTargetsForWeek(age)
            val feedPerBird = targets.targetFeedGramsPerBird + variation
            val waterPerBird = stressDay?.waterPerBird ?:
                ((targets.minWaterCm3PerBird + targets.maxWaterCm3PerBird) / 2 + variation)
            if (dayIndex > 0) waterMeterM3 += waterPerBird * aliveBirds / CUBIC_CENTIMETERS_PER_CUBIC_METER
            val digitalTempMin = STABLE_MIN_TEMPERATURE + variation
            val digitalTempMax = stressDay?.digitalTempMax ?: (STABLE_MAX_TEMPERATURE + variation)
            DailyLog(
                barnId = barn.id,
                epochDay = epochDay,
                analogTempMin = digitalTempMin - ANALOG_MIN_OFFSET,
                analogTempMax = digitalTempMax - ANALOG_MAX_OFFSET,
                digitalTempMin = digitalTempMin,
                digitalTempMax = digitalTempMax,
                relativeHumidityAvg = STABLE_HUMIDITY + variation,
                mortalityCount = mortalityCount,
                feedKg = feedPerBird * aliveBirds / GRAMS_PER_KILOGRAM,
                waterM3 = round(waterMeterM3 * WATER_METER_DECIMAL_SCALE) / WATER_METER_DECIMAL_SCALE,
                litterPercentage = LITTER_PERCENTAGE,
                syncStatus = SyncStatus.SYNCED,
            )
        }
    }

    private data class StressDay(
        val waterPerBird: Double,
        val digitalTempMax: Double,
        val mortalityCount: Int,
    )
}
