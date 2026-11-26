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
import org.avitrack.app.data.remote.BarnRow
import org.avitrack.app.data.remote.SupabaseConfig
import org.avitrack.app.data.remote.SupabaseRestClient
import org.avitrack.app.data.seed.DemoFarmSeed
import org.avitrack.app.domain.engine.ZootechnicalEngine
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.EarlyAlert
import org.avitrack.app.domain.model.SyncStatus
import org.avitrack.app.domain.repository.FarmRepository
import kotlin.coroutines.cancellation.CancellationException

/** [pushLogs] handles demo synchronization when Supabase is unconfigured. */
class OfflineFirstFarmRepository(
    private val pushLogs: suspend (List<DailyLog>) -> Unit = {},
) : FarmRepository {
    private val remote = if (SupabaseConfig.isConfigured) SupabaseRestClient() else null
    private val demoDataset = DemoFarmSeed.createDemoDataset()
    private val demoBarnIds = demoDataset.first.map { it.id }.toSet()
    private val demoLogsByDate = demoDataset.second.associateBy { it.barnId to it.epochDay }
    private val state = MutableStateFlow(createDemoState())
    private val syncMutex = Mutex()

    override val barns: StateFlow<List<Barn>> = FarmStateFlow(state) { it.barns }
    override val logs: StateFlow<List<DailyLog>> = FarmStateFlow(state) { it.logs }
    override val activeAlerts: StateFlow<List<EarlyAlert>> = FarmStateFlow(state) { it.activeAlerts }
    override val barnBreeds: StateFlow<Map<String, String>> = FarmStateFlow(state) { it.barnBreeds }

    override suspend fun addBarn(barn: Barn) = addBarn(barn, "")

    override suspend fun addBarn(barn: Barn, breed: String) {
        state.update { current ->
            require(current.barns.none { it.id == barn.id }) { "Duplicate barn ID." }
            createState(
                current.barns + barn,
                current.logs,
                current.barnBreeds + (barn.id to breed),
                current.pendingBarnIds + barn.id,
            )
        }
    }

    override suspend fun addDailyLog(log: DailyLog) {
        val pendingLog = log.copy(syncStatus = SyncStatus.PENDING)
        state.update { current ->
            require(current.barns.any { it.id == log.barnId }) { "Unknown barn ID." }
            val previousLog = current.logs.asSequence()
                .filter { it.barnId == log.barnId && it.epochDay < log.epochDay }
                .maxByOrNull { it.epochDay }
            val minimumWaterReading = previousLog?.waterM3?.coerceAtLeast(0.0) ?: 0.0
            require(log.waterM3.isFinite() && log.waterM3 >= minimumWaterReading) {
                "Water meter reading must be finite and cannot decrease."
            }
            val existingIndex = current.logs.indexOfFirst {
                it.barnId == log.barnId && it.epochDay == log.epochDay
            }
            val updatedLogs = current.logs.toMutableList()
            if (existingIndex < 0) {
                updatedLogs.add(pendingLog)
            } else {
                updatedLogs[existingIndex] = pendingLog
            }
            createState(current.barns, updatedLogs.toList(), current.barnBreeds, current.pendingBarnIds)
        }
    }

    override suspend fun syncPendingLogs(): Result<Int> = syncMutex.withLock {
        val snapshot = state.value
        val pendingLogs = snapshot.logs.filter { it.syncStatus == SyncStatus.PENDING }
        val pendingBarns = snapshot.barns.filter { it.id in snapshot.pendingBarnIds }
        remoteResult {
            if (remote == null) {
                if (pendingLogs.isNotEmpty()) pushLogs(pendingLogs)
            } else {
                remote.upsertBarns(snapshot.barns.map { barn ->
                    BarnRow(barn.id, barn.name, snapshot.barnBreeds[barn.id].orEmpty(), barn.initialBirdCount)
                })
                remote.upsertLogs(pendingLogs)
            }
            markSynced(pendingLogs, pendingBarns)
        }
    }

    suspend fun refreshFromRemote(): Result<Unit> = syncMutex.withLock {
        val client = remote ?: return@withLock Result.success(Unit)
        remoteResult {
            val remoteBarns = client.fetchBarns()
            val remoteLogs = client.fetchLogs().map { it.toLog() }
            state.update { current ->
                val pendingLogs = current.logs.filter { it.syncStatus == SyncStatus.PENDING }
                val editedParentIds = current.logs
                    .filter { it !== demoLogsByDate[it.barnId to it.epochDay] }
                    .map { it.barnId }.toSet()
                val remoteIds = remoteBarns.map { it.id }.toSet()
                val localBarns = current.barns.filter {
                    it.id !in demoBarnIds || it.id in editedParentIds || remoteBarns.isEmpty()
                }
                val mergedBarns = localBarns.associateBy { it.id } + remoteBarns
                    .filterNot { it.id in current.pendingBarnIds }
                    .associate { it.id to it.toBarn() }
                val localLogs = current.logs.filter {
                    it !== demoLogsByDate[it.barnId to it.epochDay] ||
                        (it.barnId in mergedBarns && (remoteBarns.isEmpty() || it.barnId !in remoteIds))
                }
                val mergedLogs = (localLogs + remoteLogs + pendingLogs)
                    .associateBy { it.barnId to it.epochDay }
                    .values.toList()
                val mergedBreeds = current.barnBreeds.filterKeys { it in mergedBarns } + remoteBarns
                    .filterNot { it.id in current.pendingBarnIds }.associate { it.id to it.breed }
                createState(mergedBarns.values.toList(), mergedLogs, mergedBreeds, current.pendingBarnIds)
            }
        }
    }

    fun close() {
        remote?.close()
    }

    private suspend fun <T> remoteResult(action: suspend () -> T): Result<T> = try {
        Result.success(action())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    override fun resetToDemoData() {
        state.value = createDemoState()
    }

    private fun markSynced(uploadedLogs: List<DailyLog>, uploadedBarns: List<Barn>): Int {
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
            val syncedBarnIds = uploadedBarns.filter { uploaded -> current.barns.any { it === uploaded } }
                .map { it.id }.toSet()
            if (syncedCount == 0 && syncedBarnIds.isEmpty()) return 0
            val updated = createState(
                current.barns, updatedLogs, current.barnBreeds, current.pendingBarnIds - syncedBarnIds,
            )
            if (state.compareAndSet(current, updated)) return syncedCount
        }
    }

    private fun createDemoState(): FarmState {
        val (barns, logs) = demoDataset
        val breeds = barns.mapNotNull { barn -> DemoFarmSeed.breedFor(barn.id)?.let { barn.id to it } }.toMap()
        return createState(barns, logs, breeds)
    }

    private fun createState(
        barns: List<Barn>,
        logs: List<DailyLog>,
        barnBreeds: Map<String, String>,
        pendingBarnIds: Set<String> = emptySet(),
    ): FarmState {
        val logsByBarn = logs.groupBy { it.barnId }
        // Active alerts describe the latest recorded date of each barn.
        val alerts = barns.flatMap { barn ->
            val barnLogs = logsByBarn[barn.id].orEmpty()
            val latestLog = barnLogs.maxByOrNull { it.epochDay }
            if (latestLog == null) emptyList() else ZootechnicalEngine.evaluateAlerts(barn, latestLog, barnLogs)
        }
        return FarmState(barns, logs, alerts, barnBreeds, pendingBarnIds)
    }

    private class FarmState(
        val barns: List<Barn>,
        val logs: List<DailyLog>,
        val activeAlerts: List<EarlyAlert>,
        val barnBreeds: Map<String, String>,
        val pendingBarnIds: Set<String>,
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
