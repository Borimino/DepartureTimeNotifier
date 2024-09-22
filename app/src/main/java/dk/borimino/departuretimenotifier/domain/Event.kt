package dk.borimino.departuretimenotifier.domain

import java.time.Instant

data class Event(
    val title: String,
    val time: Instant,
    var location: String
    ) {
}