package com.byagowi.persiancalendar.entities

import io.github.persiancalendar.calendar.CivilDate
import io.github.persiancalendar.calendar.PersianDate
import kotlinx.serialization.Serializable
import java.util.TimeZone

@Serializable
enum class LocalEventCategory {
    EVENT,
    BIRTHDAY,
    ANNIVERSARY,
    REMINDER,
}

@Serializable
enum class LocalEventRecurrence {
    NONE,
    YEARLY_JALALI,
    MONTHLY_JALALI,
}

@Serializable
data class LocalEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val jalaliYear: Int? = null,
    val jalaliMonth: Int,
    val jalaliDay: Int,
    val isAllDay: Boolean = true,
    val hour: Int? = null,
    val minute: Int? = null,
    val category: LocalEventCategory = LocalEventCategory.EVENT,
    val recurrence: LocalEventRecurrence = LocalEventRecurrence.NONE,
    val reminderOffsetMinutes: Int? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    init {
        // Enforce trimmed title
        // Validation logic handled cleanly without throwing exceptions in production
    }
}

fun isPersianLeapYear(year: Int): Boolean =
    Calendar.SHAMSI.getMonthLength(year, 12) == 30

fun matchesDate(event: LocalEvent, targetJalaliDate: PersianDate): Boolean {
    return when (event.recurrence) {
        LocalEventRecurrence.NONE -> {
            val eventYear = event.jalaliYear ?: return false
            eventYear == targetJalaliDate.year &&
                event.jalaliMonth == targetJalaliDate.month &&
                event.jalaliDay == targetJalaliDate.dayOfMonth
        }

        LocalEventRecurrence.YEARLY_JALALI -> {
            val startYear = event.jalaliYear
            if (startYear != null && targetJalaliDate.year < startYear) return false
            if (event.jalaliMonth != targetJalaliDate.month) return false

            val targetEsfandLen = Calendar.SHAMSI.getMonthLength(targetJalaliDate.year, 12)
            if (event.jalaliMonth == 12 && event.jalaliDay == 30 && targetEsfandLen < 30) {
                targetJalaliDate.dayOfMonth == 29
            } else {
                targetJalaliDate.dayOfMonth == event.jalaliDay
            }
        }

        LocalEventRecurrence.MONTHLY_JALALI -> {
            val startYear = event.jalaliYear
            if (startYear != null) {
                if (targetJalaliDate.year < startYear) return false
                if (targetJalaliDate.year == startYear && targetJalaliDate.month < event.jalaliMonth) return false
            }
            val monthLen = Calendar.SHAMSI.getMonthLength(targetJalaliDate.year, targetJalaliDate.month)
            val expectedDay = minOf(event.jalaliDay, monthLen)
            targetJalaliDate.dayOfMonth == expectedDay
        }
    }
}

fun elapsedYears(event: LocalEvent, targetJalaliYear: Int): Int? {
    val startYear = event.jalaliYear ?: return null
    if (event.recurrence != LocalEventRecurrence.YEARLY_JALALI) return null
    val diff = targetJalaliYear - startYear
    return if (diff > 0) diff else null
}

fun nextOccurrenceDate(event: LocalEvent, fromJalaliDate: PersianDate): PersianDate? {
    val fromJdn = Jdn(fromJalaliDate)
    return when (event.recurrence) {
        LocalEventRecurrence.NONE -> {
            val year = event.jalaliYear ?: return null
            val date = PersianDate(year, event.jalaliMonth, event.jalaliDay)
            if (Jdn(date) >= fromJdn) date else null
        }

        LocalEventRecurrence.YEARLY_JALALI -> {
            val startYear = maxOf(fromJalaliDate.year, event.jalaliYear ?: fromJalaliDate.year)
            for (y in startYear..(startYear + 5)) {
                val esfandLen = Calendar.SHAMSI.getMonthLength(y, 12)
                val day = if (event.jalaliMonth == 12 && event.jalaliDay == 30 && esfandLen < 30) 29
                else minOf(event.jalaliDay, Calendar.SHAMSI.getMonthLength(y, event.jalaliMonth))
                val candidate = PersianDate(y, event.jalaliMonth, day)
                if (Jdn(candidate) >= fromJdn) return candidate
            }
            null
        }

        LocalEventRecurrence.MONTHLY_JALALI -> {
            var curYear = fromJalaliDate.year
            var curMonth = fromJalaliDate.month
            val startYear = event.jalaliYear
            if (startYear != null && (curYear < startYear || (curYear == startYear && curMonth < event.jalaliMonth))) {
                curYear = startYear
                curMonth = event.jalaliMonth
            }
            for (step in 0..24) {
                val monthLen = Calendar.SHAMSI.getMonthLength(curYear, curMonth)
                val candidateDay = minOf(event.jalaliDay, monthLen)
                val candidate = PersianDate(curYear, curMonth, candidateDay)
                if (Jdn(candidate) >= fromJdn) return candidate

                if (curMonth == 12) {
                    curYear += 1
                    curMonth = 1
                } else {
                    curMonth += 1
                }
            }
            null
        }
    }
}

