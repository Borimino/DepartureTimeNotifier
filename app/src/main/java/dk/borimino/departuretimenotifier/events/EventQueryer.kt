package dk.borimino.departuretimenotifier.events

import android.content.ContentResolver
import android.provider.CalendarContract
import android.util.Log
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.domain.Event
import java.time.Instant

object EventQueryer {
    private val EVENT_PROJECTION = arrayOf(
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.TITLE
    )

    private const val PROJECTION_DTSTART_INDEX = 0
    private const val PROJECTION_LOCATION_INDEX = 1
    private const val PROJECTION_TITLE_INDEX = 2

    fun getEventsFromUntil(from: Instant, to: Instant, contentResolver: ContentResolver): List<Event> {
        val uri = CalendarContract.Events.CONTENT_URI
        val selection = "((${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.EVENT_LOCATION} != ''))"
        val selectionArgs = arrayOf(from.toEpochMilli().toString(), to.toEpochMilli().toString())
        Log.d(LOG_TAG, "Prepared parameters for event-gathering: ${selectionArgs.map { it }}")
        val cursor = contentResolver.query(uri, EVENT_PROJECTION, selection, selectionArgs, CalendarContract.Events.DTSTART + " ASC")
            ?: return emptyList()

        Log.d(LOG_TAG, "Gotten cursor with ${cursor.count} elements")
        val result = mutableListOf<Event>()
        while (cursor.moveToNext()) {
            Log.d(LOG_TAG, "Handling event")
            // get values
            val dtStart = cursor.getString(PROJECTION_DTSTART_INDEX)
            val start = Instant.ofEpochMilli(dtStart.toLong())
            val eventLocation = cursor.getString(PROJECTION_LOCATION_INDEX)
            val title = cursor.getString(PROJECTION_TITLE_INDEX)

            // write values to Event
            val event = Event(title, start, eventLocation)

            // put Event in list
            result.add(event)
        }
        cursor.close()
        return result
    }
}