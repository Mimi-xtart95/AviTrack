package org.avitrack.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.AlertSeverity
import org.avitrack.app.domain.model.AlertType
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.DailyMetrics
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.domain.model.SyncStatus
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.ui.theme.AviTrackCard
import org.avitrack.app.ui.theme.AviTrackPrimaryButton
import org.avitrack.app.ui.theme.AviTrackSecondaryButton
import org.avitrack.app.ui.theme.AviTrackStatusBadge
import org.avitrack.app.ui.theme.AviTrackTextField
import org.avitrack.app.ui.theme.LocalAviTrackColors
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Instant

private const val MILLIS_PER_DAY = 86_400_000L
private const val MAX_LITTER_PERCENTAGE = 100
private const val LITTER_PERCENTAGE_STEP = 10
private const val GRAMS_PER_KILOGRAM = 1_000.0
private const val CUBIC_CENTIMETERS_PER_CUBIC_METER = 1_000_000.0
private val FeedPreviewRange = 100.0..136.0
private val WaterPreviewRange = 210.0..260.0
private const val PREVIEW_DECIMAL_SCALE = 10.0
private const val MORTALITY_LIMIT_PERCENT = 0.02
private val FormSpacing = 12.dp
private val FormPadding = 20.dp
private val FormMaxWidth = 800.dp
private val TwoFieldColumnsWidth = 540.dp
private val WarningDialogShape = RoundedCornerShape(16.dp)
private val WarningBorder = 1.dp
private val WarningMaxHeight = 360.dp

private enum class Reading {
    ANALOG_MIN, ANALOG_MAX, DIGITAL_MIN, DIGITAL_MAX, HUMIDITY, MORTALITY, FEED, WATER,
}

