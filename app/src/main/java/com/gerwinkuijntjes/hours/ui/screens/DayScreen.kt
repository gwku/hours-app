package com.gerwinkuijntjes.hours.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.data.Client
import com.gerwinkuijntjes.hours.data.Visit
import com.gerwinkuijntjes.hours.ui.components.ClientDot
import com.gerwinkuijntjes.hours.ui.components.DecimalField
import com.gerwinkuijntjes.hours.ui.components.HoursStepper
import com.gerwinkuijntjes.hours.ui.components.SectionHeader
import com.gerwinkuijntjes.hours.ui.components.SurfaceCard
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.dayNameCapitalised
import com.gerwinkuijntjes.hours.ui.formatHours
import com.gerwinkuijntjes.hours.ui.formatMoney
import com.gerwinkuijntjes.hours.ui.fullDayText
import com.gerwinkuijntjes.hours.ui.parseNumber
import com.gerwinkuijntjes.hours.ui.weekRangeText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayScreen(
    viewModel: HoursViewModel,
    contentPadding: PaddingValues
) {
    val locale = currentLocale()
    val date by viewModel.selectedDate.collectAsState()
    val rows by viewModel.dayRows.collectAsState()
    val others by viewModel.otherClients.collectAsState()
    val daySummary by viewModel.daySummary.collectAsState()
    val visitDates by viewModel.datesWithVisits.collectAsState()
    val weekSummary by viewModel.weekSummary.collectAsState()
    val backupOverdue by viewModel.backupOverdue.collectAsState()
    val backupRunning by viewModel.backupInProgress.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    // Re-check when the screen comes back into view: that is when somebody is
    // there to read the warning and do something about it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshBackupState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.padding(horizontal = 18.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = when (date) {
                    today -> stringResource(R.string.today)
                    today.minusDays(1) -> stringResource(R.string.yesterday)
                    else -> date.dayNameCapitalised(locale)
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = date.fullDayText(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
        }

        item {
            WeekNavigator(
                weekLabel = weekRangeText(date.mondayOfSameWeek(), locale),
                onPrevious = { viewModel.shiftWeek(-1) },
                onNext = { viewModel.shiftWeek(1) },
                onPickDate = { showDatePicker = true }
            )
            Spacer(Modifier.height(10.dp))
        }

        item {
            WeekPager(
                selected = date,
                today = today,
                visitDates = visitDates,
                locale = locale,
                onSelect = viewModel::selectDate
            )
            Spacer(Modifier.height(11.dp))
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (weekSummary.visitCount == 0) {
                        stringResource(R.string.week_empty)
                    } else {
                        stringResource(
                            R.string.week_summary,
                            weekSummary.visitCount,
                            stringResource(
                                R.string.hours_with_unit,
                                formatHours(weekSummary.hours, locale)
                            ),
                            formatMoney(weekSummary.amount, locale)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (date != today) {
                    OutlinedButton(
                        onClick = viewModel::goToToday,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.go_to_today))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (backupOverdue) {
            item {
                SurfaceCard(
                    background = MaterialTheme.colorScheme.errorContainer,
                    bordered = false,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_overdue),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = viewModel::backupNow,
                        enabled = !backupRunning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 10.dp).height(44.dp)
                    ) {
                        Text(
                            if (backupRunning) stringResource(R.string.backup_running)
                            else stringResource(R.string.backup_now)
                        )
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        R.string.no_fixed_clients,
                        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(rows, key = { it.client.id }) { row ->
            val visit = row.visit
            if (visit == null) {
                UnrecordedVisitCard(
                    client = row.client,
                    locale = locale,
                    onSave = { hours -> viewModel.recordVisit(row.client, hours) }
                )
            } else {
                RecordedVisitCard(
                    client = row.client,
                    visit = visit,
                    locale = locale,
                    onSave = { hours, amount -> viewModel.updateVisit(visit, hours, amount) },
                    onDelete = { viewModel.deleteVisit(visit) }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        item {
            SectionHeader(stringResource(R.string.other_client))
            if (others.isEmpty()) {
                Text(
                    text = stringResource(R.string.all_clients_shown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OtherClientChips(others) { client ->
                    viewModel.recordVisit(client, client.defaultHours)
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                if (daySummary.visitCount == 0) {
                    Text(
                        text = stringResource(R.string.day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.day_total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatMoney(daySummary.amount, locale),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.hours_with_unit,
                                formatHours(daySummary.hours, locale)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPick = {
                viewModel.selectDate(it)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun WeekNavigator(
    weekLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        NavSquare(Icons.Default.ChevronLeft, stringResource(R.string.previous_week), onPrevious)
        OutlinedButton(
            onClick = onPickDate,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = stringResource(R.string.pick_a_date),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(9.dp))
            Text(weekLabel, color = MaterialTheme.colorScheme.onSurface)
        }
        NavSquare(Icons.Default.ChevronRight, stringResource(R.string.next_week), onNext)
    }
}

@Composable
private fun NavSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(44.dp)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The week strip, swipeable through any week.
 *
 * Swiping keeps the same weekday and moves into the neighbouring week, which is
 * what the arrows do too, so both gestures agree on what "next week" means.
 */
@Composable
private fun WeekPager(
    selected: LocalDate,
    today: LocalDate,
    visitDates: Set<String>,
    locale: Locale,
    onSelect: (LocalDate) -> Unit
) {
    val anchor = remember { today.mondayOfSameWeek() }
    fun pageFor(date: LocalDate) =
        CENTER_PAGE + ChronoUnit.WEEKS.between(anchor, date.mondayOfSameWeek()).toInt()

    val pagerState = rememberPagerState(initialPage = pageFor(selected)) { WEEK_PAGES }

    // Keep the strip in step when the date changes elsewhere: the arrows, the
    // date picker, or the "today" button.
    LaunchedEffect(selected) {
        val target = pageFor(selected)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    // And the other way round: a swipe moves the selection into the week shown.
    LaunchedEffect(pagerState.settledPage) {
        val monday = anchor.plusWeeks((pagerState.settledPage - CENTER_PAGE).toLong())
        val candidate = monday.plusDays((selected.dayOfWeek.value - 1).toLong())
        if (candidate != selected) onSelect(candidate)
    }

    HorizontalPager(
        state = pagerState,
        pageSpacing = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        val monday = anchor.plusWeeks((page - CENTER_PAGE).toLong())
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (0L..6L).forEach { offset ->
                val day = monday.plusDays(offset)
                val isSelected = day == selected
                val hasVisits = day.toString() in visitDates
                val shape = RoundedCornerShape(12.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.surface,
                            shape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .clickable { onSelect(day) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                when {
                                    !hasVisits -> Color.Transparent
                                    isSelected -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

/** Roughly forty years either way, which is more weeks than anyone will swipe. */
private const val WEEK_PAGES = 4001
private const val CENTER_PAGE = WEEK_PAGES / 2

/**
 * Chips that wrap to fill each row, rather than a fixed two per line: names vary
 * from "De Wit" to "Van der Heijden" and a fixed grid wastes half the width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherClientChips(clients: List<Client>, onPick: (Client) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        clients.forEach { client ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(client) }
                    // Equal padding left and right; the built-in chip reserves a
                    // narrower inset for its leading icon and looks lopsided.
                    .padding(horizontal = 16.dp)
            ) {
                ClientDot(client.color)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun UnrecordedVisitCard(
    client: Client,
    locale: Locale,
    onSave: (Double) -> Unit
) {
    var hoursText by remember(client.id) {
        mutableStateOf(formatHours(client.defaultHours, locale, maxDecimals = 2, minDecimals = 2))
    }
    val hours = parseNumber(hoursText) ?: 0.0

    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClientDot(client.color)
            Spacer(Modifier.width(13.dp))
            Text(
                text = client.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            HoursStepper(text = hoursText, onTextChange = { hoursText = it })
        }
        // Full width rather than beside the stepper: with an extra allowance the
        // line is long enough to wrap into the client's name on a narrow screen.
        Text(
            text = amountLine(client, hours, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(hours) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun amountLine(client: Client, hours: Double, locale: Locale): String {
    val amount = formatMoney(client.amountFor(hours), locale)
    return if (client.extra > 0) {
        stringResource(R.string.includes_extra, amount, formatMoney(client.extra, locale))
    } else {
        amount
    }
}

@Composable
private fun RecordedVisitCard(
    client: Client,
    visit: Visit,
    locale: Locale,
    onSave: (Double, Double?) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(visit.id) { mutableStateOf(false) }
    var confirmDelete by remember(visit.id) { mutableStateOf(false) }

    if (!editing) {
        SurfaceCard(
            background = MaterialTheme.colorScheme.primaryContainer,
            bordered = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(client.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(
                            R.string.hours_with_unit,
                            formatHours(visit.hours, locale)
                        ) + " · " + formatMoney(visit.amount, locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
        }
        return
    }

    var hoursText by remember(visit.id) { mutableStateOf(formatHours(visit.hours, locale, maxDecimals = 2, minDecimals = 2)) }
    var amountText by remember(visit.id) { mutableStateOf(formatHours(visit.amount, locale, 2)) }

    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClientDot(client.color)
            Spacer(Modifier.width(13.dp))
            Text(client.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            text = if (visit.extra > 0) {
                stringResource(
                    R.string.rate_per_hour_plus_extra,
                    formatMoney(visit.rate, locale),
                    formatMoney(visit.extra, locale)
                )
            } else {
                stringResource(R.string.rate_per_hour, formatMoney(visit.rate, locale))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        HoursStepper(
            text = hoursText,
            onTextChange = { typed ->
                hoursText = typed
                // Recalculating on every keystroke keeps the amount honest; the user
                // can still overwrite it afterwards for a one-off different payment.
                parseNumber(typed)?.let {
                    amountText = formatHours(visit.recalculated(it), locale, 2)
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        DecimalField(
            label = stringResource(R.string.amount_label),
            value = amountText,
            onValueChange = { amountText = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedIconButton(
                onClick = { editing = false },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            OutlinedIconButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    onSave(parseNumber(hoursText) ?: 0.0, parseNumber(amountText))
                    editing = false
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_visit_title)) },
            text = { Text(stringResource(R.string.delete_visit_message, client.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    editing = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.mondayOfSameWeek(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())
