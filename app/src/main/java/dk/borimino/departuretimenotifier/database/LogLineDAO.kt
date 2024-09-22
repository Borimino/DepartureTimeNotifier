package dk.borimino.departuretimenotifier.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogLineDAO {
    @Query("SELECT * FROM logline")
    fun getAll(): List<LogLine>

    @Insert
    fun insert(vararg logLine: LogLine)

    @Delete
    fun delete(logLine: LogLine)

    @Query("DELETE FROM logline")
    fun deleteAll()
}