@Composable
fun BarnModeScreen(
    barn: Barn,
    logs: List<DailyLog>,
    onBack: () -> Unit,
    onSubmit: suspend (DailyLog) -> Boolean,
) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    val scope = rememberCoroutineScope()
    val latestLogs by rememberUpdatedState(logs)
    var selectedDay by remember(barn.id) {
        // Start with today's UTC date. The operator can select the farm's local date.
        mutableIntStateOf(Clock.System.now().toEpochMilliseconds().floorDiv(MILLIS_PER_DAY).toInt())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastSubmissionDay by remember(barn.id) { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedWarning by remember(barn.id) { mutableStateOf<SavedWarning?>(null) }
    val existingLog = logs.find { it.barnId == barn.id && it.epochDay == selectedDay }
    var readings by remember(barn.id, selectedDay) { mutableStateOf(existingLog.toReadings()) }
    var litter by remember(barn.id, selectedDay) { mutableIntStateOf(existingLog?.litterPercentage ?: 0) }
    var notes by remember(barn.id, selectedDay) { mutableStateOf(existingLog?.notes.orEmpty()) }
    var invalidReadings by remember(barn.id, selectedDay) { mutableStateOf(false) }
    var saveRejected by remember(barn.id, selectedDay) { mutableStateOf(false) }
    val lastSubmission = logs.find { it.barnId == barn.id && it.epochDay == lastSubmissionDay }
        ?: logs.filter { it.barnId == barn.id }.maxByOrNull { it.epochDay }
    val currentAliveBirds = remember(barn, logs, selectedDay) {
        // Use the opening census for the edited date so replaced mortality is counted only once.
        ZootechnicalEngine.calculateAliveBirds(barn, logs.filter { it.epochDay < selectedDay })
    }
    val mortalityInput = readings[Reading.MORTALITY].orEmpty().trim()
    val projectedAliveBirds = (currentAliveBirds - (mortalityInput.toIntOrNull() ?: 0).coerceAtLeast(0))
        .coerceAtLeast(1)
    val feedKg = readings.decimal(Reading.FEED)?.takeIf { it.isFinite() && it > 0.0 }
    val waterM3 = readings.decimal(Reading.WATER)?.takeIf { it.isFinite() && it > 0.0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FormPadding),
        verticalArrangement = Arrangement.spacedBy(FormSpacing),
    ) {
        AviTrackSecondaryButton(strings.backToBarns, onClick = onBack, enabled = !saving)
        Text(strings.dailyReport, style = MaterialTheme.typography.headlineMedium)
        Text(barn.name, style = MaterialTheme.typography.titleMedium)
        AviTrackSecondaryButton(
            text = "${strings.date} · ${formatEpochDay(selectedDay)}",
            onClick = { showDatePicker = true },
            enabled = !saving,
        )
        lastSubmission?.let { log ->
            AviTrackStatusBadge(
                text = "${strings.lastSubmission} · ${strings.syncStatusText(log.syncStatus)}",
                color = when (log.syncStatus) {
                    SyncStatus.SYNCED -> colors.dsmTechBlue
                    SyncStatus.PENDING -> colors.ochreAccent
                    SyncStatus.FAILED -> colors.combCrimson
                },
            )
        }
        Column(Modifier.widthIn(max = FormMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FormSpacing)) {
            ReadingCard(
                title = strings.environment,
                fields = listOf(
                    Reading.ANALOG_MIN to "${strings.analogTempMin} (${strings.celsiusUnit})",
                    Reading.ANALOG_MAX to "${strings.analogTempMax} (${strings.celsiusUnit})",
                    Reading.DIGITAL_MIN to "${strings.digitalTempMin} (${strings.celsiusUnit})",
                    Reading.DIGITAL_MAX to "${strings.digitalTempMax} (${strings.celsiusUnit})",
                    Reading.HUMIDITY to "${strings.relativeHumidityAvg} (${strings.percentUnit})",
                ),
                values = readings,
                enabled = !saving,
                onChange = { field, value -> readings = readings + (field to value) },
            )
            ReadingCard(
                title = strings.dailyInputs,
                fields = listOf(
                    Reading.MORTALITY to "${strings.mortalityCount} (${strings.birdsPerDayUnit})",
                    Reading.FEED to "${strings.feedKg} (${strings.kilogramsPerDayUnit})",
                    Reading.WATER to "${strings.waterM3} (${strings.cubicMetersPerDayUnit})",
                ),
                values = readings,
                enabled = !saving,
                onChange = { field, value -> readings = readings + (field to value) },
            ) { field ->
                when (field) {
                    Reading.FEED -> if (feedKg != null) {
                        val gramsPerBird = ZootechnicalEngine.calculateFeedGramsPerBird(feedKg, projectedAliveBirds)
                        IntakeEstimate(gramsPerBird, strings.gramsPerBirdUnit, strings.feedTargetHint, FeedPreviewRange)
                    }
                    Reading.WATER -> if (waterM3 != null) {
                        val cm3PerBird = ZootechnicalEngine.calculateWaterCm3PerBird(waterM3, projectedAliveBirds)
                        IntakeEstimate(
                            cm3PerBird,
                            strings.cubicCentimetersPerBirdUnit,
                            strings.waterTargetHint,
                            WaterPreviewRange,
                        )
                    }
                    else -> Unit
                }
            }
            AviTrackCard(Modifier.fillMaxWidth()) {
                Text(strings.litterPercentage, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FormSpacing)) {
                    for (percentage in 0..MAX_LITTER_PERCENTAGE step LITTER_PERCENTAGE_STEP) {
                        FilterChip(
                            selected = litter == percentage,
                            onClick = { litter = percentage },
                            label = { Text("$percentage${strings.percentUnit}") },
                            enabled = !saving,
                        )
                    }
                }
                AviTrackTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = strings.optionalNotes,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    singleLine = false,
                )
            }
            if (invalidReadings || saveRejected) {
                Text(
                    if (invalidReadings) strings.requiredReadings else strings.invalidLog,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AviTrackPrimaryButton(
                text = strings.save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    val log = readings.toLog(barn.id, selectedDay, litter, notes)
                    invalidReadings = log == null
                    saveRejected = false
                    if (log != null) {
                        saving = true
                        scope.launch {
                            try {
                                if (onSubmit(log)) {
                                    lastSubmissionDay = log.epochDay
                                    savedWarning = activeWarningAfterSave(barn, log, latestLogs)
                                } else {
                                    saveRejected = true
                                }
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
    }
    savedWarning?.let { EarlyWarningDialog(it, onDismiss = { savedWarning = null }) }
    if (showDatePicker) {
        var dateInput by remember { mutableStateOf(formatEpochDay(selectedDay)) }
        var invalidDate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text(strings.date) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FormSpacing)) {
                    AviTrackTextField(
                        value = dateInput,
                        onValueChange = {
                            dateInput = it
                            invalidDate = false
                        },
                        label = strings.dateFormat,
                        modifier = Modifier.fillMaxWidth(),
                        isError = invalidDate,
                    )
                    if (invalidDate) Text(strings.invalidDate, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedDay = parseEpochDay(dateInput.trim())
                    if (parsedDay == null) {
                        invalidDate = true
                    } else {
                        selectedDay = parsedDay
                        showDatePicker = false
                    }
                }) { Text(strings.confirmDate) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun ReadingCard(
    title: String,
    fields: List<Pair<Reading, String>>,
    values: Map<Reading, String>,
    enabled: Boolean,
    onChange: (Reading, String) -> Unit,
    supportingContent: (@Composable (Reading) -> Unit)? = null,
) {
    AviTrackCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = FormSpacing)) {
            val fieldWidth = if (maxWidth >= TwoFieldColumnsWidth) (maxWidth - FormSpacing) / 2 else maxWidth
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FormSpacing),
                verticalArrangement = Arrangement.spacedBy(FormSpacing),
            ) {
                fields.forEach { (field, label) ->
                    val type = if (field == Reading.MORTALITY) KeyboardType.Number else KeyboardType.Decimal
                    Column(Modifier.width(fieldWidth)) {
                        AviTrackTextField(
                            value = values[field].orEmpty(),
                            onValueChange = { onChange(field, it) },
                            label = label,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = type),
                        )
                        supportingContent?.invoke(field)
                    }
                }
            }
        }
    }
}

