package org.avitrack.app.domain.repository

import kotlinx.coroutines.flow.StateFlow
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.EarlyAlert

interface FarmRepository {
    val barns: StateFlow<List<Barn>>
    val logs: StateFlow<List<DailyLog>>
    val activeAlerts: StateFlow<List<EarlyAlert>>

    /** Saves one record per barn and date, replacing an earlier entry for the same day. */
    suspend fun addDailyLog(log: DailyLog)

    /** Returns the number of local records that moved from pending to synced. */
    suspend fun syncPendingLogs(): Result<Int>

    fun resetToDemoData()
}
