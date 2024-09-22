package dk.borimino.departuretimenotifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import dk.borimino.departuretimenotifier.maps.LocationManager
import dk.borimino.departuretimenotifier.maps.MapsManager
import dk.borimino.departuretimenotifier.ui.theme.DepartureTimeNotifierTheme
import android.app.Notification
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.unit.sp
import dk.borimino.departuretimenotifier.workers.MemoryHolder
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.textFieldPreference
import java.time.Instant
import java.time.temporal.ChronoUnit


const val CHANNEL_ID = "DEPARTURE_TIME_NOTIFICATION_CHANNEL_ID"
const val CHANNEL_ID_PERSISTENT = "DEPARTURE_TIME_NOTIFICATION_PERSISTENT_CHANNEL_ID"
const val PERMISSION_REQUEST_CODE = 1729
const val LOG_TAG = "DEP_TIME_NOTF"

class MainActivity : ComponentActivity() {

    lateinit var memoryHolder: MemoryHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_TAG, this.localClassName)
        createNotificationChannel()
        checkPermission()
        //Log.d(LOG_TAG, "Checked permissions")
        MapsManager.setup(this)
        Log.d(LOG_TAG, "Setup MapsManager")
        startService(Intent(this, MemoryHolder::class.java))
        bindService(Intent(this, MemoryHolder::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                memoryHolder = (service as MemoryHolder.LocalBinder).getService()
                setContent {
                    MainScreen(memoryHolder = memoryHolder)
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
        setContent {
            DepartureTimeNotifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting(Instant.now())
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


    @Composable
    fun MainScreen(memoryHolder: MemoryHolder) {
        DepartureTimeNotifierTheme {
            // A surface container using the 'background' color from the theme
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Greeting(memoryHolder.lastScanTime)
                    Log(memoryHolder.getLogLines()) {
                        memoryHolder.clearLogLines()
                        setContent { MainScreen(memoryHolder = memoryHolder) }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(lastScanTime: Instant?, modifier: Modifier = Modifier) {
    Text(
            text = "Last scan time was ${lastScanTime?.truncatedTo(ChronoUnit.MILLIS)}!",
            modifier = modifier
    )
    ProvidePreferenceLocals {
        LazyColumn() {
            textFieldPreference(
                key = "drivingTime",
                defaultValue = 0,
                title = { Text(text = "Driving time in minutes")},
                textToValue = String::toLong
            )
            textFieldPreference(
                key = "walkingTime",
                defaultValue = 30,
                title = { Text(text = "Walking time in minutes")},
                textToValue = String::toLong
            )
            textFieldPreference(
                key = "bicyclingTime",
                defaultValue = 0,
                title = { Text(text = "Bicycling time in minutes")},
                textToValue = String::toLong
            )
            textFieldPreference(
                key = "transitTime",
                defaultValue = 120,
                title = { Text(text = "Transit time in minutes")},
                textToValue = String::toLong
            )
            textFieldPreference(
                key = "locationSearchRegex",
                defaultValue = "",
                title = { Text(text = "Locations to be replaced (regex). Separated by ;")},
                textToValue = String::toString
            )
            textFieldPreference(
                key = "locationReplace",
                defaultValue = "",
                title = { Text(text = "Locations to replace with. Separated by ;")},
                textToValue = String::toString
            )
        }
    }
}

@Composable
fun Log(logLines: List<String>, clearLog: () -> Unit) {
    Text(
        text = "Log:"
    )
    Column (
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        logLines.forEach {
            Text(
                text = it,
                modifier = Modifier,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
        Button(onClick = { clearLog() }) {
            Text(text = "Clear log")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DepartureTimeNotifierTheme {
        Greeting(Instant.now())
    }
}