// The engine exposes these conversions together in calculateDailyMetrics.
private fun ZootechnicalEngine.calculateFeedGramsPerBird(feedKg: Double, aliveBirds: Int): Double =
    feedKg * GRAMS_PER_KILOGRAM / aliveBirds

private fun ZootechnicalEngine.calculateWaterCm3PerBird(waterM3: Double, aliveBirds: Int): Double =
    waterM3 * CUBIC_CENTIMETERS_PER_CUBIC_METER / aliveBirds

@Composable
private fun IntakeEstimate(value: Double, unit: String, target: String, normalRange: ClosedFloatingPointRange<Double>) {
    if (!value.isFinite()) return
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    val scheme = MaterialTheme.colorScheme
    val warningColor = if (scheme.surface == colors.mahoganySurface) colors.yolkAmber else colors.combCrimson
    val displayed = (round(value * PREVIEW_DECIMAL_SCALE) / PREVIEW_DECIMAL_SCALE)
        .takeIf { it.isFinite() } ?: value
    Text(
        "${strings.estimatedIntakePrefix}: ${formatAlertValue(displayed, strings.decimalSeparator)} $unit · $target",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (value in normalRange) scheme.onSurfaceVariant else warningColor,
    )
}

private data class SavedWarning(val log: DailyLog, val metrics: DailyMetrics, val alerts: List<EarlyAlert>)

private fun activeWarningAfterSave(barn: Barn, submitted: DailyLog, history: List<DailyLog>): SavedWarning? {
    // Match the repository's latest-date rule without waiting for Compose to collect the updated logs.
    val barnLogs = history.filter { it.barnId == barn.id && it.epochDay != submitted.epochDay } + submitted
    val latest = barnLogs.maxBy { it.epochDay }
    val activeAlerts = ZootechnicalEngine.evaluateAlerts(barn, latest, barnLogs)
        .sortedByDescending { it.severity == AlertSeverity.CRITICAL }
    if (activeAlerts.isEmpty()) return null
    return SavedWarning(latest, ZootechnicalEngine.calculateDailyMetrics(barn, latest, barnLogs), activeAlerts)
}

@Composable
private fun EarlyWarningDialog(warning: SavedWarning, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    val accent = if (warning.alerts.any { it.severity == AlertSeverity.CRITICAL }) {
        colors.combCrimson
    } else {
        colors.yolkAmber
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(WarningBorder, accent, WarningDialogShape),
        shape = WarningDialogShape,
        icon = { Icon(EarlyWarningIcon, contentDescription = null, tint = accent) },
        title = { Text(strings.alertDialogTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = WarningMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FormSpacing),
            ) {
                Text(strings.alertDialogSubtitle)
                Text("${strings.date}: ${formatEpochDay(warning.log.epochDay)}")
                warning.alerts.forEach { alert ->
                    val copy = strings.alertText(alert.type)
                    val (value, range) = alertDetails(alert.type, warning)
                    Column {
                        Text(copy.title, style = MaterialTheme.typography.titleSmall)
                        Text(copy.description, style = MaterialTheme.typography.bodyMedium)
                        Text("${strings.detectedValue}: $value")
                        Text("${strings.expectedRange}: $range")
                    }
                }
            }
        },
        confirmButton = { AviTrackPrimaryButton(strings.alertDialogConfirm, onClick = onDismiss) },
    )
}

