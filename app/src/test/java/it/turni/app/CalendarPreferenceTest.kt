package it.turni.app

import it.turni.app.calendar.WritableCalendar
import it.turni.app.calendar.preferredCalendar
import org.junit.Assert.*
import org.junit.Test

class CalendarPreferenceTest {
    @Test
    fun savedDestinationIsReusedOnlyWhileUsableWithoutChoosingAnotherAccount() {
        val other = WritableCalendar(10, "Turni", "other")
        val saved = WritableCalendar(11, "Turni", "saved")
        assertEquals(saved, preferredCalendar(listOf(other, saved), 11))
        for (invalid in
            listOf(
                saved.copy(visible = false),
                saved.copy(syncEnabled = false),
                saved.copy(writable = false),
            )) {
            assertNull(preferredCalendar(listOf(other, invalid), 11))
        }
        assertNull(preferredCalendar(listOf(other), 11))
        assertNull(preferredCalendar(listOf(other, saved), null))
    }
}
