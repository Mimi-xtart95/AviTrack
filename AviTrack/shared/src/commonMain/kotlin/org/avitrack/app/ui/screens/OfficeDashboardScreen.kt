package org.avitrack.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avitrack.app.data.export.CsvExportLocation
import org.avitrack.app.data.export.CsvExportResult
import org.avitrack.app.data.export.CsvFileSaver
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.export.ZootechnicalCsvExporter
import org.avitrack.app.domain.model.AlertSeverity
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.DailyMetrics
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.ui.theme.AviTrackCard
import org.avitrack.app.ui.theme.AviTrackPrimaryButton
import org.avitrack.app.ui.theme.AviTrackSecondaryButton
import org.avitrack.app.ui.theme.AviTrackStatusBadge
import org.avitrack.app.ui.theme.AviTrackTextField
import org.avitrack.app.ui.theme.LocalAviTrackColors
import kotlin.math.round

private const val CHART_DAYS = 14
private const val RECENT_LOG_COUNT = 7
private const val DECIMAL_SCALE = 10.0
private const val MISSING_VALUE = "—"
private val DashboardBreakpoint = 860.dp
private val SidebarWidth = 230.dp
private val DashboardPadding = 20.dp
private val DashboardSpacing = 12.dp
private val BarnSettingsMaxHeight = 360.dp
private val KpiSpacing = 16.dp
private const val WideKpiColumns = 4
private const val CompactKpiColumns = 2
private val ChartHeight = 112.dp
private val ChartStroke = 2.dp
private val ChartPointRadius = 3.dp
private val UnsafeFileNameCharacters = Regex("[^a-zA-Z0-9_-]+")
private const val MAX_FILE_NAME_BARN_LENGTH = 80

@Composable
fun OfficeDashboardScreen(
    barn: Barn,
    barns: List<Barn>,
    logs: List<DailyLog>,
    activeAlerts: List<EarlyAlert>,
    onBack: () -> Unit,
    onSelectBarn: (String) -> Unit,
    onCreateBarn: suspend (String, String, Int) -> Boolean,
    onUpdateBarn: suspend (Barn) -> Boolean,
) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    var showCreateBarn by remember { mutableStateOf(false) }
    var showBarnSettings by remember(barn.id) { mutableStateOf(false) }
    val openCreateBarn = { showCreateBarn = true }
    val openBarnSettings = { showBarnSettings = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= DashboardBreakpoint) {
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.width(SidebarWidth).fillMaxHeight(),
                    color = colors.mahoganyPrimary,
                    contentColor = colors.eggshellCanvas,
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(DashboardPadding),
                        verticalArrangement = Arrangement.spacedBy(DashboardSpacing),
                    ) {
                        Text(strings.officeDashboard, style = MaterialTheme.typography.titleLarge)
                        DashboardNavigation(onBack, openCreateBarn, vertical = true)
                        barns.forEach { option ->
                            NavigationDrawerItem(
                                label = { Text(option.name) },
                                selected = option.id == barn.id,
                                onClick = { onSelectBarn(option.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = colors.cinnamonBorder,
                                    unselectedContainerColor = Color.Transparent,
                                    selectedTextColor = colors.eggshellCanvas,
                                    unselectedTextColor = colors.eggshellCanvas,
                                ),
                            )
                        }
                    }
                }
                DashboardContent(barn, logs, activeAlerts, Modifier.weight(1f), WideKpiColumns, openBarnSettings)
            }
        } else {
            DashboardContent(barn, logs, activeAlerts, Modifier.fillMaxWidth(), CompactKpiColumns, openBarnSettings) {
                DashboardNavigation(onBack, openCreateBarn, vertical = false)
            }
        }
    }

    if (showCreateBarn) {
        CreateBarnDialog(onDismiss = { showCreateBarn = false }, onCreateBarn = onCreateBarn)
    }
    if (showBarnSettings) {
        BarnConfigurationDialog(barn, onUpdateBarn, onDismiss = { showBarnSettings = false })
    }
}

@Composable
private fun DashboardNavigation(onBack: () -> Unit, onNewBarn: () -> Unit, vertical: Boolean) {
    val strings = LocalStrings.current
    val actions: @Composable () -> Unit = {
        AviTrackSecondaryButton(strings.backToBarns, onClick = onBack)
        AviTrackPrimaryButton(strings.newBarn, onClick = onNewBarn)
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing)) { actions() }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(DashboardSpacing)) { actions() }
    }
}

private data class DashboardRecord(val log: DailyLog, val metrics: DailyMetrics) {
    val maxTemperature: Double get() = maxOf(log.analogTempMax, log.digitalTempMax)
}

