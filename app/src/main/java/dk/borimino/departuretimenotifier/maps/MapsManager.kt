package dk.borimino.departuretimenotifier.maps

import android.content.Context
import android.util.Log
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.errors.NotFoundException
import com.google.maps.errors.ZeroResultsException
import com.google.maps.model.LatLng
import com.google.maps.model.TravelMode
import dk.borimino.departuretimenotifier.LOG_TAG
import dk.borimino.departuretimenotifier.R
import dk.borimino.departuretimenotifier.domain.Directions
import dk.borimino.departuretimenotifier.domain.Event
import dk.borimino.departuretimenotifier.domain.Location
import dk.borimino.departuretimenotifier.domain.ModePreferences
import java.util.concurrent.TimeUnit

object MapsManager {
    lateinit var geoApiContext: GeoApiContext

    fun setup(context: Context) {
        geoApiContext = GeoApiContext.Builder().apiKey(context.getString(R.string.maps_api_key)).build()
    }
    fun getPreferedDistance(location: Location, event: Event, modePreferences: ModePreferences): Directions? {
        val directionsDriving = if (modePreferences.drivingTime == 0L) {
            null
        } else {
            try {
                val resultDriving = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.DRIVING)
                    .origin(LatLng(location.latitude, location.longitude))
                    .destination(event.location)
                    .await()
                Log.d(LOG_TAG, "Found ${resultDriving.routes.size} driving routes")
                val routeDriving = resultDriving.routes[0]
                val legsDriving = routeDriving.legs
                val directionsDriving = Directions(
                    TravelMode.DRIVING,
                    legsDriving.sumOf { it.duration.inSeconds },
                    null, null
                )
                Log.d(
                    LOG_TAG,
                    "Duration: ${TimeUnit.SECONDS.toMinutes(directionsDriving.totalDurationInSeconds)}"
                )
                directionsDriving
            } catch (e: NotFoundException) {
                null
            } catch (e: ZeroResultsException) {
                null
            }
        }

        val directionsWalking = if (modePreferences.walkingTime == 0L) {
            null
        } else {
            try {
                val resultWalking = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.WALKING)
                    .origin(LatLng(location.latitude, location.longitude))
                    .destination(event.location)
                    .await()
                Log.d(LOG_TAG, "Found ${resultWalking.routes.size} walking routes")
                val routeWalking = resultWalking.routes[0]
                val legsWalking = routeWalking.legs
                val directionsWalking = Directions(
                    TravelMode.WALKING,
                    legsWalking.sumOf { it.duration.inSeconds },
                    null, null
                )
                Log.d(
                    LOG_TAG,
                    "Duration: ${TimeUnit.SECONDS.toMinutes(directionsWalking.totalDurationInSeconds)}"
                )
                directionsWalking
            } catch (e: NotFoundException) {
                null
            } catch (e: ZeroResultsException) {
                null
            }
        }

        val directionsBicycling = if (modePreferences.bicyclingTime == 0L) {
            null
        } else {
            try {
                val resultBicycling = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.BICYCLING)
                    .origin(LatLng(location.latitude, location.longitude))
                    .destination(event.location)
                    .await()
                Log.d(LOG_TAG, "Found ${resultBicycling.routes.size} bicycling routes")
                val routeBicycling = resultBicycling.routes[0]
                val legsBicycling = routeBicycling.legs
                val directionsBicycling = Directions(
                    TravelMode.BICYCLING,
                    legsBicycling.sumOf { it.duration.inSeconds },
                    null, null
                )
                Log.d(
                    LOG_TAG,
                    "Duration: ${TimeUnit.SECONDS.toMinutes(directionsBicycling.totalDurationInSeconds)}"
                )
                directionsBicycling
            } catch (e: NotFoundException) {
                null
            } catch (e: ZeroResultsException) {
                null
            }
        }

        val directionsTransit = if (modePreferences.transitTime == 0L) {
            null
        } else {
            try {
                val resultTransit = DirectionsApi.newRequest(geoApiContext)
                    .mode(TravelMode.TRANSIT)
                    .origin(LatLng(location.latitude, location.longitude))
                    .destination(event.location)
                    .arrivalTime(event.time)
                    .await()
                if (resultTransit.routes.none {route ->
                        route.legs.any {leg ->
                            leg.steps.any {step ->
                                step.travelMode == TravelMode.TRANSIT
                            }
                        }
                    }) {
                    Log.d(LOG_TAG, "Found 0 transit routes")
                    null
                } else {
                    Log.d(LOG_TAG, "Found ${resultTransit.routes.size} transit routes")
                    Log.d(LOG_TAG, resultTransit.routes[0].legs.size.toString())
                    Log.d(LOG_TAG, resultTransit.routes[0].legs[0].toString())
                    Log.d(LOG_TAG, resultTransit.routes[0].legs[0].steps[0].toString())
                    Log.d(LOG_TAG, resultTransit.routes[0].legs[0].departureTime.toString())
                    val routeTransit = resultTransit.routes[0]
                    val legsTransit = routeTransit.legs
                    val directionsTransit = Directions(
                        TravelMode.TRANSIT,
                        legsTransit.sumOf { it.duration.inSeconds },
                        legsTransit[0].departureTime.toInstant(),
                        legsTransit[legsTransit.size - 1].arrivalTime.toInstant()
                    )
                    Log.d(
                        LOG_TAG,
                        "Duration: ${TimeUnit.SECONDS.toMinutes(directionsTransit.totalDurationInSeconds)}"
                    )
                    directionsTransit
                }
            } catch (e: NotFoundException) {
                null
            } catch (e: ZeroResultsException) {
                null
            }
        }

        val allDirections: List<Pair<Long, Directions?>> = listOf(Pair(modePreferences.bicyclingTime, directionsBicycling), Pair(modePreferences.drivingTime, directionsDriving), Pair(modePreferences.transitTime, directionsTransit), Pair(modePreferences.walkingTime, directionsWalking))

        Log.d(LOG_TAG, allDirections.sortedBy { it.first }.filter {it.second != null}.toString())

        return allDirections.sortedBy { it.first }.filter { it.second != null }
            .firstOrNull() { it.first >= it.second!!.totalDurationInSeconds }?.second
    }
}