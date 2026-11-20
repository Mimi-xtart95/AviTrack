package org.avitrack.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.avitrack.app.i18n.AppLanguage
import org.avitrack.app.i18n.LocalStrings
import org.avitrack.app.ui.state.STAFF_PIN_LENGTH
import org.avitrack.app.ui.state.StaffMember
import org.avitrack.app.ui.state.UserRole
import org.avitrack.app.ui.theme.AviTrackPrimaryButton
import org.avitrack.app.ui.theme.AviTrackSecondaryButton

private val LoginWidth = 460.dp
private val LoginPadding = 24.dp
private val LoginSpacing = 12.dp
private val ProfileCorner = RoundedCornerShape(16.dp)
private val ProfileBorder = 1.dp

@Composable
fun LoginScreen(
    staffMembers: List<StaffMember>,
    language: AppLanguage,
    darkTheme: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onLogin: (StaffMember, String) -> Boolean,
) {
    val strings = LocalStrings.current
    var selectedMember by remember { mutableStateOf(staffMembers.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var invalidPin by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LoginPadding)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = LoginWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LoginSpacing),
        ) {
            Text("AviTrack", style = MaterialTheme.typography.headlineLarge)
            Text(strings.selectProfile, style = MaterialTheme.typography.titleLarge)
            staffMembers.forEach { member ->
                val selected = member.id == selectedMember?.id
                val role = when (member.role) {
                    UserRole.FARM_OPERATOR -> strings.farmOperator
                    UserRole.OFFICE_ADMIN -> strings.officeAdmin
                }
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .semantics { this.selected = selected }
                        .clickable(role = Role.RadioButton) {
                            selectedMember = member
                            pin = ""
                            invalidPin = false
                        },
                    shape = ProfileCorner,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        ProfileBorder,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(Modifier.padding(LoginSpacing)) {
                        Text(role, style = MaterialTheme.typography.titleMedium)
                        Text(member.name.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge)
                        Text("${strings.demoPin} · ${member.pin}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter { digit -> digit in '0'..'9' }.take(STAFF_PIN_LENGTH)
                    invalidPin = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.pinLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = invalidPin,
                supportingText = if (invalidPin) ({ Text(strings.invalidPin) }) else null,
            )
            AviTrackSecondaryButton(
                text = strings.fillPin,
                onClick = {
                    pin = selectedMember?.pin.orEmpty()
                    invalidPin = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMember != null,
            )
            AviTrackPrimaryButton(
                text = strings.signIn,
                onClick = { selectedMember?.let { invalidPin = !onLogin(it, pin) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMember != null && pin.length == STAFF_PIN_LENGTH,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LoginSpacing)) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { onLanguageChange(option) },
                        label = { Text(option.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LoginSpacing)) {
                FilterChip(selected = !darkTheme, onClick = { onThemeChange(false) }, label = { Text(strings.dayMode) })
                FilterChip(selected = darkTheme, onClick = { onThemeChange(true) }, label = { Text(strings.nightMode) })
            }
        }
    }
}
