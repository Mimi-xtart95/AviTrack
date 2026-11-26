package org.avitrack.app.domain.export

import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import kotlin.time.Instant

object ZootechnicalCsvExporter {
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val ROW_END = "\r\n"
    private const val HEADER = "date,barn,alive_birds,analog_min,analog_max,digital_min,digital_max," +
        "humidity,mortality,feed_kg,feed_g_per_bird,water_m3,water_cm3_per_bird,manure_removed_pct,sync_status,notes"

    fun buildBarnReportCsv(barn: Barn, logs: List<DailyLog>): String = buildString {
        append(HEADER)
        append(ROW_END)
        val barnLogs = logs.filter { it.barnId == barn.id }.sortedBy { it.epochDay }
        val history = mutableListOf<DailyLog>()
        for (log in barnLogs) {
            val metrics = ZootechnicalEngine.calculateDailyMetrics(barn, log, history)
            val date = Instant.fromEpochMilliseconds(log.epochDay * MILLIS_PER_DAY).toString().substringBefore('T')
            append(
                listOf(
                    date, escapeText(barn.name), metrics.aliveBirds, log.analogTempMin, log.analogTempMax,
                    log.digitalTempMin, log.digitalTempMax, log.relativeHumidityAvg, log.mortalityCount,
                    log.feedKg, metrics.feedGramsPerBird ?: "", log.waterM3,
                    metrics.waterCubicCentimetersPerBird ?: "", log.litterPercentage,
                    log.syncStatus.name, escapeText(log.notes),
                ).joinToString(","),
            )
            append(ROW_END)
            history.add(log)
        }
    }

    private fun escapeText(value: String): String {
        val first = value.trimStart().firstOrNull()
        val spreadsheetText = if (first in listOf('=', '+', '-', '@') ||
            value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')
        ) "'$value" else value
        return if (spreadsheetText.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${spreadsheetText.replace("\"", "\"\"")}\""
        } else {
            spreadsheetText
        }
    }
}
