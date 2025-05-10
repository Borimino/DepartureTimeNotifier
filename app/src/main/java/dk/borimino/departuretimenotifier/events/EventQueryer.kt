package dk.borimino.departuretimenotifier.events

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import android.util.Log
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.domain.Event
import java.time.Instant

object EventQueryer {
    private val EVENT_PROJECTION = arrayOf(
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.TITLE
    )

    private const val PROJECTION_BEGIN_INDEX = 0
    private const val PROJECTION_LOCATION_INDEX = 1
    private const val PROJECTION_TITLE_INDEX = 2

    fun getEventsFromUntil(from: Instant, to: Instant, contentResolver: ContentResolver): List<Event> {
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, from.toEpochMilli())
        ContentUris.appendId(uriBuilder, to.toEpochMilli())
        Log.d(LOG_TAG, "Getting events from $from to $to")
        val selection = "(${CalendarContract.Instances.EVENT_LOCATION} != '')"
        val cursor = contentResolver.query(uriBuilder.build(), EVENT_PROJECTION, selection, null, CalendarContract.Instances.BEGIN + " ASC")
            ?: return emptyList()

        Log.d(LOG_TAG, "Gotten cursor with ${cursor.count} elements")
        val result = mutableListOf<Event>()
        while (cursor.moveToNext()) {
            Log.d(LOG_TAG, "Handling event")
            // get values
            val begin = cursor.getString(PROJECTION_BEGIN_INDEX)
            val start = Instant.ofEpochMilli(begin.toLong())
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