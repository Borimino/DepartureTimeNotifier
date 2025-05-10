package dk.borimino.departuretimenotifier.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.R
import dk.borimino.departuretimenotifier.domain.Directions
import dk.borimino.departuretimenotifier.domain.Event
import dk.borimino.departuretimenotifier.domain.Location
import dk.borimino.departuretimenotifier.domain.ModePreferences
import dk.borimino.departuretimenotifier.events.EventQueryer
import dk.borimino.departuretimenotifier.maps.MapsManager
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Every 5 minutes it checks the current location against all future events (in the next X hours) for the following:
 *      * If the location and event has already been saved, do nothing
 *      * If it hasn't been saved
 *          * Check the distance by all wanted (non-0) modes of travel, and save the route with the shortest distance
 *          * Set an alarm with a NotificationWorker for Y minutes before departure time
 *          * If a previous location for the same event exists, remove that alarm from the AlarmManager
 */
class MainWorker : BroadcastReceiver() {

    private lateinit var alarmManager: AlarmManager

    override fun onReceive(context: Context?, intent: Intent?) {
        val memoryHolder = (peekService(
            context,
            Intent(context, MemoryHolder::class.java)
        ) as MemoryHolder.LocalBinder?)?.getService()
        if (memoryHolder == null) {
            Log.e(LOG_TAG, "No MemoryHolder service is running")
            return
        }
        Log.d(LOG_TAG, "Gotten MemoryHolder with ${memoryHolder.alarmIds.size} entries")
        try {
            if (context == null) {
                Log.e(LOG_TAG, "No context in MainWorker")
                return
            }
            Log.d(LOG_TAG, "Received in MainWorker")
            alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
            val modePreferences =
                intent!!.getSerializableExtra("ModePreferences", ModePreferences::class.java)
                    ?: ModePreferences(
                        0,
                        TimeUnit.MINUTES.toSeconds(30),
                        0,
                        TimeUnit.HOURS.toSeconds(2)
                    )
            Log.d(LOG_TAG, "Prepared objects")
            // Get list of future events
            val events = EventQueryer.getEventsFromUntil(
                Instant.now(),
                Instant.now()
                    .plusSeconds(
                        maxOf(
                            modePreferences.bicyclingTime,
                            modePreferences.drivingTime,
                            modePreferences.transitTime,
                            modePreferences.walkingTime
                        )
                    )
                    .plusSeconds(
                        TimeUnit.MINUTES.toSeconds(
                            context.resources.getInteger(R.integer.forewarning_minutes).toLong()
                        )
                    )
                    .plusSeconds(
                        TimeUnit.MINUTES.toSeconds(
                            context.resources.getInteger(R.integer.scan_interval_minutes).toLong()
                        )
                    ),
                context.contentResolver
            )
            Log.d(LOG_TAG, "Gotten list of events. There are ${events.size}")
            val location = Location(
                intent.getDoubleExtra("longitude", 0.0),
                intent.getDoubleExtra("latitude", 0.0)
            )
            val preciseLocation = Location(
                intent.getDoubleExtra("precise_longitude", 0.0),
                intent.getDoubleExtra("precise_latitude", 0.0)
            )

            // For each event
            events.filter { event ->
                memoryHolder.notifiedEvents.none { event.title == it.first && event.time == it.second } //Don't notify about events that have already had a notification shown
            }.forEach { event ->
                thread {
                    try {
                        if (memoryHolder.notifiedEvents.contains(Pair(event.title, event.time))) {
                            memoryHolder.addLogLine("Already notified event " + event.title)
                            return@thread
                        }
                        val sharedPreferences = context.getSharedPreferences(
                            "dk.borimino.departuretimenotifier_preferences",
                            Context.MODE_PRIVATE
                        )
                        val locationSearchRegexes =
                            (sharedPreferences.getString("locationSearchRegex", "") ?: "").split(";").map { string ->
                                Regex(string)
                            }
                        val locationReplacees =
                            (sharedPreferences.getString("locationReplace", "") ?: "").split(";")
                        Log.d(LOG_TAG, locationSearchRegexes.toString())
                        Log.d(LOG_TAG, locationReplacees.toString())
                        locationSearchRegexes.forEachIndexed { index, value ->
                            if (locationReplacees.size > index) {
                                event.location =
                                    event.location.replace(value, locationReplacees[index])
                            } else {
                                event.location =
                                    event.location.replace(value, "")
                            }
                        }
                        if (event.location.trim() == "") {
                            return@thread
                        }
                        // If already saved
                        if (memoryHolder.alarmIds.containsKey(Pair(location, event))) {
                            Log.d(LOG_TAG, "Alarm already found")
                            return@thread
                        }
                        val directions =
                            try {
                                MapsManager.getPreferedDistance(preciseLocation, event, modePreferences)
                            } catch (e: UninitializedPropertyAccessException) {
                                MapsManager.setup(context)
                                MapsManager.getPreferedDistance(preciseLocation, event, modePreferences)
                            }
                        if (directions == null) {
                            Log.d(LOG_TAG, "No directions found")
                            memoryHolder.addLogLine("No directions found from $location to ${event.location}")
                            memoryHolder.alarmIds[Pair(location, event)] = null
                            return@thread
                        } else {
                            Log.d(LOG_TAG, "Directions are of type ${directions.mode}")
                        }
                        memoryHolder.alarmIds.keys.filter { pair -> pair.second == event }
                            .forEach { key -> removeAlarmForEvent(memoryHolder.alarmIds[key]) }
                        memoryHolder.addLogLine("Currently at $preciseLocation near $location")
                        val alarmId = setAlarmForEvent(directions, event, context, memoryHolder)
                        memoryHolder.alarmIds[Pair(location, event)] = alarmId
                    } catch (e: Exception) {
                        memoryHolder.addLogLine((e.stackTraceToString()))
                    }
                }
            }
            memoryHolder.lastScanTime = Instant.now()
        } catch (e: Exception) {
            memoryHolder.addLogLine(e.stackTraceToString())
        }
    }

