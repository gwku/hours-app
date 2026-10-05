package com.gerwinkuijntjes.hours.reminder

import android.content.Context
import java.time.LocalTime

/**
 * Whether to nudge on a workday when the hours have not been filled in yet, and
 * at what time of day.
 *
 * On by default: forgetting a day is exactly the mistake the app exists to
 * prevent, and somebody who forgets is not going to go looking for a setting.
 */
class ReminderSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Kept as minutes past midnight, which is all a time of day needs. */
    var time: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt(KEY_MINUTES, DEFAULT_MINUTES) * 60L)
        set(value) = prefs.edit().putInt(KEY_MINUTES, value.hour * 60 + value.minute).apply()

    /**
     * Android 13 and later ask before an app may post notifications. That
     * question is put once, at first launch; after that, the settings switch is
     * where it is asked again.
     */
    var askedForPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_ASKED = "asked_permission"

        /** Six in the evening: after the work, before the evening has gone. */
        private const val DEFAULT_MINUTES = 18 * 60
    }
}