private data class KpiSummary(val label: String, val value: String, val reference: String? = null)

@Composable
private fun DashboardContent(
    barn: Barn,
    logs: List<DailyLog>,
    activeAlerts: List<EarlyAlert>,
    modifier: Modifier,
    kpiColumns: Int,
    onBarnSettings: () -> Unit,
    navigation: (@Composable () -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val records = remember(barn, logs) {
        val barnLogs = logs.filter { it.barnId == barn.id }.sortedBy { it.epochDay }
        barnLogs.map { DashboardRecord(it, ZootechnicalEngine.calculateDailyMetrics(barn, it, barnLogs)) }
    }
    val alerts = remember(barn.id, activeAlerts) {
        activeAlerts.filter { it.barnId == barn.id }
            .sortedByDescending { it.severity == AlertSeverity.CRITICAL }
    }
    val alive = records.lastOrNull()?.metrics?.aliveBirds ?: barn.initialBirdCount
    val averageFeed = records.mapNotNull { it.metrics.feedGramsPerBird }.average()
    val averageWater = records.mapNotNull { it.metrics.waterCubicCentimetersPerBird }.average()
    val latestMetrics = records.lastOrNull()?.metrics
    val ageWeeks = latestMetrics?.flockAgeWeeks ?: ZootechnicalEngine.flockAgeWeeksAt(barn, currentEpochDay())
    val targets = latestMetrics?.intakeTargets ?: ZootechnicalEngine.standardIntakeTargetsForWeek(ageWeeks)
    var exportResult by remember { mutableStateOf<CsvExportResult?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val saveCsv = CsvFileSaver.rememberSave {
        exporting = false
        exportResult = it
    }

    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(DashboardPadding),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing),
    ) {
        navigation?.let {
            it()
            Text(strings.officeDashboard, style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(barn.name, style = MaterialTheme.typography.headlineMedium)
            AviTrackSecondaryButton(strings.barnSettings, onClick = onBarnSettings)
            AviTrackSecondaryButton(
                text = strings.exportCsv,
                enabled = !exporting,
                onClick = {
                    exportResult = null
                    val barnName = barn.name.replace(UnsafeFileNameCharacters, "_")
                        .take(MAX_FILE_NAME_BARN_LENGTH).trim('_').ifEmpty { "barn" }
                    val report = runCatching { ZootechnicalCsvExporter.buildBarnReportCsv(barn, logs) }
                    report.onSuccess {
                        exporting = true
                        saveCsv("avitrack_${barnName}_report.csv", it)
                    }.onFailure { exportResult = CsvExportResult.Failed }
                },
            )
        }
        Text(strings.flockAge.replace("{week}", ageWeeks.toString()))
        val kpis = listOf(
            KpiSummary(strings.aliveBirds, "$alive ${strings.birdsUnit}"),
            KpiSummary(
                strings.averageFeed,
                withUnit(averageFeed, strings.gramsPerBirdPerDayUnit),
                "${strings.target} · ${targets.targetFeedGramsPerBird.toInt()} " +
                    strings.gramsPerBirdPerDayUnit,
            ),
            KpiSummary(
                strings.averageWater,
                withUnit(averageWater, strings.cubicCentimetersPerBirdPerDayUnit),
                "${strings.referenceRange} · ${targets.minWaterCm3PerBird.toInt()}–${targets.maxWaterCm3PerBird.toInt()} " +
                    strings.cubicCentimetersPerBirdPerDayUnit,
            ),
            KpiSummary(strings.activeAlerts, alerts.size.toString()),
        )
        kpis.chunked(kpiColumns).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(KpiSpacing),
            ) {
                row.forEach { kpi ->
                    DashboardKpi(kpi, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        DashboardAlerts(alerts)
        DashboardTrends(records)
        DashboardHistory(records.takeLast(RECENT_LOG_COUNT).asReversed())
    }
    exportResult?.let { CsvExportDialog(it, onDismiss = { exportResult = null }) }
}

@Composable
private fun BarnConfigurationDialog(
    barn: Barn,
    onSave: suspend (Barn) -> Boolean,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val today = currentEpochDay()
    var placementDate by remember(barn.id) {
        mutableStateOf(
            if (barn.placementEpochDay == Barn.UNKNOWN_PLACEMENT_EPOCH_DAY) "" else formatEpochDay(barn.placementEpochDay),
        )
    }
    var age by remember(barn.id) { mutableStateOf(barn.placementAgeWeeks.toString()) }
    var minimum by remember(barn.id) { mutableStateOf(barn.comfortTempMin.toString()) }
    var maximum by remember(barn.id) { mutableStateOf(barn.comfortTempMax.toString()) }
    var deviation by remember(barn.id) { mutableStateOf(barn.sensorDeviationMax.toString()) }
    var saving by remember(barn.id) { mutableStateOf(false) }
    var saveFailed by remember(barn.id) { mutableStateOf(false) }
    val epochDay = parseEpochDay(placementDate.trim())
        ?.takeUnless { it == Barn.UNKNOWN_PLACEMENT_EPOCH_DAY }
    val ageWeeks = age.trim().toIntOrNull()?.takeIf { it >= 0 }
    val minTemp = minimum.toFiniteDecimal()
    val maxTemp = maximum.toFiniteDecimal()
    val maxDeviation = deviation.toFiniteDecimal()?.takeIf { it >= 0.0 }
    val valid = epochDay != null && ageWeeks != null && minTemp != null && maxTemp != null &&
        minTemp <= maxTemp && maxDeviation != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(strings.barnSettings) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = BarnSettingsMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DashboardSpacing),
            ) {
                Text(barn.name, style = MaterialTheme.typography.titleSmall)
                AviTrackTextField(
                    placementDate, { placementDate = it }, "${strings.placementDate} (${strings.dateFormat})",
                    modifier = Modifier.fillMaxWidth(), enabled = !saving,
                )
                AviTrackTextField(
                    age, { age = it }, strings.placementAgeWeeks,
                    modifier = Modifier.fillMaxWidth(), enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (epochDay != null && ageWeeks != null) {
                    val updated = barn.copy(placementEpochDay = epochDay, placementAgeWeeks = ageWeeks)
                    val currentAge = ZootechnicalEngine.flockAgeWeeksAt(updated, today)
                    Text(strings.flockAge.replace("{week}", currentAge.toString()))
                }
                for ((label, value, change) in listOf(
                    Triple(strings.comfortTempMin, minimum, { input: String -> minimum = input }),
                    Triple(strings.comfortTempMax, maximum, { input: String -> maximum = input }),
                    Triple(strings.sensorDeviationMax, deviation, { input: String -> deviation = input }),
                )) {
                    AviTrackTextField(
                        value, change, "$label (${strings.celsiusUnit})",
                        modifier = Modifier.fillMaxWidth(), enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (!valid || saveFailed) {
                    Text(
                        if (!valid) strings.invalidBarnSettings else strings.barnSaveFailed,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            AviTrackPrimaryButton(
                strings.save,
                enabled = valid && !saving,
                onClick = save@{
                    val updated = barn.copy(
                        placementEpochDay = epochDay ?: return@save,
                        placementAgeWeeks = ageWeeks ?: return@save,
                        comfortTempMin = minTemp ?: return@save,
                        comfortTempMax = maxTemp ?: return@save,
                        sensorDeviationMax = maxDeviation ?: return@save,
                    )
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            if (onSave(updated)) onDismiss() else saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        },
        dismissButton = { AviTrackSecondaryButton(strings.cancel, onClick = onDismiss, enabled = !saving) },
    )
}

private fun String.toFiniteDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
private fun CsvExportDialog(result: CsvExportResult, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    val title = when (result) {
        is CsvExportResult.Saved -> strings.csvDialogTitle
        CsvExportResult.Failed -> strings.csvExportFailed
        CsvExportResult.Cancelled -> strings.csvExportCancelled
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        icon = if (result is CsvExportResult.Saved) {
            { Icon(ConfirmationCheckIcon, contentDescription = null, tint = colors.dsmTechBlue) }
        } else {
            null
        },
        text = if (result is CsvExportResult.Saved) {
            {
                val location = when (val destination = result.location) {
                    is CsvExportLocation.FilePath -> destination.path
                    CsvExportLocation.BrowserDownloads -> strings.csvBrowserDownloadLocation
                    CsvExportLocation.SelectedDocument -> strings.csvSelectedLocation
                }
                Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
                    Text("${strings.csvFileNameLabel}: ${result.fileName}")
                    Text("${strings.csvLocationLabel}: $location")
                }
            }
        } else {
            null
        },
        confirmButton = { AviTrackPrimaryButton(strings.ok, onClick = onDismiss) },
    )
}

@Composable
private fun DashboardKpi(kpi: KpiSummary, modifier: Modifier) {
    AviTrackCard(modifier) {
        Text(kpi.label, style = MaterialTheme.typography.labelLarge)
        Text(kpi.value, style = MaterialTheme.typography.titleMedium)
        kpi.reference?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DashboardAlerts(alerts: List<EarlyAlert>) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    AviTrackCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
            Text(strings.earlyWarnings, style = MaterialTheme.typography.titleLarge)
            if (alerts.isEmpty()) Text(strings.noActiveAlerts)
            alerts.forEach { alert ->
                val copy = strings.alertText(alert.type)
                val critical = alert.severity == AlertSeverity.CRITICAL
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
                    Text(copy.title, style = MaterialTheme.typography.titleMedium)
                    AviTrackStatusBadge(
                        text = if (critical) strings.critical else strings.warning,
                        color = if (critical) colors.combCrimson else colors.yolkAmber,
                    )
                }
                Text(copy.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DashboardTrends(records: List<DashboardRecord>) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    AviTrackCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
            Text(strings.lastFourteenDays, style = MaterialTheme.typography.titleLarge)
            if (records.isEmpty()) {
                Text(strings.noRecords)
            } else {
                val lastDay = records.last().log.epochDay
                val firstDay = lastDay - (CHART_DAYS - 1)
                val byDay = remember(records) { records.associateBy { it.log.epochDay } }
                val days = firstDay..lastDay
                TrendChart(
                    title = strings.waterPerBird,
                    values = days.map { byDay[it]?.metrics?.waterCubicCentimetersPerBird },
                    unit = strings.cubicCentimetersPerBirdPerDayUnit,
                    color = colors.dsmTechBlue,
                )
                TrendChart(
                    title = strings.maximumTemperature,
                    values = days.map { byDay[it]?.maxTemperature },
                    unit = strings.celsiusUnit,
                    color = colors.yolkDeep,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatEpochDay(firstDay), style = MaterialTheme.typography.bodySmall)
                    Text(formatEpochDay(lastDay), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TrendChart(title: String, values: List<Double?>, unit: String, color: Color) {
    val strings = LocalStrings.current
    val points = values.filterNotNull().filter { it.isFinite() }
    val low = points.minOrNull()
    val high = points.maxOrNull()
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (low == null || high == null) {
        Text(strings.noRecords)
        return
    }
    Text("${displayNumber(low)}–${displayNumber(high)} $unit", style = MaterialTheme.typography.bodySmall)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
        val inset = ChartPointRadius.toPx()
        val plotWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val plotHeight = (size.height - inset * 2).coerceAtLeast(0f)
        drawLine(gridColor, Offset(inset, inset), Offset(inset + plotWidth, inset))
        drawLine(
            gridColor,
            Offset(inset, inset + plotHeight),
            Offset(inset + plotWidth, inset + plotHeight),
        )
        var previous: Offset? = null
        values.forEachIndexed { index, value ->
            if (value == null || !value.isFinite()) {
                previous = null
            } else {
                val normalized = if (high == low) 0.5f else ((value - low) / (high - low)).toFloat()
                val point = Offset(
                    x = inset + plotWidth * index / (CHART_DAYS - 1),
                    y = inset + plotHeight * (1f - normalized),
                )
                previous?.let { drawLine(color, it, point, strokeWidth = ChartStroke.toPx()) }
                drawCircle(color, radius = inset, center = point)
                previous = point
            }
        }
    }
}

@Composable
private fun DashboardHistory(records: List<DashboardRecord>) {
    val strings = LocalStrings.current
    AviTrackCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
            Text(strings.recentRecords, style = MaterialTheme.typography.titleLarge)
            if (records.isEmpty()) Text(strings.noRecords)
            records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                val log = record.log
                Text(formatEpochDay(log.epochDay), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DashboardSpacing)) {
                    Text("${strings.maximumTemperature} · ${withUnit(record.maxTemperature, strings.celsiusUnit)}")
                    Text(
                        "${strings.waterPerBird} · " +
                            withUnit(record.metrics.waterCubicCentimetersPerBird, strings.cubicCentimetersPerBirdPerDayUnit),
                    )
                    Text("${strings.mortalityCount} · ${log.mortalityCount}")
                    val meterReading = if (log.waterM3.isFinite()) {
                        "${formatWaterMeterReading(log.waterM3, strings.decimalSeparator)} ${strings.cubicMetersUnit}"
                    } else {
                        MISSING_VALUE
                    }
                    Text("${strings.waterM3} · $meterReading")
                    Text("${strings.litterPercentage} · ${log.litterPercentage}${strings.percentUnit}")
                    Text(strings.syncStatusText(log.syncStatus))
                }
            }
        }
    }
}

private fun withUnit(value: Double?, unit: String): String =
    if (value == null || !value.isFinite()) MISSING_VALUE else "${displayNumber(value)} $unit"

private fun displayNumber(value: Double): String =
    (round(value * DECIMAL_SCALE) / DECIMAL_SCALE).toString().removeSuffix(".0")
