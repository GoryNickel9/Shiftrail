package it.turni.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable

class CalendarFixture(account: String, displayName: String = "PC Sync") : Closeable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val uri =
        CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
    val id: Long

    init {
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
        }
        id =
            ContentUris.parseId(
                requireNotNull(
                    resolver.insert(
                        uri,
                        ContentValues().apply {
                            put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                            put(
                                CalendarContract.Calendars.ACCOUNT_TYPE,
                                CalendarContract.ACCOUNT_TYPE_LOCAL,
                            )
                            put(CalendarContract.Calendars.NAME, "PC Sync")
                            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
                            put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                            put(
                                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                                CalendarContract.Calendars.CAL_ACCESS_OWNER,
                            )
                            put(CalendarContract.Calendars.VISIBLE, 1)
                            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "Europe/Rome")
                            put(CalendarContract.Calendars.ALLOWED_REMINDERS, "1")
                        },
                    )
                )
            )
    }

    override fun close() {
        resolver.delete(uri, "${CalendarContract.Calendars._ID} = ?", arrayOf(id.toString()))
    }
}
