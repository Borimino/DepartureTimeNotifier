package dk.borimino.departuretimenotifier.domain

import java.io.Serializable

data class ModePreferences(val drivingTime: Long, val walkingTime: Long, val bicyclingTime: Long, val transitTime: Long): Serializable