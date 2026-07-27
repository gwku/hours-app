# Hours

An Android app for tracking hours worked and money earned across a handful of
private clients.

I built it for my mother, who cleans for private households. She used to scribble
hours in her diary and retype them into a spreadsheet later, an evening's work
every month, and by mid-year the spreadsheet had drifted: hand-typed amounts
where the formulas used to be, two conflicting total rows, and a `#REF!` in one
of them.

The insight the app is built on is that the work is almost entirely predictable.
The same clients, on the same weekdays, for the same number of hours, at the same
rate. So the app does not ask her to fill anything in. It shows what it expects
and asks her to confirm it.

## Features

- **The day already filled in.** Every client with that weekday as a regular day
  is waiting on the screen with their usual hours and the amount worked out. One
  tap records it.
- **Adjust in quarters, or type anything.** The − and + buttons snap to the next
  whole quarter; the field itself takes any number, so 3.4 hours is no harder to
  enter than 3.75.
- **Week strip** showing which days already have something on them, so catching
  up on a few days is one tap per day rather than a date picker each time.
- **Frozen rates.** Each visit stores the rate and allowance that applied when it
  was recorded. Raising a client's rate changes what you record from now on and
  never rewrites the past.
- **Overview** per month or per year, totalled per client, with every individual
  visit listed. Tapping one jumps to its day, where it can be corrected.
- **Off-device backup.** A full snapshot is uploaded daily, and again shortly
  after any change. If anything has been sitting unsaved for more than an hour,
  the day screen says so and offers a button. A backup that fails silently is
  worse than none.
- **Backup to a file** as well, and restore from one.
- **English and Dutch**, following the system language.

## Tech stack

- **Kotlin** with **Jetpack Compose** and **Material 3**
- **MVVM** architecture with a repository layer
- **Room** (SQLite) for clients and visits
- **WorkManager** for the scheduled backup upload
- **Gson** for the backup format
- Min SDK 26 (Android 8.0), target/compile SDK 35

## The backup

Android's own Auto Backup is enabled, but it only restores when a new device is
set up, so it cannot recover a mistake made today. The app ships its own:

`BackupWorker` uploads the entire dataset as one JSON document to a WebDAV
folder. There is no incremental sync; a decade of entries is still well under a
megabyte, so every upload is a complete, self-contained snapshot. That keeps the
restore path obvious and needs nothing on the server beyond a folder.

Two triggers: a daily periodic run so an untouched phone still checks in, and a
debounced run fifteen minutes after an edit, so a burst of changes results in one
upload rather than five.

Every upload is its own file, `hours-2026-07-26-171405.json`, so nothing is ever
overwritten and any earlier state can be recovered. Nothing prunes them, so a
year of use leaves a few hundred small files in the folder.

The server address and the folder are configured separately, and the folder is
browsed over WebDAV rather than typed, with an option to create one. Failures are
reported as sentences ("the user name or app password is wrong") rather than
status codes, and the reason for the last failure survives on the backup screen,
because almost every upload runs in the background where nobody is watching.

Two things worth knowing about `HttpURLConnection` and WebDAV, both of which cost
an evening to find:

- It refuses any verb outside HTTP/1.1, so `PROPFIND` and `MKCOL` have to be
  written straight into the method field by reflection.
- Over https it hands back a wrapper that forwards to a second connection object.
  Setting the method on the wrapper alone leaves the delegate on `POST`, and the
  server answers 405. `WebDavClient` walks the delegate chain.

## Building

```bash
./gradlew :app:assembleRelease
```

Signing follows `keystore.properties` in the project root, or `KEYSTORE_FILE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` from the environment, the
same arrangement as my other apps. `keystore.properties.example` documents the
four values and the `keytool` command that creates the key.

Without either, the build falls back to the debug key and says so loudly. Take
that warning seriously: Android refuses an update signed with a different key
than the installed version, and the only way out is uninstalling, which takes the
database with it.

Debug builds additionally accept an `http://` backup address and allow cleartext
traffic, so the upload can be tested against a server on the development machine
(`http://10.0.2.2:8899/...` from the emulator). Neither applies to release.

## First run

`app/src/main/assets/seed.json` is loaded when the database is empty. What ships
here is example data, five invented clients with their own rates and regular
days, so a fresh clone has something to look at. Replace that file to start from
your own, or empty its two arrays to start from nothing.
