package it.turni.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(@PrimaryKey val id: String, val name: String, val aliases: String)

@Entity(tableName = "weeks", indices = [Index(value = ["employeeId", "weekStart"], unique = true)])
data class WeekEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val weekStart: String,
    val importedAt: Long,
    val sourceHash: String,
)

@Entity(
    tableName = "shifts",
    foreignKeys =
        [
            ForeignKey(
                entity = WeekEntity::class,
                parentColumns = ["id"],
                childColumns = ["weekId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("weekId"), Index(value = ["employeeId", "date", "calendarId"], unique = true)],
)
data class ShiftEntity(
    @PrimaryKey val id: String,
    val weekId: String,
    val employeeId: String,
    val date: String,
    val startTime: String?,
    val endTime: String?,
    val endNextDay: Boolean,
    val code: String?,
    val type: String,
    val confidence: Double,
    val calendarEventId: Long,
    val calendarId: Long,
    val contentSignature: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun saved() = SavedEvent(LocalDate.parse(date), contentSignature, calendarEventId, calendarId)
}

@Entity(tableName = "code_mappings")
data class MappingEntity(
    @PrimaryKey val code: String,
    val meaning: String,
    val behavior: String,
    val startTime: String?,
    val endTime: String?,
) {
    fun domain() =
        CodeMapping(
            code,
            meaning,
            CalendarBehavior.valueOf(behavior),
            startTime?.let(LocalTime::parse),
            endTime?.let(LocalTime::parse),
        )
}

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val calendarId: Long? = null,
    val title: String = EventFactory.WORK_EVENT_TITLE,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = 30,
    val eventColor: Int? = 0xff1a73e8.toInt(),
    val developer: Boolean = false,
    @ColumnInfo(defaultValue = "-16023485") val restColor: Int? = 0xff0b8043.toInt(),
    @ColumnInfo(defaultValue = "1") val saveRestDays: Boolean = true,
    @ColumnInfo(defaultValue = "0") val notificationsEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "30") val notificationMinutes: Int = 30,
    @ColumnInfo(defaultValue = "1") val notificationSound: Boolean = true,
    @ColumnInfo(defaultValue = "1") val notificationVibration: Boolean = true,
)

@Entity(
    tableName = "extra_work",
    indices = [Index(value = ["employeeId", "date", "startTime", "endTime"], unique = true)],
)
data class ExtraWorkEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val createdAt: Long,
) {
    fun period() =
        WorkPeriod(
            id,
            LocalDate.parse(date),
            LocalTime.parse(startTime),
            LocalTime.parse(endTime),
            WorkSource.EXTRA,
        )
}

@Dao
interface TurniDao {
    @Query("SELECT * FROM profiles LIMIT 1") suspend fun profile(): ProfileEntity?

    @Upsert suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM settings WHERE id = 1") suspend fun settings(): SettingsEntity?

    @Upsert suspend fun saveSettings(settings: SettingsEntity)

    @Query("SELECT * FROM settings WHERE id = 1") fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM code_mappings ORDER BY code") suspend fun mappings(): List<MappingEntity>

    @Upsert suspend fun saveMapping(mapping: MappingEntity)

    @Query("DELETE FROM code_mappings WHERE code = :code") suspend fun removeMapping(code: String)

    @Query("SELECT * FROM weeks WHERE employeeId = :employee ORDER BY weekStart DESC")
    fun weeks(employee: String): Flow<List<WeekEntity>>

    @Query("SELECT * FROM weeks WHERE employeeId = :employee AND sourceHash = :hash LIMIT 1")
    suspend fun byHash(employee: String, hash: String): WeekEntity?

    @Query("SELECT * FROM shifts WHERE weekId = :week ORDER BY date")
    suspend fun shifts(week: String): List<ShiftEntity>

    @Query(
        "SELECT * FROM shifts WHERE employeeId = :employee AND date BETWEEN :fromDate AND :untilDate AND calendarId = :calendar"
    )
    suspend fun previous(
        employee: String,
        fromDate: String,
        untilDate: String,
        calendar: Long,
    ): List<ShiftEntity>

    @Upsert suspend fun saveWeek(week: WeekEntity)

    @Query("SELECT * FROM weeks WHERE employeeId = :employee AND weekStart = :monday LIMIT 1")
    suspend fun weekFor(employee: String, monday: String): WeekEntity?

    @Upsert suspend fun saveShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE id = :id AND employeeId = :employee")
    suspend fun shift(id: String, employee: String): ShiftEntity?

    @Query("DELETE FROM shifts WHERE id = :id") suspend fun removeShift(id: String)

    @Query(
        "SELECT * FROM shifts WHERE employeeId = :employee AND date BETWEEN :fromDate AND :untilDate ORDER BY date"
    )
    fun recordedShifts(
        employee: String,
        fromDate: String,
        untilDate: String,
    ): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE employeeId = :employee ORDER BY date")
    fun allRecordedShifts(employee: String): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM extra_work WHERE employeeId = :employee ORDER BY date, startTime, id")
    fun allExtraWork(employee: String): Flow<List<ExtraWorkEntity>>

    @Query(
        "SELECT * FROM extra_work WHERE employeeId = :employee AND date BETWEEN :fromDate AND :untilDate ORDER BY date, startTime"
    )
    fun extraWork(
        employee: String,
        fromDate: String,
        untilDate: String,
    ): Flow<List<ExtraWorkEntity>>

    @Query(
        "SELECT * FROM extra_work WHERE employeeId = :employee AND date = :date AND startTime = :start AND endTime = :end LIMIT 1"
    )
    suspend fun findExtra(
        employee: String,
        date: String,
        start: String,
        end: String,
    ): ExtraWorkEntity?

    @Upsert suspend fun saveExtra(extra: ExtraWorkEntity)

    @Query("DELETE FROM extra_work WHERE id = :id AND employeeId = :employee")
    suspend fun removeExtra(id: String, employee: String)
}

@Database(
    entities =
        [
            ProfileEntity::class,
            WeekEntity::class,
            ShiftEntity::class,
            MappingEntity::class,
            SettingsEntity::class,
            ExtraWorkEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class TurniDatabase : RoomDatabase() {
    abstract fun dao(): TurniDao

    companion object {
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN notificationMinutes INTEGER NOT NULL DEFAULT 30"
                    )
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN notificationSound INTEGER NOT NULL DEFAULT 1"
                    )
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN notificationVibration INTEGER NOT NULL DEFAULT 1"
                    )
                }
            }

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN restColor INTEGER DEFAULT -16023485"
                    )
                    db.execSQL(
                        "ALTER TABLE settings ADD COLUMN saveRestDays INTEGER NOT NULL DEFAULT 1"
                    )
                    db.execSQL(
                        "UPDATE settings SET title = 'Lavoro', reminderMinutes = COALESCE(reminderMinutes, 30), eventColor = COALESCE(eventColor, -15043608)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS extra_work (id TEXT NOT NULL, employeeId TEXT NOT NULL, date TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_extra_work_employeeId_date_startTime_endTime ON extra_work (employeeId, date, startTime, endTime)"
                    )
                }
            }
    }
}
