package org.avitrack.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.avitrack.app.domain.model.Barn
import org.avitrack.app.domain.model.DailyLog
import org.avitrack.app.domain.model.SyncStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

private const val BARNS = "barns"
private const val DAILY_LOGS = "daily_logs"
private const val PAGE_SIZE = 500
private const val REQUEST_TIMEOUT_MILLIS = 10_000L

internal class SupabaseRestClient(
    url: String = SupabaseConfig.SUPABASE_URL,
    anonKey: String = SupabaseConfig.SUPABASE_ANON_KEY,
) {
    private val endpoint = url.trim().trimEnd('/') + "/rest/v1"
    private val key = anonKey.trim()
    private val client = HttpClient {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun fetchBarns(): List<BarnRow> = fetchRows(BARNS)

    suspend fun fetchLogs(): List<DailyLogRow> = fetchRows(DAILY_LOGS)

    suspend fun upsertBarns(rows: List<BarnRow>) = upsert(BARNS, rows)

    suspend fun upsertLogs(logs: List<DailyLog>) {
        val updatedAt = Clock.System.now().toString()
        upsert(DAILY_LOGS, logs.map { DailyLogRow.from(it, updatedAt) })
    }

    fun close() = client.close()

    private suspend inline fun <reified T : RemoteRow> fetchRows(table: String): List<T> {
        val rows = mutableListOf<T>()
        var lastId: String? = null
        while (true) {
            val page = client.get("$endpoint/$table") {
                supabaseHeaders()
                accept(ContentType.Application.Json)
                parameter("order", "id.asc")
                parameter("limit", PAGE_SIZE)
                lastId?.let { id ->
                    val escaped = id.replace("\\", "\\\\").replace("\"", "\\\"")
                    parameter("id", "gt.\"$escaped\"")
                }
            }.requireSuccess("GET", table).body<List<T>>()
            if (page.isEmpty()) return rows
            rows.addAll(page)
            val nextId = page.last().id
            check(nextId != lastId) { "Supabase pagination did not advance." }
            lastId = nextId
        }
    }

    private suspend inline fun <reified T> upsert(table: String, rows: List<T>) {
        for (batch in rows.chunked(PAGE_SIZE)) {
            val response = client.post("$endpoint/$table") {
                supabaseHeaders()
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                parameter("on_conflict", "id")
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            response.requireSuccess("POST", table)
        }
    }

    private fun HttpRequestBuilder.supabaseHeaders() {
        header("apikey", key)
        if (key.startsWith("eyJ")) bearerAuth(key)
    }

    private suspend fun HttpResponse.requireSuccess(method: String, table: String): HttpResponse {
        if (status.isSuccess()) return this
        val errorBody = try {
            bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "<response body unavailable>"
        }
        val diagnostic = if (key.isEmpty()) errorBody else errorBody.replace(key, "[redacted]")
        println("Supabase $method /rest/v1/$table failed (${status.value}): $diagnostic")
        error("Supabase $method /rest/v1/$table failed with HTTP ${status.value}.")
    }
}

internal interface RemoteRow {
    val id: String
}

@Serializable
internal data class BarnRow(
    @SerialName("id") override val id: String,
    @SerialName("name") val name: String,
    @SerialName("breed") val breed: String,
    @SerialName("initial_bird_count") val initialBirdCount: Int,
    @SerialName("created_at") val createdAt: String? = null,
) : RemoteRow {
    fun toBarn(): Barn = Barn(id, name, initialBirdCount)
}

@Serializable
internal data class DailyLogRow(
    @SerialName("id") override val id: String,
    @SerialName("barn_id") val barnId: String,
    @SerialName("epoch_day") val epochDay: Int,
    @SerialName("analog_temp_min") val analogTempMin: Double,
    @SerialName("analog_temp_max") val analogTempMax: Double,
    @SerialName("digital_temp_min") val digitalTempMin: Double,
    @SerialName("digital_temp_max") val digitalTempMax: Double,
    @SerialName("relative_humidity_avg") val relativeHumidityAvg: Double,
    @SerialName("mortality_count") val mortalityCount: Int,
    @SerialName("feed_kg") val feedKg: Double,
    @SerialName("water_m3") val waterM3: Double,
    @SerialName("litter_percentage") val litterPercentage: Int,
    @SerialName("notes") val notes: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) : RemoteRow {
    fun toLog(): DailyLog = DailyLog(
        barnId, epochDay, analogTempMin, analogTempMax, digitalTempMin, digitalTempMax,
        relativeHumidityAvg, mortalityCount, feedKg, waterM3, litterPercentage, notes.orEmpty(),
        SyncStatus.SYNCED,
    )

    companion object {
        fun from(log: DailyLog, updatedAt: String): DailyLogRow = DailyLogRow(
            "${log.barnId}_${log.epochDay}", log.barnId, log.epochDay, log.analogTempMin, log.analogTempMax,
            log.digitalTempMin, log.digitalTempMax, log.relativeHumidityAvg, log.mortalityCount,
            log.feedKg, log.waterM3, log.litterPercentage, log.notes, updatedAt,
        )
    }
}
