package com.gerwinkuijntjes.hours.data

/**
 * The backup format, used both for the file export and for the upload to the server.
 *
 * Weekdays are ISO numbers (1 = Monday ... 7 = Sunday), dates are ISO yyyy-MM-dd.
 * Everything a restore needs is in here; there is no hidden state elsewhere.
 */
data class BackupPayload(
    val clients: List<BackupClient>,
    val visits: List<BackupVisit>,
    val savedAt: String? = null,
    val appVersion: String? = null
)

data class BackupClient(
    val id: String,
    val name: String,
    val rate: Double,
    val defaultHours: Double,
    val days: List<Int>,
    val extra: Double,
    val color: String
)

data class BackupVisit(
    val id: String,
    val date: String,
    val clientId: String,
    val hours: Double,
    val amount: Double,
    val extra: Double,
    val rate: Double
)

fun BackupClient.toEntity(sortOrder: Int): Client = Client(
    id = id,
    name = name,
    rate = rate,
    defaultHours = defaultHours,
    fixedDays = days.filter { it in 1..7 }.sorted().joinToString(","),
    extra = extra,
    color = parseColor(color),
    sortOrder = sortOrder
)

fun BackupVisit.toEntity(): Visit = Visit(
    id = id,
    date = date,
    clientId = clientId,
    hours = hours,
    amount = amount,
    extra = extra,
    rate = rate
)

fun Client.toBackup(): BackupClient = BackupClient(
    id = id,
    name = name,
    rate = rate,
    defaultHours = defaultHours,
    days = days.map { it.value }.sorted(),
    extra = extra,
    color = formatColor(color)
)

fun Visit.toBackup(): BackupVisit = BackupVisit(
    id = id,
    date = date,
    clientId = clientId,
    hours = hours,
    amount = amount,
    extra = extra,
    rate = rate
)

private fun parseColor(hex: String): Long =
    hex.removePrefix("#").toLongOrNull(16)?.or(0xFF000000L) ?: 0xFF888888L

private fun formatColor(color: Long): String =
    "#%06X".format(color and 0xFFFFFF)
