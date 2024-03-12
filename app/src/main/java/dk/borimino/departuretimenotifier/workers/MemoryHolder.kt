package dk.borimino.departuretimenotifier.workers

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.domain.Event
import dk.borimino.departuretimenotifier.domain.Location
import java.time.Instant

class MemoryHolder: Service() {
    private val MAX_LOG_LINES = 100

    val alarmIds : MutableMap<Pair<Location, Event>, PendingIntent> = mutableMapOf()

    val notifiedEvents : MutableList<Pair<String, Instant>> = mutableListOf() //TODO: Clear this up once event has been held

    private val logLines : MutableList<String> = mutableListOf()

    fun addLogLine(logLine: String) {
        logLines.add(logLine)
        while (logLines.size >= MAX_LOG_LINES) {
            logLines.removeAt(0)
        }
    }

    fun getLogLines() : List<String> {
        return logLines
    }

    fun clearEvent(eventName: String, eventTime: Instant) {
        alarmIds.filter { it.key.second.title == eventName && it.key.second.time == eventTime }.forEach {
            alarmIds.remove(it.key)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "MemoryHolder started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "MemoryHolder destroyed")
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MemoryHolder = this@MemoryHolder
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}