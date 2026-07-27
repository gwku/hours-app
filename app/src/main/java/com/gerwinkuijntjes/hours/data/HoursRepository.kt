package com.gerwinkuijntjes.hours.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class HoursRepository(private val context: Context) {

    private val dao = HoursDatabase.getDatabase(context).hoursDao()
    private val gson = Gson()

    val clients: Flow<List<Client>> = dao.clients()
    val allVisits: Flow<List<Visit>> = dao.allVisits()

    fun visitsOn(date: LocalDate): Flow<List<Visit>> = dao.visitsOn(date.iso())

    fun visitsBetween(from: LocalDate, to: LocalDate): Flow<List<Visit>> =
        dao.visitsBetween(from.iso(), to.iso())

    // ---- clients ----

    suspend fun saveClient(client: Client) = dao.saveClient(client)

    suspend fun updateClient(client: Client) = dao.updateClient(client)

    suspend fun deleteClient(client: Client) = dao.deleteClient(client)

    suspend fun addClient(name: String): Client {
        val existing = dao.clientsNow()
        val client = Client(
            id = UUID.randomUUID().toString(),
            name = name,
            rate = 15.0,
            defaultHours = 3.0,
            fixedDays = "",
            extra = 0.0,
            color = PALETTE[existing.size % PALETTE.size],
            sortOrder = existing.size
        )
        dao.saveClient(client)
        return client
    }

    // ---- visits ----

    /** Record a visit, freezing the client's current rate and extra onto it. */
    suspend fun addVisit(client: Client, date: LocalDate, hours: Double) {
        dao.saveVisit(
            Visit(
                id = UUID.randomUUID().toString(),
                date = date.iso(),
                clientId = client.id,
                hours = hours,
                amount = client.amountFor(hours),
                extra = client.extra,
                rate = client.rate
            )
        )
    }

    suspend fun updateVisit(visit: Visit) = dao.updateVisit(visit)

    suspend fun deleteVisit(visit: Visit) = dao.deleteVisit(visit)

    // ---- backup ----

    suspend fun exportJson(appVersion: String): String = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            clients = dao.clientsNow().map { it.toBackup() },
            visits = dao.allVisitsNow().map { it.toBackup() },
            savedAt = LocalDate.now().iso(),
            appVersion = appVersion
        )
        gson.toJson(payload)
    }

    /** Replaces everything. Returns how many clients and visits came in. */
    suspend fun importJson(json: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val payload = gson.fromJson(json, BackupPayload::class.java)
            ?: throw IllegalArgumentException("empty backup")
        val clients = payload.clients.mapIndexed { index, it -> it.toEntity(index) }
        val visits = payload.visits.map { it.toEntity() }
        require(clients.isNotEmpty()) { "backup without clients" }

        dao.clearVisits()
        dao.clearClients()
        dao.saveClients(clients)
        dao.saveVisits(visits)
        clients.size to visits.size
    }

    suspend fun clientCount(): Int = dao.clientCount()

    suspend fun eraseAll() {
        dao.clearVisits()
        dao.clearClients()
    }

    /** First launch: load the clients and history bundled with the app. */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.clientCount() > 0) return@withContext
        val json = context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
        importJson(json)
    }

    companion object {
        private const val SEED_ASSET = "seed.json"

        val PALETTE = listOf(
            0xFFC2703DL, 0xFF3D7AC2L, 0xFF8A5BB5L, 0xFF3F9E7AL,
            0xFFC24A6BL, 0xFFA8912FL, 0xFF5A7D3DL, 0xFFB5563DL
        )
    }
}

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.iso(): String = format(ISO)

fun String.toLocalDate(): LocalDate = LocalDate.parse(this, ISO)
