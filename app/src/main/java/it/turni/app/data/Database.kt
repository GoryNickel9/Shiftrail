package it.turni.app.data

import androidx.room.*
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
    val title: String = "Turno di lavoro",
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = null,
    val eventColor: Int? = null,
    val developer: Boolean = false,
)

@Dao
interface TurniDao {
    @Query("SELECT * FROM profiles LIMIT 1") suspend fun profile(): ProfileEntity?

    @Upsert suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM settings WHERE id = 1") suspend fun settings(): SettingsEntity?

    @Upsert suspend fun saveSettings(settings: SettingsEntity)

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
        "SELECT * FROM shifts WHERE employeeId = :employee AND date >= :fromDate ORDER BY date LIMIT 14"
    )
    fun upcoming(employee: String, fromDate: String): Flow<List<ShiftEntity>>

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

    @Upsert suspend fun saveShift(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE id = :id") suspend fun removeShift(id: String)
}

@Database(
    entities =
        [
            ProfileEntity::class,
            WeekEntity::class,
            ShiftEntity::class,
            MappingEntity::class,
            SettingsEntity::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class TurniDatabase : RoomDatabase() {
    abstract fun dao(): TurniDao
}
