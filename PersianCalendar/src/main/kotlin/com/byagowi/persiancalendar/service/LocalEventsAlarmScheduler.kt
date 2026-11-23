package com.byagowi.persiancalendar.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.byagowi.persiancalendar.BROADCAST_LOCAL_EVENT_REMINDER
import com.byagowi.persiancalendar.LOCAL_EVENTS_CHANNEL_ID
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.data.LocalEventsRepository
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.LocalEvent
import com.byagowi.persiancalendar.entities.LocalEventCategory
import com.byagowi.persiancalendar.entities.elapsedYears
import com.byagowi.persiancalendar.entities.nextOccurrenceDate
import com.byagowi.persiancalendar.entities.nextTriggerEpochMillis
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.numeral
import com.byagowi.persiancalendar.ui.MainActivity
import com.byagowi.persiancalendar.utils.jdnActionKey
import com.byagowi.persiancalendar.utils.logException
import com.byagowi.persiancalendar.utils.scheduleWithExactAlarmFallback
import java.util.TimeZone

private const val LOCAL_EVENTS_ALARM_REQUEST_CODE = 4500
private const val LOCAL_EVENTS_NOTIFICATION_GROUP = "local_events_reminders_group"

fun rescheduleLocalEventsAlarm(context: Context) {
    val am = context.getSystemService<AlarmManager>() ?: return
    val alarmIntent = Intent(context, BroadcastReceivers::class.java).apply {
        action = BROADCAST_LOCAL_EVENT_REMINDER
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        LOCAL_EVENTS_ALARM_REQUEST_CODE,
        alarmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    if (!LocalEventsRepository.isLocalEventsEnabled(context)) {
        am.cancel(pendingIntent)
        return
    }

    val events = LocalEventsRepository.getAllEvents(context)
    val now = System.currentTimeMillis()
    val (defaultHour, defaultMinute) = LocalEventsRepository.getDefaultAllDayReminderTime(context)
    val timeZone = TimeZone.getDefault() ?: TimeZone.getTimeZone("UTC")

    var earliestTrigger: Long? = null
    for (event in events) {
        val trigger = nextTriggerEpochMillis(event, now, defaultHour, defaultMinute, timeZone) ?: continue
        if (earliestTrigger == null || trigger < earliestTrigger) {
            earliestTrigger = trigger
        }
    }

    if (earliestTrigger == null) {
        am.cancel(pendingIntent)
        return
    }

    scheduleWithExactAlarmFallback(
        canScheduleExact = { AlarmManagerCompat.canScheduleExactAlarms(am) },
        scheduleExact = {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                am,
                AlarmManager.RTC_WAKEUP,
                earliestTrigger,
                pendingIntent,
            )
        },
        scheduleInexact = {
            AlarmManagerCompat.setAndAllowWhileIdle(
                am,
                AlarmManager.RTC_WAKEUP,
                earliestTrigger,
                pendingIntent,
            )
        },
        onPermissionFailure = logException,
    )
}

fun handleLocalEventAlarmTrigger(context: Context) {
    val now = System.currentTimeMillis()
    val events = LocalEventsRepository.getAllEvents(context)
    val (defaultHour, defaultMinute) = LocalEventsRepository.getDefaultAllDayReminderTime(context)
    val timeZone = TimeZone.getDefault() ?: TimeZone.getTimeZone("UTC")

    val windowStart = now - 15 * 60 * 1000L
    val windowEnd = now + 60 * 1000L

    val dueEvents = mutableListOf<LocalEvent>()
    for (event in events) {
        val trigger = nextTriggerEpochMillis(event, windowStart - 1, defaultHour, defaultMinute, timeZone) ?: continue
        if (trigger in windowStart..windowEnd) {
            dueEvents.add(event)
        }
    }

    if (dueEvents.isNotEmpty()) {
        showNotificationsForDueEvents(context, dueEvents)
    }

    // Schedule next upcoming alarm
    rescheduleLocalEventsAlarm(context)
}

private fun showNotificationsForDueEvents(context: Context, dueEvents: List<LocalEvent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return
    }

    val notificationManager = NotificationManagerCompat.from(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelName = runCatching {
            context.getString(com.byagowi.persiancalendar.shared.R.string.local_events_reminders)
        }.getOrDefault("Local Event Reminders")
        val channel = NotificationChannel(
            LOCAL_EVENTS_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            setShowBadge(true)
        }
        val systemNm = context.getSystemService<NotificationManager>()
        systemNm?.createNotificationChannel(channel)
    }

    val todayJdn = Jdn.today()
    val todayPersian = todayJdn.toPersianDate()

    for (event in dueEvents) {
        val nextOccurrence = nextOccurrenceDate(event, todayPersian) ?: todayPersian
        val occurrenceJdn = Jdn(nextOccurrence)

        val clickIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(jdnActionKey, occurrenceJdn.value)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val age = elapsedYears(event, nextOccurrence.year)
        val categoryPrefix = when (event.category) {
            LocalEventCategory.BIRTHDAY -> if (age != null) {
                if (language.isPersianOrDari) "تولد ${numeral.format(age)} سالگی"
                else "Birthday ($age)"
            } else {
                if (language.isPersianOrDari) "تولد" else "Birthday"
            }
            LocalEventCategory.ANNIVERSARY -> if (age != null) {
                if (language.isPersianOrDari) "سالگرد ${numeral.format(age)} سالگی"
                else "Anniversary ($age)"
            } else {
                if (language.isPersianOrDari) "سالگرد" else "Anniversary"
            }
            LocalEventCategory.REMINDER -> if (language.isPersianOrDari) "یادآور" else "Reminder"
            LocalEventCategory.EVENT -> if (language.isPersianOrDari) "رویداد" else "Event"
        }

        val notificationTitle = "$categoryPrefix: ${event.title}"
        val notificationText = event.description.ifBlank {
            if (language.isPersianOrDari) "یادآور رویداد محلی" else "Local event reminder"
        }

        val notification = NotificationCompat.Builder(context, LOCAL_EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.day19)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup(LOCAL_EVENTS_NOTIFICATION_GROUP)
            .build()

        val notificationId = (event.id.hashCode() and 0x7FFFFFFF)
        runCatching {
            notificationManager.notify(notificationId, notification)
        }.onFailure(logException)
    }

    // Group summary if multiple events
    if (dueEvents.size > 1) {
        val summaryText = if (language.isPersianOrDari) {
            "${numeral.format(dueEvents.size)} رویداد امروز"
        } else {
            "${dueEvents.size} events today"
        }
        val summaryNotification = NotificationCompat.Builder(context, LOCAL_EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.day19)
            .setContentTitle(if (language.isPersianOrDari) "یادآور رویدادها" else "Event Reminders")
            .setContentText(summaryText)
            .setGroup(LOCAL_EVENTS_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        runCatching {
            notificationManager.notify(LOCAL_EVENTS_ALARM_REQUEST_CODE, summaryNotification)
        }.onFailure(logException)
    }
}