    private fun setAlarmForEvent(directions: Directions?, event: Event, context: Context, memoryHolder: MemoryHolder): PendingIntent {
        val alarmTime: Long = if (directions == null) {
            Log.d(LOG_TAG, "Event without directions")
            event.time.toEpochMilli() - TimeUnit.MINUTES.toMillis(context.resources.getInteger(R.integer.forewarning_minutes).toLong())
        } else if (directions.departureTime != null) {
            Log.d(LOG_TAG, "Event with departure time")
            directions.departureTime.toEpochMilli() - TimeUnit.MINUTES.toMillis(context.resources.getInteger(R.integer.forewarning_minutes).toLong())
        } else {
            Log.d(LOG_TAG, "Event without departure time")
            event.time.toEpochMilli() - TimeUnit.SECONDS.toMillis(directions.totalDurationInSeconds) - TimeUnit.MINUTES.toMillis(context.resources.getInteger(R.integer.forewarning_minutes).toLong())
        }
        Log.d(LOG_TAG, "alarmTime: $alarmTime")

        val intent = Intent(context, NotificationWorker::class.java)
        intent.putExtra("eventName", event.title)
        intent.putExtra("eventTime", event.time.toEpochMilli())
        intent.putExtra("departureTime", alarmTime + TimeUnit.MINUTES.toMillis(context.resources.getInteger(R.integer.forewarning_minutes).toLong()))
        if (directions == null) {
            intent.putExtra("mode", "walking")
        } else {
            intent.putExtra("mode", directions.mode.toString())
        }
        intent.putExtra("destination", event.location)
        val pendingIntent = PendingIntent.getBroadcast(context, Random.nextInt(), intent, PendingIntent.FLAG_IMMUTABLE)
        try {
            alarmManager.setExact(
                AlarmManager.RTC,
                alarmTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            if (e.message != null) {
                memoryHolder.addLogLine(e.message!!)
            }
            alarmManager.set(
                AlarmManager.RTC,
                alarmTime,
                pendingIntent
            )
        }
        Log.d(LOG_TAG, "Alarm set for event")
        memoryHolder.addLogLine("Alarm set for event at ${Instant.ofEpochMilli(alarmTime)}")
        memoryHolder.addLogLine("Alarm set for event at $alarmTime")
        return pendingIntent
    }

    private fun removeAlarmForEvent(alarmId: PendingIntent?) {
        if (alarmId != null) {
            alarmManager.cancel(alarmId)
        }
    }
}