package dk.borimino.departuretimenotifier.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LogLine (
    @PrimaryKey val pid: Long,
    @ColumnInfo(name = "line") val line: String,
    @ColumnInfo(name = "time") val time: String
)