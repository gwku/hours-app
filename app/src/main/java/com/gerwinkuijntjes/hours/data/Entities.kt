package com.gerwinkuijntjes.hours.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import kotlin.math.roundToLong

/**
 * A household she cleans for.
 *
 * [fixedDays] is a comma separated list of ISO day numbers (1 = Monday ... 7 = Sunday).
 * On those days the client is suggested automatically on the day screen.
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey val id: String,
    val name: String,
    val rate: Double,
    val defaultHours: Double,
    val fixedDays: String,
    val extra: Double,
    val color: Long,
    val sortOrder: Int
) {
    val days: Set<DayOfWeek>
        get() = fixedDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .map { DayOfWeek.of(it) }
            .toSet()

    fun amountFor(hours: Double): Double = roundToCents(hours * rate + extra)

    companion object {
        fun daysToText(days: Set<DayOfWeek>): String =
            days.map { it.value }.sorted().joinToString(",")
    }
}

/**
 * One cleaning session: a single client on a single day.
 *
 * [rate] and [extra] are snapshots of what applied when the visit was entered, so
 * raising a client's rate later never rewrites what is already recorded.
 */
@Entity(
    tableName = "visits",
    indices = [Index("date"), Index("clientId")]
)
data class Visit(
    @PrimaryKey val id: String,
    val date: String,
    val clientId: String,
    val hours: Double,
    val amount: Double,
    val extra: Double,
    val rate: Double
) {
    /** The amount that follows from the hours, using the rate that applied back then. */
    fun recalculated(newHours: Double): Double = roundToCents(newHours * rate + extra)
}

/** Round to whole cents, so 3 × 13.3333 lands on € 40.00 rather than € 39.9999. */
fun roundToCents(amount: Double): Double = (amount * 100).roundToLong() / 100.0