@Composable
private fun alertDetails(type: AlertType, warning: SavedWarning): Pair<String, String> {
    val strings = LocalStrings.current
    val log = warning.log
    val metrics = warning.metrics

    fun measurement(value: Double?, unit: String): String =
        value?.let { "${formatAlertValue(it, strings.decimalSeparator)} $unit" } ?: "—"

    return when (type) {
        AlertType.LOW_TEMPERATURE ->
            measurement(minOf(log.analogTempMin, log.digitalTempMin), strings.celsiusUnit) to "18–28 ${strings.celsiusUnit}"
        AlertType.HIGH_TEMPERATURE ->
            measurement(maxOf(log.analogTempMax, log.digitalTempMax), strings.celsiusUnit) to "18–28 ${strings.celsiusUnit}"
        AlertType.SENSOR_DRIFT ->
            measurement(metrics.sensorDriftCelsius, strings.celsiusUnit) to "≤ 2 ${strings.celsiusUnit}"
        AlertType.SUSTAINED_MORTALITY -> {
            val count = "${log.mortalityCount} ${strings.birdsPerDayUnit}"
            val value = metrics.mortalityPercentage?.let { "${measurement(it, strings.percentUnit)} ($count)" } ?: count
            value to "≤ ${formatAlertValue(MORTALITY_LIMIT_PERCENT, strings.decimalSeparator)} ${strings.percentUnit}"
        }
        AlertType.LOW_FEED_INTAKE, AlertType.HIGH_FEED_INTAKE ->
            measurement(metrics.feedGramsPerBird, strings.gramsPerBirdUnit) to "110–126 ${strings.gramsPerBirdUnit}"
        AlertType.LOW_WATER_INTAKE, AlertType.HIGH_WATER_INTAKE ->
            measurement(metrics.waterCubicCentimetersPerBird, strings.cubicCentimetersPerBirdUnit) to
                "210–260 ${strings.cubicCentimetersPerBirdUnit}"
        AlertType.LOW_HUMIDITY, AlertType.HIGH_HUMIDITY ->
            measurement(log.relativeHumidityAvg, strings.percentUnit) to "50–75 ${strings.percentUnit}"
    }
}

private fun formatAlertValue(value: Double, decimalSeparator: String): String =
    value.toString().removeSuffix(".0").replace(".", decimalSeparator)

private val EarlyWarningIcon = ImageVector.Builder(
    name = "EarlyWarning",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 2f)
        lineTo(23f, 21f)
        lineTo(1f, 21f)
        close()
        moveTo(11f, 9f)
        lineTo(13f, 9f)
        lineTo(13f, 15f)
        lineTo(11f, 15f)
        close()
        moveTo(11f, 17f)
        lineTo(13f, 17f)
        lineTo(13f, 19f)
        lineTo(11f, 19f)
        close()
    }
}.build()

private fun DailyLog?.toReadings(): Map<Reading, String> {
    if (this == null) return emptyMap()
    return mapOf(
        Reading.ANALOG_MIN to analogTempMin.toString(),
        Reading.ANALOG_MAX to analogTempMax.toString(),
        Reading.DIGITAL_MIN to digitalTempMin.toString(),
        Reading.DIGITAL_MAX to digitalTempMax.toString(),
        Reading.HUMIDITY to relativeHumidityAvg.toString(),
        Reading.MORTALITY to mortalityCount.toString(),
        Reading.FEED to feedKg.toString(),
        Reading.WATER to waterM3.toString(),
    )
}

private fun Map<Reading, String>.decimal(field: Reading): Double? =
    this[field]?.trim()?.replace(',', '.')?.toDoubleOrNull()

private fun Map<Reading, String>.toLog(barnId: String, day: Int, litter: Int, notes: String): DailyLog? {
    val mortality = this[Reading.MORTALITY]?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return DailyLog(
        barnId = barnId,
        epochDay = day,
        analogTempMin = decimal(Reading.ANALOG_MIN) ?: return null,
        analogTempMax = decimal(Reading.ANALOG_MAX) ?: return null,
        digitalTempMin = decimal(Reading.DIGITAL_MIN) ?: return null,
        digitalTempMax = decimal(Reading.DIGITAL_MAX) ?: return null,
        relativeHumidityAvg = decimal(Reading.HUMIDITY) ?: return null,
        mortalityCount = mortality,
        feedKg = decimal(Reading.FEED) ?: return null,
        waterM3 = decimal(Reading.WATER) ?: return null,
        litterPercentage = litter,
        notes = notes.trim(),
    )
}

internal fun formatEpochDay(day: Int): String =
    Instant.fromEpochMilliseconds(day * MILLIS_PER_DAY).toString().substringBefore('T')

private fun parseEpochDay(date: String): Int? {
    val instant = try {
        Instant.parse("${date}T00:00:00Z")
    } catch (_: IllegalArgumentException) {
        return null
    }
    val day = instant.toEpochMilliseconds().floorDiv(MILLIS_PER_DAY).toInt()
    return day.takeIf { formatEpochDay(it) == date }
}
