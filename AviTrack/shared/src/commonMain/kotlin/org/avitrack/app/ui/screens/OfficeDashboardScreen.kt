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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.avitrack.app.domain.engine.ZootechnicalEngine
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
import org.avitrack.app.ui.theme.LocalAviTrackColors
import kotlin.math.round

private const val CHART_DAYS = 14
private const val RECENT_LOG_COUNT = 7
private const val MIN_WATER_REFERENCE = 210
private const val MAX_WATER_REFERENCE = 260
private const val DECIMAL_SCALE = 10.0
private const val MISSING_VALUE = "—"
private val DashboardBreakpoint = 860.dp
private val SidebarWidth = 230.dp
private val DashboardPadding = 20.dp
private val DashboardSpacing = 12.dp
private val KpiSpacing = 16.dp
private const val WideKpiColumns = 4
private const val CompactKpiColumns = 2
private val ChartHeight = 112.dp
private val ChartStroke = 2.dp
private val ChartPointRadius = 3.dp

@Composable
fun OfficeDashboardScreen(
    barn: Barn,
    barns: List<Barn>,
    logs: List<DailyLog>,
    activeAlerts: List<EarlyAlert>,
    onBack: () -> Unit,
    onSelectBarn: (String) -> Unit,
    onCreateBarn: suspend (String, String, Int) -> Boolean,
) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    var showCreateBarn by remember { mutableStateOf(false) }
    val openCreateBarn = { showCreateBarn = true }

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
                DashboardContent(barn, logs, activeAlerts, Modifier.weight(1f), WideKpiColumns)
            }
        } else {
            DashboardContent(barn, logs, activeAlerts, Modifier.fillMaxWidth(), CompactKpiColumns) {
                DashboardNavigation(onBack, openCreateBarn, vertical = false)
            }
        }
    }

    if (showCreateBarn) {
        CreateBarnDialog(onDismiss = { showCreateBarn = false }, onCreateBarn = onCreateBarn)
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

    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(DashboardPadding),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing),
    ) {
        navigation?.let {
            it()
            Text(strings.officeDashboard, style = MaterialTheme.typography.labelLarge)
        }
        Text(barn.name, style = MaterialTheme.typography.headlineMedium)
        val kpis = listOf(
            KpiSummary(strings.aliveBirds, "$alive ${strings.birdsUnit}"),
            KpiSummary(
                strings.averageFeed,
                withUnit(averageFeed, strings.gramsPerBirdPerDayUnit),
                "${strings.target} · ${ZootechnicalEngine.TARGET_FEED_GRAMS_PER_BIRD.toInt()} " +
                    strings.gramsPerBirdPerDayUnit,
            ),
            KpiSummary(
                strings.averageWater,
                withUnit(averageWater, strings.cubicCentimetersPerBirdPerDayUnit),
                "${strings.referenceRange} · $MIN_WATER_REFERENCE–$MAX_WATER_REFERENCE " +
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
