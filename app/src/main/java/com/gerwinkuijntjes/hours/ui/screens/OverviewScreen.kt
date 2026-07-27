package com.gerwinkuijntjes.hours.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.ui.components.ClientDot
import com.gerwinkuijntjes.hours.ui.components.MonthChart
import com.gerwinkuijntjes.hours.ui.components.SectionHeader
import com.gerwinkuijntjes.hours.ui.components.StatTile
import com.gerwinkuijntjes.hours.ui.components.SurfaceCard
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.formatHours
import com.gerwinkuijntjes.hours.ui.formatMoney
import com.gerwinkuijntjes.hours.ui.monthAndYear
import com.gerwinkuijntjes.hours.ui.shortDayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: HoursViewModel,
    contentPadding: PaddingValues,
    onOpenDay: () -> Unit
) {
    val locale = currentLocale()
    val wholeYear by viewModel.overviewWholeYear.collectAsState()
    val month by viewModel.overviewMonth.collectAsState()
    val summary by viewModel.overviewSummary.collectAsState()
    val byClient by viewModel.overviewByClient.collectAsState()
    val visits by viewModel.overviewVisits.collectAsState()
    val monthly by viewModel.monthlyTotals.collectAsState()

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    // Money divided by hours, not the average of the rates: an hour at the highest
    // paying client and one at the lowest are not worth the same, so weighting matters.
    val perHour = if (summary.hours > 0) summary.amount / summary.hours else 0.0
    val monthsWithWork = monthly.count { it > 0 }
    val perMonth = if (monthsWithWork > 0) monthly.sum() / monthsWithWork else 0.0

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_overview)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 18.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !wholeYear,
                        onClick = { viewModel.setOverviewWholeYear(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(stringResource(R.string.by_month)) }
                    SegmentedButton(
                        selected = wholeYear,
                        onClick = { viewModel.setOverviewWholeYear(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(stringResource(R.string.whole_year)) }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                // In year mode the arrows step a whole year, so a previous year's
                // total is reachable without walking back twelve months.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (wholeYear) viewModel.shiftOverviewYear(-1)
                            else viewModel.shiftOverviewMonth(-1)
                        }
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = stringResource(
                                if (wholeYear) R.string.previous_year else R.string.previous_month
                            )
                        )
                    }
                    Text(
                        text = if (wholeYear) month.year.toString() else month.monthAndYear(locale),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            if (wholeYear) viewModel.shiftOverviewYear(1)
                            else viewModel.shiftOverviewMonth(1)
                        }
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(
                                if (wholeYear) R.string.next_year else R.string.next_month
                            )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.earned),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMoney(summary.amount, locale),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (summary.extra > 0) {
                        Text(
                            text = stringResource(
                                R.string.of_which_extra,
                                formatMoney(summary.extra, locale)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = stringResource(R.string.stat_visits),
                            value = summary.visitCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = stringResource(R.string.stat_hours),
                            value = formatHours(summary.hours, locale, maxDecimals = 2),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = if (wholeYear) stringResource(R.string.stat_per_month)
                            else stringResource(R.string.stat_per_hour),
                            value = formatMoney(if (wholeYear) perMonth else perHour, locale),
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.chart_title, month.year)) }

            item {
                if (monthly.all { it <= 0.0 }) {
                    Text(
                        text = stringResource(R.string.chart_empty, month.year),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        MonthChart(
                            totals = monthly,
                            selectedMonth = if (wholeYear) null else month.monthValue,
                            onSelectMonth = viewModel::selectOverviewMonth
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.per_client)) }

            if (byClient.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.nothing_recorded),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(byClient, key = { it.client.id }) { total ->
                // A row with a proportion bar rather than a four column table:
                // the columns clipped on narrow screens, and the bar says more.
                val share = if (summary.amount > 0) (total.amount / summary.amount).toFloat() else 0f
                Column(Modifier.padding(vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClientDot(total.client.color)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = total.client.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatMoney(total.amount, locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(share)
                                .height(6.dp)
                                .background(Color(total.client.color), CircleShape)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(
                            R.string.overview_summary,
                            stringResource(
                                R.string.hours_with_unit,
                                formatHours(total.hours, locale, maxDecimals = 2)
                            ),
                            total.visitCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            item { SectionHeader(stringResource(R.string.all_visits)) }

            if (visits.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_visits_in_period),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visits, key = { it.visit.id }) { row ->
                val visit = row.visit
                val client = row.client
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Editing lives on the day screen, so jump there rather
                            // than building a second place that changes the same record.
                            viewModel.selectDate(row.date)
                            onOpenDay()
                        }
                        .padding(vertical = 11.dp)
                ) {
                    ClientDot(client?.color ?: 0xFF888888L)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = client?.name ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = row.date.shortDayText(locale) + " · " +
                                stringResource(
                                    R.string.hours_with_unit,
                                    formatHours(visit.hours, locale, maxDecimals = 2)
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = formatMoney(visit.amount, locale),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
