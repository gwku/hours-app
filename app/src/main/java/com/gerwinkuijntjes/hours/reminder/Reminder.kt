package com.gerwinkuijntjes.hours.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gerwinkuijntjes.hours.MainActivity
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.data.Client
import com.gerwinkuijntjes.hours.data.Visit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * What, if anything, is still waiting to be filled in on [date].
 *
 * A workday is a day some client has as a regular day: that is how this app
 * already knows the work is coming, and it is what the day screen fills in. Only
 * when no client has a regular day at all does it fall back to Monday to Friday,
 * so somebody with irregular work is still reminded on a weekday with nothing on it.
 */
sealed interface ReminderDue {
    /** Regular clients for the day that have no visit yet. */
    data class Clients(val names: List<String>) : ReminderDue

    /** No regular days anywhere, and nothing at all recorded on this weekday. */
    data object NothingRecorded : ReminderDue

    companion object {
        fun on(date: LocalDate, clients: List<Client>, visitsThatDay: List<Visit>): ReminderDue? {
            // A fresh install has nothing to forget.
            if (clients.isEmpty()) return null

            val recorded = visitsThatDay.map { it.clientId }.toSet()
            if (clients.any { it.days.isNotEmpty() }) {
                val open = clients.filter { date.dayOfWeek in it.days && it.id !in recorded }
                return if (open.isEmpty()) null else Clients(open.map { it.name })
            }

            val weekday = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            return if (weekday && visitsThatDay.isEmpty()) NothingRecorded else null
        }
    }
}

/**
 * Arms one alarm at a time, for the next occurrence of the reminder time. The
 * alarm decides when it goes off whether there is anything to say, and arms the
 * next one. Deciding then rather than now means a day filled in during the
 * afternoon stays quiet.
 *
 * An alarm rather than WorkManager: this has to land at a time of day, and a
 * deferred job can drift by hours.
 */
object Reminders {

    private const val CHANNEL_ID = "reminder"
    private const val NOTIFICATION_ID = 1

    /** Call on app start and after any change to the settings. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val settings = ReminderSettings(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (!settings.enabled) {
            alarms.cancel(pending)
            cancelNotification(context)
            return
        }
        // Inexact and allowed while idle: no special permission, and a few
        // minutes late is fine for a nudge.
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(settings.time, ZonedDateTime.now()).toInstant().toEpochMilli(),
            pending
        )
    }

    /** Today at [time] if that is still ahead, otherwise tomorrow. */
    fun nextTrigger(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val today = now.with(time)
        return if (today.isAfter(now)) today else now.plusDays(1).with(time)
    }

    /**
     * Whether a reminder would actually appear. Before Android 13 there is no
     * permission, but notifications can still be switched off for the app.
     */
    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    // Checked by canNotify, which lint cannot see through.
    @SuppressLint("MissingPermission")
    fun notify(context: Context, due: ReminderDue) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val text = when (due) {
            is ReminderDue.Clients -> context.getString(
                R.string.reminder_text_clients,
                due.names.joinToString(", ")
            )
            ReminderDue.NothingRecorded -> context.getString(R.string.reminder_text_nothing)
        }

        // A fresh task, so the day screen opens on today rather than wherever the
        // app was last left.
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post; nothing to do.
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
