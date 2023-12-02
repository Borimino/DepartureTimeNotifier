package dk.borimino.departuretimenotifier

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class MyWorker : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(LOG_TAG, "Received broadcast")
        var builder = NotificationCompat.Builder(context!!, CHANNEL_ID)
            .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
            .setContentTitle("TEST TITLE")
            .setContentText("This is some test content")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
    }
}