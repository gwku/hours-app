package com.gerwinkuijntjes.hours.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.monthLabel
import java.time.Month

/**
 * Twelve months of earnings as bars, each tappable to jump to that month.
 *
 * Bars are laid out with plain composables rather than a Canvas so the labels
 * scale with the user's font size and the whole thing stays accessible.
 */
@Composable
fun MonthChart(
    totals: List<Double>,
    selectedMonth: Int?,
    modifier: Modifier = Modifier,
    onSelectMonth: (Int) -> Unit
) {
    val locale = currentLocale()
    val peak = totals.maxOrNull() ?: 0.0
    if (peak <= 0.0) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        totals.forEachIndexed { index, amount ->
            val monthValue = index + 1
            val isSelected = monthValue == selectedMonth
            // A month with a little work should still show a sliver, so the
            // difference between "quiet" and "nothing" stays visible.
            val fraction = if (amount <= 0.0) 0f else (amount / peak).toFloat().coerceAtLeast(0.04f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMonth(monthValue) }
            ) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.height(96.dp).fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((96 * fraction).dp)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    amount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                },
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    // Three letters, not one: "j" for January, June and July tells
                    // the reader nothing. Sized to fit twelve across a phone.
                    text = monthLabel(Month.of(monthValue), locale)
                        .trimEnd('.')
                        .take(3),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    maxLines = 1,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** A label above a figure, sized so three fit across a phone without clipping. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
