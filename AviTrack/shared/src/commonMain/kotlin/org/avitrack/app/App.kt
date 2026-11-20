package org.avitrack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val HeaderPadding = 16.dp
private val HeaderSpacing = 8.dp

@Composable
fun App() {
    val viewModel = remember { AviTrackViewModel(OfflineFirstFarmRepository()) }
    val scope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsState()
    val language by viewModel.language.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
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
                                )
                                else -> OfficeDashboardScreen(
                                    barn = selectedBarn,
                                    logs = logs,
                                    activeAlerts = alerts,
                                    onBack = { viewModel.selectBarn(null) },
                                    onCreateBarn = viewModel::createBarn,
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

    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = HeaderSpacing),
            horizontalArrangement = Arrangement.spacedBy(HeaderPadding),
            verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("AviTrack", style = MaterialTheme.typography.titleLarge)
                Text("${user.name.substringBefore(" (")} · $role", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(HeaderSpacing), verticalAlignment = Alignment.CenterVertically) {
                Text(networkStatus)
                Switch(
                    checked = online,
                    onCheckedChange = onOnlineChange,
                    modifier = Modifier.semantics { contentDescription = networkStatus },
                )
            }
            AviTrackSecondaryButton(if (darkTheme) strings.dayMode else strings.nightMode, onClick = onThemeToggle)
            AviTrackSecondaryButton("${strings.language} · ${language.name}", onClick = onLanguageToggle)
            AviTrackSecondaryButton(strings.signOut, onClick = onLogout)
        }
    }
}
