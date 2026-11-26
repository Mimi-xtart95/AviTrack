package org.avitrack.app.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.SyncStatus
import org.avitrack.app.domain.repository.FarmRepository
import org.avitrack.app.i18n.AppLanguage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class UserRole { FARM_OPERATOR, OFFICE_ADMIN }

data class StaffMember(val id: String, val name: String, val role: UserRole, val pin: String)

const val STAFF_PIN_LENGTH = 4

class AviTrackViewModel(private val repository: FarmRepository) {
    // These profiles and PINs exist for the demo.
    val staffMembers = listOf(
        StaffMember("carlos", "Carlos (Farm Operator)", UserRole.FARM_OPERATOR, "1234"),
        StaffMember("elena", "Dr. Elena (Vet & Office)", UserRole.OFFICE_ADMIN, "0000"),
    )

    private val mutableCurrentUser = MutableStateFlow<StaffMember?>(null)
    private val mutableSelectedBarnId = MutableStateFlow<String?>(null)
    private val mutableLanguage = MutableStateFlow(AppLanguage.EN)
    private val mutableIsDarkTheme = MutableStateFlow(false)
    private val mutableIsOnline = MutableStateFlow(true)

    val currentUser: StateFlow<StaffMember?> = mutableCurrentUser.asStateFlow()
    val selectedBarnId: StateFlow<String?> = mutableSelectedBarnId.asStateFlow()
    val language: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()
    val isDarkTheme: StateFlow<Boolean> = mutableIsDarkTheme.asStateFlow()
    val isOnline: StateFlow<Boolean> = mutableIsOnline.asStateFlow()
    val barns = repository.barns
    val barnBreeds = repository.barnBreeds
    val logs = repository.logs
    val activeAlerts = repository.activeAlerts

    fun loginWithPin(member: StaffMember, enteredPin: String): Boolean {
        val registered = staffMembers.find { it.id == member.id } ?: return false
        if (enteredPin.length != STAFF_PIN_LENGTH || registered.pin != enteredPin) return false
        mutableSelectedBarnId.value = null
        mutableCurrentUser.value = registered
        return true
    }

    fun logout() {
        mutableCurrentUser.value = null
        mutableSelectedBarnId.value = null
    }

    fun selectBarn(barnId: String?) {
        if (barnId == null || (currentUser.value != null && barns.value.any { it.id == barnId })) {
            mutableSelectedBarnId.value = barnId
        }
    }

    fun setLanguage(language: AppLanguage) {
        mutableLanguage.value = language
    }

    fun setDarkTheme(enabled: Boolean) {
        mutableIsDarkTheme.value = enabled
    }

    suspend fun setOnline(online: Boolean) {
        mutableIsOnline.value = online
        if (online && currentUser.value != null) repository.syncPendingLogs()
    }

    suspend fun submitDailyLog(log: DailyLog): Boolean {
        if (currentUser.value?.role != UserRole.FARM_OPERATOR || selectedBarnId.value != log.barnId) return false
        if (!log.analogTempMin.isFinite() || !log.analogTempMax.isFinite() ||
            !log.digitalTempMin.isFinite() || !log.digitalTempMax.isFinite() ||
            log.analogTempMin > log.analogTempMax || log.digitalTempMin > log.digitalTempMax ||
            log.relativeHumidityAvg !in 0.0..MAX_HUMIDITY_PERCENTAGE ||
            !log.feedKg.isFinite() || log.feedKg < 0.0 || !log.waterM3.isFinite() || log.waterM3 < 0.0
        ) return false

        try {
            repository.addDailyLog(log.copy(syncStatus = SyncStatus.PENDING))
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (isOnline.value) repository.syncPendingLogs()
        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createBarn(name: String, breed: String, initialBirdCount: Int): Boolean {
        if (currentUser.value?.role != UserRole.OFFICE_ADMIN) return false
        if (name.isBlank() || breed.isBlank() || initialBirdCount <= 0) return false

        return try {
            repository.addBarn(Barn(Uuid.random().toString(), name.trim(), initialBirdCount), breed.trim())
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    suspend fun updateBarn(barn: Barn): Boolean {
        if (currentUser.value?.role != UserRole.OFFICE_ADMIN || selectedBarnId.value != barn.id) return false
        try {
            repository.updateBarn(barn)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (isOnline.value) repository.syncPendingLogs()
        return true
    }

    private companion object {
        const val MAX_HUMIDITY_PERCENTAGE = 100.0
    }
}
