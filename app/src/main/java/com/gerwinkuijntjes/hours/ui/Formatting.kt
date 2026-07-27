package com.gerwinkuijntjes.hours.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The locale the user actually sees, so formatting follows the app language. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Building a NumberFormat is expensive, since it resolves the locale and parses
 * a pattern, and these are called several times per list row while scrolling.
 * One instance per locale, reused.
 *
 * NumberFormat is not thread safe; formatting is guarded because Compose is not
 * the only caller in principle, and an uncontended lock costs nothing.
 */
private val moneyFormats = ConcurrentHashMap<Locale, NumberFormat>()
private val numberFormats = ConcurrentHashMap<String, NumberFormat>()

fun formatMoney(amount: Double, locale: Locale): String {
    val format = moneyFormats.getOrPut(locale) {
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("EUR")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    return synchronized(format) { format.format(amount) }
}

/**
 * Hours without trailing zeroes by default: 3.75 -> "3,75", 3.0 -> "3".
 * Pass [minDecimals] where a stable width matters, such as the hours picker.
 */
fun formatHours(
    hours: Double,
    locale: Locale,
    maxDecimals: Int = 4,
    minDecimals: Int = 0
): String {
    val format = numberFormats.getOrPut("$locale/$minDecimals/$maxDecimals") {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = minDecimals
            maximumFractionDigits = maxDecimals
        }
    }
    return synchronized(format) { format.format(hours) }
}

/**
 * Parse a number the way someone actually types it: "3,75" and "3.75" both work,
 * regardless of which separator the locale prefers.
 */
fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

fun LocalDate.fullDayText(locale: Locale): String {
    val day = dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val month = month.getDisplayName(TextStyle.FULL, locale)
    return "$day $dayOfMonth $month $year"
}

fun LocalDate.dayNameCapitalised(locale: Locale): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { it.titlecase(locale) }

fun LocalDate.shortDayText(locale: Locale): String {
    val day = dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val month = month.getDisplayName(TextStyle.SHORT, locale)
    return "$day $dayOfMonth $month"
}

fun LocalDate.monthAndYear(locale: Locale): String =
    "${month.getDisplayName(TextStyle.FULL, locale)} $year"

/** "20 – 26 July", dropping the repeated month when the week does not straddle one. */
fun weekRangeText(monday: LocalDate, locale: Locale): String {
    val sunday = monday.plusDays(6)
    val start = if (monday.month == sunday.month) {
        "${monday.dayOfMonth}"
    } else {
        "${monday.dayOfMonth} ${monday.month.getDisplayName(TextStyle.SHORT, locale)}"
    }
    val end = "${sunday.dayOfMonth} ${sunday.month.getDisplayName(TextStyle.FULL, locale)}"
    return "$start – $end"
}

fun shortDate(epochMillis: Long, locale: Locale): String =
    DateTimeFormatter.ofPattern("d MMM", locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun longDateTime(epochMillis: Long, locale: Locale): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** Short month label for chart axes: "Jan", "Feb", ... */
fun monthLabel(month: java.time.Month, locale: Locale): String =
    month.getDisplayName(java.time.format.TextStyle.SHORT, locale)
