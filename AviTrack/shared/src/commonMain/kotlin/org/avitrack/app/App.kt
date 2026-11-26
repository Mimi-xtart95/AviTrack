package org.avitrack.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avitrack.app.data.repository.OfflineFirstFarmRepository
import org.avitrack.app.i18n.AppLanguage
import org.avitrack.app.i18n.EnglishStrings
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.i18n.SpanishStrings
import org.avitrack.app.ui.screens.BarnModeScreen
import org.avitrack.app.ui.screens.BarnSelectionScreen
import org.avitrack.app.ui.screens.LoginScreen
import org.avitrack.app.ui.screens.OfficeDashboardScreen
import org.avitrack.app.ui.state.AviTrackViewModel
import org.avitrack.app.ui.state.StaffMember
import org.avitrack.app.ui.state.UserRole
import org.avitrack.app.ui.theme.AviTrackSecondaryButton
import org.avitrack.app.ui.theme.AviTrackTheme
import org.avitrack.app.ui.theme.LocalAviTrackColors

private val HeaderBreakpoint = 768.dp
private val HeaderPadding = 16.dp
private val HeaderSpacing = 8.dp
private val WideHeaderSpacing = 12.dp
private val WideIdentityMaxWidth = 210.dp
private val CompactHeaderShape = RoundedCornerShape(12.dp)
private val CompactHeaderButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
private val CompactHeaderBorderWidth = 1.dp
private const val COMPACT_HEADER_BORDER_ALPHA = 0.3f

@Composable
fun App() {
    val repository = remember { OfflineFirstFarmRepository() }
    val viewModel = remember(repository) { AviTrackViewModel(repository) }
    val scope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsState()
    val language by viewModel.language.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    LaunchedEffect(repository, isOnline) {
        if (isOnline) {
            repository.refreshFromRemote()
            repository.syncPendingLogs()
        }
    }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    val barnId by viewModel.selectedBarnId.collectAsState()
    val barns by viewModel.barns.collectAsState()
    val breeds by viewModel.barnBreeds.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val alerts by viewModel.activeAlerts.collectAsState()
    val selectedBarn = barns.find { it.id == barnId }
    val strings = when (language) {
        AppLanguage.EN -> EnglishStrings
        AppLanguage.ES -> SpanishStrings
    }

    AviTrackTheme(darkTheme = isDarkTheme) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                Column(Modifier.fillMaxSize().safeContentPadding()) {
                    val activeUser = user
                    if (activeUser == null) {
                        LoginScreen(
                            staffMembers = viewModel.staffMembers,
                            language = language,
                            darkTheme = isDarkTheme,
                            onLanguageChange = viewModel::setLanguage,
                            onThemeChange = viewModel::setDarkTheme,
                            onLogin = viewModel::loginWithPin,
                        )
                    } else {
                        AviTrackHeader(
                            user = activeUser,
                            language = language,
                            darkTheme = isDarkTheme,
                            online = isOnline,
                            onOnlineChange = { online -> scope.launch { viewModel.setOnline(online) } },
                            onThemeToggle = { viewModel.setDarkTheme(!isDarkTheme) },
                            onLanguageToggle = {
                                viewModel.setLanguage(if (language == AppLanguage.EN) AppLanguage.ES else AppLanguage.EN)
                            },
                            onLogout = viewModel::logout,
                        )
                        HorizontalDivider()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                selectedBarn == null -> BarnSelectionScreen(
                                    role = activeUser.role,
                                    barns = barns,
                                    barnBreeds = breeds,
                                    logs = logs,
                                    activeAlerts = alerts,
                                    onSelectBarn = viewModel::selectBarn,
                                    onCreateBarn = viewModel::createBarn,
                                )
                                activeUser.role == UserRole.FARM_OPERATOR -> BarnModeScreen(
                                    barn = selectedBarn,
                                    logs = logs,
                                    onBack = { viewModel.selectBarn(null) },
                                    onSubmit = viewModel::submitDailyLog,
                                    // Read the repository snapshot before the UI collector recomposes.
                                    readSavedLogs = { viewModel.logs.value },
                                )
                                else -> OfficeDashboardScreen(
                                    barn = selectedBarn,
                                    barns = barns,
                                    logs = logs,
                                    activeAlerts = alerts,
                                    onBack = { viewModel.selectBarn(null) },
                                    onSelectBarn = viewModel::selectBarn,
                                    onCreateBarn = viewModel::createBarn,
                                    onUpdateBarn = viewModel::updateBarn,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AviTrackHeader(
    user: StaffMember,
    language: AppLanguage,
    darkTheme: Boolean,
    online: Boolean,
    onOnlineChange: (Boolean) -> Unit,
    onThemeToggle: () -> Unit,
    onLanguageToggle: () -> Unit,
    onLogout: () -> Unit,
) {
    val strings = LocalStrings.current
    val role = when (user.role) {
        UserRole.FARM_OPERATOR -> strings.farmOperator
        UserRole.OFFICE_ADMIN -> strings.officeAdmin
    }
    val networkStatus = if (online) strings.online else strings.offline
    val themeLabel = if (darkTheme) strings.dayMode else strings.nightMode
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text("AviTrack", style = MaterialTheme.typography.titleLarge)
            Text(
                "${user.name.substringBefore(" (")} · $role",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val networkControl: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(HeaderSpacing), verticalAlignment = Alignment.CenterVertically) {
            Text(networkStatus, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = online,
                onCheckedChange = onOnlineChange,
                modifier = Modifier.semantics { contentDescription = networkStatus },
            )
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val headerModifier = Modifier.fillMaxWidth().padding(horizontal = HeaderPadding)
            if (maxWidth >= HeaderBreakpoint) {
                Row(
                    headerModifier.padding(vertical = HeaderSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    identity(Modifier.widthIn(max = WideIdentityMaxWidth))
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WideHeaderSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        networkControl()
                        AviTrackSecondaryButton(themeLabel, onClick = onThemeToggle)
                        AviTrackSecondaryButton("${strings.language} · ${language.name}", onClick = onLanguageToggle)
                        AviTrackSecondaryButton(strings.signOut, onClick = onLogout)
                    }
                }
            } else {
                Column(headerModifier) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        identity(Modifier.weight(1f))
                        networkControl()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HeaderSpacing)) {
                        CompactHeaderButton(themeLabel, onThemeToggle, Modifier.weight(1f))
                        CompactHeaderButton(language.name, onLanguageToggle, Modifier.weight(1f), strings.language)
                        CompactHeaderButton(strings.signOut, onLogout, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHeaderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    label: String = text,
) {
    val colors = LocalAviTrackColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) colors.mahoganyPrimary else colors.cinnamonBorder,
        label = "Header button color",
    )
    Button(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = CompactHeaderShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = colors.eggshellCanvas,
        ),
        contentPadding = CompactHeaderButtonPadding,
        border = BorderStroke(CompactHeaderBorderWidth, colors.ochreAccent.copy(alpha = COMPACT_HEADER_BORDER_ALPHA)),
        interactionSource = interactionSource,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
