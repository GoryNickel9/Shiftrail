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
    fun extraOnlyAndMidnightWorkSupportZeroAndSevenDayNotice() {
        val extra = listOf(period("extra", "00:00", "06:00", WorkSource.EXTRA))
        assertEquals(
            workReminders(extra, 0, zone).single().start,
            workReminders(extra, 0, zone).single().trigger,
        )
        assertEquals(
            10080 * 60_000L,
            workReminders(extra, 10080, zone).single().let { it.start - it.trigger },
        )
        assertTrue(workReminders(emptyList(), 30, zone).isEmpty())
        for (invalid in listOf(-1, 10081, Int.MAX_VALUE)) assertThrows(
            IllegalArgumentException::class.java
        ) {
            workReminders(extra, invalid, zone)
        }
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
