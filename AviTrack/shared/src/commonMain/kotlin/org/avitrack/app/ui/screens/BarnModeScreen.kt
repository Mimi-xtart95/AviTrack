package org.avitrack.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.SyncStatus
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.ui.theme.AviTrackCard
import org.avitrack.app.ui.theme.AviTrackPrimaryButton
import org.avitrack.app.ui.theme.AviTrackSecondaryButton
import org.avitrack.app.ui.theme.AviTrackStatusBadge
import org.avitrack.app.ui.theme.AviTrackTextField
import org.avitrack.app.ui.theme.LocalAviTrackColors
import kotlin.time.Clock
import kotlin.time.Instant

private const val MILLIS_PER_DAY = 86_400_000L
private const val MAX_LITTER_PERCENTAGE = 100
private const val LITTER_PERCENTAGE_STEP = 10
private val FormSpacing = 12.dp
private val FormPadding = 20.dp
private val FormMaxWidth = 800.dp
private val TwoFieldColumnsWidth = 540.dp

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
    var selectedDay by remember(barn.id) {
        // Start with today's UTC date. The operator can select the farm's local date.
        mutableIntStateOf(Clock.System.now().toEpochMilliseconds().floorDiv(MILLIS_PER_DAY).toInt())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastSubmissionDay by remember(barn.id) { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val existingLog = logs.find { it.barnId == barn.id && it.epochDay == selectedDay }
    var readings by remember(barn.id, selectedDay) { mutableStateOf(existingLog.toReadings()) }
    var litter by remember(barn.id, selectedDay) { mutableIntStateOf(existingLog?.litterPercentage ?: 0) }
    var notes by remember(barn.id, selectedDay) { mutableStateOf(existingLog?.notes.orEmpty()) }
    var invalidReadings by remember(barn.id, selectedDay) { mutableStateOf(false) }
    var saveRejected by remember(barn.id, selectedDay) { mutableStateOf(false) }
    val lastSubmission = logs.find { it.barnId == barn.id && it.epochDay == lastSubmissionDay }
        ?: logs.filter { it.barnId == barn.id }.maxByOrNull { it.epochDay }

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
            )
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
                                if (onSubmit(log)) lastSubmissionDay = log.epochDay else saveRejected = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
    }
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
                    AviTrackTextField(
                        value = values[field].orEmpty(),
                        onValueChange = { onChange(field, it) },
                        label = label,
                        modifier = Modifier.width(fieldWidth),
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = type),
                    )
                }
            }
        }
    }
}

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

private fun Map<Reading, String>.toLog(barnId: String, day: Int, litter: Int, notes: String): DailyLog? {
    fun reading(field: Reading) = this[field]?.trim()?.replace(',', '.')?.toDoubleOrNull()
    val mortality = this[Reading.MORTALITY]?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return DailyLog(
        barnId = barnId,
        epochDay = day,
        analogTempMin = reading(Reading.ANALOG_MIN) ?: return null,
        analogTempMax = reading(Reading.ANALOG_MAX) ?: return null,
        digitalTempMin = reading(Reading.DIGITAL_MIN) ?: return null,
        digitalTempMax = reading(Reading.DIGITAL_MAX) ?: return null,
        relativeHumidityAvg = reading(Reading.HUMIDITY) ?: return null,
        mortalityCount = mortality,
        feedKg = reading(Reading.FEED) ?: return null,
        waterM3 = reading(Reading.WATER) ?: return null,
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
