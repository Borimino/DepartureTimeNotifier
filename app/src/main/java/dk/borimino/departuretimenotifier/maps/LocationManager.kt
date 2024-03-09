package dk.borimino.departuretimenotifier.maps

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dk.borimino.departuretimenotifier.CHANNEL_ID_PERSISTENT
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.R
import dk.borimino.departuretimenotifier.domain.Location
import dk.borimino.departuretimenotifier.domain.ModePreferences
import dk.borimino.departuretimenotifier.workers.MainWorker
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

class LocationManager: Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(TimeUnit.MINUTES.toMillis(resources.getInteger(R.integer.scan_interval_minutes).toLong())).apply {  }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    val currentLocation = Location(
                        it.longitude.toBigDecimal().setScale(3, RoundingMode.HALF_UP).toDouble(),
                        it.latitude.toBigDecimal().setScale(3, RoundingMode.HALF_UP).toDouble())
                    val currentPreciseLocation = Location(
                        it.longitude,
                        it.latitude)
                    Log.d(LOG_TAG, "Location information refreshed")

                    val modePreferences = createModePreferences();

                    val newIntent = Intent(this@LocationManager.applicationContext, MainWorker::class.java)
                    newIntent.putExtra("latitude", currentLocation.latitude)
                    newIntent.putExtra("longitude", currentLocation.longitude)
                    newIntent.putExtra("precise_latitude", currentPreciseLocation.latitude)
                    newIntent.putExtra("precise_longitude", currentPreciseLocation.longitude)
                    newIntent.putExtra("ModePreferences", modePreferences);
                    sendBroadcast(newIntent)
                    Log.d(LOG_TAG, "MainWorker called")
                } ?: {
                    Log.d(LOG_TAG, "Location information isn't available")
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(LOG_TAG, "Permissions denied")
            return START_STICKY
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d(LOG_TAG, "LocationManager started")


        val notification = NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setOngoing(true)
            .setSmallIcon(androidx.core.R.drawable.notification_bg)
            .setContentTitle("Collecting position")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationManager.IMPORTANCE_NONE)
            .setGroup("LocationManagerPersistent")
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    private fun createModePreferences(): ModePreferences {
        return ModePreferences(
            resources.getInteger(R.integer.driving_time_minutes).toLong(),
            resources.getInteger(R.integer.walking_time_minutes).toLong(),
            resources.getInteger(R.integer.bicycling_time_minutes).toLong(),
            resources.getInteger(R.integer.transit_time_minutes).toLong()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "LocationManager destroyed")
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LocationManager = this@LocationManager
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}