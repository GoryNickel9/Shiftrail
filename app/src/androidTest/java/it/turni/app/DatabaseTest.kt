package it.turni.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test
    fun roomRoundTripAndUpsertDoesNotDuplicate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveProfile(ProfileEntity("employee", "Luca Baldeddu", "BALDEDDU"))
            assertEquals("BALDEDDU", dao.profile()?.aliases)
            dao.saveWeek(WeekEntity("week", "employee", "2026-10-05", 1, "hash"))
            val shift =
                ShiftEntity(
                    "shift",
                    "week",
                    "employee",
                    "2026-10-05",
                    "19:00",
                    "01:00",
                    true,
                    null,
                    "WORK_SHIFT",
                    0.95,
                    123,
                    1,
                    "sig",
                    1,
                    1,
                )
            dao.saveShift(shift)
            dao.saveShift(shift.copy(startTime = "18:30", updatedAt = 2))
            assertEquals(1, dao.shifts("week").size)
            assertEquals("18:30", dao.shifts("week").single().startTime)
            assertEquals("week", dao.byHash("employee", "hash")?.id)
        } finally {
            db.close()
        }
    }
}
