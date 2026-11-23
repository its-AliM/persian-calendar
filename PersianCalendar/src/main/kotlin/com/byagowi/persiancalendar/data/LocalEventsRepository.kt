package com.byagowi.persiancalendar.data

import android.content.Context
import androidx.core.content.edit
import com.byagowi.persiancalendar.DEFAULT_ALL_DAY_REMINDER_TIME
import com.byagowi.persiancalendar.PREF_DEFAULT_ALL_DAY_REMINDER_TIME
import com.byagowi.persiancalendar.PREF_SHOW_LOCAL_EVENTS
import com.byagowi.persiancalendar.entities.CalendarEvent
import com.byagowi.persiancalendar.entities.Clock
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.LocalEvent
import com.byagowi.persiancalendar.entities.elapsedYears
import com.byagowi.persiancalendar.entities.matchesDate
import com.byagowi.persiancalendar.service.rescheduleLocalEventsAlarm
import com.byagowi.persiancalendar.utils.logException
import com.byagowi.persiancalendar.utils.preferences
import com.byagowi.persiancalendar.utils.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class LocalEventsBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val events: List<LocalEvent> = emptyList(),
)

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
)

object LocalEventsRepository {
    private const val FILE_NAME = "local_events.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _events = MutableStateFlow<List<LocalEvent>>(emptyList())
    val events: StateFlow<List<LocalEvent>> = _events.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            _events.value = loadFromFile(context)
            isInitialized = true
        }
    }

    private fun getStorageFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    private fun loadFromFile(context: Context): List<LocalEvent> {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()

        return runCatching {
            val content = file.readText()
            if (content.isBlank()) return@runCatching emptyList()
            json.decodeFromString<List<LocalEvent>>(content)
        }.onFailure(logException).getOrElse { emptyList() }
    }

    private fun saveToFile(context: Context, items: List<LocalEvent>): Boolean {
        val destination = getStorageFile(context)
        return runCatching {
            val tempFile = File.createTempFile("localevents-", ".tmp", context.filesDir)
            tempFile.outputStream().use { output ->
                output.write(json.encodeToString(items).toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.onFailure(logException).getOrElse { false }
    }

    fun getAllEvents(context: Context? = null): List<LocalEvent> {
        if (!isInitialized && context != null) init(context)
        return _events.value
    }

    fun getEventsForJdn(jdn: Jdn, isEnabled: Boolean): List<CalendarEvent.LocalCalendarEvent> {
        if (!isEnabled) return emptyList()
        val currentEvents = _events.value
        if (currentEvents.isEmpty()) return emptyList()

        val persianDate = jdn.toPersianDate()
        return currentEvents.filter { matchesDate(it, persianDate) }.map { event ->
            val formattedTime = if (event.isAllDay) null else {
                val hour = event.hour ?: 0
                val minute = event.minute ?: 0
                Clock(hour + minute / 60.0).toFormattedString(printAmPm = true)
            }
            val elapsed = elapsedYears(event, persianDate.year)
            CalendarEvent.LocalCalendarEvent(
                localEvent = event,
                date = persianDate,
                formattedTime = formattedTime,
                elapsedYears = elapsed,
            )
        }
    }

    fun addEvent(context: Context, event: LocalEvent): Boolean {
        init(context)
        val current = _events.value.toMutableList()
        current.removeAll { it.id == event.id }
        current.add(event)
        val saved = saveToFile(context, current)
        if (saved) {
            _events.value = current
            onEventsChanged(context)
        }
        return saved
    }

    fun updateEvent(context: Context, event: LocalEvent): Boolean {
        init(context)
        val current = _events.value.toMutableList()
        val index = current.indexOfFirst { it.id == event.id }
        if (index != -1) {
            current[index] = event
        } else {
            current.add(event)
        }
        val saved = saveToFile(context, current)
        if (saved) {
            _events.value = current
            onEventsChanged(context)
        }
        return saved
    }

    fun deleteEvent(context: Context, id: String): Boolean {
        init(context)
        val current = _events.value.toMutableList()
        val removed = current.removeAll { it.id == id }
        if (!removed) return true
        val saved = saveToFile(context, current)
        if (saved) {
            _events.value = current
            onEventsChanged(context)
        }
        return saved
    }

    fun exportBackup(outputStream: OutputStream): Boolean {
        return runCatching {
            val payload = LocalEventsBackupPayload(
                schemaVersion = 1,
                exportedAt = System.currentTimeMillis(),
                events = _events.value,
            )
            val jsonString = prettyJson.encodeToString(payload)
            outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
            true
        }.onFailure(logException).getOrElse { false }
    }

    fun importBackup(context: Context, inputStream: InputStream): ImportResult {
        init(context)
        return runCatching {
            val content = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (content.isBlank()) return@runCatching ImportResult(0, 0)

            val parsedEvents = runCatching {
                json.decodeFromString<LocalEventsBackupPayload>(content).events
            }.getOrElse {
                json.decodeFromString<List<LocalEvent>>(content)
            }

            if (parsedEvents.isEmpty()) return@runCatching ImportResult(0, 0)

            val current = _events.value.toMutableList()
            var imported = 0
            var skipped = 0

            for (incoming in parsedEvents) {
                val isDuplicateId = current.any { it.id == incoming.id }
                val isDuplicateContent = current.any {
                    it.title.trim() == incoming.title.trim() &&
                        it.jalaliYear == incoming.jalaliYear &&
                        it.jalaliMonth == incoming.jalaliMonth &&
                        it.jalaliDay == incoming.jalaliDay &&
                        it.recurrence == incoming.recurrence
                }

                if (isDuplicateId || isDuplicateContent) {
                    skipped++
                } else {
                    current.add(incoming)
                    imported++
                }
            }

            if (imported > 0) {
                saveToFile(context, current)
                _events.value = current
                onEventsChanged(context)
            }

            ImportResult(imported, skipped)
        }.onFailure(logException).getOrElse { ImportResult(0, 0) }
    }

    fun onEventsChanged(context: Context) {
        rescheduleLocalEventsAlarm(context)
        update(context, true)
    }

    fun isLocalEventsEnabled(context: Context): Boolean =
        context.preferences.getBoolean(PREF_SHOW_LOCAL_EVENTS, true)

    fun getDefaultAllDayReminderTime(context: Context): Pair<Int, Int> {
        val raw = context.preferences.getString(
            PREF_DEFAULT_ALL_DAY_REMINDER_TIME,
            DEFAULT_ALL_DAY_REMINDER_TIME,
        ) ?: DEFAULT_ALL_DAY_REMINDER_TIME
        val parts = raw.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour to minute
    }

    fun setDefaultAllDayReminderTime(context: Context, hour: Int, minute: Int) {
        val formatted = "%02d:%02d".format(java.util.Locale.ENGLISH, hour, minute)
        context.preferences.edit {
            putString(PREF_DEFAULT_ALL_DAY_REMINDER_TIME, formatted)
        }
        rescheduleLocalEventsAlarm(context)
    }
}
