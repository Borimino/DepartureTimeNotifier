package dk.borimino.departuretimenotifier.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LogLine::class], version = 1)
abstract class LogLineDatabase : RoomDatabase() {
    abstract fun logLineDAO(): LogLineDAO
}