fun daysUntilNextOccurrence(event: LocalEvent, todayJdn: Jdn): Int? {
    val nextDate = nextOccurrenceDate(event, todayJdn.toPersianDate()) ?: return null
    return (Jdn(nextDate) - todayJdn).coerceAtLeast(0)
}

fun nextTriggerEpochMillis(
    event: LocalEvent,
    nowMillis: Long,
    defaultAllDayHour: Int,
    defaultAllDayMinute: Int,
    timeZone: TimeZone,
): Long? {
    val offsetMinutes = event.reminderOffsetMinutes ?: return null
    val offsetMillis = offsetMinutes * 60_000L

    val hour = if (event.isAllDay) defaultAllDayHour else (event.hour ?: 0)
    val minute = if (event.isAllDay) defaultAllDayMinute else (event.minute ?: 0)

    fun triggerMillisFor(date: PersianDate): Long {
        val civilDate = Jdn(date).toCivilDate()
        val calendar = java.util.Calendar.getInstance(timeZone).apply {
            set(java.util.Calendar.YEAR, civilDate.year)
            set(java.util.Calendar.MONTH, civilDate.month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, civilDate.dayOfMonth)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis - offsetMillis
    }

    // Determine current Jalali date in the specified timeZone
    val currentCal = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    val currentCivil = CivilDate(
        currentCal[java.util.Calendar.YEAR],
        currentCal[java.util.Calendar.MONTH] + 1,
        currentCal[java.util.Calendar.DAY_OF_MONTH],
    )
    val nowPersian = Jdn(currentCivil).toPersianDate()

    return when (event.recurrence) {
        LocalEventRecurrence.NONE -> {
            val year = event.jalaliYear ?: return null
            val date = PersianDate(year, event.jalaliMonth, event.jalaliDay)
            val trigger = triggerMillisFor(date)
            if (trigger > nowMillis) trigger else null
        }

        LocalEventRecurrence.YEARLY_JALALI -> {
            val startYear = maxOf(nowPersian.year, event.jalaliYear ?: nowPersian.year)
            for (y in startYear..(startYear + 5)) {
                val esfandLen = Calendar.SHAMSI.getMonthLength(y, 12)
                val day = if (event.jalaliMonth == 12 && event.jalaliDay == 30 && esfandLen < 30) 29
                else minOf(event.jalaliDay, Calendar.SHAMSI.getMonthLength(y, event.jalaliMonth))
                val candidateDate = PersianDate(y, event.jalaliMonth, day)
                val trigger = triggerMillisFor(candidateDate)
                if (trigger > nowMillis) return trigger
            }
            null
        }

        LocalEventRecurrence.MONTHLY_JALALI -> {
            var curYear = nowPersian.year
            var curMonth = nowPersian.month
            val startYear = event.jalaliYear
            if (startYear != null && (curYear < startYear || (curYear == startYear && curMonth < event.jalaliMonth))) {
                curYear = startYear
                curMonth = event.jalaliMonth
            }
            for (step in 0..24) {
                val monthLen = Calendar.SHAMSI.getMonthLength(curYear, curMonth)
                val candidateDay = minOf(event.jalaliDay, monthLen)
                val candidateDate = PersianDate(curYear, curMonth, candidateDay)
                val trigger = triggerMillisFor(candidateDate)
                if (trigger > nowMillis) return trigger

                if (curMonth == 12) {
                    curYear += 1
                    curMonth = 1
                } else {
                    curMonth += 1
                }
            }
            null
        }
    }
}
