package dk.borimino.departuretimenotifier.workers

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dk.borimino.departuretimenotifier.CHANNEL_ID
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.R
import java.time.Instant
import kotlin.random.Random

/**
 * Sends a notification with a link to Google Maps with instructions when it is time to depart for an event
 */
class NotificationWorker : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(LOG_TAG, "Received broadcast")
        val memoryHolder = (peekService(
            context,
            Intent(context, MemoryHolder::class.java)
        ) as MemoryHolder.LocalBinder?)?.getService()
        if (memoryHolder == null) {
            Log.e(LOG_TAG, "No MemoryHolder service is running")
            return
        }
        Log.d(LOG_TAG, "Gotten MemoryHolder with ${memoryHolder.alarmIds.size} entries")

        val eventName = intent!!.getStringExtra("eventName")
        val eventTime = Instant.ofEpochMilli(intent.getLongExtra("eventTime", 0))
        val mode = intent.getStringExtra("mode")
        val destination = intent.getStringExtra("destination")
        val mapsMode = when (mode) {
            "bicycling" -> "b"
            "driving" -> "d"
            "walking" -> "w"
            "transit" -> "t"
            else -> "d"
        }

        val actionIntent =
            if (mode == "transit") {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=$destination")
                )
            } else {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=$destination&mode=$mapsMode")
                )
            }
        actionIntent.setPackage("com.google.android.apps.maps")
        val builder = NotificationCompat.Builder(context!!, CHANNEL_ID)
            .setSmallIcon(R.drawable.event_icon_11)
            .setContentTitle(eventName)
            .setContentText("Start $mode in ${context.resources.getInteger(R.integer.forewarning_minutes)} minutes. Click here for Google Maps instructions")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(PendingIntent.getActivity(context, Random.nextInt(), actionIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(LOG_TAG, "Permission not granted")
                return
            }
            notify(Random.nextInt(), builder.build())
            Log.d(LOG_TAG, "Sent notification")
        }

        memoryHolder.clearEvent(eventName!!, eventTime)
        memoryHolder.notifiedEvents.add(Pair(eventName, eventTime))
    }
}