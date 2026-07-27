package com.gerwinkuijntjes.hours.backup

import android.content.Context
import android.util.Log
import com.gerwinkuijntjes.hours.BuildConfig
import com.gerwinkuijntjes.hours.data.HoursRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Uploads the whole dataset to a WebDAV folder as one JSON document.
 *
 * The payload is small, a year of work being well under 100 kB, so there is no
 * incremental sync: every upload is a complete, self-contained snapshot. That
 * keeps the restore path obvious and needs nothing on the server beyond a folder.
 *
 * Every upload is its own file, stamped with the date and time. Nothing is ever
 * overwritten, so any earlier state can still be recovered, including one from
 * a few hours ago, not just from yesterday.
 */
class BackupUploader(private val context: Context) {

    private val settings = BackupSettings(context)
    private val repository = HoursRepository(context)

    sealed interface Result {
        data object Success : Result
        data object NotConfigured : Result
        /** Nothing to back up; uploading would risk replacing a good copy with an empty one. */
        data object NothingToBackUp : Result
        data class Failed(val error: DavError, val retryable: Boolean) : Result
    }

    suspend fun upload(): Result = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext Result.NotConfigured

        // Without a server to vet the payload, this guard lives here: a phone that
        // has been wiped or reset must not overwrite a good backup with nothing.
        if (repository.clientCount() == 0) return@withContext Result.NothingToBackUp

        val client = WebDavClient(settings.serverUrl, settings.username, settings.password)
        val json = repository.exportJson(BuildConfig.VERSION_NAME).toByteArray(Charsets.UTF_8)
        val target = "${settings.folderPath}/hours-${LocalDateTime.now().format(STAMP)}.json"

        var outcome = client.upload(target, json)

        // WebDAV refuses to write into a folder that is not there. Create it once
        // and try again, so a folder typed rather than picked still works.
        if (outcome.exceptionOrNull()?.davError() == DavError.NotFound &&
            settings.folderPath.isNotEmpty()
        ) {
            if (client.createFolder(settings.folderPath).isSuccess) {
                outcome = client.upload(target, json)
            }
        }

        val result = outcome.fold(
            onSuccess = {
                settings.lastSuccessMillis = System.currentTimeMillis()
                Result.Success
            },
            onFailure = { throwable ->
                val error = throwable.davError()
                // Only a server or connection hiccup is worth coming back for; a
                // wrong password will still be wrong in half an hour.
                val retryable = error is DavError.Unreachable || error is DavError.Server
                Result.Failed(error, retryable)
            }
        )

        // Remember the reason: nearly every upload runs in the background, where
        // there is nobody to read a message when it goes wrong.
        settings.lastError = if (result is Result.Failed) result.error.toString() else ""

        Log.i(TAG, "backup upload: $result")
        result
    }

    private companion object {
        const val TAG = "BackupUploader"

        /** Sorts chronologically as plain text, and has no characters a file
         *  system objects to. */
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
