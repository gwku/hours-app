package com.gerwinkuijntjes.hours

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gerwinkuijntjes.hours.backup.BackupSettings
import com.gerwinkuijntjes.hours.backup.DavError
import com.gerwinkuijntjes.hours.backup.DavFolder
import com.gerwinkuijntjes.hours.backup.WebDavClient
import com.gerwinkuijntjes.hours.backup.BackupUploader
import com.gerwinkuijntjes.hours.backup.BackupWorker
import com.gerwinkuijntjes.hours.data.Client
import com.gerwinkuijntjes.hours.data.HoursRepository
import com.gerwinkuijntjes.hours.data.Visit
import com.gerwinkuijntjes.hours.data.iso
import com.gerwinkuijntjes.hours.data.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/** One entry on the day screen: either already recorded, or waiting to be. */
data class DayRow(
    val client: Client,
    val visit: Visit?
) {
    val isRecorded: Boolean get() = visit != null
}

data class DaySummary(
    val visitCount: Int,
    val hours: Double,
    val amount: Double,
    val extra: Double
)

/**
 * A visit prepared for display: the date is parsed and the client resolved once,
 * when the data changes, rather than inside every row on every scroll frame.
 */
data class VisitRow(
    val visit: Visit,
    val client: Client?,
    val date: LocalDate
)

data class ClientTotal(
    val client: Client,
    val visitCount: Int,
    val hours: Double,
    val amount: Double
)

/** One-shot messages for the snackbar, so the UI stays free of business rules. */
sealed interface UiMessage {
    data class Saved(val clientName: String, val amount: Double) : UiMessage
    data object Updated : UiMessage
    data object Deleted : UiMessage
    data object Erased : UiMessage
    data object BackupSucceeded : UiMessage
    data class BackupFailed(val error: DavError) : UiMessage
    data class BackupNotConfigured(val problem: BackupSettings.Problem) : UiMessage
    data object BackupNothingToDo : UiMessage
    data object ExportDone : UiMessage
    data class RestoreDone(val clients: Int, val visits: Int) : UiMessage
    data object RestoreFailed : UiMessage
    data object EnterHoursFirst : UiMessage
    data object AmountNotValid : UiMessage
}

class HoursViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HoursRepository(application)
    private val backupSettings = BackupSettings(application)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _overviewWholeYear = MutableStateFlow(false)
    val overviewWholeYear: StateFlow<Boolean> = _overviewWholeYear.asStateFlow()

    private val _overviewMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val overviewMonth: StateFlow<LocalDate> = _overviewMonth.asStateFlow()

    private val _messages = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _messages.asStateFlow()

    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    private val _lastBackupMillis = MutableStateFlow(backupSettings.lastSuccessMillis)
    val lastBackupMillis: StateFlow<Long> = _lastBackupMillis.asStateFlow()

    val clients: StateFlow<List<Client>> = repository.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allVisits: StateFlow<List<Visit>> = repository.allVisits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        BackupWorker.schedulePeriodic(application)
    }

    // ---- day screen ----

    /**
     * The clients to show for the selected day: everyone with that weekday as a
     * regular day, plus anyone already recorded (so an off-schedule visit does not
     * vanish from the day it belongs to).
     */
    val dayRows: StateFlow<List<DayRow>> = combine(
        clients,
        allVisits,
        _selectedDate
    ) { clients, visits, date ->
        val onDay = visits.filter { it.date == date.iso() }
        val recordedIds = onDay.map { it.clientId }.toSet()
        clients
            .filter { date.dayOfWeek in it.days || it.id in recordedIds }
            .map { client -> DayRow(client, onDay.firstOrNull { it.clientId == client.id }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Everyone not already on the day screen, offered as one-tap additions. */
    val otherClients: StateFlow<List<Client>> = combine(clients, dayRows) { clients, rows ->
        val shown = rows.map { it.client.id }.toSet()
        clients.filterNot { it.id in shown }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daySummary: StateFlow<DaySummary> = combine(allVisits, _selectedDate) { visits, date ->
        visits.filter { it.date == date.iso() }.summarise()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DaySummary(0, 0.0, 0.0, 0.0))

    /**
     * Every date that has at least one visit. The week strip can be swiped through
     * any week, so it needs more than the currently selected one.
     */
    val datesWithVisits: StateFlow<Set<String>> = allVisits
        .combine(MutableStateFlow(Unit)) { visits, _ -> visits.map { it.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val weekSummary: StateFlow<DaySummary> = combine(allVisits, _selectedDate) { visits, date ->
        val monday = date.mondayOfWeek()
        val sunday = monday.plusDays(6)
        visits.filter { it.date.toLocalDate() in monday..sunday }.summarise()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DaySummary(0, 0.0, 0.0, 0.0))

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftWeek(weeks: Long) {
        _selectedDate.value = _selectedDate.value.plusWeeks(weeks)
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun recordVisit(client: Client, hours: Double) {
        if (hours <= 0) {
            _messages.value = UiMessage.EnterHoursFirst
            return
        }
        viewModelScope.launch {
            repository.addVisit(client, _selectedDate.value, hours)
            _messages.value = UiMessage.Saved(client.name, client.amountFor(hours))
            afterChange()
        }
    }

    fun updateVisit(visit: Visit, hours: Double, amount: Double?) {
        if (hours <= 0) {
            _messages.value = UiMessage.EnterHoursFirst
            return
        }
        if (amount == null) {
            _messages.value = UiMessage.AmountNotValid
            return
        }
        viewModelScope.launch {
            repository.updateVisit(visit.copy(hours = hours, amount = amount))
            _messages.value = UiMessage.Updated
            afterChange()
        }
    }

    fun deleteVisit(visit: Visit) {
        viewModelScope.launch {
            repository.deleteVisit(visit)
            _messages.value = UiMessage.Deleted
            afterChange()
        }
    }

    // ---- overview ----

    val overviewVisits: StateFlow<List<VisitRow>> = combine(
        allVisits,
        clients,
        _overviewMonth,
        _overviewWholeYear
    ) { visits, clients, month, wholeYear ->
        val byId = clients.associateBy { it.id }
        visits
            .asSequence()
            .map { VisitRow(it, byId[it.clientId], it.date.toLocalDate()) }
            .filter { row ->
                if (wholeYear) row.date.year == month.year
                else row.date.year == month.year && row.date.month == month.month
            }
            .sortedByDescending { it.visit.date }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overviewSummary: StateFlow<DaySummary> = overviewVisits
        .combine(MutableStateFlow(Unit)) { rows, _ -> rows.map { it.visit }.summarise() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DaySummary(0, 0.0, 0.0, 0.0))

    val overviewByClient: StateFlow<List<ClientTotal>> =
        combine(overviewVisits, clients) { rows, clients ->
            val visits = rows.map { it.visit }
            clients.mapNotNull { client ->
                val own = visits.filter { it.clientId == client.id }
                if (own.isEmpty()) null
                else ClientTotal(
                    client = client,
                    visitCount = own.size,
                    hours = own.sumOf { it.hours },
                    amount = own.sumOf { it.amount }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Totals for all twelve months of the selected year, for the bar chart. */
    val monthlyTotals: StateFlow<List<Double>> = combine(
        allVisits,
        _overviewMonth
    ) { visits, month ->
        val perMonth = DoubleArray(12)
        visits.forEach { visit ->
            val date = visit.date.toLocalDate()
            if (date.year == month.year) perMonth[date.monthValue - 1] += visit.amount
        }
        perMonth.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(12) { 0.0 })

    fun selectOverviewMonth(monthValue: Int) {
        _overviewMonth.value = _overviewMonth.value.withMonth(monthValue)
        _overviewWholeYear.value = false
    }

    fun setOverviewWholeYear(wholeYear: Boolean) {
        _overviewWholeYear.value = wholeYear
    }

    fun shiftOverviewMonth(months: Long) {
        _overviewMonth.value = _overviewMonth.value.plusMonths(months)
    }

    fun shiftOverviewYear(years: Long) {
        _overviewMonth.value = _overviewMonth.value.plusYears(years)
    }

    fun clientById(id: String): Client? = clients.value.firstOrNull { it.id == id }

    // ---- settings ----

    fun updateClient(client: Client) {
        viewModelScope.launch {
            repository.updateClient(client)
            afterChange()
        }
    }

    fun toggleClientDay(client: Client, day: DayOfWeek) {
        val days = client.days.toMutableSet()
        if (!days.add(day)) days.remove(day)
        updateClient(client.copy(fixedDays = Client.daysToText(days)))
    }

    /**
     * Set to the new client's id right after it is created, so the settings screen
     * can open its detail page: a client with only a name is not finished yet.
     */
    private val _openClientId = MutableStateFlow<String?>(null)
    val openClientId: StateFlow<String?> = _openClientId.asStateFlow()

    fun addClient(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _openClientId.value = repository.addClient(name.trim()).id
            afterChange()
        }
    }

    fun consumeOpenClient() {
        _openClientId.value = null
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            repository.deleteClient(client)
            afterChange()
        }
    }

    /**
     * Counted once per data change rather than per recomposition: the settings
     * list asks for this for every client while it scrolls.
     */
    val visitCounts: StateFlow<Map<String, Int>> = allVisits
        .combine(MutableStateFlow(Unit)) { visits, _ ->
            visits.groupingBy { it.clientId }.eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun clientFlowById(id: String): StateFlow<Client?> = clients
        .combine(MutableStateFlow(Unit)) { list, _ -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun eraseAll() {
        viewModelScope.launch {
            repository.eraseAll()
            _messages.value = UiMessage.Erased
            afterChange()
        }
    }

    // ---- backup ----

    // ---- backup settings ----
    //
    // The fields are held in memory and written to preferences on a debounce, so
    // typing a long URL does not mean a write per keystroke. Everything that acts
    // on the settings flushes first, so nothing can act on a stale value.

    private val _serverUrl = MutableStateFlow(backupSettings.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _folderPath = MutableStateFlow(backupSettings.folderPath)
    val folderPath: StateFlow<String> = _folderPath.asStateFlow()

    private val _backupUsername = MutableStateFlow(backupSettings.username)
    val backupUsername: StateFlow<String> = _backupUsername.asStateFlow()

    private val _backupPassword = MutableStateFlow(backupSettings.password)
    val backupPassword: StateFlow<String> = _backupPassword.asStateFlow()

    private val _backupProblem = MutableStateFlow(backupSettings.problem)
    val backupProblem: StateFlow<BackupSettings.Problem> = _backupProblem.asStateFlow()

    private val _lastBackupError = MutableStateFlow(backupSettings.lastError)
    val lastBackupError: StateFlow<String> = _lastBackupError.asStateFlow()

    private val settingsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Its own init block, below the flow it collects: an init block placed above a
    // property runs before that property exists, and collecting null crashes.
    @OptIn(FlowPreview::class)
    private val debouncedWrites = viewModelScope.launch {
        settingsChanged.debounce(DEBOUNCE_MILLIS).collect { persistBackupSettings() }
    }

    fun setServerUrl(value: String) {
        _serverUrl.value = value
        settingsChanged.tryEmit(Unit)
    }

    fun setFolderPath(value: String) {
        _folderPath.value = value
        settingsChanged.tryEmit(Unit)
    }

    fun setBackupUsername(value: String) {
        _backupUsername.value = value
        settingsChanged.tryEmit(Unit)
    }

    fun setBackupPassword(value: String) {
        _backupPassword.value = value
        settingsChanged.tryEmit(Unit)
    }

    private fun persistBackupSettings() {
        backupSettings.serverUrl = _serverUrl.value
        backupSettings.folderPath = _folderPath.value
        backupSettings.username = _backupUsername.value
        backupSettings.password = _backupPassword.value
        _backupProblem.value = backupSettings.problem
    }

    /** A client built from what is on screen right now, for the folder picker. */
    fun webDavClient(): WebDavClient = WebDavClient(
        baseUrl = _serverUrl.value.trim().trimEnd('/'),
        username = _backupUsername.value.trim(),
        password = _backupPassword.value
    )

    suspend fun listFolders(path: String): Result<List<DavFolder>> =
        webDavClient().listFolders(path)

    suspend fun createFolder(parentPath: String, name: String): Result<Unit> =
        webDavClient().createFolder("$parentPath/${name.trim()}")

    fun daysSinceLastBackup(): Long? = backupSettings.daysSinceLastSuccess()

    fun backupNow() {
        if (_backupInProgress.value) return
        viewModelScope.launch {
            persistBackupSettings()
            _backupInProgress.value = true
            val result = BackupUploader(getApplication()).upload()
            _backupInProgress.value = false
            refreshBackupState()
            _messages.value = when (result) {
                is BackupUploader.Result.Success -> UiMessage.BackupSucceeded
                is BackupUploader.Result.NotConfigured ->
                    UiMessage.BackupNotConfigured(backupSettings.problem)
                is BackupUploader.Result.NothingToBackUp -> UiMessage.BackupNothingToDo
                is BackupUploader.Result.Failed -> UiMessage.BackupFailed(result.error)
            }
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val json = repository.exportJson(BuildConfig.VERSION_NAME)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("no stream")
                }.isSuccess
            }
            _messages.value = if (ok) UiMessage.ExportDone else UiMessage.RestoreFailed
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: error("no stream")
                }
                repository.importJson(json)
            }
            _messages.value = result.fold(
                onSuccess = { (clients, visits) -> UiMessage.RestoreDone(clients, visits) },
                onFailure = { UiMessage.RestoreFailed }
            )
            if (result.isSuccess) afterChange()
        }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    /** Every change is worth getting off the device; the worker debounces the burst. */
    private fun afterChange() {
        backupSettings.lastChangeMillis = System.currentTimeMillis()
        _backupOverdue.value = false
        BackupWorker.scheduleAfterEdit(getApplication())
    }

    /**
     * Whether there is work sitting on this phone that no upload has taken off it.
     * Re-checked when the app comes back to the foreground, which is the moment
     * somebody is actually there to read a warning and press the button.
     */
    private val _backupOverdue = MutableStateFlow(backupSettings.backupIsOverdue())
    val backupOverdue: StateFlow<Boolean> = _backupOverdue.asStateFlow()

    fun refreshBackupState() {
        _lastBackupMillis.value = backupSettings.lastSuccessMillis
        _lastBackupError.value = backupSettings.lastError
        _backupOverdue.value = backupSettings.backupIsOverdue()
    }
}

/** Long enough to swallow a burst of typing, short enough to feel immediate. */
private const val DEBOUNCE_MILLIS = 400L

private fun List<Visit>.summarise() = DaySummary(
    visitCount = size,
    hours = sumOf { it.hours },
    amount = sumOf { it.amount },
    extra = sumOf { it.extra }
)

private fun LocalDate.mondayOfWeek(): LocalDate =
    with(WeekFields.ISO.dayOfWeek(), 1L)
