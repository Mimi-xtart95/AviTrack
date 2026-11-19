package org.avitrack.app.data.repository

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avitrack.app.data.seed.DemoFarmSeed
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.domain.model.SyncStatus
import org.avitrack.app.domain.repository.FarmRepository
import kotlin.coroutines.cancellation.CancellationException

/** [pushLogs] can be supplied by a remote client. The default simulates a successful upload. */
class OfflineFirstFarmRepository(
    private val pushLogs: suspend (List<DailyLog>) -> Unit = {},
) : FarmRepository {
    private val state = MutableStateFlow(createDemoState())
    private val syncMutex = Mutex()

    override val barns: StateFlow<List<Barn>> = FarmStateFlow(state) { it.barns }
    override val logs: StateFlow<List<DailyLog>> = FarmStateFlow(state) { it.logs }
    override val activeAlerts: StateFlow<List<EarlyAlert>> = FarmStateFlow(state) { it.activeAlerts }

    override suspend fun addDailyLog(log: DailyLog) {
        val pendingLog = log.copy(syncStatus = SyncStatus.PENDING)
        state.update { current ->
            require(current.barns.any { it.id == log.barnId }) { "Unknown barn ID." }
            val existingIndex = current.logs.indexOfFirst {
                it.barnId == log.barnId && it.epochDay == log.epochDay
            }
            val updatedLogs = current.logs.toMutableList()
            if (existingIndex < 0) {
                updatedLogs.add(pendingLog)
            } else {
                updatedLogs[existingIndex] = pendingLog
            }
            createState(current.barns, updatedLogs.toList())
        }
    }

    override suspend fun syncPendingLogs(): Result<Int> = syncMutex.withLock {
        val pendingLogs = state.value.logs.filter { it.syncStatus == SyncStatus.PENDING }
        if (pendingLogs.isEmpty()) return@withLock Result.success(0)

        try {
            pushLogs(pendingLogs)
            Result.success(markSynced(pendingLogs))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override fun resetToDemoData() {
        state.value = createDemoState()
    }

    private fun markSynced(uploadedLogs: List<DailyLog>): Int {
        val uploadedByBarnAndDay = uploadedLogs.groupBy { it.barnId }
            .mapValues { (_, barnLogs) -> barnLogs.associateBy { it.epochDay } }
        while (true) {
            val current = state.value
            var syncedCount = 0
            val updatedLogs = current.logs.map { log ->
                val uploadedLog = uploadedByBarnAndDay[log.barnId]?.get(log.epochDay)
                // A new edit must stay pending if it arrived while this upload was in flight.
                if (log === uploadedLog) {
                    syncedCount++
                    log.copy(syncStatus = SyncStatus.SYNCED)
                } else {
                    log
                }
            }
            if (syncedCount == 0) return 0
            if (state.compareAndSet(current, createState(current.barns, updatedLogs))) return syncedCount
        }
    }

    private fun createDemoState(): FarmState {
        val (barns, logs) = DemoFarmSeed.createDemoDataset()
        return createState(barns, logs)
    }

    private fun createState(barns: List<Barn>, logs: List<DailyLog>): FarmState {
        val logsByBarn = logs.groupBy { it.barnId }
        // Active alerts describe the latest recorded date of each barn.
        val alerts = barns.flatMap { barn ->
            val barnLogs = logsByBarn[barn.id].orEmpty()
            val latestLog = barnLogs.maxByOrNull { it.epochDay }
            if (latestLog == null) emptyList() else ZootechnicalEngine.evaluateAlerts(barn, latestLog, barnLogs)
        }
        return FarmState(barns, logs, alerts)
    }

    private class FarmState(
        val barns: List<Barn>,
        val logs: List<DailyLog>,
        val activeAlerts: List<EarlyAlert>,
    )

    // Each view reads from the same snapshot, including before the first collection.
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class FarmStateFlow<T>(
        private val state: StateFlow<FarmState>,
        private val select: (FarmState) -> T,
    ) : StateFlow<T> {
        override val value: T get() = select(state.value)
        override val replayCache: List<T> get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            state.map(select).distinctUntilChanged().collect(collector)
            awaitCancellation()
        }
    }
}
