package dk.borimino.departuretimenotifier

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dk.borimino.departuretimenotifier.maps.LocationManager
import dk.borimino.departuretimenotifier.maps.MapsManager
import dk.borimino.departuretimenotifier.ui.theme.DepartureTimeNotifierTheme
import dk.borimino.departuretimenotifier.workers.MainWorker
import dk.borimino.departuretimenotifier.workers.NotificationWorker
import java.util.Date
import java.util.concurrent.TimeUnit
import android.app.ActivityManager
import android.app.Notification
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.foundation.layout.Column
import androidx.core.app.NotificationCompat
import dk.borimino.departuretimenotifier.workers.MemoryHolder


const val CHANNEL_ID = "DEPARTURE_TIME_NOTIFICATION_CHANNEL_ID"
const val CHANNEL_ID_PERSISTENT = "DEPARTURE_TIME_NOTIFICATION_PERSISTENT_CHANNEL_ID"
const val PERMISSION_REQUEST_CODE = 1729
const val LOG_TAG = "DEP_TIME_NOTF"

class MainActivity : ComponentActivity() {

    lateinit var memoryHolder: MemoryHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        checkPermission()
        //Log.d(LOG_TAG, "Checked permissions")
        startService(Intent(this, MemoryHolder::class.java))
        bindService(Intent(this, MemoryHolder::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                memoryHolder = (service as MemoryHolder.LocalBinder).getService()
                setContent {
                    DepartureTimeNotifierTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column {
                                Greeting("Markus")
                                Log(logLines = memoryHolder.getLogLines())
                            }
                        }
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }, Context.BIND_AUTO_CREATE)
        //Log.d(LOG_TAG, "Created MemoryHolder")
        startService(Intent(this, LocationManager::class.java))
        bindService(Intent(this, LocationManager::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            }
            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }, Context.BIND_AUTO_CREATE)
        //Log.d(LOG_TAG, "Set LocationManager")
        MapsManager.setup(this)
        //Log.d(LOG_TAG, "Setup MapsManager")
        setContent {
            DepartureTimeNotifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Markus")
                }
            }
        }
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SCHEDULE_EXACT_ALARM
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.INTERNET
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.VIBRATE
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.SCHEDULE_EXACT_ALARM, Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CALENDAR, Manifest.permission.INTERNET, Manifest.permission.VIBRATE, Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION), PERMISSION_REQUEST_CODE)
                //Log.d(LOG_TAG, "Requested permission")
            }
            return
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(LOG_TAG, "Received permissions" + grantResults.contentToString())

    }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = descriptionText
            enableVibration(true)
            setShowBadge(false)
        }
        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        //Log.d(LOG_TAG, "Created NotificationChannel")

        val channelPersistent = NotificationChannel(CHANNEL_ID_PERSISTENT, "Background Service", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channelPersistent)
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Composable
fun Log(logLines: List<String>) {
    Text(
        text = "Log:"
    )
    logLines.forEach {
        Text(
            text = it,
            modifier = Modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DepartureTimeNotifierTheme {
        Greeting("Android")
    }
}