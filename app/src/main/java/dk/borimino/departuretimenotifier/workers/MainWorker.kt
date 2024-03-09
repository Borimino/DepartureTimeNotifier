package dk.borimino.departuretimenotifier.workers

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.R
import dk.borimino.departuretimenotifier.domain.Directions
import dk.borimino.departuretimenotifier.domain.Event
import dk.borimino.departuretimenotifier.domain.Location
import dk.borimino.departuretimenotifier.domain.ModePreferences
import dk.borimino.departuretimenotifier.events.EventQueryer
import dk.borimino.departuretimenotifier.maps.LocationManager
import dk.borimino.departuretimenotifier.maps.MapsManager
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.util.Date
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
        try {
            if (context == null) {
                Log.e(LOG_TAG, "No context in MainWorker")
                return
            }
            Log.d(LOG_TAG, "Received in MainWorker")
            alarmManager = context.getSystemService(ComponentActivity.ALARM_SERVICE) as AlarmManager
            val modePreferences = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent!!.getSerializableExtra("ModePreferences", ModePreferences::class.java)
                    ?: ModePreferences(
                        0,
                        TimeUnit.MINUTES.toSeconds(30),
                        0,
                        TimeUnit.HOURS.toSeconds(2)
                    )
            } else {
                (intent!!.getSerializableExtra("ModePreferences") as ModePreferences?)
                    ?: ModePreferences(
                        0,
                        TimeUnit.MINUTES.toSeconds(30),
                        0,
                        TimeUnit.HOURS.toSeconds(2)
                    )
            }
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
            val memoryHolder = (peekService(
                context,
                Intent(context, MemoryHolder::class.java)
            ) as MemoryHolder.LocalBinder?)?.getService()
            if (memoryHolder == null) {
                Log.e(LOG_TAG, "No MemoryHolder service is running")
                return
            }
            Log.d(LOG_TAG, "Gotten MemoryHolder with ${memoryHolder.alarmIds.size} entries")

            // For each event
            events.filter { event ->
                memoryHolder.notifiedEvents.none { event.title == it.first && event.time == it.second } //Don't notify about events that have already had a notification shown
            }.forEach { event ->
                thread {
                    // If already saved
                    if (memoryHolder.alarmIds.containsKey(Pair(location, event))) {
                        Log.d(LOG_TAG, "Alarm already found")
                        return@thread
                    }
                    val directions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        MapsManager.getPreferedDistance(preciseLocation, event, modePreferences)
                    } else {
                        MapsManager.getPreferedDistance(preciseLocation, event, modePreferences)
                    }
                    if (directions == null) {
                        Log.d(LOG_TAG, "No directions found")
                    } else {
                        Log.d(LOG_TAG, "Directions are of type ${directions.mode}")
                    }
                    memoryHolder.alarmIds.keys.filter { pair -> pair.second == event }
                        .forEach { key -> removeAlarmForEvent(memoryHolder.alarmIds[key]) }
                    val alarmId = setAlarmForEvent(directions, event, context)
                    memoryHolder.alarmIds[Pair(location, event)] = alarmId
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setAlarmForEvent(directions: Directions?, event: Event, context: Context): PendingIntent {
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
        if (directions == null) {
            intent.putExtra("mode", "walking")
        } else {
            intent.putExtra("mode", directions.mode.toString())
        }
        intent.putExtra("destination", event.location)
        val pendingIntent = PendingIntent.getBroadcast(context, Random.nextInt(), intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.set(AlarmManager.RTC_WAKEUP,
            alarmTime,
            pendingIntent)
        Log.d(LOG_TAG, "Alarm set for event")
        return pendingIntent
    }

    private fun removeAlarmForEvent(alarmId: PendingIntent?) {
        if (alarmId != null) {
            alarmManager.cancel(alarmId)
        }
    }
}