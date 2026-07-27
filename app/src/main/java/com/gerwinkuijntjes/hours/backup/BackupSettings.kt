package com.gerwinkuijntjes.hours.backup

import android.content.Context
import com.gerwinkuijntjes.hours.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Where the off-device backup goes, and how the last attempt went.
 *
 * The address is split in two: [serverUrl] is the WebDAV root the account lives
 * at, [folderPath] is the folder inside it. Splitting them is what lets the
 * folder be picked from the server rather than typed by hand.
 *
 * Kept in plain preferences rather than the database: it is configuration about
 * this device, not data worth backing up.
 */
class BackupSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("backup", Context.MODE_PRIVATE)

    /** WebDAV root for this account, without a trailing slash. */
    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    /** Folder inside the server, as "/Backups/Hours". Empty means the root itself. */
    var folderPath: String
        get() = prefs.getString(KEY_FOLDER, "").orEmpty()
        set(value) {
            val cleaned = value.trim().trim('/').let { if (it.isEmpty()) "" else "/$it" }
            prefs.edit().putString(KEY_FOLDER, cleaned).apply()
        }

    /** The two halves joined: where the backup files actually go. */
    val folderUrl: String get() = serverUrl.trimEnd('/') + folderPath

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    /**
     * An app password, not the account password: it can be revoked on its own if
     * the phone is lost, without changing anything else.
     */
    var password: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PASSWORD, value.trim()).apply()

    /**
     * When the data last changed. Compared against [lastSuccessMillis] this says
     * exactly what "not backed up yet" means, without guessing from the clock.
     */
    var lastChangeMillis: Long
        get() = prefs.getLong(KEY_LAST_CHANGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHANGE, value).apply()

    /** Changes that no upload has carried off the device yet. */
    val hasUnsavedChanges: Boolean
        get() = lastChangeMillis > lastSuccessMillis

    /**
     * True once those changes have sat here longer than the scheduled upload
     * should have needed. Below that, the worker is simply still doing its job
     * and a warning would only be noise.
     */
    fun backupIsOverdue(now: Long = System.currentTimeMillis()): Boolean =
        isConfigured && hasUnsavedChanges && now - lastChangeMillis > OVERDUE_AFTER_MILLIS

    /** Epoch millis of the last upload the server confirmed, or 0 if there is none. */
    var lastSuccessMillis: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()

    /**
     * Why the last attempt failed, kept until one succeeds.
     *
     * Most uploads happen in the background, where a snackbar nobody sees is no
     * use at all. This is what the backup screen shows instead.
     */
    var lastError: String
        get() = prefs.getString(KEY_LAST_ERROR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    /** What still stands between these settings and a working upload. */
    enum class Problem { NONE, NO_FOLDER, NOT_SECURE, NO_CREDENTIALS }

    /**
     * Release builds refuse anything but https: Basic auth over plain http would
     * put the password on the wire. Debug builds also accept http so the upload
     * can be tried against a server on the development machine.
     */
    val problem: Problem
        get() = when {
            serverUrl.isEmpty() -> Problem.NO_FOLDER
            !serverUrl.startsWith("https://") &&
                !(BuildConfig.DEBUG && serverUrl.startsWith("http://")) -> Problem.NOT_SECURE
            username.isEmpty() || password.isEmpty() -> Problem.NO_CREDENTIALS
            else -> Problem.NONE
        }

    val isConfigured: Boolean get() = problem == Problem.NONE

    val lastSuccessDate: LocalDate?
        get() = lastSuccessMillis
            .takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    /** Days since the last confirmed upload, or null if there has never been one. */
    fun daysSinceLastSuccess(today: LocalDate = LocalDate.now()): Long? =
        lastSuccessDate?.let { ChronoUnit.DAYS.between(it, today) }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_FOLDER = "folder_path"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_CHANGE = "last_change"

        /**
         * The scheduled upload waits fifteen minutes after an edit and retries
         * with backoff; an hour is comfortably past "it is still trying".
         */
        private const val OVERDUE_AFTER_MILLIS = 60L * 60 * 1000
    }
}
