package it.turni.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WorkReminderTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 10, 27)

    private fun period(
        id: String,
        start: String,
        end: String,
        source: WorkSource = WorkSource.SCHEDULE,
    ) = WorkPeriod(id, day, LocalTime.parse(start), LocalTime.parse(end), source)

    @Test
    fun oneDailyReminderIncludesExtraEnvelopeAndOvernightEnd() {
        val periods =
            listOf(
                period("work", "19:00", "01:00"),
                period("extra", "17:00", "20:00", WorkSource.EXTRA),
            )
        val reminder = workReminders(periods, 90, zone).single()
        assertEquals(day.atTime(17, 0).atZone(zone).toInstant().toEpochMilli(), reminder.start)
        assertEquals(
            day.plusDays(1).atTime(1, 0).atZone(zone).toInstant().toEpochMilli(),
            reminder.end,
        )
        assertEquals(reminder.start - 90 * 60_000L, reminder.trigger)
        assertEquals(listOf(reminder), workReminders(periods.reversed(), 90, zone))
    }

    @Test
    fun extraOnlyAndMidnightWorkSupportZeroAndTwentyFourHourNotice() {
        val extra = listOf(period("extra", "00:00", "06:00", WorkSource.EXTRA))
        assertEquals(
            workReminders(extra, 0, zone).single().start,
            workReminders(extra, 0, zone).single().trigger,
        )
        assertEquals(
            1440 * 60_000L,
            workReminders(extra, 1440, zone).single().let { it.start - it.trigger },
        )
        assertTrue(workReminders(emptyList(), 30, zone).isEmpty())
        for (invalid in listOf(-1, 1, 4, 6, 1441, 1445, Int.MAX_VALUE)) assertThrows(
            IllegalArgumentException::class.java
        ) {
            workReminders(extra, invalid, zone)
        }
    }

    @Test
    fun legacyNotificationMinutesAreBoundedAndRoundedDown() {
        for ((before, after) in
            listOf(-1 to 0, 7 to 5, 59 to 55, 1441 to 1440, 10080 to 1440, Int.MAX_VALUE to 1440)) {
            assertEquals(after, normalizeNotificationMinutes(before))
            assertTrue(validNotificationMinutes(after))
        }
        for (minutes in 0..1440 step 5) assertTrue(validNotificationMinutes(minutes))
    }

    @Test
    fun daylightSavingNoticeUsesElapsedMinutesAndEditsChangeIdentity() {
        val work = period("work", "06:00", "10:00").copy(date = LocalDate.of(2026, 10, 25))
        val reminder = workReminders(listOf(work), 300, zone).single()
        assertEquals(300 * 60_000L, reminder.start - reminder.trigger)
        assertEquals(
            LocalTime.of(2, 0),
            Instant.ofEpochMilli(reminder.trigger).atZone(zone).toLocalTime(),
        )
        assertNotEquals(
            reminder.key,
            workReminders(listOf(work.copy(start = LocalTime.of(7, 0))), 300, zone).single().key,
        )
        assertNotEquals(reminder.key, workReminders(listOf(work), 60, zone).single().key)
    }
}
