package org.avitrack.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.ui.state.UserRole
import org.avitrack.app.ui.theme.AviTrackCard
import org.avitrack.app.ui.theme.AviTrackPrimaryButton
import org.avitrack.app.ui.theme.AviTrackSecondaryButton
import org.avitrack.app.ui.theme.AviTrackStatusBadge
import org.avitrack.app.ui.theme.AviTrackTextField
import org.avitrack.app.ui.theme.LocalAviTrackColors

private val SelectionPadding = 20.dp
private val SelectionSpacing = 16.dp
private val SelectionBreakpoint = 768.dp
private const val WideBarnColumnCount = 3

@Composable
fun BarnSelectionScreen(
    role: UserRole,
    barns: List<Barn>,
    barnBreeds: Map<String, String>,
    logs: List<DailyLog>,
    activeAlerts: List<EarlyAlert>,
    onSelectBarn: (String) -> Unit,
    onCreateBarn: suspend (String, String, Int) -> Boolean,
) {
    val strings = LocalStrings.current
    val logsByBarn = remember(logs) { logs.groupBy { it.barnId } }
    val barnsWithAlerts = remember(activeAlerts) { activeAlerts.map { it.barnId }.toSet() }
    var showCreateBarn by remember { mutableStateOf(false) }
    val card: @Composable (Barn, Modifier) -> Unit = { barn, modifier ->
        val alive = remember(barn, logsByBarn) {
            ZootechnicalEngine.calculateAliveBirds(barn, logsByBarn[barn.id].orEmpty())
        }
        BarnSelectionCard(barn, barnBreeds[barn.id], alive, barn.id in barnsWithAlerts, modifier) {
            onSelectBarn(barn.id)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= SelectionBreakpoint
        val columnCount = if (wide) WideBarnColumnCount else 1
        val rows = remember(barns, columnCount) { barns.chunked(columnCount) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SelectionPadding),
            verticalArrangement = Arrangement.spacedBy(SelectionSpacing),
        ) {
            item {
                val title: @Composable () -> Unit = {
                    Text(strings.selectBarn, style = MaterialTheme.typography.headlineMedium)
                }
                val createButton: @Composable () -> Unit = {
                    if (role == UserRole.OFFICE_ADMIN) {
                        AviTrackPrimaryButton(strings.newBarn, onClick = { showCreateBarn = true })
                    }
                }
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title()
                        createButton()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(SelectionSpacing)) {
                        title()
                        createButton()
                    }
                }
            }
            items(rows, key = { it.first().id }) { row ->
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SelectionSpacing)) {
                        row.forEach { barn ->
                            key(barn.id) { card(barn, Modifier.weight(1f)) }
                        }
                        repeat(WideBarnColumnCount - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                } else {
                    card(row.single(), Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (role == UserRole.OFFICE_ADMIN && showCreateBarn) {
        CreateBarnDialog(onDismiss = { showCreateBarn = false }, onCreateBarn = onCreateBarn)
    }
}

@Composable
private fun BarnSelectionCard(
    barn: Barn,
    breed: String?,
    alive: Int,
    hasAlerts: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalAviTrackColors.current
    AviTrackCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(SelectionSpacing)) {
            Text(barn.name, style = MaterialTheme.typography.titleLarge)
            breed?.takeIf { it.isNotBlank() }?.let { Text(it) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SelectionSpacing),
                verticalArrangement = Arrangement.spacedBy(SelectionSpacing),
            ) {
                Text("${strings.aliveBirds} · $alive")
                AviTrackStatusBadge(
                    text = if (hasAlerts) strings.activeAlerts else strings.allClear,
                    color = if (hasAlerts) colors.combCrimson else colors.ochreAccent,
                )
            }
            AviTrackSecondaryButton(strings.enterBarn, onClick = onSelect)
        }
    }
}

@Composable
internal fun CreateBarnDialog(
    onDismiss: () -> Unit,
    onCreateBarn: suspend (String, String, Int) -> Boolean,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var birdCount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val count = birdCount.toIntOrNull()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(strings.createBarnTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SelectionSpacing)) {
                AviTrackTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = strings.barnName,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                AviTrackTextField(
                    value = breed,
                    onValueChange = {
                        breed = it
                        error = null
                    },
                    label = strings.breed,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                AviTrackTextField(
                    value = birdCount,
                    onValueChange = {
                        birdCount = it.filter { digit -> digit in '0'..'9' }
                        error = null
                    },
                    label = strings.initialBirdCount,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AviTrackPrimaryButton(
                text = strings.createBarn,
                enabled = !saving,
                onClick = {
                    if (name.isBlank() || breed.isBlank() || count == null || count <= 0) {
                        error = strings.invalidBarn
                    } else {
                        saving = true
                        scope.launch {
                            try {
                                if (onCreateBarn(name, breed, count)) onDismiss() else error = strings.barnSaveFailed
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(strings.cancel) } },
    )
}
