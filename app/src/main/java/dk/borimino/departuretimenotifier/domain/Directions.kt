package dk.borimino.departuretimenotifier.domain

import com.google.maps.model.TravelMode
import java.time.Instant

data class Directions(val mode: TravelMode, val totalDurationInSeconds: Long, val departureTime: Instant?, val arrivalTime: Instant?)
