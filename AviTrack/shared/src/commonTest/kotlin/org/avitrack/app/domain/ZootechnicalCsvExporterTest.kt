package org.avitrack.app.domain

import org.avitrack.app.domain.export.ZootechnicalCsvExporter
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import kotlin.test.Test
import kotlin.test.assertEquals

class ZootechnicalCsvExporterTest {
    @Test
    fun exportsMeterReadingsAndDailyWaterIntakeSeparately() {
        val barn = Barn("north", "North", 1_000)
        val saturday = DailyLog(
            barnId = barn.id,
            epochDay = 20_001,
            analogTempMin = 21.0,
            analogTempMax = 24.0,
            digitalTempMin = 21.0,
            digitalTempMax = 24.0,
            relativeHumidityAvg = 20.0,
            mortalityCount = 0,
            feedKg = 118.0,
            waterM3 = 1_200.0,
            litterPercentage = 30,
        )
        val monday = saturday.copy(epochDay = saturday.epochDay + 2, waterM3 = 1_200.47, relativeHumidityAvg = 95.0)
        val rows = ZootechnicalCsvExporter.buildBarnReportCsv(barn, listOf(monday, saturday))
            .lineSequence().filter { it.isNotEmpty() }.map { it.split(',') }.toList()
        val header = rows.first()
        val meterColumn = header.indexOf("water_m3")
        val intakeColumn = header.indexOf("water_cm3_per_bird")
        val manureColumn = header.indexOf("manure_removed_pct")

        assertEquals(3, rows.size)
        assertEquals("1200.0", rows[1][meterColumn])
        assertEquals("", rows[1][intakeColumn])
        assertEquals("1200.47", rows[2][meterColumn])
        assertEquals(235.0, rows[2][intakeColumn].toDouble(), 1e-6)
        assertEquals("30", rows[2][manureColumn])
    }
}
