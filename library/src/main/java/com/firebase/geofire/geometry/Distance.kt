package com.firebase.geofire.geometry

import com.firebase.geofire.EARTH_E2
import com.firebase.geofire.EARTH_EQ_RADIUS
import com.firebase.geofire.EPSILON
import com.firebase.geofire.METERS_PER_DEGREE_LATITUDE
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface Distance {
    val value: Double
}

@JvmInline value class Meters(override val value: Double) : Distance
@JvmInline value class Kilometers(override val value: Double) : Distance

val Double.kilometers: Kilometers get() = Kilometers(this)
val Double.meters: Meters get() = Meters(this)

fun Distance.inMeters(): Meters = when (this) {
    is Kilometers -> (value * METERS_IN_KILOMETER).meters
    is Meters -> this
}

fun Distance.inKilometers(): Kilometers = when (this) {
    is Kilometers -> this
    is Meters -> (value / METERS_IN_KILOMETER).kilometers
}

fun Distance.toLatitudeDegrees(): Double = inMeters().value / METERS_PER_DEGREE_LATITUDE

fun Distance.toLongitudeDegrees(latitude: Latitude): Double {
    val radians = Math.toRadians(latitude.value)
    val numerator = cos(radians) * EARTH_EQ_RADIUS * Math.PI / 180
    val denominator = 1 / sqrt(1 - EARTH_E2 * sin(radians) * sin(radians))
    val deltaDegrees = numerator * denominator
    val distanceInMeters = inMeters().value
    val result = distanceInMeters
        .takeIf { deltaDegrees < EPSILON }
        ?: (distanceInMeters / deltaDegrees)
    return result.coerceAtMost(360.0)
}

private const val METERS_IN_KILOMETER = 1000.0