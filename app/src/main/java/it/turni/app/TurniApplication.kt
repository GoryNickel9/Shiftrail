package it.turni.app

import android.app.Application
import androidx.room.Room
import it.turni.app.calendar.CalendarRepository
import it.turni.app.calendar.SyncRepository
import it.turni.app.data.TurniDatabase
import it.turni.app.update.UpdateRepository

class TurniApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, TurniDatabase::class.java, "turni.db")
            .addMigrations(TurniDatabase.MIGRATION_1_2)
            .build()
    }
    val calendar by lazy { CalendarRepository(contentResolver) }
    val sync by lazy { SyncRepository(database, calendar) }
    val updater by lazy { UpdateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Captures are private, temporary and never backed up. Clear leftovers after process death.
        cacheDir.listFiles()?.filter { it.name.startsWith("turni-") }?.forEach { it.delete() }
    }
}
