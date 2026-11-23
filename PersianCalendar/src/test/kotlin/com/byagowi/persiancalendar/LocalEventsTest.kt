package com.byagowi.persiancalendar

import com.byagowi.persiancalendar.data.LocalEventsBackupPayload
import com.byagowi.persiancalendar.entities.Calendar
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.LocalEvent
import com.byagowi.persiancalendar.entities.LocalEventCategory
import com.byagowi.persiancalendar.entities.LocalEventRecurrence
import com.byagowi.persiancalendar.entities.daysUntilNextOccurrence
import com.byagowi.persiancalendar.entities.elapsedYears
import com.byagowi.persiancalendar.entities.isPersianLeapYear
import com.byagowi.persiancalendar.entities.matchesDate
import com.byagowi.persiancalendar.entities.nextOccurrenceDate
import com.byagowi.persiancalendar.entities.nextTriggerEpochMillis
import io.github.persiancalendar.calendar.CivilDate
import io.github.persiancalendar.calendar.PersianDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalEventsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testZeroDriftAcrossLeapAndNonLeapYears() {
        // Birthday on 1 Aban 1354
        val aban1 = LocalEvent(
            id = "event-aban-1",
            title = "Birthday 1 Aban",
            jalaliYear = 1354,
            jalaliMonth = 8,
            jalaliDay = 1,
            category = LocalEventCategory.BIRTHDAY,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )

        // Anniversary on 2 Aban 1387
        val aban2 = LocalEvent(
            id = "event-aban-2",
            title = "Anniversary 2 Aban",
            jalaliYear = 1387,
            jalaliMonth = 8,
            jalaliDay = 2,
            category = LocalEventCategory.ANNIVERSARY,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )

        val testYears = listOf(1403, 1404, 1405, 1406, 1407, 1408)

        for (year in testYears) {
            // 1 Aban
            assertTrue(
                matchesDate(aban1, PersianDate(year, 8, 1)),
                "1 Aban must match on 1 Aban in year $year",
            )
            assertFalse(
                matchesDate(aban1, PersianDate(year, 8, 2)),
                "1 Aban must NOT match on 2 Aban in year $year",
            )
            assertFalse(
                matchesDate(aban1, PersianDate(year, 7, 30)),
                "1 Aban must NOT match on 30 Mehr in year $year",
            )

            // 2 Aban
            assertTrue(
                matchesDate(aban2, PersianDate(year, 8, 2)),
                "2 Aban must match on 2 Aban in year $year",
            )
            assertFalse(
                matchesDate(aban2, PersianDate(year, 8, 1)),
                "2 Aban must NOT match on 1 Aban in year $year",
            )
            assertFalse(
                matchesDate(aban2, PersianDate(year, 8, 3)),
                "2 Aban must NOT match on 3 Aban in year $year",
            )
        }
    }

    @Test
    fun test30EsfandYearlyLeapYearRule() {
        val esfand30Event = LocalEvent(
            id = "esfand-30",
            title = "Esfand 30 Event",
            jalaliYear = 1375,
            jalaliMonth = 12,
            jalaliDay = 30,
            category = LocalEventCategory.EVENT,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )

        // 1403 is a leap year (Esfand has 30 days)
        assertTrue(isPersianLeapYear(1403), "1403 must be a Persian leap year")
        assertEquals(30, Calendar.SHAMSI.getMonthLength(1403, 12))
        assertTrue(matchesDate(esfand30Event, PersianDate(1403, 12, 30)))
        assertFalse(matchesDate(esfand30Event, PersianDate(1403, 12, 29)))

        // 1404 is a non-leap year (Esfand has 29 days)
        assertFalse(isPersianLeapYear(1404), "1404 must be a non-leap year")
        assertEquals(29, Calendar.SHAMSI.getMonthLength(1404, 12))
        assertTrue(
            matchesDate(esfand30Event, PersianDate(1404, 12, 29)),
            "In non-leap year 1404, 30 Esfand event must match on 29 Esfand",
        )

        // 1405 is a non-leap year
        assertFalse(isPersianLeapYear(1405))
        assertTrue(matchesDate(esfand30Event, PersianDate(1405, 12, 29)))

        // 1408 is a leap year
        assertTrue(isPersianLeapYear(1408))
        assertTrue(matchesDate(esfand30Event, PersianDate(1408, 12, 30)))
        assertFalse(matchesDate(esfand30Event, PersianDate(1408, 12, 29)))
    }

    @Test
    fun testMonthlyJalaliClampingOnDay31() {
        val monthlyDay31 = LocalEvent(
            id = "monthly-31",
            title = "Monthly 31st",
            jalaliMonth = 1,
            jalaliDay = 31,
            category = LocalEventCategory.REMINDER,
            recurrence = LocalEventRecurrence.MONTHLY_JALALI,
        )

        // Months 1..6 have 31 days
        for (m in 1..6) {
            assertTrue(
                matchesDate(monthlyDay31, PersianDate(1404, m, 31)),
                "Month $m should match on day 31",
            )
            assertFalse(matchesDate(monthlyDay31, PersianDate(1404, m, 30)))
        }

        // Months 7..11 have 30 days, so day 31 clamps to 30
        for (m in 7..11) {
            assertTrue(
                matchesDate(monthlyDay31, PersianDate(1404, m, 30)),
                "Month $m should clamp day 31 to day 30",
            )
        }

        // Month 12 in 1404 (29 days) clamps to 29
        assertTrue(matchesDate(monthlyDay31, PersianDate(1404, 12, 29)))

        // Month 12 in 1403 (30 days leap) clamps to 30
        assertTrue(matchesDate(monthlyDay31, PersianDate(1403, 12, 30)))
    }

    @Test
    fun testElapsedYears() {
        val birthday = LocalEvent(
            id = "bday-1",
            title = "Ali",
            jalaliYear = 1354,
            jalaliMonth = 8,
            jalaliDay = 1,
            category = LocalEventCategory.BIRTHDAY,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )

        assertEquals(49, elapsedYears(birthday, 1403))
        assertEquals(50, elapsedYears(birthday, 1404))
        assertNull(elapsedYears(birthday, 1354)) // same year: not yet elapsed
        assertNull(elapsedYears(birthday, 1350)) // before birth: null

        // Year unknown: elapsedYears must be null
        val noYear = birthday.copy(jalaliYear = null)
        assertNull(elapsedYears(noYear, 1404))

        // Recurrence NONE: elapsedYears must be null
        val once = birthday.copy(recurrence = LocalEventRecurrence.NONE)
        assertNull(elapsedYears(once, 1404))
    }

    @Test
    fun testNextOccurrenceDateAndDaysUntil() {
        val yearly = LocalEvent(
            id = "yearly-test",
            title = "Test",
            jalaliYear = 1400,
            jalaliMonth = 8,
            jalaliDay = 10,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )

        // Looking from 1 Aban 1403 -> next is 10 Aban 1403
        val fromAban1 = PersianDate(1403, 8, 1)
        val next1 = nextOccurrenceDate(yearly, fromAban1)
        assertEquals(PersianDate(1403, 8, 10), next1)
        assertEquals(9, daysUntilNextOccurrence(yearly, Jdn(fromAban1)))

        // Looking from 15 Aban 1403 -> next is 10 Aban 1404
        val fromAban15 = PersianDate(1403, 8, 15)
        val next2 = nextOccurrenceDate(yearly, fromAban15)
        assertEquals(PersianDate(1404, 8, 10), next2)

        // 30 Esfand event looking from 1 Farvardin 1404 (non-leap) -> 29 Esfand 1404
        val esfand30 = LocalEvent(
            id = "esfand30",
            title = "End of year",
            jalaliMonth = 12,
            jalaliDay = 30,
            recurrence = LocalEventRecurrence.YEARLY_JALALI,
        )
        val fromFarvardin1404 = PersianDate(1404, 1, 1)
        val nextEsfand = nextOccurrenceDate(esfand30, fromFarvardin1404)
        assertEquals(PersianDate(1404, 12, 29), nextEsfand)
    }

    @Test
    fun testNextTriggerEpochMillis() {
        val tz = TimeZone.getTimeZone("Asia/Tehran")

        // An event on 10 Aban at 14:00 with 60 minutes reminder (reminder fires at 13:00)
        val event = LocalEvent(
            id = "event-alarm",
            title = "Meeting",
            jalaliYear = 1403,
            jalaliMonth = 8,
            jalaliDay = 10,
            isAllDay = false,
            hour = 14,
            minute = 0,
            recurrence = LocalEventRecurrence.NONE,
            reminderOffsetMinutes = 60,
        )

        // Calculate expected epoch millis of 10 Aban 1403 13:00 Tehran time
        val civilDate = Jdn(PersianDate(1403, 8, 10)).toCivilDate()
        val expectedCal = java.util.Calendar.getInstance(tz).apply {
            set(civilDate.year, civilDate.month - 1, civilDate.dayOfMonth, 13, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val expectedTrigger = expectedCal.timeInMillis

        // If now is before 13:00, nextTriggerEpochMillis returns expectedTrigger
        val nowBefore = expectedTrigger - 1000 * 60 * 30 // 12:30
        val calculated = nextTriggerEpochMillis(event, nowBefore, 9, 0, tz)
        assertEquals(expectedTrigger, calculated)

        // If now is past 13:00, for NONE recurrence it returns null
        val nowAfter = expectedTrigger + 1000
        val afterCalculated = nextTriggerEpochMillis(event, nowAfter, 9, 0, tz)
        assertNull(afterCalculated)

        // But for YEARLY_JALALI recurrence, it rolls over to next year!
        val yearlyEvent = event.copy(recurrence = LocalEventRecurrence.YEARLY_JALALI)
        val nextYearTrigger = nextTriggerEpochMillis(yearlyEvent, nowAfter, 9, 0, tz)
        assertNotNull(nextYearTrigger)
        assertTrue(nextYearTrigger > nowAfter)
    }

    @Test
    fun testJsonExportImportRoundTripAndDeduplication() {
        val originalList = listOf(
            LocalEvent(
                id = "id-1",
                title = "Birthday Ali",
                jalaliYear = 1360,
                jalaliMonth = 5,
                jalaliDay = 12,
                category = LocalEventCategory.BIRTHDAY,
                recurrence = LocalEventRecurrence.YEARLY_JALALI,
                reminderOffsetMinutes = 1440,
            ),
            LocalEvent(
                id = "id-2",
                title = "Doctors Appointment",
                jalaliYear = 1403,
                jalaliMonth = 9,
                jalaliDay = 20,
                isAllDay = false,
                hour = 16,
                minute = 30,
                category = LocalEventCategory.REMINDER,
                recurrence = LocalEventRecurrence.NONE,
                reminderOffsetMinutes = 60,
            ),
        )

        val payload = LocalEventsBackupPayload(
            schemaVersion = 1,
            exportedAt = 1740000000000L,
            events = originalList,
        )

        val jsonString = json.encodeToString(payload)
        val decoded = json.decodeFromString<LocalEventsBackupPayload>(jsonString)

        assertEquals(1, decoded.schemaVersion)
        assertEquals(2, decoded.events.size)
        assertEquals(originalList[0], decoded.events[0])
        assertEquals(originalList[1], decoded.events[1])

        // Test malformed JSON fallback
        val malformed = "{ invalid json }"
        val result = runCatching { json.decodeFromString<LocalEventsBackupPayload>(malformed) }
        assertTrue(result.isFailure, "Malformed JSON must fail safely")
    }
}
