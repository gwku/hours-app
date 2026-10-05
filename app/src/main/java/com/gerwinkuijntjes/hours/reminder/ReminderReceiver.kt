package com.gerwinkuijntjes.hours.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gerwinkuijntjes.hours.data.HoursRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Two jobs: going off at the reminder time, and putting the alarm back after
 * something has cleared or shifted it. A reboot clears every alarm, and a change
 * of clock or time zone would otherwise leave it at the old wall-clock time.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) {
            Reminders.schedule(context)
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ReminderSettings(context).enabled) {
                    val repository = HoursRepository(context)
                    val today = LocalDate.now()
                    ReminderDue.on(today, repository.clientsNow(), repository.visitsOnNow(today))
                        ?.let { Reminders.notify(context, it) }
                }
            } finally {
                Reminders.schedule(context)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.gerwinkuijntjes.hours.action.REMIND"
    }
}
