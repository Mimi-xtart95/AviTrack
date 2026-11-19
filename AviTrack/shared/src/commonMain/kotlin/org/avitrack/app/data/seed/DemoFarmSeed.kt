package org.avitrack.app.data.seed

import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.SyncStatus

object DemoFarmSeed {
    private const val NORTH_BARN_ID = "barn-01-north"
    private const val SOUTH_BARN_ID = "barn-02-south"
    private const val DAYS_PER_BARN = 30
    private const val LAST_DEMO_EPOCH_DAY = 20_775 // 2026-11-18
    private const val STABLE_WATER_PER_BIRD = 235.0
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
        val northBarn = Barn(NORTH_BARN_ID, "Barn 01 - North", 18_000)
        val southBarn = Barn(SOUTH_BARN_ID, "Barn 02 - South", 15_000)
        return listOf(northBarn, southBarn) to
            (createLogs(northBarn) + createLogs(southBarn, southBarnStressDays))
    }

    private fun createLogs(barn: Barn, stressDays: List<StressDay> = emptyList()): List<DailyLog> {
        val firstEpochDay = LAST_DEMO_EPOCH_DAY - DAYS_PER_BARN + 1
        val stableDays = DAYS_PER_BARN - stressDays.size
        var aliveBirds = barn.initialBirdCount

        return List(DAYS_PER_BARN) { dayIndex ->
            val variation = dailyVariation[dayIndex % dailyVariation.size]
            val stressDay = stressDays.getOrNull(dayIndex - stableDays)
            val mortalityCount = stressDay?.mortalityCount ?: if (variation > 0.0) 2 else 1
            aliveBirds -= mortalityCount

            val feedPerBird = ZootechnicalEngine.TARGET_FEED_GRAMS_PER_BIRD + variation
            val waterPerBird = stressDay?.waterPerBird ?: (STABLE_WATER_PER_BIRD + variation)
            val digitalTempMin = STABLE_MIN_TEMPERATURE + variation
            val digitalTempMax = stressDay?.digitalTempMax ?: (STABLE_MAX_TEMPERATURE + variation)
            DailyLog(
                barnId = barn.id,
                epochDay = firstEpochDay + dayIndex,
                analogTempMin = digitalTempMin - ANALOG_MIN_OFFSET,
                analogTempMax = digitalTempMax - ANALOG_MAX_OFFSET,
                digitalTempMin = digitalTempMin,
                digitalTempMax = digitalTempMax,
                relativeHumidityAvg = STABLE_HUMIDITY + variation,
                mortalityCount = mortalityCount,
                feedKg = feedPerBird * aliveBirds / GRAMS_PER_KILOGRAM,
                waterM3 = waterPerBird * aliveBirds / CUBIC_CENTIMETERS_PER_CUBIC_METER,
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
