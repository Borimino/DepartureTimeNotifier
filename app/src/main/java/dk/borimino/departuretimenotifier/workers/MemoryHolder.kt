package dk.borimino.departuretimenotifier.workers

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.room.Room
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.database.LogLine
import dk.borimino.departuretimenotifier.database.LogLineDAO
import dk.borimino.departuretimenotifier.database.LogLineDatabase
import dk.borimino.departuretimenotifier.domain.Event
import dk.borimino.departuretimenotifier.domain.Location
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class MemoryHolder: Service() {
    val alarmIds : MutableMap<Pair<Location, Event>, PendingIntent?> = mutableMapOf()

    val notifiedEvents : MutableList<Pair<String, Instant>> = mutableListOf() //TODO: Clear this up once event has been held

    var lastScanTime : Instant? = null

    lateinit var db : LogLineDatabase
    lateinit var logLineDao : LogLineDAO


    fun addLogLine(logLine: String) {
        logLineDao.insert(LogLine(UUID.randomUUID().leastSignificantBits, logLine, Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()))
    }

    fun getLogLines() : List<String> {
        return logLineDao.getAll().sortedBy { logLine: LogLine -> logLine.time }.map { logLine: LogLine -> logLine.time + ": " + logLine.line }
    }

    fun clearEvent(eventName: String, eventTime: Instant) {
        alarmIds.filter { it.key.second.title == eventName && it.key.second.time == eventTime }.forEach {
            alarmIds.remove(it.key)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        db = Room.databaseBuilder(
            this,
            LogLineDatabase::class.java, "logline"
        ).allowMainThreadQueries().build()
        logLineDao = db.logLineDAO()
        Log.d(LOG_TAG, "MemoryHolder started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "MemoryHolder destroyed")
        addLogLine("MemoryHolder destroyed")
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MemoryHolder = this@MemoryHolder
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun clearLogLines() {
        logLineDao.deleteAll()
    }
}